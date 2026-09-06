package com.kamal.lc.common.security;

import com.kamal.lc.auth.entity.AppUser;
import com.kamal.lc.auth.entity.Permission;
import com.kamal.lc.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.transaction.annotation.Transactional;

/**
 * Loads user details from the database for Spring Security authentication.
 * PLATFORM_ADMIN gets platform permissions; ADMIN gets all tenant permissions;
 * MANAGER gets their job-title permissions.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("المستخدم غير موجود: " + username));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("حساب المستخدم معطل: " + username);
        }

        // Check if tenant is active (skip for platform admins who have no tenant)
        if (!user.isPlatformAdmin() && user.getTenant() != null && !user.getTenant().isActive()) {
            throw new UsernameNotFoundException("حساب الشركة معطل: " + username);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        if (user.isPlatformAdmin()) {
            // Platform admin gets all permissions including platform-level ones
            Arrays.stream(Permission.values())
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        } else if (user.isAdmin()) {
            // Company admin gets all tenant-level permissions (same as old SUPER_ADMIN)
            Arrays.stream(Permission.values())
                    .filter(p -> !p.name().startsWith("PLATFORM_") && !p.name().startsWith("TENANTS_")
                              && !p.name().startsWith("SUBSCRIPTIONS_") && !p.name().startsWith("PLANS_"))
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        } else if (user.getJobTitle() != null) {
            // Manager gets their job-title permissions
            user.getJobTitle().getPermissions()
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }

        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isActive(),                    // enabled
                true,                               // accountNonExpired
                true,                               // credentialsNonExpired
                true,                               // accountNonLocked
                authorities
        );
    }
}
