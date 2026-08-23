package com.cachewraith.blog_post_api_spring.modules.auth.repository;

import com.cachewraith.blog_post_api_spring.modules.auth.entity.OtpVerification;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {}
