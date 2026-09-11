package com.cachewraith.blog_post_api_spring.modules.friendship.repository;

import com.cachewraith.blog_post_api_spring.modules.friendship.entity.Friendship;
import com.cachewraith.blog_post_api_spring.modules.friendship.entity.FriendshipStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query(
            """
            select f from Friendship f
            where (f.requesterId = :a and f.addresseeId = :b)
               or (f.requesterId = :b and f.addresseeId = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    /** Every row between {@code userId} and any of {@code otherIds}, either direction, one query. */
    @Query(
            """
            select f from Friendship f
            where (f.requesterId = :userId and f.addresseeId in :otherIds)
               or (f.addresseeId = :userId and f.requesterId in :otherIds)
            """)
    List<Friendship> findBetweenUserAndAny(
            @Param("userId") UUID userId, @Param("otherIds") Collection<UUID> otherIds);

    Page<Friendship> findByAddresseeIdAndStatus(
            UUID addresseeId, FriendshipStatus status, Pageable pageable);

    /** Ids of everyone the user is ACCEPTED friends with, in either direction. */
    @Query(
            """
            select case when f.requesterId = :userId then f.addresseeId else f.requesterId end
            from Friendship f
            where (f.requesterId = :userId or f.addresseeId = :userId)
              and f.status = com.cachewraith.blog_post_api_spring.modules.friendship.entity.FriendshipStatus.ACCEPTED
            """)
    List<UUID> findFriendIds(@Param("userId") UUID userId);

    @Query(
            """
            select f from Friendship f
            where (f.requesterId = :userId or f.addresseeId = :userId)
              and f.status = :status
            """)
    Page<Friendship> findAllForUser(
            @Param("userId") UUID userId, @Param("status") FriendshipStatus status, Pageable pageable);
}
