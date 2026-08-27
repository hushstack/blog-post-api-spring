package com.cachewraith.blog_post_api_spring.modules.notification.service.impl;

import com.cachewraith.blog_post_api_spring.common.exception.ResourceNotFoundException;
import com.cachewraith.blog_post_api_spring.common.response.PageResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.dto.v1.response.NotificationResponse;
import com.cachewraith.blog_post_api_spring.modules.notification.entity.Notification;
import com.cachewraith.blog_post_api_spring.modules.notification.entity.NotificationType;
import com.cachewraith.blog_post_api_spring.modules.notification.repository.NotificationRepository;
import com.cachewraith.blog_post_api_spring.modules.notification.service.NotificationService;
import com.cachewraith.blog_post_api_spring.modules.user.dto.v1.response.AuthorSummary;
import com.cachewraith.blog_post_api_spring.modules.user.service.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    @Override
    @Transactional
    public void record(UUID recipientId, UUID actorId, NotificationType type, UUID targetId) {
        // Suppressed once here rather than at each publisher: commenting on your own post or
        // reposting yourself is not news, and every call site would otherwise repeat this check.
        if (recipientId.equals(actorId)) {
            return;
        }
        notificationRepository.save(
                Notification.builder()
                        .recipientId(recipientId)
                        .actorId(actorId)
                        .type(type)
                        .targetId(targetId)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(
            UUID recipientId, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page =
                unreadOnly
                        ? notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(
                                recipientId, pageable)
                        : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(
                                recipientId, pageable);

        // One batched author lookup for the whole page, not one per row.
        Set<UUID> actorIds =
                page.getContent().stream()
                        .map(Notification::getActorId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        Map<UUID, AuthorSummary> actors = userService.authorSummaries(actorIds);

        return PageResponse.from(page.map(notification -> toResponse(notification, actors)));
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Override
    @Transactional
    public NotificationResponse markRead(UUID notificationId, UUID recipientId) {
        Notification notification = requireOwn(notificationId, recipientId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        Map<UUID, AuthorSummary> actors =
                notification.getActorId() == null
                        ? Map.of()
                        : userService.authorSummaries(Set.of(notification.getActorId()));
        return toResponse(notificationRepository.save(notification), actors);
    }

    @Override
    @Transactional
    public int markAllRead(UUID recipientId) {
        return notificationRepository.markAllRead(recipientId, Instant.now());
    }

    /**
     * Someone else's notification is reported as missing rather than forbidden: a 403 would confirm
     * that the id exists, turning the endpoint into an enumeration oracle (OWASP A01).
     */
    private Notification requireOwn(UUID notificationId, UUID recipientId) {
        return notificationRepository
                .findById(notificationId)
                .filter(notification -> notification.getRecipientId().equals(recipientId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification"));
    }

    private NotificationResponse toResponse(
            Notification notification, Map<UUID, AuthorSummary> actors) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getActorId() == null ? null : actors.get(notification.getActorId()),
                notification.getTargetId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt());
    }
}
