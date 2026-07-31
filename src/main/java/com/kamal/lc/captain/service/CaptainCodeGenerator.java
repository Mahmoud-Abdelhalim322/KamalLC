package com.kamal.lc.captain.service;

import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the next unused kamal_code for a captain.
 *
 * <p>The prefix encodes the captain type, matching the heuristic already used when importing
 * Excel rows ({@code kamalCode.startsWith("KSCFL")} means FREELANCE). Because the employee
 * prefix is a prefix of the freelance one, employee codes must explicitly exclude freelance
 * codes when scanning for the highest sequence number.
 */
@Component
@RequiredArgsConstructor
public class CaptainCodeGenerator {

    public static final String TYPE_EMPLOYEE = "EMPLOYEE";
    public static final String TYPE_FREELANCE = "FREELANCE";

    private static final int SEQUENCE_WIDTH = 4;
    private static final int MAX_ATTEMPTS = 50;

    private final CaptainRepository captainRepo;

    @Value("${app.captain-code.employee-prefix:KSC}")
    private String employeePrefix;

    @Value("${app.captain-code.freelance-prefix:KSCFL}")
    private String freelancePrefix;

    /** Returns the prefix used for the given captain type. */
    public String prefixFor(String captainType) {
        if (TYPE_FREELANCE.equalsIgnoreCase(captainType)) {
            return freelancePrefix;
        }
        if (TYPE_EMPLOYEE.equalsIgnoreCase(captainType)) {
            return employeePrefix;
        }
        throw new BusinessException("INVALID_TYPE", "نوع الكابتن غير صالح");
    }

    /**
     * Builds the next free code for the given type. The result is checked against the database,
     * but callers must still rely on the unique constraint on {@code kamal_code} for the final word.
     */
    public String generate(String captainType) {
        String prefix = prefixFor(captainType);
        boolean isEmployee = prefix.equals(employeePrefix) && !prefix.equals(freelancePrefix);

        List<String> existing = captainRepo.findCodesStartingWith(prefix);
        long highest = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d+)$");

        for (String code : existing) {
            // "KSC" is a prefix of "KSCFL" — skip freelance codes when numbering employees.
            if (isEmployee && code.toUpperCase().startsWith(freelancePrefix.toUpperCase())) {
                continue;
            }
            Matcher matcher = pattern.matcher(code);
            if (matcher.matches()) {
                try {
                    highest = Math.max(highest, Long.parseLong(matcher.group(1)));
                } catch (NumberFormatException ignored) {
                    // Code has a numeric part too large to be a real sequence — ignore it.
                }
            }
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String candidate = prefix + String.format("%0" + SEQUENCE_WIDTH + "d", highest + attempt);
            if (!captainRepo.existsByKamalCode(candidate)) {
                return candidate;
            }
        }

        throw new BusinessException("CODE_GENERATION_FAILED", "تعذّر توليد كود كمال جديد، حاول مرة أخرى");
    }
}
