package com.cachewraith.blog_post_api_spring.modules.post.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.common.response.CursorPageResponse;
import com.cachewraith.blog_post_api_spring.modules.post.dto.v1.response.PostResponse;
import com.cachewraith.blog_post_api_spring.modules.post.service.PostService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1 + "/feed")
@RequiredArgsConstructor
@Tag(name = "Feed")
public class FeedController {

    private final PostService postService;

    @GetMapping
    @Operation(summary = "Cursor-paginated feed of your own and friends' posts")
    public ApiResponse<CursorPageResponse<PostResponse>> feed(
            @CurrentUser AppUserPrincipal principal,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(postService.feed(principal.getId(), parseCursor(cursor), size));
    }

    /** A malformed cursor is treated as "start from the top" rather than a 500. */
    private Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
