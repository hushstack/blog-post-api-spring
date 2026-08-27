package com.cachewraith.blog_post_api_spring.modules.auth.repository;

import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpVerification;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    /**
     * Purges audit rows whose code expired before {@code cutoff}. A bulk delete rather than
     * findAll-then-delete: this runs over rows nobody needs loaded.
     */
    @Modifying
    @Query("delete from OtpVerification o where o.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
