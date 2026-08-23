package com.cachewraith.blog_post_api_spring.modules.user.service.impl;

import com.cachewraith.blog_post_api_spring.common.exception.BusinessException;
import com.cachewraith.blog_post_api_spring.common.exception.ErrorCode;
import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.integration.storage.StorageService;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.request.UpdateProfileRequest;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.PublicUserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.UserResponse;
import com.cachewraith.blog_post_api_spring.modules.user.entity.User;
import com.cachewraith.blog_post_api_spring.modules.user.mapper.v1.UserMapper;
import com.cachewraith.blog_post_api_spring.modules.user.repository.UserRepository;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getOwnProfile(UUID userId) {
        return userMapper.toResponse(requireUser(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicUserResponse getPublicProfile(UUID userId) {
        return userMapper.toPublicResponse(requireUser(userId));
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);

        if (request.username() != null && !request.username().equalsIgnoreCase(user.getUsername())) {
            if (userRepository.existsByUsernameIgnoreCase(request.username())) {
                throw new BusinessException(ErrorCode.USERNAME_ALREADY_USED);
            }
            user.setUsername(request.username());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(UUID userId, MultipartFile file) {
        User user = requireUser(userId);
        String previous = user.getAvatarUrl();

        user.setAvatarUrl(storageService.storeImage(file, AVATAR_FOLDER));
        User saved = userRepository.save(user);

        if (previous != null) {
            storageService.delete(previous);
        }
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User"));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, AuthorSummary> authorSummaries(Collection<UUID> userIds) {
        Set<UUID> distinct = Set.copyOf(userIds);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(distinct).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toAuthorSummary, (a, b) -> a));
    }
}
