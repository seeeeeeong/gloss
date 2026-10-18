# Gloss

A reader enters a book title; Gloss fetches the book's real table of contents, generates an
original learning article per TOC item on demand, and puts a tutor chat beside each article.
Invite-only: the operator creates accounts. Each reader brings their own DeepSeek API key.

`server/` is the Spring Boot app and the only Gradle module, `web/` is Next.js. The design lives in local, uncommitted notes: `docs/design/decisions.md`,
`architecture.md`, `conventions.md`, and the research behind them in `docs/research/`. Read
the relevant note before changing a module; follow the build order in `architecture.md` §8.

**Status: early.** Built one pull request at a time. An architecture rule arrives with the
code it guards.

## Never do this

- **Never commit, log or print a secret**: a `.env` value, a reader's API key (encrypted or
  decrypted), a password or a temporary password. Not in code, tests, commits, PRs or chat.
- **Never call DeepSeek on a reader's key without a budget reservation**, and never leave a
  reservation without a settle or release on every path.
  The one call that needs none runs no model: `GET /user/balance`, to check a key on save.
- **Never change a model id, `max_tokens`, the thinking mode or a limit default as a side
  effect.** Each is its own pull request that states the cost.
- **Never put company-internal material in this repository.** No code, data, schema,
  internal names or screenshots from work projects.
- **Never edit a merged Flyway migration.** A change is a new migration.
- Never start a dev server from a shell (`pnpm dev`, `bootRun`). process-compose owns them;
  see the global instructions.

## Constraints of the model API

DeepSeek, OpenAI-compatible chat completions, called with the reader's own key through
`LlmService` only: it opens the key, reserves, streams through `LlmGateway`, and settles or
releases on every path. The client is our own (`DeepSeekLlmGateway`), so the provider's exact
usage fields reach the ledger; another provider is another `LlmGateway`. These fail silently when gotten wrong: the request succeeds and the reader's bill grows.
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

- **Layout**: `gloss/common/` (config, entity, error, security, util) and one package per
  bounded context under `gloss/domain/` (`gloss/domain/account`, `gloss/domain/audit`), each in
  these layers:

  | Layer | Holds |
  |---|---|
  | `presentation/` | controllers, and the web's `security/` (filters, session) |
  | `service/` | services (the transaction boundary), the module's `XxxError`, listeners, types other modules get |
  | `dto/request/`, `dto/response/` | `XxxRequest`, `XxxResponse` |
  | `domain/` | entities, value objects, rules, `repository/` (Spring Data), `event/` |
  | `infrastructure/` | `JdbcXxx` on JdbcClient, files, external clients |

  `presentation → service → domain ← infrastructure`, and `service → infrastructure` for what
  has no interface. `dto/` is used by presentation and service and reads only the domain.
  `domain/` uses only the JDK, Kotlin, JPA, Spring Data, other models and `common`'s entity and
  util. `ArchitectureTest` checks both.
- **Modules**: each `gloss/domain/<context>` is a Spring Modulith module, declared in its
  `ModuleMetadata.kt` (`@ApplicationModule(id = "<context>", allowedDependencies = ...)`;
  detection is `explicitly-annotated`). Its layers are hidden; `service/` and
  `domain/event/` are opened as the named interfaces `<context> :: service` and
  `<context> :: event`; a module may open a package of value types its callers need, as
  `llm :: chat` (`domain/chat/`: messages, roles, thinking, finish reasons), rather than copy
  them into `service/`. `common` is an open module; `gloss/domain/` is a plain package, not a
  module. `ModularityTest` verifies the boundaries and that no package under `gloss/` or
  `gloss/domain/` forgot to declare itself.
- **No ports**: no use-case interfaces; an interface only for an external system
  (`LlmGateway`, `BookSourceGateway`).
- **Events** are for side effects another module owns (audit, key revocation). A past-tense
  data class in `domain/event/` with an `eventId` (UUIDv7) and `occurredAt`, ids only, never a
  secret. The listener is `@ApplicationModuleListener(id = "<module>.<source>")`: it runs after
  commit on its own thread, and `event_publication` redelivers it, so it must be idempotent.
  The id and the event's class name and fields are stored, so changing them strands waiting
  events; a move needs a migration rewriting `event_publication.event_type`.
- **Sessions**: Spring Session stores `gloss.common.security.SessionAccount` by class name;
  moving or renaming it signs every reader out.
- **Rules** (`XxxPolicy`, `XxxDecision`, `XxxParser`, `XxxPrompt`, `XxxCheck`, `XxxCipher`) sit
  in `domain/` and depend only on Kotlin, the JDK's pure packages and other rule classes;
  `ArchitectureTest` checks it with an allow list.
- **Value objects** (`LoginId`, `DisplayName`, `Password`) are `@JvmInline value class`es with a
  private constructor: only `of(raw)`, or a decision's `Accepted`, makes one, so a value in hand
  is valid. Entity factories and methods take them, never a raw `String`. They are held to the
  same purity rule as the rule classes.
- **Persistence**: JPA for accounts, keys, books, TOCs, plans, posts and chat sessions.
  JdbcClient for append-only ledgers, the job queue and chat messages. Append-only tables are
  guarded by triggers and, in JPA, `@Immutable`.
- **DTOs**: `XxxRequest` / `XxxResponse` data classes in `dto/request/` and `dto/response/`, one
  per file. The service takes requests and returns responses (`MyAccountResponse.from(account)`);
  a method another module calls returns a type of its own from `service/`, since `dto/` is hidden. Entities never
  leave through a controller. Validation annotations name their target (`@field:NotBlank`).
- **Controllers** are thin: one service call, its result returned as is. They return the response
  type, never a `ResponseEntity`; a status other than 200 is `@ResponseStatus`. Sign-in and
  password change also call `SignedInSession`, since session work is not the service's.
  One step per line with a named value, rather than calls nested inside calls.
- **Errors**: each module has a `service/XxxError` enum implementing `ErrorCode` (status,
  `module.snake_case` code, Korean message shown as is). The service throws
  `GlossException(XxxError.Y)`; `GlobalExceptionHandler` and the security filters answer
  `{code, message}`. A sealed result only where the caller really branches on it.
- **Paths**: `/api/auth/*` for the session (sign-in, sign-out, CSRF), `/api/me/*` for the
  signed-in account; each module's own controller answers its part of `/api/me` and takes the
  reader as `@CurrentAccount account: SessionAccount`.
- **Injected dependencies** are named after their type: `accountService: AccountService`.
- **Entities** are made by a named factory (`Account.createFirstOperator(...)`) behind a
  private constructor, held in a variable, then saved on the next line.
- **Parameters**: a function takes four at most; a constructor keeps detekt's default, since a
  bean's parameters are its collaborators. A call with four or more arguments names them;
  detekt catches one that names none. A service method that needs more takes a request or a
  command object.
- **Transactions** stay short: no model call and no SSE inside one.
- Naming, JPA rules and test layout: `docs/design/conventions.md`.

## Comments

- **Few comments.** Only the "why" someone would get wrong without it: a price, a limit, a
  timeout, a cache setting, a trap. One line where possible.
- No "what", no history, no sources, nothing the code or a config key already says.
- No commented-out code. No `TODO`/`FIXME` without an issue link.
- Code, identifiers, comments, commits, pull request titles and the README are English.
  Design docs and pull request descriptions are Korean.

## Conventions

- Time comes from an injected `java.time.Clock`; detekt forbids `now()` and
  `currentTimeMillis`. Tests move a clock; they never sleep.
- IDs are UUIDv7 made by the application (`gloss.common.util.UuidV7`).
- Closely related types share a file named after the first of them: `Password.kt` opens
  with `Password`, then `PasswordDecision`; a module's events share `XxxEvent.kt`. A request or
  response has a file of its own. A rule or value object with its own test keeps its own file. Events stay top-level, never nested: their class name is stored.
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
  `chore(deploy): <summary>`. Never rebase merge a release: GitHub's rebase merge rewrites
  SHAs, so `main` and `dev` would disagree and the next release would carry the same
  commits again.
- **Commits are English.** `<type>(<scope>): <imperative summary>`, lowercase, no period,
  72 characters at most, scope from `cog.toml`. The body is `- ` bullets in the imperative,
  the last one on tests when there are any. CI checks every commit.

  ```
  feat(account): add login, logout and session endpoints

  - Store the account id and session epoch in a JDBC-backed session
  - Reject requests whose session epoch no longer matches the account
  - Add regression tests for epoch mismatch and forced password change
  ```
- **Pull request titles follow the commit format**, English and checked the same way:
  `feat(account): add sign-in, sessions and password change`. With one commit, the title
  is its subject.
- **Pull request descriptions are Korean.** They pick sections by the change, each a list
  of `~했습니다` bullets:

  | Change | Sections |
  |---|---|
  | Most | `## 설명` |
  | A fix | `## 문제` (one or two sentences), `## 변경 사항` |
  | Rules about who may do what | add `## 동작`: each case and what it allows |
  | A migration | add `## 데이터베이스`: the migration, its constraints and triggers |

  Keep it as short as a release note: three to five bullets per section, each one line on
  what the reader can now do or what changed for them. No pixel values, colors, copy
  strings or step-by-step UI, since the diff shows those; an API change names its endpoints.
  Lead with behavior a user sees, then implementation. When permissions or existing
  behavior are touched, say what stays the same (`기존 ~은 유지합니다`). No verification
  line: CI shows the checks; mention only checks CI cannot see, such as a browser pass. The
  English words developers actually say (스켈레톤, 세팅, 룰, 엔드포인트, 마이그레이션) rather
  than forced Korean. No English translation, no attribution lines.
- One change per branch, usually one commit.
- Commits are authored by the personal account, set per clone (the global identity is a
  work one):

  ```bash
  git config user.name  "seeeeeeong"
  git config user.email "136677284+seeeeeeong@users.noreply.github.com"
  ```

## Commands

```bash
./gradlew check           # compile, test, ktlint, detekt, Konsist, ArchUnit, Modulith
./gradlew spotlessApply   # fix Kotlin formatting
pnpm --dir web check      # web: Biome, type check, Vitest
pnpm --dir web fix        # web: fix formatting and lint
pnpm --dir web build      # web: production build

docker compose --profile local up -d postgres   # the local database the server expects (port 55434)

# tests need Docker running: they start PostgreSQL through Testcontainers
# once per clone: hooks guard main and dev, format staged files, verify commit messages
brew install lefthook cocogitto ktlint && lefthook install
```
