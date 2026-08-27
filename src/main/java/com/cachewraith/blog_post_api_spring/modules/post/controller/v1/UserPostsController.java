package com.cachewraith.blog_post_api_spring.modules.post.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostResponse;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A user's own timeline. It lives in the post module, which owns the resource being listed, and in
 * its own controller because the route hangs off /users rather than /posts — the same split as
 * {@code FeedController}.
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/users")
@RequiredArgsConstructor
@Tag(name = "Posts")
public class UserPostsController {

    private final PostService postService;

    @GetMapping("/{id}/posts")
    @Operation(summary = "List a user's posts, filtered to what you may see")
    public ApiResponse<PageResponse<PostResponse>> byAuthor(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        // Anonymous callers are allowed; they simply see only PUBLIC posts.
        UUID viewerId = principal == null ? null : principal.getId();
        return ApiResponse.of(postService.listByAuthor(id, viewerId, pageable));
    }
}
