package com.kamal.lc.auth.service;

import com.kamal.lc.auth.dto.UserManagementDtos.*;
import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.entity.JobTitle;
import com.kamal.lc.auth.entity.Permission;
import com.kamal.lc.auth.repository.JobTitleRepository;
import com.kamal.lc.auth.repository.UserRepository;
import com.kamal.lc.common.exception.BusinessException;
import com.kamal.lc.staff.entity.Staff;
import com.kamal.lc.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages system users and job titles. Only SUPER_ADMIN may call these.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserManagementService {

    private final UserRepository userRepo;
    private final JobTitleRepository jobTitleRepo;
    private final StaffRepository staffRepo;
    private final PasswordEncoder passwordEncoder;

    // ---- Permissions (static) ----

    public List<PermissionInfo> allPermissions() {
        return Arrays.stream(Permission.values())
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

        JobTitle jt = JobTitle.builder()
                .name(name)
                .labelAr(request.getLabelAr() != null ? request.getLabelAr().trim() : name)
                .permissions(request.getPermissions() != null ? request.getPermissions() : Set.of())
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
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BusinessException("INVALID_INPUT", "اسم المستخدم مطلوب");
        }
        if (request.getPassword() == null || request.getPassword().length() < 4) {
            throw new BusinessException("INVALID_INPUT", "كلمة المرور يجب أن تكون 4 أحرف على الأقل");
        }
        if (userRepo.existsByUsername(request.getUsername().trim())) {
            throw new BusinessException("DUPLICATE", "اسم المستخدم موجود بالفعل");
        }

        AppUser user = AppUser.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .displayName(request.getDisplayName() != null ? request.getDisplayName().trim() : null)
                .role("MANAGER")
                .active(true)
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

        if (user.isSuperAdmin()) {
            throw new BusinessException("FORBIDDEN", "لا يمكن تعديل حساب مدير النظام");
        }

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName().trim());
        }

        if (request.getJobTitleId() != null) {
            JobTitle jt = jobTitleRepo.findById(request.getJobTitleId())
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "الوظيفة غير موجودة"));
            user.setJobTitle(jt);
        }

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return toUserResponse(userRepo.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        AppUser user = userRepo.findById(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", "المستخدم غير موجود"));
        if (user.isSuperAdmin()) {
            throw new BusinessException("FORBIDDEN", "لا يمكن حذف حساب مدير النظام");
        }
        userRepo.delete(user);
    }

    // ---- helpers ----

    private void validatePermissions(Set<String> permissions) {
        if (permissions == null) return;
        Set<String> valid = Arrays.stream(Permission.values())
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
        Set<String> permissions = user.isSuperAdmin()
                ? Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet())
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
