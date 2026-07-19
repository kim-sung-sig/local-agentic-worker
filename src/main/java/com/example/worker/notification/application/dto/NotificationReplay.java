package com.example.worker.notification.application.dto;
import java.util.List;
public record NotificationReplay(boolean reset, List<NotificationStreamEvent> events) { public static NotificationReplay resetResult(){return new NotificationReplay(true,List.of());} }
