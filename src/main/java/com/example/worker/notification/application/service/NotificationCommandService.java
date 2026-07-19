package com.example.worker.notification.application.service;
import com.example.worker.notification.application.dto.CreateNotificationCommand;
import com.example.worker.notification.application.port.*;
import com.example.worker.notification.domain.model.Notification;
import com.example.worker.notification.application.dto.NotificationStreamEvent;
import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization; import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant; import java.util.*;
@Service public class NotificationCommandService {
 private final NotificationRepository repository; private final NotificationStreamPublisher publisher;
 public NotificationCommandService(NotificationRepository repository,NotificationStreamPublisher publisher){this.repository=repository;this.publisher=publisher;}
 @Transactional public Notification create(CreateNotificationCommand c){ Optional<Notification> existing=repository.findByEventKey(c.eventKey()); if(existing.isPresent())return existing.get(); Notification saved=repository.save(Notification.create(c.projectId(),c.workflowRunId(),c.eventKey(),c.type(),c.severity(),c.title(),c.message())); afterCommit(()->publisher.publishCreated(NotificationStreamEvent.from(saved))); return saved; }
 @Transactional public boolean markRead(UUID projectId,UUID notificationId){ Optional<Notification> found=repository.findByNotificationIdAndProjectId(notificationId,projectId); if(found.isEmpty()||found.get().readAt()!=null)return false; Notification n=found.get(); n.markRead(Instant.now()); Notification saved=repository.save(n); afterCommit(()->publisher.publishRead(NotificationStreamEvent.from(saved))); return true; }
 @Transactional public int markRead(UUID projectId,List<UUID> ids){ if(ids.size()>100) throw new IllegalArgumentException("A maximum of 100 notifications may be marked read"); return (int)ids.stream().filter(id->markRead(projectId,id)).count(); }
 private static void afterCommit(Runnable task){ TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){public void afterCommit(){task.run();}}); }
}
