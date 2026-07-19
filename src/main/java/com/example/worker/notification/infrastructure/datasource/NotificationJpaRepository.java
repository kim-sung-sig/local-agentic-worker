package com.example.worker.notification.infrastructure.datasource;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, Long> {
 @Query("select n from NotificationJpaEntity n where n.projectId=:p and n.id>:c order by n.id asc") List<NotificationJpaEntity> findAfter(@Param("p") UUID p,@Param("c") long c,org.springframework.data.domain.Pageable page);
 @Query("select n from NotificationJpaEntity n where n.projectId=:p and n.id<:b order by n.id desc") List<NotificationJpaEntity> findLatest(@Param("p") UUID p,@Param("b") long b,org.springframework.data.domain.Pageable page);
 Optional<NotificationJpaEntity> findByNotificationIdAndProjectId(UUID notificationId,UUID projectId); long countByProjectIdAndReadAtIsNull(UUID projectId);
 Optional<NotificationJpaEntity> findByEventKey(String eventKey);
 boolean existsByProjectIdAndId(UUID projectId, Long id);
 @Query("select count(n)>0 from NotificationJpaEntity n where n.projectId=:p and n.id<=:c and n.createdAt<:cutoff") boolean hasExpiredBefore(@Param("p") UUID p,@Param("c") long c,@Param("cutoff") Instant cutoff);
 void deleteByCreatedAtBefore(Instant cutoff);
}
