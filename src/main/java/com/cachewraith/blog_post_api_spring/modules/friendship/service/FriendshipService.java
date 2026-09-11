package com.cachewraith.blog_post_api_spring.modules.friendship.service;

import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendshipResponse;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendStatus;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface FriendshipService {

    FriendshipResponse sendRequest(UUID requesterId, UUID addresseeId);

    FriendshipResponse accept(UUID userId, UUID friendshipId);

    FriendshipResponse decline(UUID userId, UUID friendshipId);

    void unfriend(UUID userId, UUID otherUserId);

    PageResponse<FriendshipResponse> listFriends(UUID userId, Pageable pageable);

    PageResponse<FriendshipResponse> listIncomingRequests(UUID userId, Pageable pageable);

    /** Ids of accepted friends — used by feed and post visibility. */
    List<UUID> friendIds(UUID userId);

    boolean areFriends(UUID a, UUID b);

    /**
     * The viewer's {@link FriendStatus} toward each of {@code otherIds}, in one query. Every id
     * requested is present in the result, NONE when no row exists.
     */
    Map<UUID, FriendStatus> statusesFor(UUID viewerId, Collection<UUID> otherIds);
}
