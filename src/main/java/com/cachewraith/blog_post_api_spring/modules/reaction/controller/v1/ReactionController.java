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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1 + "/reactions")
@RequiredArgsConstructor
@Tag(name = "Reactions")
public class ReactionController {

    private final ReactionService reactionService;

    @PutMapping("/{targetType}/{targetId}")
    @Operation(summary = "Add or change your reaction")
    public ApiResponse<ReactionSummaryResponse> upsert(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable TargetType targetType,
            @PathVariable UUID targetId,
            @Valid @RequestBody ReactionRequest request) {
        return ApiResponse.of(
                reactionService.upsert(principal.getId(), targetType, targetId, request));
    }

    @DeleteMapping("/{targetType}/{targetId}")
    @Operation(summary = "Remove your reaction")
    public ApiResponse<ReactionSummaryResponse> remove(
            @CurrentUser AppUserPrincipal principal,
            @PathVariable TargetType targetType,
            @PathVariable UUID targetId) {
        return ApiResponse.of(reactionService.remove(principal.getId(), targetType, targetId));
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
