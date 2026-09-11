package com.cachewraith.blog_post_api_spring.modules.reaction.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.request.ReactionRequest;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactionSummaryResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.TargetType;
import com.cachewraith.blog_post_api_spring.modules.reaction.service.ReactionService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.dto.v1.response.ReactorResponse;
import com.cachewraith.blog_post_api_spring.modules.reaction.entity.ReactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1 + "/reactions")
@RequiredArgsConstructor
@Tag(name = "Reactions")
public class ReactionController {

    private final ReactionService reactionService;

    /**
     * One endpoint for react and un-react. {@code type} is what the button represents; the server
     * works out whether that means add, change or remove from the caller's current reaction. No
     * body at all means {@code LIKE}, so a plain like button needs nothing but the URL.
     */
    @PostMapping("/{targetType}/{targetId}")
    @Operation(summary = "Toggle your reaction: add, change, or send the same type again to remove. No body = LIKE")
    public ApiResponse<ReactionSummaryResponse> toggle(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable TargetType targetType,
            @PathVariable UUID targetId,
            @Valid @RequestBody(required = false) ReactionRequest request) {
        ReactionRequest effective = request == null ? ReactionRequest.like() : request;
        return ApiResponse.of(
                reactionService.toggle(principal.getId(), targetType, targetId, effective));
    }

    /**
     * The "who reacted" list. Readable by whoever may read the target, anonymous included, since
     * a reaction is public to everyone the post is.
     */
    @GetMapping("/{targetType}/{targetId}")
    @Operation(summary = "List who reacted and with what, newest first; ?type= narrows to one reaction")
    public ApiResponse<PageResponse<ReactorResponse>> reactors(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable TargetType targetType,
            @PathVariable UUID targetId,
            @RequestParam(required = false) ReactionType type,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ApiResponse.of(
                reactionService.reactors(targetType, targetId, type, viewerId, pageable));
    }

    @GetMapping("/{targetType}/{targetId}/summary")
    @Operation(summary = "Reaction counts and the viewer's own reaction")
    public ApiResponse<ReactionSummaryResponse> summary(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable TargetType targetType,
            @PathVariable UUID targetId) {
        UUID viewerId = principal == null ? null : principal.getId();
        return ApiResponse.of(reactionService.summary(targetType, targetId, viewerId));
    }
}
