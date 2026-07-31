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

/**
 * Loads user details from the database for Spring Security authentication.
 * SUPER_ADMIN gets all permissions; managers get their job-title permissions.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("المستخدم غير موجود: " + username));

        if (!user.isActive()) {
            throw new UsernameNotFoundException("حساب المستخدم معطل: " + username);
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));

        if (user.isSuperAdmin()) {
            // SUPER_ADMIN gets every permission
            Arrays.stream(Permission.values())
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p.name())));
        } else if (user.getJobTitle() != null) {
            // Manager gets their job-title permissions
            user.getJobTitle().getPermissions()
                    .forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }

        return new User(user.getUsername(), user.getPasswordHash(), authorities);
    }
}

