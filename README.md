# Gloss

Learning articles that follow a book's real table of contents, with a tutor beside every page.

## Features

- Find a book and import its real table of contents
- An original learning article for each TOC item, generated when you open it
- Automatic checks on every article before it is shown
- A tutor chat beside each article, grounded in that article
- Bring your own DeepSeek API key, with monthly usage and limits
- Invite-only accounts, light and dark themes

## Getting started

```bash
docker compose --profile local up -d postgres
./gradlew check
pnpm --dir web check
```

## Development

See [CLAUDE.md](CLAUDE.md) for the rules this codebase follows.
