package com.cachewraith.blog_post_api_spring.common.response;

import java.util.List;

/** Cursor pagination, used by the feed (spec section 1). */
public record CursorPageResponse<T>(List<T> content, String nextCursor, boolean hasMore) {

    public static <T> CursorPageResponse<T> of(List<T> content, String nextCursor) {
        return new CursorPageResponse<>(content, nextCursor, nextCursor != null);
    }
}
