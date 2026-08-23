package com.cachewraith.blog_post_api_spring.modules.post.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.CreatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.RepostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.UpdatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.ShareLinkResponse;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiVersions.V1 + "/posts")
@RequiredArgsConstructor
@Tag(name = "Posts")
public class PostController {

    private final PostService postService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a post with optional images")
    public ApiResponse<PostResponse> create(
            @CurrentUser AppUserPrincipal principal,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) Visibility visibility,
            @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        return ApiResponse.of(
                postService.create(
                        principal.getId(), new CreatePostRequest(content, visibility), images));
    }

    /**
     * Readable without a token; {@code viewerId} is null for anonymous callers and the service
     * resolves visibility from that (OWASP A01).
     */
    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single post")
    public ApiResponse<PostResponse> get(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.of(postService.get(id, principal == null ? null : principal.getId()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update your own post")
    public ApiResponse<PostResponse> update(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest request) {
        return ApiResponse.of(postService.update(id, principal.getId(), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your own post")
    public void delete(@CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        postService.delete(id, principal.getId());
    }

    @PostMapping("/{id}/repost")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Repost a post")
    public ApiResponse<PostResponse> repost(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RepostRequest request) {
        return ApiResponse.of(postService.repost(id, principal.getId(), request));
    }

    @GetMapping("/{id}/share-link")
    @Operation(summary = "Canonical public URL for a post")
    public ApiResponse<ShareLinkResponse> shareLink(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.of(
                postService.shareLink(id, principal == null ? null : principal.getId()));
    }
}
