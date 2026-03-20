---
name: perf
description: "성능 병목을 분석하고 구체적인 개선안을 제시합니다.
  '성능 문제', 'N+1 쿼리', '느려', '최적화', '캐시 미스', '인덱스 누락', '쿼리 성능',
  'performance', 'N+1', 'slow query', 'optimize', 'cache miss', 'missing index' 등의 요청에 반응합니다."
---

Analyze performance hotspots and suggest concrete fixes.

Scope: $ARGUMENTS

## Instructions

1. Read the specified code to identify hotspots.
2. Focus on:
   - **N+1 queries**: missing `JOIN FETCH` or batch loading in JPA
   - **Missing indexes**: columns used in `WHERE` / `JOIN` without an index
   - **Cache misses**: data fetched repeatedly without Redis caching
   - **Blocking I/O on virtual threads**: synchronized blocks, ThreadLocal usage
   - **Kafka consumer lag**: slow processing or missing parallelism
   - **Cursor pagination**: check if offset-based pagination was used instead
3. For each finding, provide:
   - Location (file, line)
   - Problem description
   - Concrete fix with code example
4. Prioritize by impact; label each as HIGH / MEDIUM / LOW.
