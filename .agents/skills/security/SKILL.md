---
name: security
description: "보안 취약점을 검토하고 개선안을 제시합니다.
  '보안 검토', '취약점', '인증 확인', '권한 체크', 'IDOR', 'XSS', '보안 이슈', '@PreAuthorize',
  'security review', 'vulnerability', 'auth check', 'permission', 'IDOR', 'injection' 등의 요청에 반응합니다."
---

Review the specified scope for security risks.

Scope: $ARGUMENTS

## Instructions

1. Read the specified files.
2. Check for:
   - **Authentication/Authorization**: endpoints missing `@PreAuthorize` or JWT validation; improper role checks
   - **Input validation**: missing `@Valid`, unvalidated user input reaching the database or Kafka
   - **SQL injection**: JPQL/native queries with string concatenation
   - **Sensitive data exposure**: tokens, passwords, or PII logged or returned in responses
   - **Mass assignment**: DTOs mapped directly to entities without filtering
   - **IDOR**: resource access not scoped to the authenticated user
   - **Kafka/Redis**: unauthenticated topic access or sensitive data in cache without TTL
3. For each finding, provide:
   - Severity: CRITICAL / HIGH / MEDIUM / LOW
   - Location (file, line)
   - Description and exploit scenario
   - Recommended fix

## Checklist

- [ ] All REST endpoints have appropriate `@PreAuthorize`
- [ ] All user inputs validated with `@Valid` or manual checks
- [ ] No string concatenation in JPQL/native queries
- [ ] No tokens/passwords in logs or API responses
- [ ] Resource ownership verified before access (IDOR check)
- [ ] Redis cached sensitive data has TTL set
