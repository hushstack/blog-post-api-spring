package com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response;

/**
 * The viewer's relationship to another user, the way a client renders a friend button. It is a
 * view of {@code FriendshipStatus} from one side: a PENDING row is REQUEST_SENT to its requester
 * and REQUEST_RECEIVED to its addressee, and DECLINED or BLOCKED both read as NONE — a declined
 * request may be sent again, and a block must never announce itself to the blocked party.
 */
public enum FriendStatus {
    SELF,
    FRIENDS,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    NONE
}
