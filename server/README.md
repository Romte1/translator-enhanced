# Collection server

Aggregates the untranslated texts that clients send when `General -> Enable text capture` is
on. The worker reads the aggregated queue, so translations are built from what **all**
players see, not from one machine.

```
client (opt-in) ──POST /api/v1/data-collection──► server ──GET /api/v1/pending──► worker ──► PR
```

## Endpoints

| Method | Path | Auth | Payload |
| --- | --- | --- | --- |
| `POST` | `/api/v1/data-collection` | none | `[{"type":"interface","text":"..."}]` |
| `GET` | `/api/v1/pending?limit=500&type=interface` | bearer | sorted by `count` |
| `POST` | `/api/v1/translated` | bearer | `{"items":[{"type":"interface","text":"..."}]}` |
| `GET` | `/health` | none | counters |

## Where to host it

| Host | Cost | Verdict |
| --- | --- | --- |
| **Cloudflare Workers + D1** | free: 100k requests/day, 5 GB | recommended |
| Railway | trial credits, then ~$5/month | not free |
| Render free | sleeps after 15 min, disk is ephemeral | loses the queue |
| Fly.io | no free tier | paid |
| VPS | ~$4/month | paid |

## Cloudflare (recommended, free)

```bash
npm install -g wrangler
wrangler login

wrangler d1 create translator-enhanced
# copy the database_id into wrangler.toml

cd server
wrangler d1 execute translator-enhanced --file schema.sql --remote
wrangler secret put ADMIN_TOKEN
wrangler deploy
```

The URL is printed by `wrangler deploy`, something like
`https://translator-enhanced-collection.<account>.workers.dev`. Endpoints:

```
POST https://<worker>/api/v1/data-collection    ← plugin
GET  https://<worker>/api/v1/pending            ← worker, bearer ADMIN_TOKEN
```

Install `server/worker.mjs`, `server/schema.sql` and `server/wrangler.toml` at the repository
root of the worker project (or deploy from this folder).

## Self hosting

`server/server.mjs` is the same service with no dependencies, for a VPS or a container:

```bash
ADMIN_TOKEN=long-random-string DATA_DIR=/data node server/server.mjs
```

`server/Dockerfile` builds it.

## Worker

```bash
PENDING_URL=https://<worker>/api/v1/pending \
PENDING_TOKEN=... LLM_API_KEY=... \
node scripts/translate-worker.mjs spanish
```

Translated texts are marked through `/api/v1/translated`, so the queue only grows when
players find genuinely new strings.
