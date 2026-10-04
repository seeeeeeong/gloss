# Gloss

책 제목을 넣으면 실제 목차를 가져와, 목차 항목마다 독립된 학습 글을 만들고 글 옆에서 튜터에게 묻는 학습 서비스.

- 초대형: 운영자가 계정을 만들어 준다.
- 사용자가 자기 DeepSeek API 키를 등록해서 쓴다.
- 생성된 글은 책 본문 요약이 아니다.

`server/` 는 Kotlin, Spring Boot. `web/` 은 Next.js.

## 개발

```bash
docker compose --profile local up -d postgres   # 로컬 DB (55433)
./gradlew check                                 # 서버 전체 검사 (Docker 필요)
pnpm --dir web check && pnpm --dir web build    # 웹 검사와 빌드
brew install lefthook cocogitto ktlint && lefthook install   # 클론마다 한 번
```
