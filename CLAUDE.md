# Gloss

A reader enters a book title; Gloss fetches the book's real table of contents, generates an
original learning article per TOC item on demand, and puts a tutor chat beside each article.
Invite-only: the operator creates accounts. Each reader brings their own DeepSeek API key.

`core/` holds rules with no framework, `server/` is the only Spring Boot module, `web/` is
Next.js. The design lives in local, uncommitted notes: `docs/design/decisions.md`,
`architecture.md`, `conventions.md`, and the research behind them in `docs/research/`. Read
the relevant note before changing a module; follow the build order in `architecture.md` §8.

**Status: early.** Built one pull request at a time. An architecture rule arrives with the
code it guards.

## Never do this

- **Never commit, log or print a secret**: a `.env` value, a reader's API key (encrypted or
  decrypted), a password or a temporary password. Not in code, tests, commits, PRs or chat.
- **Never call DeepSeek on a reader's key without a budget reservation**, and never leave a
  reservation without a settle or release on every path.
- **Never change a model id, `max_tokens`, the thinking mode or a limit default as a side
  effect.** Each is its own pull request that states the cost.
- **Never put company-internal material in this repository.** No code, data, schema,
  internal names or screenshots from work projects.
- **Never edit a merged Flyway migration.** A change is a new migration.
- Never start a dev server from a shell (`pnpm dev`, `bootRun`). process-compose owns them;
  see the global instructions.

## Constraints of the model API

DeepSeek, OpenAI-compatible chat completions, called through Spring AI with the reader's own
key. These fail silently when gotten wrong: the request succeeds and the reader's bill grows.
Prices, limits and model ids are verified against DeepSeek's documentation, never written
from memory.

| Constraint | Consequence |
|---|---|
| The context cache matches an identical prompt prefix, automatically | Nothing that varies per request goes into the system prompt, the book plan or the article before the conversation. The plan is canonical JSON, byte-identical for every item of a book |
| A changed earlier turn is a new prefix from that turn on | Conversation history is append-only. A correction is a new message |
| Caching is best effort and reported per request (`prompt_cache_hit_tokens`, `prompt_cache_miss_tokens`) | Both are recorded for every call. A falling hit rate is a prompt-assembly regression |
| `finish_reason` is not always `stop` | Checked before anything is stored or shown as complete |
| Model ids are exact strings from the provider's documentation | One constant, with a KDoc naming the documented model and its price |

## Architecture

- **Modules**: each package directly under `gloss` is a Spring Modulith application module.
  Its top-level package is its API; `domain/`, `web/`, `persistence/`, `client/`, `worker/`
  are hidden. `ModularityTest` verifies it. Dependencies are declared in `ModuleMetadata.kt`.
- **Layers** inside a module: `web/XxxController` → `XxxService` (transaction boundary) →
  `domain/XxxRepository` (JPA + Kotlin JDSL) or `persistence/JdbcXxx` (JdbcClient). No use-case
  interfaces, no ports; an interface only for an external system (`LlmGateway`, `BookSourceGateway`).
- **Persistence**: JPA for accounts, keys, books, TOCs, plans, posts and chat sessions.
  JdbcClient for append-only ledgers, the job queue and chat messages. Append-only tables are
  guarded by triggers and, in JPA, `@Immutable`.
- **DTOs**: `XxxRequest` / `XxxResponse` data classes, in `web/` only. Entities never leave
  through a controller. Validation annotations name their target (`@field:NotBlank`).
- **Transactions** stay short: no model call and no SSE inside one.
- Naming, JPA rules and test layout: `docs/design/conventions.md`.

## Comments

- **Do not comment "what".** The code says it.
- **Do comment "why"**, especially a limit, a price, a cache setting or a timeout.
- Every constant carries a KDoc. No commented-out code. No `TODO`/`FIXME` without an issue link.
- Code, identifiers, comments and the README are English. Design docs, pull requests and
  commits are Korean.

## Conventions

- Time comes from an injected `java.time.Clock`. Tests move a clock; they never sleep.
- IDs are UUIDv7 made by the application (`gloss.common.UuidV7`).
- Tests mirror production files: `PostService.kt` → `PostServiceTest.kt`, same package.
  Each migration `V<n>__<name>.sql` has a `<Name>SchemaTest` against real PostgreSQL.
- Every architecture rule has a fixture that breaks it.

## Commits and pull requests

- `dev` collects work; `main` is what is deployed. Branch from a fresh `origin/dev`:
  `git fetch && git switch -c <type>/<english-slug> --no-track origin/dev`. Without
  `--no-track` the branch pushes to `dev` by default and the pre-push hook cannot resolve it.
  Pull requests target `dev`.
- Work pull requests are **rebase merged**: every commit on the branch lands on `dev` as
  written. Clean up fix-ups before merging
  (`git commit --fixup <sha>`, then `GIT_SEQUENCE_EDITOR=: git rebase -i --autosquash origin/dev`).
- Deploying is a release pull request from `dev` to `main`, **merge commit** only, titled
  `chore(deploy): <요약>`. Never rebase merge a release: GitHub's rebase merge rewrites
  SHAs, so `main` and `dev` would disagree and the next release would carry the same
  commits again.
- Commit and PR title: `<type>(<scope>): <짧은 한국어 명사형 요약>`, scope from `cog.toml`.
  Examples: `chore(repo): 프로젝트 스켈레톤 세팅`, `feat(account): 계정 생성 API 추가`,
  `fix(chat): 잘린 응답이 완료로 표시되는 문제 수정`. CI checks the title and every commit.
- PR description: a `## 설명` header, then bullets. Commit body: the same bullets, no header.
  One bullet per change, in polite past tense (`~추가했습니다`, `~수정했습니다`), the last one
  on how it was checked (`~통과를 확인했습니다`). The English words developers actually say
  (스켈레톤, 세팅, 룰, 엔드포인트, 마이그레이션) rather than forced Korean. Only what is worth
  knowing; the diff says the rest. No other headers, no long justifications, no English
  translation, no attribution lines.

  ```
  ## 설명

  - 상수 KDoc을 강제하던 Konsist 룰을 삭제했습니다.
  - ./gradlew check 통과를 확인했습니다.
  ```
- One change per branch, usually one commit.
- Commits are authored by the personal account, set per clone (the global identity is a
  work one):

  ```bash
  git config user.name  "seeeeeeong"
  git config user.email "136677284+seeeeeeong@users.noreply.github.com"
  ```

## Commands

```bash
./gradlew check           # core, server, architecture: compile, test, ktlint, detekt, Konsist, ArchUnit, Modulith
./gradlew spotlessApply   # fix Kotlin formatting
pnpm --dir web check      # web: Biome, type check, Vitest
pnpm --dir web fix        # web: fix formatting and lint
pnpm --dir web build      # web: production build

docker compose --profile local up -d postgres   # the local database the server expects (port 55433)

# tests need Docker running: they start PostgreSQL through Testcontainers
# once per clone: hooks guard main and dev, format staged files, verify commit messages
brew install lefthook cocogitto ktlint && lefthook install
```
