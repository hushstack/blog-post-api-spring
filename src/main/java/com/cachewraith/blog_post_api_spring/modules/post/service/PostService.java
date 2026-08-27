package com.cachewraith.blog_post_api_spring.modules.post.service;

import com.cachewraith.blog_post_api_spring.common.response.CursorPageResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.CreatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.RepostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.UpdatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.ShareLinkResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface PostService {

    PostResponse create(UUID authorId, CreatePostRequest request, List<MultipartFile> images);

    PostResponse get(UUID postId, UUID viewerId);

    PostResponse update(UUID postId, UUID userId, UpdatePostRequest request);

    void delete(UUID postId, UUID userId);

    PostResponse repost(UUID postId, UUID userId, RepostRequest request);

    ShareLinkResponse shareLink(UUID postId, UUID viewerId);

    CursorPageResponse<PostResponse> feed(UUID viewerId, Instant cursor, int size);

    /** One author's timeline, filtered to what {@code viewerId} may see. Anonymous viewers pass null. */
    PageResponse<PostResponse> listByAuthor(UUID authorId, UUID viewerId, Pageable pageable);

    /**
     * Adjusts the denormalised comment counter. Exposed so the comment module can keep it in step
     * without reaching into this module's repository.
     */
    void adjustCommentCount(UUID postId, long delta);

    /** Overwrites the denormalised reaction counter from an authoritative count. */
    void setReactionCount(UUID postId, long count);
}
