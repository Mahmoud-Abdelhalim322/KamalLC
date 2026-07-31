package com.kamal.lc.inventory.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.custody.service.CustodyService;
import com.kamal.lc.inventory.dto.*;
import com.kamal.lc.inventory.entity.AssetCategory;
import com.kamal.lc.inventory.entity.AssetCategoryFieldTemplate;
import com.kamal.lc.inventory.entity.AssetItem;
import com.kamal.lc.inventory.entity.AssetItemField;
import com.kamal.lc.inventory.repository.AssetCategoryRepository;
import com.kamal.lc.inventory.repository.AssetItemRepository;
import com.kamal.lc.custody.repository.CustodyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Inventory service — CRUD for asset categories and items (SIM cards, phones,
 * and user-defined custom categories). Items can be assigned to / returned from captains,
 * which creates corresponding custody records.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final AssetCategoryRepository categoryRepo;
    private final AssetItemRepository itemRepo;
    private final CaptainRepository captainRepo;
    private final CustodyService custodyService;
    private final CustodyRepository custodyRepo;

    // ──────────────────────────── Categories ────────────────────────────

    @Transactional(readOnly = true)
    public List<AssetCategoryResponse> getAllCategories() {
        return categoryRepo.findAll().stream().map(this::toCategoryResponse).toList();
    }

    public AssetCategoryResponse createCategory(AssetCategoryRequest req) {
        String slug = req.getName() != null && !req.getName().isBlank()
                ? req.getName().toUpperCase().replace(" ", "_")
                : req.getLabelAr().toUpperCase().replace(" ", "_");

        if (categoryRepo.existsByName(slug)) {
            throw new BusinessException("DUPLICATE_CATEGORY", "فئة بهذا الاسم موجودة بالفعل");
        }

        AssetCategory cat = AssetCategory.builder()
                .name(slug)
                .labelAr(req.getLabelAr())
                .systemDefined(false)
                .build();

        if (req.getFieldTemplates() != null) {
            for (AssetCategoryRequest.FieldTemplateRequest ft : req.getFieldTemplates()) {
                AssetCategoryFieldTemplate template = AssetCategoryFieldTemplate.builder()
                        .category(cat)
                        .fieldKey(ft.getFieldKey())
                        .labelAr(ft.getLabelAr())
                        .fieldOrder(ft.getFieldOrder())
                        .required(ft.isRequired())
                        .build();
                cat.getFieldTemplates().add(template);
            }
        }

        return toCategoryResponse(categoryRepo.save(cat));
    }

    public void deleteCategory(UUID id) {
        AssetCategory cat = findCategoryOrThrow(id);
        if (cat.isSystemDefined()) {
            throw new BusinessException("SYSTEM_CATEGORY", "لا يمكن حذف فئة نظامية");
        }
        if (itemRepo.countByCategoryId(id) > 0) {
            throw new BusinessException("HAS_ITEMS", "لا يمكن حذف فئة تحتوي على عناصر");
        }
        categoryRepo.delete(cat);
    }

    // ──────────────────────────── Items ────────────────────────────

    @Transactional(readOnly = true)
    public Page<AssetItemResponse> listItems(UUID categoryId, String status, String search, Pageable pageable) {
        Page<AssetItem> page;
        if (search != null && !search.isBlank()) {
            page = itemRepo.searchInCategory(categoryId, search, pageable);
        } else if (status != null && !status.isBlank()) {
            page = itemRepo.findByCategoryIdAndStatus(categoryId, status, pageable);
        } else {
            page = itemRepo.findByCategoryId(categoryId, pageable);
        }
        return page.map(this::toItemResponse);
    }

    public AssetItemResponse createItem(AssetItemRequest req) {
        AssetCategory cat = findCategoryOrThrow(req.getCategoryId());
        String serial = generateSerialCode(cat.getName());

        AssetItem item = AssetItem.builder()
                .category(cat)
                .serialCode(serial)
                .status("AVAILABLE")
                .notes(req.getNotes())
                .build();

        if (req.getFields() != null) {
            for (Map.Entry<String, String> entry : req.getFields().entrySet()) {
                AssetItemField field = AssetItemField.builder()
                        .item(item)
                        .fieldKey(entry.getKey())
                        .fieldValue(entry.getValue())
                        .build();
                item.getFields().add(field);
            }
        }

        return toItemResponse(itemRepo.save(item));
    }

    public AssetItemResponse updateItem(UUID id, AssetItemRequest req) {
        AssetItem item = findItemOrThrow(id);

        if (req.getNotes() != null) item.setNotes(req.getNotes());

        if (req.getFields() != null) {
            item.getFields().clear();
            for (Map.Entry<String, String> entry : req.getFields().entrySet()) {
                AssetItemField field = AssetItemField.builder()
                        .item(item)
                        .fieldKey(entry.getKey())
                        .fieldValue(entry.getValue())
                        .build();
                item.getFields().add(field);
            }
        }

        return toItemResponse(itemRepo.save(item));
    }

    public AssetItemResponse updateStatus(UUID id, String status) {
        AssetItem item = findItemOrThrow(id);
        if ("ASSIGNED".equals(item.getStatus()) && !"ASSIGNED".equals(status)) {
            throw new BusinessException("ASSIGNED_ITEM", "لا يمكن تغيير حالة عنصر مُسند لكابتن — يجب استرجاعه أولاً");
        }
        item.setStatus(status);
        return toItemResponse(itemRepo.save(item));
    }

    // ──────────────────────────── Assign / Return ────────────────────────────

    public AssetItemResponse assignItem(UUID itemId, AssetAssignRequest req) {
        AssetItem item = findItemOrThrow(itemId);
        if (!"AVAILABLE".equals(item.getStatus())) {
            throw new BusinessException("NOT_AVAILABLE", "العنصر غير متاح للإسناد حالياً");
        }
        Captain captain = captainRepo.findById(req.getCaptainId())
                .orElseThrow(() -> new BusinessException("CAPTAIN_NOT_FOUND", "الكابتن غير موجود"));

        item.setStatus("ASSIGNED");
        itemRepo.save(item);

        String label = buildItemLabel(item);
        custodyService.recordAssignment(captain, item.getCategory().getName(),
                item.getId(), label, currentUser());

        return toItemResponse(item);
    }

    public AssetItemResponse returnItem(UUID itemId) {
        AssetItem item = findItemOrThrow(itemId);
        if (!"ASSIGNED".equals(item.getStatus())) {
            throw new BusinessException("NOT_ASSIGNED", "العنصر غير مُسند حالياً");
        }

        item.setStatus("AVAILABLE");
        itemRepo.save(item);

        custodyService.recordReturn(item.getId(), currentUser());

        return toItemResponse(item);
    }

    // ──────────────────────────── Internal helpers ────────────────────────────

    private String generateSerialCode(String categoryName) {
        String prefix = switch (categoryName) {
            case "SIM_CARD" -> "SIM-";
            case "PHONE" -> "PHN-";
            default -> categoryName.substring(0, Math.min(3, categoryName.length())).toUpperCase() + "-";
        };

        List<String> existing = itemRepo.findSerialCodesStartingWith(prefix);
        int next = 1;
        if (!existing.isEmpty()) {
            String last = existing.get(0);
            try {
                next = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                next = existing.size() + 1;
            }
        }
        return prefix + String.format("%04d", next);
    }

    private String buildItemLabel(AssetItem item) {
        StringBuilder sb = new StringBuilder(item.getCategory().getLabelAr());
        sb.append(" ").append(item.getSerialCode());
        // Add first field value for readability
        if (!item.getFields().isEmpty()) {
            String firstVal = item.getFields().stream()
                    .filter(f -> f.getFieldValue() != null && !f.getFieldValue().isBlank())
                    .map(AssetItemField::getFieldValue)
                    .findFirst().orElse(null);
            if (firstVal != null) sb.append(" (").append(firstVal).append(")");
        }
        return sb.toString();
    }

    private AssetCategory findCategoryOrThrow(UUID id) {
        return categoryRepo.findById(id)
                .orElseThrow(() -> new BusinessException("CATEGORY_NOT_FOUND", "الفئة غير موجودة"));
    }

    private AssetItem findItemOrThrow(UUID id) {
        return itemRepo.findById(id)
                .orElseThrow(() -> new BusinessException("ITEM_NOT_FOUND", "العنصر غير موجود"));
    }

    private String currentUser() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    // ──────────────────────────── Mappers ────────────────────────────

    private AssetCategoryResponse toCategoryResponse(AssetCategory cat) {
        return AssetCategoryResponse.builder()
                .id(cat.getId())
                .name(cat.getName())
                .labelAr(cat.getLabelAr())
                .systemDefined(cat.isSystemDefined())
                .itemCount(itemRepo.countByCategoryId(cat.getId()))
                .fieldTemplates(cat.getFieldTemplates().stream()
                        .map(ft -> AssetCategoryResponse.FieldTemplateResponse.builder()
                                .id(ft.getId())
                                .fieldKey(ft.getFieldKey())
                                .labelAr(ft.getLabelAr())
                                .fieldOrder(ft.getFieldOrder())
                                .required(ft.isRequired())
                                .build())
                        .toList())
                .build();
    }

    private AssetItemResponse toItemResponse(AssetItem item) {
        Map<String, String> fields = item.getFields().stream()
                .collect(Collectors.toMap(AssetItemField::getFieldKey, f -> f.getFieldValue() != null ? f.getFieldValue() : ""));

        var builder = AssetItemResponse.builder()
                .id(item.getId())
                .categoryId(item.getCategory().getId())
                .categoryName(item.getCategory().getName())
                .categoryLabelAr(item.getCategory().getLabelAr())
                .serialCode(item.getSerialCode())
                .status(item.getStatus())
                .notes(item.getNotes())
                .fields(fields);

        // Add current assignment info if assigned
        if ("ASSIGNED".equals(item.getStatus())) {
            custodyRepo.findByReferenceIdAndStatus(item.getId(), "ACTIVE")
                    .ifPresent(cr -> {
                        builder.currentCaptainName(cr.getCaptain().getFullNameAr());
                        builder.currentCaptainCode(cr.getCaptain().getKamalCode());
                        builder.currentCaptainId(cr.getCaptain().getId());
                        builder.assignedSince(cr.getAssignedAt().toString());
                    });
        }

        return builder.build();
    }
}
