package com.cachewraith.blog_post_api_spring.modules.post.service.impl;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.common.response.CursorPageResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.config.ShareProperties;
import com.cachewraith.blog_post_api_spring.integration.storage.StorageService;
import com.cachewraith.blog_post_api_spring.messaging.event.FeedFanoutEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.ImageProcessEvent;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import com.cachewraith.blog_post_api_spring.messaging.producer.EventPublisher;
import com.cachewraith.blog_post_api_spring.modules.friendship.service.FriendshipService;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.CreatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.RepostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request.UpdatePostRequest;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostImageResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.ShareLinkResponse;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Post;
import com.cachewraith.blog_post_api_spring.modules.post.entity.PostImage;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import com.cachewraith.blog_post_api_spring.modules.post.repository.PostImageRepository;
import com.cachewraith.blog_post_api_spring.modules.post.repository.PostRepository;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostVisibilityService;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import com.cachewraith.blog_post_api_spring.modules.reaction.service.ReactionService;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final String POST_IMAGE_FOLDER = "posts";
    private static final int MAX_IMAGES = 10;
    private static final int MAX_FEED_SIZE = 50;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final PostVisibilityService visibility;
    private final ReactionService reactionService;
    private final UserService userService;
    private final FriendshipService friendshipService;
    private final StorageService storageService;
    private final EventPublisher eventPublisher;
    private final ShareProperties shareProperties;

    @Override
    @Transactional
    public PostResponse create(
            UUID authorId, CreatePostRequest request, List<MultipartFile> images) {
        List<MultipartFile> files = images == null ? List.of() : images;
        if (files.size() > MAX_IMAGES) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "At most " + MAX_IMAGES + " images per post");
        }
        if ((request.content() == null || request.content().isBlank()) && files.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A post needs text or an image");
        }

        Post post =
                postRepository.save(
                        Post.builder()
                                .authorId(authorId)
                                .content(request.content())
                                .visibility(
                                        request.visibility() == null
                                                ? visibility.defaultVisibility()
                                                : request.visibility())
                                .build());

        List<String> urls = storeImages(post.getId(), files, 0);

        if (!urls.isEmpty()) {
            eventPublisher.processImages(new ImageProcessEvent(post.getId(), authorId, urls));
        }
        eventPublisher.fanOutFeed(new FeedFanoutEvent(post.getId(), authorId));

        return toResponse(post, authorId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse get(UUID postId, UUID viewerId) {
        Post post = visibility.requireVisible(postId, viewerId);
        return toResponse(post, viewerId, true);
    }

    @Override
    @Transactional
    public PostResponse update(
            UUID postId, UUID userId, UpdatePostRequest request, List<MultipartFile> newImages) {
        Post post = visibility.requireOwned(postId, userId);
        List<MultipartFile> files = newImages == null ? List.of() : newImages;

        if (request.content() != null) {
            post.setContent(request.content());
        }
        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }

        List<PostImage> current = postImageRepository.findByPostIdOrderByPositionAsc(postId);
        Set<UUID> toRemove = new HashSet<>(request.removeImageIdsOrEmpty());
        // An id that is not on this post is rejected outright, and the same way whether it is on
        // someone else's post or on none: the owner check above is the only authorization here,
        // and a silent skip would let a bad id pass unnoticed (OWASP A01).
        Set<UUID> ownIds = current.stream().map(PostImage::getId).collect(Collectors.toSet());
        if (!ownIds.containsAll(toRemove)) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "removeImageIds names an image not on this post");
        }

        List<PostImage> kept = new ArrayList<>();
        List<PostImage> removed = new ArrayList<>();
        for (PostImage image : current) {
            (toRemove.contains(image.getId()) ? removed : kept).add(image);
        }

        if (kept.size() + files.size() > MAX_IMAGES) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED, "At most " + MAX_IMAGES + " images per post");
        }
        if ((post.getContent() == null || post.getContent().isBlank())
                && kept.isEmpty()
                && files.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A post needs text or an image");
        }

        // Positions stay contiguous after a removal, so a later append lands at the end.
        for (int i = 0; i < kept.size(); i++) {
            kept.get(i).setPosition(i);
        }
        postImageRepository.saveAll(kept);
        postImageRepository.deleteAll(removed);
        removed.forEach(image -> storageService.delete(image.getUrl()));

        List<String> urls = storeImages(postId, files, kept.size());
        if (!urls.isEmpty()) {
            eventPublisher.processImages(new ImageProcessEvent(postId, userId, urls));
        }

        return toResponse(postRepository.save(post), userId, true);
    }

    @Override
    @Transactional
    public void delete(UUID postId, UUID userId) {
        Post post = visibility.requireOwned(postId, userId);

        postImageRepository
                .findByPostIdOrderByPositionAsc(postId)
                .forEach(image -> storageService.delete(image.getUrl()));
        postImageRepository.deleteByPostId(postId);

        if (post.getOriginalPostId() != null) {
            postRepository.adjustRepostCount(post.getOriginalPostId(), -1);
        }
        postRepository.delete(post);
    }

    @Override
    @Transactional
    public PostResponse repost(UUID postId, UUID userId, RepostRequest request) {
        Post original = visibility.requireVisible(postId, userId);

        // Reposting a repost points at the root, so the chain never nests more than one deep.
        UUID rootId = original.getOriginalPostId() == null ? original.getId() : original.getOriginalPostId();

        Post repost =
                postRepository.save(
                        Post.builder()
                                .authorId(userId)
                                .content(request == null ? null : request.content())
                                .visibility(Visibility.PUBLIC)
                                .originalPostId(rootId)
                                .build());

        postRepository.adjustRepostCount(rootId, 1);
        eventPublisher.fanOutFeed(new FeedFanoutEvent(repost.getId(), userId));
        eventPublisher.dispatchNotification(
                new NotificationDispatchEvent(original.getAuthorId(), userId, "REPOST", repost.getId()));

        return toResponse(repost, userId, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ShareLinkResponse shareLink(UUID postId, UUID viewerId) {
        Post post = visibility.requireVisible(postId, viewerId);
        // A share link is only meaningful for something the recipient can open.
        if (post.getVisibility() != Visibility.PUBLIC) {
            throw new BusinessException(
                    ErrorCode.POST_NOT_VISIBLE, "Only public posts can be shared by link");
        }
        return new ShareLinkResponse(shareUrl(postId));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> feed(UUID viewerId, Instant cursor, int size) {
        int limit = Math.min(Math.max(size, 1), MAX_FEED_SIZE);

        List<UUID> friendIds = friendshipService.friendIds(viewerId);
        // 'in ()' is invalid SQL, so an empty friend list needs a placeholder that matches nothing;
        // the PUBLIC branch of the query still fills the page for a viewer with no friends.
        Collection<UUID> effectiveFriends =
                friendIds.isEmpty() ? List.of(new UUID(0, 0)) : friendIds;

        PageRequest window = PageRequest.of(0, limit + 1);
        List<Post> posts =
                cursor == null
                        ? postRepository.findFeedFirstPage(viewerId, effectiveFriends, window)
                        : postRepository.findFeedAfter(viewerId, effectiveFriends, cursor, window);

        boolean hasMore = posts.size() > limit;
        List<Post> page = hasMore ? posts.subList(0, limit) : posts;

        List<PostResponse> content = toResponses(page, viewerId);
        String nextCursor =
                hasMore && !page.isEmpty()
                        ? page.get(page.size() - 1).getCreatedAt().toString()
                        : null;

        return CursorPageResponse.of(content, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> listByAuthor(UUID authorId, UUID viewerId, Pageable pageable) {
        // An unknown author is a 404 rather than an empty page, so the caller can tell the two apart.
        userService.requireUser(authorId);

        Set<Visibility> visibilities = visibility.visibleVisibilitiesFor(authorId, viewerId);
        Page<Post> page = postRepository.findByAuthorVisibleTo(authorId, visibilities, pageable);

        // Mapped through the batch assembler, then rewrapped so the page metadata survives.
        List<PostResponse> content = toResponses(page.getContent(), viewerId);
        return PageResponse.from(new PageImpl<>(content, pageable, page.getTotalElements()));
    }

    @Override
    @Transactional
    public void adjustCommentCount(UUID postId, long delta) {
        // Relative update in SQL, so concurrent comments cannot lose each other's increment.
        postRepository.adjustCommentCount(postId, delta);
    }

    @Override
    @Transactional
    public void setReactionCount(UUID postId, long count) {
        postRepository.setReactionCount(postId, count);
    }

    /** Stores {@code files} at positions {@code firstPosition}, {@code firstPosition + 1}, … */
    private List<String> storeImages(UUID postId, List<MultipartFile> files, int firstPosition) {
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            String url = storageService.storeImage(files.get(i), POST_IMAGE_FOLDER);
            urls.add(url);
            postImageRepository.save(
                    PostImage.builder()
                            .postId(postId)
                            .url(url)
                            .position(firstPosition + i)
                            .build());
        }
        return urls;
    }

    private String shareUrl(UUID postId) {
        return shareProperties.getBaseUrl() + "/posts/" + postId;
    }

    /** Single post. {@code withOriginal} stops a repost from recursing forever. */
    private PostResponse toResponse(Post post, UUID viewerId, boolean withOriginal) {
        return toResponses(List.of(post), viewerId, withOriginal).get(0);
    }

    private List<PostResponse> toResponses(List<Post> posts, UUID viewerId) {
        return toResponses(posts, viewerId, true);
    }

    /**
     * Assembles the section 6 shape for a whole page with a fixed number of queries: authors,
     * images, reaction summaries and embedded originals are each fetched in one batch rather than
     * per post.
     */
    private List<PostResponse> toResponses(List<Post> posts, UUID viewerId, boolean withOriginal) {
        if (posts.isEmpty()) {
            return List.of();
        }

        Set<UUID> postIds = posts.stream().map(Post::getId).collect(Collectors.toSet());
        Set<UUID> authorIds = posts.stream().map(Post::getAuthorId).collect(Collectors.toCollection(HashSet::new));

        Map<UUID, List<PostImageResponse>> imagesByPost =
                postImageRepository.findByPostIdInOrderByPositionAsc(postIds).stream()
                        .collect(
                                Collectors.groupingBy(
                                        PostImage::getPostId,
                                        Collectors.mapping(
                                                image ->
                                                        new PostImageResponse(
                                                                image.getId(),
                                                                image.getUrl(),
                                                                image.getPosition()),
                                                Collectors.toList())));

        Map<UUID, ReactionSummaryResponse> reactions =
                reactionService.summaries(TargetType.POST, postIds, viewerId);

        Map<UUID, PostResponse> originals = Map.of();
        if (withOriginal) {
            Set<UUID> originalIds =
                    posts.stream()
                            .map(Post::getOriginalPostId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
            if (!originalIds.isEmpty()) {
                List<Post> originalPosts =
                        postRepository.findAllByIdIn(originalIds).stream()
                                // An embedded original is only shown if the viewer may see it.
                                .filter(p -> visibility.isVisible(p, viewerId))
                                .toList();
                originalPosts.forEach(p -> authorIds.add(p.getAuthorId()));
                originals =
                        toResponses(originalPosts, viewerId, false).stream()
                                .collect(Collectors.toMap(PostResponse::id, r -> r));
            }
        }

        Map<UUID, AuthorSummary> authors = userService.authorSummaries(authorIds);

        List<PostResponse> result = new ArrayList<>(posts.size());
        for (Post post : posts) {
            ReactionSummaryResponse summary =
                    reactions.getOrDefault(
                            post.getId(), new ReactionSummaryResponse(Map.of(), 0L, null));

            Map<String, Long> counts = new java.util.LinkedHashMap<>(summary.counts());
            counts.put("total", summary.total());

            result.add(
                    new PostResponse(
                            post.getId(),
                            authors.get(post.getAuthorId()),
                            post.getContent(),
                            imagesByPost.getOrDefault(post.getId(), List.of()),
                            post.getVisibility().name(),
                            post.getAuthorId().equals(viewerId),
                            counts,
                            summary.viewerReaction(),
                            post.getCommentCount(),
                            post.getRepostCount(),
                            post.getOriginalPostId() == null
                                    ? null
                                    : originals.get(post.getOriginalPostId()),
                            shareUrl(post.getId()),
                            post.getCreatedAt()));
        }
        return result;
    }
}
