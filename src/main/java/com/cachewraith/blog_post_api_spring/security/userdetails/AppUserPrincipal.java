package com.cachewraith.blog_post_api_spring.security.userdetails;

import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import com.cachewraith.blog_post_api_spring.modules.user.entity.UserStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal. Carries the user id so controllers never take one from the client. */
@Getter
public class AppUserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final String username;
    private final String passwordHash;
    private final UserStatus status;

    public AppUserPrincipal(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.status = user.getStatus();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.SUSPENDED;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
