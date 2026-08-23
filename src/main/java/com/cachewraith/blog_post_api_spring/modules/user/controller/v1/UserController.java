package com.cachewraith.blog_post_api_spring.modules.user.controller.v1;

import com.cachewraith.blog_post_api_spring.common.annotation.CurrentUser;
import com.cachewraith.blog_post_api_spring.common.constant.ApiVersions;
import com.cachewraith.blog_post_api_spring.common.response.ApiResponse;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.request.UpdateProfileRequest;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.PublicUserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.UserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import com.cachewraith.blog_post_api_spring.security.userdetails.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(ApiVersions.V1 + "/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Own profile")
    public ApiResponse<UserResponse> me(@CurrentUser AppUserPrincipal principal) {
        return ApiResponse.of(userService.getOwnProfile(principal.getId()));
    }

    /** The id comes from the token, never from the request — see {@code @CurrentUser}. */
    @PutMapping("/me")
    @Operation(summary = "Update own profile")
    public ApiResponse<UserResponse> updateMe(
            @CurrentUser AppUserPrincipal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.of(userService.updateProfile(principal.getId(), request));
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Replace own avatar")
    public ApiResponse<UserResponse> updateAvatar(
            @CurrentUser AppUserPrincipal principal, @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(userService.updateAvatar(principal.getId(), file));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Public profile")
    public ApiResponse<PublicUserResponse> publicProfile(@PathVariable UUID id) {
        return ApiResponse.of(userService.getPublicProfile(id));
    }
}
