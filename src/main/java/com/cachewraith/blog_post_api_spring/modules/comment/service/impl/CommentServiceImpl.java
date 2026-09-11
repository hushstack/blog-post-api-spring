package com.cachewraith.blog_post_api_spring.modules.comment.service.impl;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import com.cachewraith.blog_post_api_spring.messaging.producer.EventPublisher;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.request.CreateCommentRequest;
import com.cachewraith.blog_post_api_spring.modules.comment.dto.v1.response.CommentResponse;
import com.cachewraith.blog_post_api_spring.modules.comment.entity.Comment;
import com.cachewraith.blog_post_api_spring.modules.comment.repository.CommentRepository;
import com.cachewraith.blog_post_api_spring.modules.comment.service.CommentService;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Post;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostVisibilityService;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import com.cachewraith.blog_post_api_spring.modules.reaction.service.ReactionService;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final PostVisibilityService visibility;
    private final PostService postService;
    private final UserService userService;
    private final ReactionService reactionService;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public CommentResponse create(UUID postId, UUID authorId, CreateCommentRequest request) {
        // Commenting requires the same visibility as reading — otherwise a PRIVATE post could be
        // probed by posting to it (OWASP A01).
        Post post = visibility.requireVisible(postId, authorId);

        if (request.parentCommentId() != null) {
            Comment parent =
                    commentRepository
                            .findById(request.parentCommentId())
                            .orElseThrow(() -> new ResourceNotFoundException("Parent comment"));
            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED, "Parent comment belongs to another post");
            }
            if (parent.getParentCommentId() != null) {
                throw new BusinessException(
                        ErrorCode.VALIDATION_FAILED, "Replies are only one level deep");
            }
        }

        Comment comment =
                commentRepository.save(
                        Comment.builder()
                                .postId(postId)
                                .authorId(authorId)
                                .parentCommentId(request.parentCommentId())
                                .content(request.content())
                                .build());

        postService.adjustCommentCount(postId, 1);

        if (!post.getAuthorId().equals(authorId)) {
            eventPublisher.dispatchNotification(
                    new NotificationDispatchEvent(
                            post.getAuthorId(), authorId, "COMMENT", comment.getId()));
        }

        return toResponses(List.of(comment), authorId).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> list(UUID postId, UUID viewerId, Pageable pageable) {
        visibility.requireVisible(postId, viewerId);

        Page<Comment> page =
                commentRepository.findByPostIdAndParentCommentIdIsNullOrderByCreatedAtDesc(
                        postId, pageable);

        // Replies for the whole page in one query, then grouped under their parents. Paging
        // applies to top-level comments only; a thread is never cut in half.
        Set<UUID> parentIds = page.getContent().stream().map(Comment::getId).collect(Collectors.toSet());
        Map<UUID, List<CommentResponse>> repliesByParent =
                parentIds.isEmpty()
                        ? Map.of()
                        : toResponses(
                                        commentRepository.findByParentCommentIdInOrderByCreatedAtAsc(
                                                parentIds),
                                        viewerId,
                                        Map.of())
                                .stream()
                                .collect(Collectors.groupingBy(CommentResponse::parentCommentId));

        List<CommentResponse> content = toResponses(page.getContent(), viewerId, repliesByParent);
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    @Override
    @Transactional
    public void delete(UUID commentId, UUID userId) {
        Comment comment =
                commentRepository
                        .findById(commentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Comment"));

        // The comment's author or the post's author may delete it; nobody else.
        Post post = visibility.requirePost(comment.getPostId());
        if (!comment.getAuthorId().equals(userId) && !post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        commentRepository.delete(comment);
        postService.adjustCommentCount(comment.getPostId(), -1);
    }

    private List<CommentResponse> toResponses(List<Comment> comments, UUID viewerId) {
        return toResponses(comments, viewerId, Map.of());
    }

    private List<CommentResponse> toResponses(
            List<Comment> comments,
            UUID viewerId,
            Map<UUID, List<CommentResponse>> repliesByParent) {
        if (comments.isEmpty()) {
            return List.of();
        }
        Set<UUID> authorIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Set<UUID> commentIds = comments.stream().map(Comment::getId).collect(Collectors.toSet());

        Map<UUID, AuthorSummary> authors = userService.authorSummaries(authorIds);
        Map<UUID, ReactionSummaryResponse> reactions =
                reactionService.summaries(TargetType.COMMENT, commentIds, viewerId);

        return comments.stream()
                .map(
                        comment -> {
                            ReactionSummaryResponse summary =
                                    reactions.getOrDefault(
                                            comment.getId(),
                                            new ReactionSummaryResponse(Map.of(), 0L, null));
                            return new CommentResponse(
                                    comment.getId(),
                                    comment.getPostId(),
                                    authors.get(comment.getAuthorId()),
                                    comment.getParentCommentId(),
                                    comment.getContent(),
                                    summary.total(),
                                    summary.viewerReaction(),
                                    comment.getCreatedAt(),
                                    repliesByParent.getOrDefault(comment.getId(), List.of()));
                        })
                .toList();
    }
}
