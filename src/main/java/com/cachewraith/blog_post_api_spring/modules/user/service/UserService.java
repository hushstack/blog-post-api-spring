package com.cachewraith.blog_post_api_spring.modules.user.service;

import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.request.UpdateProfileRequest;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.PublicUserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.UserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserResponse getOwnProfile(UUID userId);

    PublicUserResponse getPublicProfile(UUID userId);

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    UserResponse updateAvatar(UUID userId, MultipartFile file);

    User requireUser(UUID userId);

    /** Batch author lookup for post/comment rendering — one query, not one per row. */
    Map<UUID, AuthorSummary> authorSummaries(Collection<UUID> userIds);
}
