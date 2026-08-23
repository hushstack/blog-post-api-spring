package com.cachewraith.blog_post_api_spring.modules.user.repository;

import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /** Batch lookup, so rendering a page of posts costs one query for authors, not one per post. */
    List<User> findAllByIdIn(Collection<UUID> ids);
}
