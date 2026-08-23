package com.cachewraith.blog_post_api_spring.modules.comment.service;

import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.request.CreateCommentRequest;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.response.CommentResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface CommentService {

    CommentResponse create(UUID postId, UUID authorId, CreateCommentRequest request);

    PageResponse<CommentResponse> list(UUID postId, UUID viewerId, Pageable pageable);

    void delete(UUID commentId, UUID userId);
}
