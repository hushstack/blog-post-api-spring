package com.cachewraith.blog_post_api_spring.modules.post.service;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.modules.friendship.service.FriendshipService;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Post;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import com.cachewraith.blog_post_api_spring.modules.post.repository.PostRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single place that decides whether a viewer may see a post.
 *
 * <p>It lives apart from {@code PostService} so that reactions and comments can enforce the same
 * rule without a circular dependency between the three services — every read path goes through
 * this one check rather than each module reimplementing it (OWASP A01).
 *
 * <p>{@code viewerId} is null for anonymous callers, which {@code GET /posts/{id}} allows.
 */
@Service
@RequiredArgsConstructor
public class PostVisibilityService {

    private final PostRepository postRepository;
    private final FriendshipService friendshipService;

    @Transactional(readOnly = true)
    public Post requirePost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post"));
    }

    @Transactional(readOnly = true)
    public Post requireVisible(UUID postId, UUID viewerId) {
        Post post = requirePost(postId);
        assertVisible(post, viewerId);
        return post;
    }

    @Transactional(readOnly = true)
    public void assertVisible(Post post, UUID viewerId) {
        if (!isVisible(post, viewerId)) {
            // 403 rather than 404 only because existence is already implied by a shared link;
            // PRIVATE posts of other users are indistinguishable from missing ones to a scraper
            // because the id space is UUIDv4.
            throw new BusinessException(ErrorCode.POST_NOT_VISIBLE);
        }
    }

    @Transactional(readOnly = true)
    public boolean isVisible(Post post, UUID viewerId) {
        if (viewerId != null && post.getAuthorId().equals(viewerId)) {
            return true;
        }
        return switch (post.getVisibility()) {
            case PUBLIC -> true;
            case FRIENDS -> viewerId != null && friendshipService.areFriends(post.getAuthorId(), viewerId);
            case PRIVATE -> false;
        };
    }

    /** Owner-only gate for mutations. */
    @Transactional(readOnly = true)
    public Post requireOwned(UUID postId, UUID userId) {
        Post post = requirePost(postId);
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return post;
    }

    public Visibility defaultVisibility() {
        return Visibility.PUBLIC;
    }
}
