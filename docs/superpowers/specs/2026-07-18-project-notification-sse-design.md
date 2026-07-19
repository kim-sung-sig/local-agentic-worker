# 프로젝트 알림 SSE 설계

## 상태

승인된 설계다. 이 문서는 계획된 동작을 설명하며, 현재 Engine에는 아직 해당 API가 없다.

## 목표와 범위

프로젝트 범위의 영속 알림 Inbox와 SSE 스트림을 제공한다. 대상은 워크플로 생성, 상태·단계 변경, Attempt 변경, 결정 기록, Activity 시작·완료·실패다. 초기 알림의 `publisher`는 항상 `SYSTEM`이다.

외부 채널(Slack, SMTP), 계정·인증, 프로젝트 팀 권한은 범위에서 제외한다. 모든 알림에 `projectId`를 보관해 이후 프로젝트 멤버십으로 접근을 제한할 수 있게 한다. 읽음 상태는 현재 프로젝트 전체에 공유된다.

## 아키텍처

도메인 모델, 애플리케이션 서비스·포트, 인프라 영속/SSE 어댑터, 얇은 API 컨트롤러를 갖는 독립 `notification` 컨텍스트를 추가한다. Engine 애플리케이션 계층은 범위 내 워크플로 이벤트마다 알림 명령을 발행한다. 알림 서비스는 `Notification`을 저장한 뒤 트랜잭션 커밋 후 SSE로 브로드캐스트한다.

`Notification`은 Inbox 항목이면서 재전송 원본이다. 정렬 가능한 불투명 `eventId`, `notificationId`, `projectId`, 선택적 `workflowRunId`, 유형, 심각도, 발행자, 제목, 일반 텍스트 메시지, `createdAt`, 공용 `readAt`을 가진다.

## API 계약

- `GET /api/projects/{projectId}/notifications`: cursor 페이지네이션 Inbox, `read`·`type` 필터 지원
- `GET /api/projects/{projectId}/notifications/unread-count`: 공용 미읽음 개수 반환
- `POST /api/projects/{projectId}/notifications/{notificationId}/read`: 멱등 공용 읽음 처리
- `POST /api/projects/{projectId}/notifications/read`: 최대 100건의 멱등 공용 일괄 읽음 처리
- `GET /api/projects/{projectId}/notifications/stream`: SSE 스트림

목록·스트림 payload는 도메인 엔티티가 아닌 API 응답 record로 반환한다. `message`는 일반 텍스트이며, UI 링크를 추가하면 앱 내부 경로만 허용한다.

## SSE 동작

스트림은 `connected`, `heartbeat`, `notification.created`, `notification.read`, `reset`을 전송한다. 연결은 최대 30분이며 15~30초마다 heartbeat를 전송하고 `retry: 3000`을 포함한다.

연결이 끊기면 클라이언트는 마지막으로 성공 처리한 `eventId`를 `Last-Event-ID` 헤더로 보낸다. 서버는 같은 프로젝트에서 해당 cursor 다음의 알림을 `eventId` 순서로 전송한 뒤 실시간 전송을 재개한다. 전달은 at-least-once이므로 클라이언트는 `eventId`로 중복을 제거한다.

SSE는 실시간 전달 전용이다. 최초 화면 진입은 Inbox와 unread-count REST API를 사용한다. cursor가 30일 보관 이력보다 오래되면 서버는 `reset`을 전송하고 클라이언트는 unread count와 Inbox를 다시 조회한다.

## 보관과 향후 계정 확장

알림은 30일 보관한 뒤 삭제한다. Inbox 이력과 SSE 재전송에 모두 적용한다. 연결이 없거나 전송에 실패해도 오류가 아니며, 저장된 Inbox와 유효한 이후 cursor로 복구한다.

계정과 프로젝트 팀을 도입하면 `Notification`은 유지하고 공용 `readAt`을 `NotificationRead(notificationId, userId, readAt)`으로 대체한다. 프로젝트 멤버십이 REST·SSE 조회를 필터링한다. `SYSTEM`은 유효한 시스템 발행자로 유지한다.

## 오류 처리와 검증

일괄 읽음 요청의 프로젝트 내 존재하지 않는 알림 ID는 무시한다. 단건 읽음은 기존 프로젝트 알림에 대해 멱등이다. 접근할 수 없는 프로젝트는 향후 프로젝트 권한의 표준 오류를 따르며, 임시 `operatorId`를 HTTP 입력으로 받지 않는다.

테스트는 알림 생성, 공용 읽음 멱등성, cursor 재전송 순서, 만료 cursor의 reset, 30일 보관 경계, SSE 재연결 계약을 검증한다. 저장된 알림이 트랜잭션 커밋 후에만 브로드캐스트되는지도 검증한다.
