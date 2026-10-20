package com.kamal.lc.captainapp.service;

import com.kamal.lc.captain.entity.Captain;
import com.kamal.lc.captain.repository.CaptainRepository;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.CaptainSession;
import com.kamal.lc.captainapp.dto.CaptainAppDtos.OtpRequestResponse;
import com.kamal.lc.captainapp.entity.CaptainOtp;
import com.kamal.lc.captainapp.repository.CaptainOtpRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.security.JwtTokenProvider;
import com.kamal.lc.common.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Captain sign-in by mobile number and a one-time code.
 *
 * <p>The number is matched on its last nine digits, so 0551234567, 966551234567 and
 * +966 55 123 4567 are the same captain however HR typed it into the file.
 */
@Service
@RequiredArgsConstructor
public class CaptainAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CaptainRepository captainRepo;
    private final CaptainOtpRepository otpRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpSender otpSender;
    private final CaptainAppService appService;

    @Value("${app.captain-app.token-expiration-ms}")
    private long tokenTtlMs;
    @Value("${app.captain-app.otp.length:4}")
    private int codeLength;
    @Value("${app.captain-app.otp.ttl-seconds:300}")
    private long codeTtlSeconds;
    @Value("${app.captain-app.otp.resend-seconds:60}")
    private long resendSeconds;
    @Value("${app.captain-app.otp.max-attempts:5}")
    private int maxAttempts;
    @Value("${app.captain-app.otp.max-per-hour:5}")
    private int maxPerHour;
    @Value("${app.captain-app.otp.dev-echo:false}")
    private boolean devEcho;

    @Transactional
    public OtpRequestResponse requestCode(String phone) {
        String phoneKey = phoneKey(phone);
        Captain captain = findCaptain(phoneKey);
        OffsetDateTime now = OffsetDateTime.now();

        otpRepo.findFirstByPhoneKeyOrderByCreatedAtDesc(phoneKey).ifPresent(last -> {
            long wait = resendSeconds - Duration.between(last.getCreatedAt(), now).toSeconds();
            if (wait > 0) {
                throw new BusinessException("OTP_TOO_SOON", "يرجى الانتظار " + wait + " ثانية قبل إعادة الإرسال");
            }
        });
        if (otpRepo.countByPhoneKeyAndCreatedAtAfter(phoneKey, now.minusHours(1)) >= maxPerHour) {
            throw new BusinessException("RATE_LIMITED", "تم تجاوز عدد المحاولات المسموحة. حاول لاحقاً");
        }

        String code = randomCode();
        otpRepo.save(CaptainOtp.builder()
                .captainId(captain.getId())
                .phoneKey(phoneKey)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusSeconds(codeTtlSeconds))
                .build());
        otpSender.send("0" + phoneKey, code);

        return new OtpRequestResponse(maskPhone(phoneKey), codeLength, codeTtlSeconds, resendSeconds,
                devEcho ? code : null);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public CaptainSession verifyCode(String phone, String code) {
        String phoneKey = phoneKey(phone);
        OffsetDateTime now = OffsetDateTime.now();
        CaptainOtp otp = otpRepo.findFirstByPhoneKeyOrderByCreatedAtDesc(phoneKey)
                .filter(o -> o.isUsable(now, maxAttempts))
                .orElseThrow(() -> new BusinessException("OTP_EXPIRED", "انتهت صلاحية رمز التحقق. اطلب رمزاً جديداً"));

        otp.setAttempts(otp.getAttempts() + 1);
        if (!passwordEncoder.matches(code.trim(), otp.getCodeHash())) {
            int left = maxAttempts - otp.getAttempts();
            throw new BusinessException("OTP_INVALID", left > 0
                    ? "رمز التحقق غير صحيح. المحاولات المتبقية: " + left
                    : "رمز التحقق غير صحيح. اطلب رمزاً جديداً");
        }
        otp.setConsumedAt(now);

        Captain captain = captainRepo.findById(otp.getCaptainId())
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .orElseThrow(() -> new BusinessException("CAPTAIN_INACTIVE", "حسابك غير نشط. تواصل مع مشرفك"));
        UUID tenantId = captain.getTenant().getId();
        if (!captainRepo.isActiveForApp(captain.getId())) {
            throw new BusinessException("CAPTAIN_INACTIVE", "حسابك غير نشط. تواصل مع مشرفك");
        }

        String token = jwtTokenProvider.generateCaptainToken(captain.getId(), tenantId, tokenTtlMs);
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return new CaptainSession(token, tokenTtlMs, appService.profileOf(captain));
        } finally {
            TenantContext.clear();
        }
    }

    /** A fresh token for a captain who is still signed in, so the app never has to sign out. */
    @Transactional(readOnly = true)
    public CaptainSession refresh(UUID captainId, UUID tenantId) {
        Captain captain = captainRepo.findById(captainId)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الكابتن غير موجود"));
        String token = jwtTokenProvider.generateCaptainToken(captainId, tenantId, tokenTtlMs);
        return new CaptainSession(token, tokenTtlMs, appService.profileOf(captain));
    }

    private Captain findCaptain(String phoneKey) {
        List<Captain> active = captainRepo.findByPhoneKey(phoneKey).stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .toList();
        if (active.isEmpty()) {
            throw new BusinessException("PHONE_NOT_REGISTERED",
                    "رقم الجوال غير مسجل لدى الشركة. تواصل مع مشرفك");
        }
        if (active.size() > 1) {
            throw new BusinessException("PHONE_AMBIGUOUS",
                    "رقم الجوال مسجل لأكثر من كابتن. تواصل مع مشرفك لتصحيح البيانات");
        }
        return active.get(0);
    }

    /** The last nine digits of a Saudi mobile number (5XXXXXXXX). */
    static String phoneKey(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        String key = digits.length() >= 9 ? digits.substring(digits.length() - 9) : "";
        if (!key.matches("5\\d{8}")) {
            throw new BusinessException("INVALID_PHONE", "رقم الجوال غير صحيح");
        }
        return key;
    }

    private static String maskPhone(String phoneKey) {
        return "05" + phoneKey.substring(1, 2) + " ••• " + phoneKey.substring(phoneKey.length() - 4);
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(codeLength);
        for (int i = 0; i < codeLength; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
