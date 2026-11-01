package com.advatix.OMS.service;

import com.advatix.OMS.repository.AdminUserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminUserRepository.findByUsername(username)
                .map(admin -> new User(
                        admin.getUsername(),
                        admin.getPassword(),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .orElseThrow(() -> new UsernameNotFoundException("Admin user not found"));
    }
}
