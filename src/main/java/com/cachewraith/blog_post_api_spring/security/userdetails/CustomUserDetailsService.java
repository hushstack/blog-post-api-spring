package com.cachewraith.blog_post_api_spring.security.userdetails;

import com.cachewraith.blog_post_api_spring.modules.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserByUsername(String email) {
        return userRepository
                .findByEmailIgnoreCase(email)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }

    @Transactional(readOnly = true)
    public AppUserPrincipal loadUserById(UUID id) {
        return userRepository
                .findById(id)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));
    }
}
