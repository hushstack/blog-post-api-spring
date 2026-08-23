package com.cachewraith.blog_post_api_spring.modules.comment.repository;

import com.cachewraith.blog_post_api_spring.modules.comment.entity.Comment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByPostIdAndParentCommentIdIsNullOrderByCreatedAtDesc(
            UUID postId, Pageable pageable);

    Page<Comment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId, Pageable pageable);

    long countByPostId(UUID postId);

    void deleteByPostId(UUID postId);
}
