package com.cachewraith.blog_post_api_spring.modules.post.repository;

import com.cachewraith.blog_post_api_spring.modules.post.entity.PostImage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, UUID> {

    List<PostImage> findByPostIdOrderByPositionAsc(UUID postId);

    List<PostImage> findByPostIdInOrderByPositionAsc(Collection<UUID> postIds);

    void deleteByPostId(UUID postId);
}
