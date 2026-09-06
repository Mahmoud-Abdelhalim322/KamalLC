package com.kamal.lc.auth.service;

import com.kamal.lc.auth.dto.CaptainAllocationDtos.*;
import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.entity.UserCaptainAssignment;
import com.kamal.lc.auth.repository.UserCaptainAssignmentRepository;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CaptainAllocationService {

    private final UserCaptainAssignmentRepository assignmentRepo;
    private final UserRepository userRepo;
    private final CaptainRepository captainRepo;

    /**
     * Determines which captain IDs are accessible by the given user.
     * @return List of UUIDs if restricted (ASSIGNED_ONLY), or null if unrestricted (ALL_CAPTAINS / ADMIN).
     */
    @Transactional(readOnly = true)
    public List<UUID> getAccessibleCaptainIds(AppUser user) {
        if (user == null || user.isAdmin() || "ALL_CAPTAINS".equalsIgnoreCase(user.getDataScope()) || user.getDataScope() == null) {
            return null; // Null means unrestricted (All Captains)
        }
        return assignmentRepo.findCaptainIdsByUserId(user.getId());
    }

    /**
     * Fetches accessible captain IDs for the currently logged in user.
     * @return List of UUIDs if restricted, or null if unrestricted.
     */
    @Transactional(readOnly = true)
    public List<UUID> getCurrentUserAccessibleCaptainIds() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        Optional<AppUser> userOpt = userRepo.findByUsername(username);
        return userOpt.map(this::getAccessibleCaptainIds).orElse(null);
    }

    /**
     * Get summary of all non-platform users and their captain allocation state within the current tenant.
     */
    @Transactional(readOnly = true)
    public List<UserAllocationSummaryResponse> getAllUserAllocations() {
        UUID tenantId = TenantContext.getCurrentTenantId();
        List<AppUser> users = tenantId != null ? userRepo.findByTenantId(tenantId) : userRepo.findAll();

        return users.stream()
                .filter(u -> !"PLATFORM_ADMIN".equalsIgnoreCase(u.getRole()))
                .map(u -> {
                    long count = assignmentRepo.countByUserId(u.getId());
                    String scope = (u.getDataScope() == null) ? "ALL_CAPTAINS" : u.getDataScope();
                    return UserAllocationSummaryResponse.builder()
                            .userId(u.getId())
                            .username(u.getUsername())
                            .displayName(u.getDisplayName() != null ? u.getDisplayName() : u.getUsername())
                            .role(u.getRole())
                            .dataScope(scope)
                            .assignedCaptainCount(count)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Get detailed allocation info for a specific user.
     */
    @Transactional(readOnly = true)
    public UserAllocationDetailResponse getUserAllocationDetail(UUID userId) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("المستخدم غير موجود"));

        UUID tenantId = TenantContext.getCurrentTenantId();
        List<Captain> allTenantCaptains = tenantId != null ? captainRepo.findByTenantId(tenantId) : captainRepo.findAll();
        List<UserCaptainAssignment> userAssignments = assignmentRepo.findByUserId(userId);
        Set<UUID> assignedIds = userAssignments.stream()
                .map(a -> a.getCaptain().getId())
                .collect(Collectors.toSet());

        List<CaptainSimpleDto> assignedCaptains = new ArrayList<>();
        List<CaptainSimpleDto> availableCaptains = new ArrayList<>();

        for (Captain c : allTenantCaptains) {
            CaptainSimpleDto dto = CaptainSimpleDto.builder()
                    .id(c.getId())
                    .fullNameAr(c.getFullNameAr())
                    .kamalCode(c.getKamalCode())
                    .phone(c.getPhone())
                    .status(c.getStatus() != null ? c.getStatus() : "ACTIVE")
                    .build();

            if (assignedIds.contains(c.getId())) {
                assignedCaptains.add(dto);
            } else {
                availableCaptains.add(dto);
            }
        }

        String scope = (user.getDataScope() == null) ? "ALL_CAPTAINS" : user.getDataScope();

        return UserAllocationDetailResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getUsername())
                .role(user.getRole())
                .dataScope(scope)
                .assignedCaptains(assignedCaptains)
                .availableCaptains(availableCaptains)
                .build();
    }

    /**
     * Update allocation and data scope for a specific user.
     */
    @Transactional
    public UserAllocationDetailResponse updateUserAllocation(UUID userId, UpdateUserAllocationRequest request, String performerUsername) {
        AppUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("المستخدم غير موجود"));

        if (user.isAdmin() || "ADMIN".equalsIgnoreCase(user.getRole()) || "SUPER_ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("لا يمكن تعديل نطاق بيانات مدير الشركة الأساسي");
        }

        AppUser performer = userRepo.findByUsername(performerUsername).orElse(null);

        String newScope = "ASSIGNED_ONLY".equalsIgnoreCase(request.getDataScope()) ? "ASSIGNED_ONLY" : "ALL_CAPTAINS";
        user.setDataScope(newScope);
        userRepo.save(user);

        // Delete existing assignments for this user
        assignmentRepo.deleteByUserId(userId);

        // If assigned only, add the selected captains
        if ("ASSIGNED_ONLY".equals(newScope) && request.getCaptainIds() != null && !request.getCaptainIds().isEmpty()) {
            List<Captain> captains = captainRepo.findAllById(request.getCaptainIds());
            List<UserCaptainAssignment> assignments = new ArrayList<>();
            for (Captain c : captains) {
                UserCaptainAssignment assignment = UserCaptainAssignment.builder()
                        .tenant(user.getTenant())
                        .user(user)
                        .captain(c)
                        .assignedBy(performer)
                        .build();
                assignments.add(assignment);
            }
            assignmentRepo.saveAll(assignments);
        }

        return getUserAllocationDetail(userId);
    }
}
