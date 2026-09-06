package com.kamal.lc.auth.service;

import com.kamal.lc.auth.dto.UserManagementDtos.*;
import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.entity.JobTitle;
import com.kamal.lc.auth.entity.Permission;
import com.kamal.lc.auth.repository.JobTitleRepository;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.common.security.TenantContext;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import com.kamal.lc.tenant.entity.Tenant;
import com.kamal.lc.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages system users and job titles. Only ADMIN (company admin) may call these.
 * All operations are scoped to the current tenant.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserManagementService {

    private final UserRepository userRepo;
    private final JobTitleRepository jobTitleRepo;
    private final StaffRepository staffRepo;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepo;

    // ---- Permissions (static) ----

    public List<PermissionInfo> allPermissions() {
        return Arrays.stream(Permission.values())
                .filter(p -> !p.isPlatformLevel()) // Don't expose platform permissions to tenant admins
                .map(p -> PermissionInfo.builder()
                        .name(p.name())
                        .labelAr(p.getLabelAr())
                        .build())
                .toList();
    }

    // ---- Job Titles ----

    public List<JobTitleResponse> listJobTitles() {
        return jobTitleRepo.findAll().stream()
                .map(this::toJobTitleResponse)
                .toList();
    }

    @Transactional
    public JobTitleResponse createJobTitle(JobTitleRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException("INVALID_INPUT", "اسم الوظيفة مطلوب");
        }
        String name = request.getName().trim().toUpperCase().replace(" ", "_");
        if (jobTitleRepo.existsByName(name)) {
            throw new BusinessException("DUPLICATE", "هذه الوظيفة موجودة بالفعل");
        }

        validatePermissions(request.getPermissions());

        Tenant tenant = tenantRepo.getReferenceById(TenantContext.requireCurrentTenantId());

        JobTitle jt = JobTitle.builder()
                .name(name)
                .labelAr(request.getLabelAr() != null ? request.getLabelAr().trim() : name)
                .permissions(request.getPermissions() != null ? request.getPermissions() : Set.of())
                .tenant(tenant)
                .build();

        return toJobTitleResponse(jobTitleRepo.save(jt));
    }

    @Transactional
    public JobTitleResponse updateJobTitle(UUID id, JobTitleRequest request) {
        JobTitle jt = jobTitleRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الوظيفة غير موجودة"));

        if (request.getLabelAr() != null && !request.getLabelAr().isBlank()) {
            jt.setLabelAr(request.getLabelAr().trim());
        }

        if (request.getPermissions() != null) {
            validatePermissions(request.getPermissions());
            jt.getPermissions().clear();
            jt.getPermissions().addAll(request.getPermissions());
        }

        return toJobTitleResponse(jobTitleRepo.save(jt));
    }

    @Transactional
    public void deleteJobTitle(UUID id) {
        JobTitle jt = jobTitleRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "الوظيفة غير موجودة"));
        if (jt.isSystemDefined()) {
            throw new BusinessException("SYSTEM_DEFINED", "لا يمكن حذف وظيفة نظامية");
        }
        jobTitleRepo.delete(jt);
    }

    // ---- Users ----

    public List<UserResponse> listUsers() {
        return userRepo.findAll().stream()
                .filter(u -> !u.isPlatformAdmin()) // Don't show platform admin in tenant user list
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException("INVALID_INPUT", "اسم المستخدم مطلوب");
        }
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new BusinessException("INVALID_INPUT", "كلمة المرور يجب أن تكون 8 أحرف على الأقل");
        }
        if (userRepo.existsByUsername(request.getUsername().trim())) {
            throw new BusinessException("DUPLICATE", "اسم المستخدم موجود بالفعل");
        }

        Tenant tenant = tenantRepo.getReferenceById(TenantContext.requireCurrentTenantId());

        AppUser user = AppUser.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName().trim() : null)
                .role("MANAGER")
                .active(true)
                .tenant(tenant)
                .build();

        if (request.getStaffId() != null) {
            Staff staff = staffRepo.findById(request.getStaffId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
            user.setStaff(staff);
        }

        if (request.getJobTitleId() != null) {
            JobTitle jt = jobTitleRepo.findById(request.getJobTitleId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الوظيفة غير موجودة"));
            user.setJobTitle(jt);
        }

        return toUserResponse(userRepo.save(user));
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستخدم غير موجود"));

        if (user.isAdmin() || "ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole())) {
            throw new BusinessException("FORBIDDEN", "لا يمكن التلاعب أو تغيير وظائف أو صلاحيات مدير الشركة الأساسي");
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        // Job Title update or clearing
        if (request.getJobTitleId() != null) {
            JobTitle jt = jobTitleRepo.findById(request.getJobTitleId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الوظيفة غير موجودة"));
            user.setJobTitle(jt);
        } else {
            user.setJobTitle(null);
        }

        // Staff linking update or clearing
        if (request.getStaffId() != null) {
            Staff staff = staffRepo.findById(request.getStaffId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الموظف غير موجود"));
            user.setStaff(staff);
        } else {
            user.setStaff(null);
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < 8) {
                throw new BusinessException("INVALID_INPUT", "كلمة المرور يجب أن تكون 8 أحرف على الأقل");
            }
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return toUserResponse(userRepo.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستخدم غير موجود"));
        if (user.isAdmin() || "ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole())) {
            throw new BusinessException("FORBIDDEN", "لا يمكن حذف حساب مدير الشركة الأساسي");
        }
        userRepo.delete(user);
    }


    // ---- helpers ----

    private void validatePermissions(Set<String> permissions) {
        if (permissions == null) return;
        Set<String> valid = Arrays.stream(Permission.values())
                .filter(p -> !p.isPlatformLevel())
                .map(Enum::name)
                .collect(Collectors.toSet());
        for (String p : permissions) {
            if (!valid.contains(p)) {
                throw new BusinessException("INVALID_PERMISSION", "صلاحية غير معروفة: " + p);
            }
        }
    }

    private JobTitleResponse toJobTitleResponse(JobTitle jt) {
        return JobTitleResponse.builder()
                .id(jt.getId())
                .name(jt.getName())
                .labelAr(jt.getLabelAr())
                .systemDefined(jt.isSystemDefined())
                .active(jt.isActive())
                .permissions(jt.getPermissions())
                .build();
    }

    private UserResponse toUserResponse(AppUser user) {
        Set<String> permissions = user.isAdmin()
                ? Arrays.stream(Permission.values())
                        .filter(p -> !p.isPlatformLevel())
                        .map(Enum::name)
                        .collect(Collectors.toSet())
                : (user.getJobTitle() != null ? user.getJobTitle().getPermissions() : Set.of());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .active(user.isActive())
                .staffId(user.getStaff() != null ? user.getStaff().getId() : null)
                .staffName(user.getStaff() != null ? user.getStaff().getFullNameAr() : null)
                .staffCode(user.getStaff() != null ? user.getStaff().getStaffCode() : null)
                .jobTitleId(user.getJobTitle() != null ? user.getJobTitle().getId() : null)
                .jobTitleLabel(user.getJobTitle() != null ? user.getJobTitle().getLabelAr() : null)
                .permissions(permissions)
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .build();
    }
}
