package com.cachewraith.blog_post_api_spring.modules.post.dto.v1.request;

import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Every field is optional; absent means unchanged. {@code removeImageIds} names existing images by
 * the {@code id} in {@code PostImageResponse}; new images arrive as multipart parts beside this,
 * never inside it.
 */
public record UpdatePostRequest(
        @Size(max = 5000) String content, Visibility visibility, List<UUID> removeImageIds) {

    public List<UUID> removeImageIdsOrEmpty() {
        return removeImageIds == null ? List.of() : removeImageIds;
    }
}
