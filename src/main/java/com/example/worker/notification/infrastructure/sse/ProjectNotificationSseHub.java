package com.example.worker.notification.infrastructure.sse;
import com.example.worker.notification.application.port.*; import com.example.worker.notification.application.dto.NotificationStreamEvent;
import org.springframework.stereotype.Component; import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;
import java.io.IOException; import java.util.*; import java.util.concurrent.*;
@Component public class ProjectNotificationSseHub implements NotificationStreamPublisher, NotificationStreamSubscriber {
 private static final long TIMEOUT=30*60*1000L; private final Map<UUID,Set<SseEmitter>> emitters=new ConcurrentHashMap<>();
 public SseEmitter subscribe(UUID projectId){ SseEmitter emitter=new SseEmitter(TIMEOUT); emitters.computeIfAbsent(projectId,k->ConcurrentHashMap.newKeySet()).add(emitter); Runnable remove=()->emitters.getOrDefault(projectId,Set.of()).remove(emitter); emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(e->remove.run()); try { emitter.send(SseEmitter.event().reconnectTime(3000).name("connected")); } catch (IOException e) { emitter.complete(); } return emitter; }
 public void publishCreated(NotificationStreamEvent n){publish(n,"notification.created");} public void publishRead(NotificationStreamEvent n){publish(n,"notification.read");}
 public void reset(SseEmitter emitter){send(emitter,"reset",null,Map.of("reason","cursor_expired"));}
 public void replay(SseEmitter emitter, NotificationStreamEvent notification){send(emitter,"notification.created",notification.eventId(),notification);}
 @Scheduled(fixedRate = 20000) public void heartbeat(){ emitters.values().forEach(set->set.forEach(e->send(e,"heartbeat",null,null))); }
 private void publish(NotificationStreamEvent n,String name){ for(SseEmitter e:emitters.getOrDefault(n.projectId(),Set.of())) send(e,name,n.eventId(),n); }
 private void send(SseEmitter e,String name,String id,Object data){try{SseEmitter.SseEventBuilder b=SseEmitter.event().name(name); if(id!=null)b.id(id); if(data!=null)b.data(data); e.send(b);}catch(IOException|RuntimeException ex){e.complete();}}
}
