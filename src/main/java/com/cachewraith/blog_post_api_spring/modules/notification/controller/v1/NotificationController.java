package com.cachewraith.blog_post_api_spring.modules.notification.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.dto.v1.response.NotificationResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.dto.v1.response.UnreadCountResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.service.NotificationService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every route here is scoped to the authenticated principal — there is no path parameter for a
 * recipient, so one user can never read another's notifications (OWASP A01).
 */
@RestController
@RequestMapping(ApiVersions.V1 + "/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List your notifications, newest first")
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @CurrentUser AppUserPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(notificationService.list(principal.getId(), unreadOnly, pageable));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count your unread notifications")
    public ApiResponse<UnreadCountResponse> unreadCount(@CurrentUser AppUserPrincipal principal) {
        return ApiResponse.of(new UnreadCountResponse(notificationService.unreadCount(principal.getId())));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark one notification read")
    public ApiResponse<NotificationResponse> markRead(
            @CurrentUser AppUserPrincipal principal, @PathVariable UUID id) {
        return ApiResponse.of(notificationService.markRead(id, principal.getId()));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark every notification read")
    public ApiResponse<UnreadCountResponse> markAllRead(@CurrentUser AppUserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ApiResponse.of(new UnreadCountResponse(0));
    }
}
