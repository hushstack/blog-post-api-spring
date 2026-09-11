package com.cachewraith.blog_post_api_spring.modules.comment.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.request.CreateCommentRequest;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.response.CommentResponse;
import com.cachewraith.blog_post_api_spring.modules.comment.service.CommentService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1)
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/posts/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a post, or reply to a comment")
    public ApiResponse<CommentResponse> create(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.of(commentService.create(id, principal.getId(), request));
    }

    @GetMapping("/posts/{id}/comments")
    @Operation(summary = "List a post's comments, each with its replies nested")
    public ApiResponse<PageResponse<CommentResponse>> list(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable UUID id,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ApiResponse.of(commentService.list(id, viewerId, pageable));
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your own comment, or one on your post")
    public void delete(@CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        commentService.delete(id, principal.getId());
    }
}
