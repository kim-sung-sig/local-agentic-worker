package com.example.worker.notification.infrastructure.datasource;
import com.example.worker.notification.application.port.NotificationRepository;
import com.example.worker.notification.domain.model.Notification;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant; import java.util.*;
@Repository @Transactional public class NotificationRepositoryAdapter implements NotificationRepository {
 private final NotificationJpaRepository repository; public NotificationRepositoryAdapter(NotificationJpaRepository repository){this.repository=repository;}
 public Notification save(Notification n){return repository.save(NotificationJpaEntity.from(n)).toDomain();}
 public List<Notification> findAfter(UUID p,long c,int l){return repository.findAfter(p,c,PageRequest.of(0,l)).stream().map(NotificationJpaEntity::toDomain).toList();}
 public List<Notification> findLatest(UUID p,long b,int l){return repository.findLatest(p,b,PageRequest.of(0,l)).stream().map(NotificationJpaEntity::toDomain).toList();}
 public Optional<Notification> findByNotificationIdAndProjectId(UUID n,UUID p){return repository.findByNotificationIdAndProjectId(n,p).map(NotificationJpaEntity::toDomain);}
 public Optional<Notification> findByEventKey(String key){return repository.findByEventKey(key).map(NotificationJpaEntity::toDomain);}
 public long countUnread(UUID p){return repository.countByProjectIdAndReadAtIsNull(p);} public boolean hasExpiredBefore(UUID p,long c,Instant cutoff){return repository.hasExpiredBefore(p,c,cutoff);} public void deleteCreatedBefore(Instant cutoff){repository.deleteByCreatedAtBefore(cutoff);}
 public boolean existsCursor(UUID p,long c){return repository.existsByProjectIdAndId(p,c);}
}
