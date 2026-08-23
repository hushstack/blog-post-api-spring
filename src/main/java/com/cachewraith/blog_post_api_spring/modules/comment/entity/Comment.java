package com.cachewraith.blog_post_api_spring.modules.comment.entity;

import com.cachewraith.blog_post_api_spring.common.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "comments",
        indexes = {
            @Index(name = "ix_comments_post_created", columnList = "post_id, created_at"),
            @Index(name = "ix_comments_parent", columnList = "parent_comment_id")
        })
public class Comment extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Set when this comment is a reply to another comment. */
    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "reaction_count", nullable = false)
    @Builder.Default
    private long reactionCount = 0;
}
