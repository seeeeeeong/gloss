# Gloss

책 제목으로 실제 목차를 가져와 목차 항목마다 독립 학습 글을 생성하고, 글 옆에서 튜터에게 묻는 서비스.
Kotlin + Spring Boot 4 (`core`, `server`), Next.js (`web`), PostgreSQL 18.

## Code Review Rules

### 언어와 형식

- 리뷰 코멘트와 요약은 **한국어**로 쓴다. 코드, 식별자, 파일 경로, 에러 메시지는 원문 그대로 둔다.
- 사람이 동료에게 말하듯 짧고 구체적으로. 무엇이 어떤 입력에서 어떻게 틀리는지를 먼저 쓰고, 고치는 방법은 그 다음에.

### 비용과 외부 API

- 사용자의 DeepSeek 키로 나가는 호출은 반드시 예산 예약(`BudgetService.reserve`) 뒤에 실행되고, 끝나면 정산 또는 해제되어야 한다. 경로 하나라도 빠지면 P1.
- 가격·한도·타임아웃 값에 왜 그 값인지 한 줄이 없으면 지적한다.
- `max_tokens` 를 지정하지 않은 호출, 모델 id·`max_tokens`·thinking 모드·한도 기본값을 다른 변경에 섞어 바꾼 것을 지적한다.
- 프롬프트의 고정 접두(system, plan, 글 본문) 앞이나 안에 시각, 요청 id, 사용자 이름처럼 요청마다 바뀌는 값을 넣으면 캐시가 깨진다. 지적한다.
- `finish_reason` 이 `stop` 이 아닌 응답을 완료로 저장하거나 보여주면 지적한다.

### 데이터와 트랜잭션

- append-only 테이블(`usage_ledger`, `generation_call`, `audit_event`, `login_failure`, `chat_message`, `post_version`, `post_check`)에 UPDATE·DELETE 경로를 만들면 P1.
- 트랜잭션 안에서 LLM 호출이나 SSE 송출을 하면 지적한다.
- JPA 와 JdbcClient 가 한 트랜잭션에 섞일 때 flush 없이 JdbcClient 가 JPA 가 쓴 행을 읽으면 지적한다.
- 머지된 Flyway 마이그레이션을 수정하면 P1. 새 마이그레이션에는 `<Name>SchemaTest` 가 있어야 한다.
- 모든 소유 데이터 조회에 `account_id` 조건이 있는지 본다. 없으면 다른 사용자 데이터가 보이는 P1.

### 보안

- 복호화한 API 키, 비밀번호, 임시 비밀번호가 로그·예외 메시지·응답·작업 payload 에 들어가면 P0.
- 운영자 전용 API 에 권한 검사가 빠지면 P1.
- 생성된 Markdown 을 신뢰하지 않는 입력으로 다루는지 본다(raw HTML, 링크 스킴, Mermaid strict).

### 구조 (docs 없이도 판단할 수 있게)

- 다른 모듈의 `domain/`, `persistence/`, `web/` 하위 타입을 직접 쓰면 지적한다. 모듈 루트의 공개 타입만 쓴다.
- 엔티티를 Controller 가 그대로 반환하면 지적한다. `XxxResponse` 로 바꾼다.
- Controller 가 에러 body 를 직접 만들거나 서비스 호출 외의 업무 로직을 가지면 지적한다. 에러는 모듈의 `XxxError` 를 `GlossException` 으로 던진다.
- 사용자에게 보이는 에러 메시지에 내부 정보(쿼리, 키, 경로, 스택)가 들어가면 지적한다.
- `core` 에 Spring, JPA, HTTP 클라이언트 의존이 생기면 지적한다.
- 시각을 `Instant.now()`·`LocalDateTime.now()` 로 직접 얻으면 지적한다. 주입한 `Clock` 을 쓴다.

### 지적하지 않을 것

- 포매팅, import 순서, 줄 길이 (ktlint, Biome 가 CI 에서 본다).
- 주석이 없다는 것 (위의 가격·한도·타임아웃은 제외). 오히려 코드를 되풀이하는 주석을 지적한다.
- 취향 차이의 이름 변경 제안.
