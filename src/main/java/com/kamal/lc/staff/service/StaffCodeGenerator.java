package com.kamal.lc.staff.service;

import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates auto-incrementing staff codes:
 * <ul>
 *   <li>Workers: KSE0001, KSE0002, …</li>
 *   <li>Managers: KSM0001, KSM0002, …</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StaffCodeGenerator {

    public static final String TYPE_WORKER  = "WORKER";
    public static final String TYPE_MANAGER = "MANAGER";

    private static final String WORKER_PREFIX  = "KSE";
    private static final String MANAGER_PREFIX = "KSM";
    private static final int SEQUENCE_WIDTH = 4;
    private static final int MAX_ATTEMPTS = 50;

    private final StaffRepository staffRepo;

    public String prefixFor(String staffType) {
        if (TYPE_WORKER.equalsIgnoreCase(staffType))  return WORKER_PREFIX;
        if (TYPE_MANAGER.equalsIgnoreCase(staffType)) return MANAGER_PREFIX;
        throw new BusinessException("INVALID_TYPE", "نوع الموظف غير صالح: " + staffType);
    }

    public String generate(String staffType) {
        String prefix = prefixFor(staffType);
        List<String> existing = staffRepo.findCodesStartingWith(prefix);

        long highest = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");

        for (String code : existing) {
            Matcher matcher = pattern.matcher(code);
            if (matcher.matches()) {
                try {
                    highest = Math.max(highest, Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException ignored) {}
            }
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + String.format("%0" + SEQUENCE_WIDTH + "d", highest + attempt);
            if (!staffRepo.existsByStaffCode(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException("CODE_GENERATION_FAILED", "تعذّر توليد كود جديد، حاول مرة أخرى");
    }
}
