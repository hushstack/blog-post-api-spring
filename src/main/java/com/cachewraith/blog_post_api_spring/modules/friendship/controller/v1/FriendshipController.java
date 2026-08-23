package com.cachewraith.blog_post_api_spring.modules.friendship.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.friendship.dto.v1.response.FriendshipResponse;
import com.cachewraith.blog_post_api_spring.modules.friendship.service.FriendshipService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiVersions.V1 + "/friends")
@RequiredArgsConstructor
@Tag(name = "Friends")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/requests/{userId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Send a friend request")
    public ApiResponse<FriendshipResponse> sendRequest(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID userId) {
        return ApiResponse.of(friendshipService.sendRequest(principal.getId(), userId));
    }

    @PutMapping("/requests/{id}/accept")
    @Operation(summary = "Accept a friend request addressed to you")
    public ApiResponse<FriendshipResponse> accept(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.of(friendshipService.accept(principal.getId(), id));
    }

    @PutMapping("/requests/{id}/decline")
    @Operation(summary = "Decline a friend request addressed to you")
    public ApiResponse<FriendshipResponse> decline(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.of(friendshipService.decline(principal.getId(), id));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unfriend a user")
    public void unfriend(@CurrentUser AppUserPrincipal principal, @PathVariable UUID userId) {
        friendshipService.unfriend(principal.getId(), userId);
    }

    @GetMapping
    @Operation(summary = "List accepted friends")
    public ApiResponse<PageResponse<FriendshipResponse>> friends(
            @CurrentUser AppUserPrincipal principal, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(friendshipService.listFriends(principal.getId(), pageable));
    }

    @GetMapping("/requests")
    @Operation(summary = "List pending incoming requests")
    public ApiResponse<PageResponse<FriendshipResponse>> incomingRequests(
            @CurrentUser AppUserPrincipal principal, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(friendshipService.listIncomingRequests(principal.getId(), pageable));
    }
}
