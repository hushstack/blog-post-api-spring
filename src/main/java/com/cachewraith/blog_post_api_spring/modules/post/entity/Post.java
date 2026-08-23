package com.cachewraith.blog_post_api_spring.modules.post.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "posts",
        indexes = {
            @Index(name = "ix_posts_author_created", columnList = "author_id, created_at"),
            @Index(name = "ix_posts_original", columnList = "original_post_id")
        })
public class Post extends BaseEntity {

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Visibility visibility = Visibility.PUBLIC;

    /** Set when this post is a repost of another. */
    @Column(name = "original_post_id")
    private UUID originalPostId;

    @Column(name = "reaction_count", nullable = false)
    @Builder.Default
    private long reactionCount = 0;

    @Column(name = "comment_count", nullable = false)
    @Builder.Default
    private long commentCount = 0;

    @Column(name = "repost_count", nullable = false)
    @Builder.Default
    private long repostCount = 0;
}
