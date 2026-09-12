package com.cachewraith.blog_post_api_spring.modules.post.repository;

import com.cachewraith.blog_post_api_spring.modules.post.entity.Post;
import com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * One author's timeline, filtered to the visibility values the viewer is entitled to. The
     * filter is part of the query rather than applied to the results so that page totals describe
     * what the viewer actually receives, and so a FRIENDS/PRIVATE post cannot leak (OWASP A01).
     */
    @Query(
            """
            select p from Post p
            where p.authorId = :authorId and p.visibility in :visibilities
            order by p.createdAt desc
            """)
    Page<Post> findByAuthorVisibleTo(
            @Param("authorId") UUID authorId,
            @Param("visibilities") Collection<Visibility> visibilities,
            Pageable pageable);

    List<Post> findAllByIdIn(Collection<UUID> ids);

    /**
     * Cursor page of the viewer's feed: every PUBLIC post, the viewer's own posts, and FRIENDS
     * posts by the given authors. Visibility is enforced in the query itself, not after the fact,
     * so a FRIENDS/PRIVATE post cannot leak through the feed (OWASP A01). A viewer with no friends
     * therefore still gets a full feed of public posts rather than an empty page.
     *
     * <p>Split into first-page and subsequent-page variants rather than one query with an
     * "or :cursor is null" branch: Postgres cannot infer the type of a bare parameter compared only
     * against NULL, and a cast to make it work would be less readable than two honest queries.
     */
    @Query(
            """
            select p from Post p
            where p.visibility = com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility.PUBLIC
               or p.authorId = :viewerId
               or (p.authorId in :friendIds
                   and p.visibility = com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility.FRIENDS)
            order by p.createdAt desc
            """)
    List<Post> findFeedFirstPage(
            @Param("viewerId") UUID viewerId,
            @Param("friendIds") Collection<UUID> friendIds,
            Pageable pageable);

    @Query(
            """
            select p from Post p
            where (p.visibility = com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility.PUBLIC
                   or p.authorId = :viewerId
                   or (p.authorId in :friendIds
                       and p.visibility = com.cachewraith.blog_post_api_spring.modules.post.entity.Visibility.FRIENDS))
              and p.createdAt < :cursor
            order by p.createdAt desc
            """)
    List<Post> findFeedAfter(
            @Param("viewerId") UUID viewerId,
            @Param("friendIds") Collection<UUID> friendIds,
            @Param("cursor") Instant cursor,
            Pageable pageable);

    @Modifying
    @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :postId")
    void adjustCommentCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("update Post p set p.repostCount = p.repostCount + :delta where p.id = :postId")
    void adjustRepostCount(@Param("postId") UUID postId, @Param("delta") long delta);

    @Modifying
    @Query("update Post p set p.reactionCount = :count where p.id = :postId")
    void setReactionCount(@Param("postId") UUID postId, @Param("count") long count);
}
