# Environments

`dev` and `prod` are isolated. A test must never write to production data or use production
credentials.

| Environment | Plugin | Collection API | Dictionaries |
| --- | --- | --- | --- |
| Local dev | `./gradlew run` | `node server/server.mjs` on a local port | Local files / local translation clone |
| CI test | `./gradlew test`, Playwright | Temporary `server/.e2e-data` directory | Agent/validator fixtures |
| Production | Plugin Hub release | Cloudflare Worker + D1 | `main` of the translation repository |

## Local development

```bash
./gradlew run
cd server
PORT=8099 ADMIN_TOKEN=local-token DATA_DIR=.local-data node server.mjs
```

The Playwright suite starts its own local server with `ADMIN_TOKEN=e2e-token` and a temporary
data directory. It never contacts the production worker.

## Production

- Plugin: RuneLite Plugin Hub, pinned to a reviewed commit.
- Collection: `https://translator-enhanced-collection.ivan-ni224.workers.dev`.
- Data: `https://raw.githubusercontent.com/Romte1/translator-enhanced-translations/main/`.
- Automation: GitHub Actions in both repositories.

## Secrets

| Secret | Location | Never |
| --- | --- | --- |
| `ADMIN_TOKEN` | Cloudflare secret / local server env | Commit, log or send to clients |
| `PENDING_TOKEN` | Translation repository secret | Commit or expose in PR output |
| `LLM_API_KEY` | Translation repository secret | Commit, log or use in local tests |
| `CLOUDFLARE_API_TOKEN` | Protected deployment environment only | PR workflows or forks |

## Safety rules

- Do not run security scanners, stress tests, fuzzing or destructive audit tools against
  production endpoints.
- Do not run the translation worker against the production queue from a pull request.
- Do not point local tests at the production D1 database.
- Production health checks (`/health`, `/metrics`) are read-only and allowed.
- Promotion is `dev -> PR -> CI -> main`; production data changes go through a reviewed PR.
