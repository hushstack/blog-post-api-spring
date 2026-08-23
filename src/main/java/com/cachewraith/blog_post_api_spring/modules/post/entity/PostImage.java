package com.cachewraith.blog_post_api_spring.modules.post.entity;

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
@Table(name = "post_images", indexes = @Index(name = "ix_post_images_post", columnList = "post_id"))
public class PostImage extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private int position;
}
