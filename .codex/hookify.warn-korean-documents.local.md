---
name: warn-korean-documents
enabled: true
event: file
action: warn
conditions:
  - field: file_path
    operator: regex_match
    pattern: (^|[\/])docs[\/].*\.md$
---

문서 작성·수정은 한글을 기본 언어로 사용하세요. 코드 식별자, API 경로, 표준 기술 용어만 필요한 범위에서 영어 표기를 유지합니다.
