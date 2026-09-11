package com.cachewraith.blog_post_api_spring.modules.friendship.service.impl;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.messaging.event.NotificationDispatchEvent;
import com.cachewraith.blog_post_api_spring.messaging.producer.EventPublisher;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendshipResponse;
import com.cachewraith.blog_post_api_spring.modules.friendship.entity.Friendship;
import com.cachewraith.blog_post_api_spring.modules.friendship.entity.FriendshipStatus;
import com.cachewraith.blog_post_api_spring.modules.friendship.repository.FriendshipRepository;
import com.cachewraith.blog_post_api_spring.modules.friendship.service.FriendshipService;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendStatus;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipServiceImpl implements FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserService userService;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public FriendshipResponse sendRequest(UUID requesterId, UUID addresseeId) {
        if (requesterId.equals(addresseeId)) {
            throw new BusinessException(ErrorCode.FRIENDSHIP_SELF);
        }
        userService.requireUser(addresseeId);

        friendshipRepository
                .findBetween(requesterId, addresseeId)
                .ifPresent(
                        existing -> {
                            throw new BusinessException(ErrorCode.FRIENDSHIP_EXISTS);
                        });

        Friendship saved =
                friendshipRepository.save(
                        Friendship.builder()
                                .requesterId(requesterId)
                                .addresseeId(addresseeId)
                                .status(FriendshipStatus.PENDING)
                                .build());

        eventPublisher.dispatchNotification(
                new NotificationDispatchEvent(
                        addresseeId, requesterId, "FRIEND_REQUEST", saved.getId()));

        return toResponse(saved, addresseeId);
    }

    @Override
    @Transactional
    public FriendshipResponse accept(UUID userId, UUID friendshipId) {
        Friendship friendship = requirePendingFor(userId, friendshipId);
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setRespondedAt(Instant.now());

        eventPublisher.dispatchNotification(
                new NotificationDispatchEvent(
                        friendship.getRequesterId(), userId, "FRIEND_ACCEPTED", friendship.getId()));

        return toResponse(friendshipRepository.save(friendship), friendship.getRequesterId());
    }

    @Override
    @Transactional
    public FriendshipResponse decline(UUID userId, UUID friendshipId) {
        Friendship friendship = requirePendingFor(userId, friendshipId);
        friendship.setStatus(FriendshipStatus.DECLINED);
        friendship.setRespondedAt(Instant.now());
        return toResponse(friendshipRepository.save(friendship), friendship.getRequesterId());
    }

    @Override
    @Transactional
    public void unfriend(UUID userId, UUID otherUserId) {
        Friendship friendship =
                friendshipRepository
                        .findBetween(userId, otherUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("Friendship"));
        friendshipRepository.delete(friendship);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> listFriends(UUID userId, Pageable pageable) {
        Page<Friendship> page =
                friendshipRepository.findAllForUser(userId, FriendshipStatus.ACCEPTED, pageable);
        return PageResponse.from(page.map(f -> toResponse(f, counterpartOf(f, userId))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FriendshipResponse> listIncomingRequests(UUID userId, Pageable pageable) {
        Page<Friendship> page =
                friendshipRepository.findByAddresseeIdAndStatus(
                        userId, FriendshipStatus.PENDING, pageable);
        return PageResponse.from(page.map(f -> toResponse(f, f.getRequesterId())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> friendIds(UUID userId) {
        return friendshipRepository.findFriendIds(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean areFriends(UUID a, UUID b) {
        return friendshipRepository
                .findBetween(a, b)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
    }

    /**
     * Loads the request and proves the caller is its addressee. Without this check any authenticated
     * user could accept a request addressed to someone else by guessing its id (OWASP A01).
     */
    private Friendship requirePendingFor(UUID userId, UUID friendshipId) {
        Friendship friendship =
                friendshipRepository
                        .findById(friendshipId)
                        .orElseThrow(() -> new ResourceNotFoundException("Friend request"));

        if (!friendship.getAddresseeId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_NOT_PENDING);
        }
        return friendship;
    }

    private UUID counterpartOf(Friendship friendship, UUID userId) {
        return friendship.getRequesterId().equals(userId)
                ? friendship.getAddresseeId()
                : friendship.getRequesterId();
    }

    private FriendshipResponse toResponse(Friendship friendship, UUID counterpartId) {
        Map<UUID, AuthorSummary> authors = userService.authorSummaries(List.of(counterpartId));
        return new FriendshipResponse(
                friendship.getId(),
                authors.get(counterpartId),
                friendship.getStatus().name(),
                friendship.getCreatedAt(),
                friendship.getRespondedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, FriendStatus> statusesFor(UUID viewerId, Collection<UUID> otherIds) {
        Set<UUID> ids = Set.copyOf(otherIds);
        Map<UUID, FriendStatus> result = new HashMap<>();
        ids.forEach(id -> result.put(id, id.equals(viewerId) ? FriendStatus.SELF : FriendStatus.NONE));
        if (ids.isEmpty()) {
            return result;
        }
        for (Friendship f : friendshipRepository.findBetweenUserAndAny(viewerId, ids)) {
            boolean viewerAsked = f.getRequesterId().equals(viewerId);
            UUID other = viewerAsked ? f.getAddresseeId() : f.getRequesterId();
            FriendStatus status =
                    switch (f.getStatus()) {
                        case ACCEPTED -> FriendStatus.FRIENDS;
                        case PENDING ->
                                viewerAsked ? FriendStatus.REQUEST_SENT : FriendStatus.REQUEST_RECEIVED;
                        case DECLINED, BLOCKED -> FriendStatus.NONE;
                    };
            result.put(other, status);
        }
        return result;
    }
}
