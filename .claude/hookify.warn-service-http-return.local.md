---
name: warn-service-http-return
enabled: true
event: file
action: block
conditions:
  - field: file_path
    operator: regex_match
    pattern: (Service|ServiceImpl)\.java$
  - field: new_text
    operator: regex_match
    pattern: ResponseEntity|ApiResult
---

**Service 레이어에서 HTTP 타입 감지**

`*Service.java` 또는 `*ServiceImpl.java` 파일에서 `ResponseEntity` 또는 `ApiResult`를 사용하고 있습니다.

**규칙:** 서비스 메서드는 순수 비즈니스 타입(`List<T>`, `T`, `void` 등)을 반환한다. HTTP 래핑은 Controller에서만 처리한다.

**잘못된 예:**
```java
// ServiceImpl에서
public ResponseEntity<ApiResult<List<DiagnosisDto>>> findAll(...) { ... }
```

**올바른 예:**
```java
// ServiceImpl에서
public List<DiagnosisDto> findAll(...) { ... }

// Controller에서
return ApiResult.successResponse(service.findAll(request));
```
