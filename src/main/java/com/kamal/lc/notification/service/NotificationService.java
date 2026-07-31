package com.kamal.lc.notification.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.expiry.DocumentAlert;
import com.kamal.lc.captain.expiry.DocumentAlertEvaluator;
import com.kamal.lc.captain.expiry.DocumentKind;
import com.kamal.lc.captain.expiry.DocumentStatus;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.notification.dto.NotificationItemDto;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.expiry.StaffDocumentAlert;
import com.kamal.lc.staff.expiry.StaffDocumentAlertEvaluator;
import com.kamal.lc.staff.expiry.StaffDocumentKind;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final CaptainRepository captainRepo;
    private final DocumentAlertEvaluator captainAlertEvaluator;
    private final StaffRepository staffRepo;
    private final StaffDocumentAlertEvaluator staffAlertEvaluator;

    public List<NotificationItemDto> getActiveNotifications() {
        LocalDate today = LocalDate.now();
        List<NotificationItemDto> items = new ArrayList<>();

        // 1. Captain Notifications
        Map<DocumentKind, Integer> captainAlertDays = captainAlertEvaluator.alertDays(today);
        List<Captain> activeCaptains = captainRepo.findByStatus("ACTIVE");
        for (Captain c : activeCaptains) {
            List<DocumentAlert> alerts = captainAlertEvaluator.evaluate(c, today, captainAlertDays);
            for (DocumentAlert alert : alerts) {
                if (alert.isAlert()) {
                    items.add(createCaptainNotification(c, alert));
                }
            }
        }

        // 2. Staff Notifications (Workers & Managers)
        Map<StaffDocumentKind, Integer> staffAlertDays = staffAlertEvaluator.alertDays(today);
        List<Staff> activeStaff = staffRepo.findByStatus("ACTIVE");
        for (Staff s : activeStaff) {
            List<StaffDocumentAlert> alerts = staffAlertEvaluator.evaluate(s, today, staffAlertDays);
            for (StaffDocumentAlert alert : alerts) {
                if (alert.isAlert()) {
                    items.add(createStaffNotification(s, alert));
                }
            }
        }

        // Sort by severity (EXPIRED high -> EXPIRING medium -> MISSING low) then days left
        items.sort((a, b) -> {
            int sevA = severityOrder(a.getSeverity());
            int sevB = severityOrder(b.getSeverity());
            if (sevA != sevB) return Integer.compare(sevA, sevB);
            if (a.getDaysLeft() != null && b.getDaysLeft() != null) {
                return Long.compare(a.getDaysLeft(), b.getDaysLeft());
            }
            return 0;
        });

        return items;
    }

    public List<NotificationItemDto> getActiveNotifications(int page, int limit) {
        List<NotificationItemDto> all = getActiveNotifications();
        if (limit <= 0) return all;
        int fromIndex = page * limit;
        if (fromIndex >= all.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + limit, all.size());
        return all.subList(fromIndex, toIndex);
    }

    public long getActiveNotificationCount() {
        return getActiveNotifications().size();
    }

    private NotificationItemDto createCaptainNotification(Captain c, DocumentAlert alert) {
        String statusLabel = alert.status().getLabelAr();
        String docLabel = alert.kind().getLabelAr();
        String message = buildMessage(c.getFullNameAr(), "الكابتن", docLabel, alert.status(), alert.daysLeft());

        return NotificationItemDto.builder()
                .id("CAPTAIN-" + c.getId() + "-" + alert.kind().name())
                .memberId(c.getId())
                .memberCode(c.getKamalCode())
                .memberName(c.getFullNameAr())
                .memberType("CAPTAIN")
                .memberTypeLabel("كابتن")
                .targetUrl("/captains/" + c.getId())
                .documentKind(alert.kind().name())
                .documentLabelAr(docLabel)
                .expiryDate(alert.expiry() != null ? alert.expiry().toString() : null)
                .status(alert.status().name())
                .statusLabelAr(statusLabel)
                .daysLeft(alert.daysLeft())
                .message(message)
                .severity(toSeverity(alert.status()))
                .build();
    }

    private NotificationItemDto createStaffNotification(Staff s, StaffDocumentAlert alert) {
        boolean isManager = "MANAGER".equalsIgnoreCase(s.getStaffType());
        String typeLabel = isManager ? "الإداري" : "العامل";
        String targetBase = isManager ? "/managers/" : "/workers/";
        String docLabel = alert.kind().getLabelAr();
        String message = buildMessage(s.getFullNameAr(), typeLabel, docLabel, alert.status(), alert.daysLeft());

        return NotificationItemDto.builder()
                .id("STAFF-" + s.getId() + "-" + alert.kind().name())
                .memberId(s.getId())
                .memberCode(s.getStaffCode())
                .memberName(s.getFullNameAr())
                .memberType(s.getStaffType())
                .memberTypeLabel(typeLabel)
                .targetUrl(targetBase + s.getId())
                .documentKind(alert.kind().name())
                .documentLabelAr(docLabel)
                .expiryDate(alert.expiry() != null ? alert.expiry().toString() : null)
                .status(alert.status().name())
                .statusLabelAr(alert.status().getLabelAr())
                .daysLeft(alert.daysLeft())
                .message(message)
                .severity(toSeverity(alert.status()))
                .build();
    }

    private String buildMessage(String name, String roleLabel, String docLabel, DocumentStatus status, Long daysLeft) {
        if (status == DocumentStatus.EXPIRED) {
            long pastDays = daysLeft != null ? Math.abs(daysLeft) : 0;
            return pastDays > 0
                    ? docLabel + " لـ " + roleLabel + " " + name + " منتهية منذ " + pastDays + " يوم"
                    : docLabel + " لـ " + roleLabel + " " + name + " منتهية";
        } else if (status == DocumentStatus.EXPIRING) {
            long remaining = daysLeft != null ? daysLeft : 0;
            return remaining == 0
                    ? docLabel + " لـ " + roleLabel + " " + name + " تنتهي اليوم"
                    : docLabel + " لـ " + roleLabel + " " + name + " تنتهي خلال " + remaining + " يوم";
        } else {
            return docLabel + " لـ " + roleLabel + " " + name + " غير مسجلة بالملف";
        }
    }

    private String toSeverity(DocumentStatus status) {
        return switch (status) {
            case EXPIRED -> "HIGH";
            case EXPIRING -> "MEDIUM";
            case MISSING -> "LOW";
            default -> "INFO";
        };
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "HIGH" -> 1;
            case "MEDIUM" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }
}
