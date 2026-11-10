# Operations

## Logging

- Java: SLF4J/Logback through RuneLite. Do not add a proprietary logging SDK.
- Node collection server: stdout/stderr, captured by the host (Cloudflare logs or the local
  process supervisor).
- CI: GitHub Actions logs are the audit trail for translation and test runs.

## Metrics

- `GET /health` returns a small JSON status.
- `GET /metrics` returns Prometheus text exposition:
  - `translator_texts_total`
  - `translator_translated_total`

Both the local server and the Cloudflare Worker expose the endpoint. Counts contain no player
text or identifiers.

Recommended open-source stack:

- Prometheus for scraping;
- Grafana for dashboards;
- Loki for log aggregation;
- Uptime Kuma for external availability checks.

The repository exposes the contract; infrastructure credentials and dashboards are kept out
of the repository.

## Hosting

- Production edge: **Cloudflare Workers + D1**, free tier, with the static dictionaries on
  GitHub raw.
- AWS free tier is optional for auxiliary workloads only. It is not part of the core runtime
  and must not duplicate the collection state. See [ADR-0001](adr/0001-hosting.md).
- Do not add a distributed component without an ADR and a concrete operational need.

## Automation and deployment

- Translation workers propose PRs; CI validates; a human reviews and merges.
- `Mark translated texts` updates the queue only after a merge.
- Plugin releases go through the Plugin Hub marker and review.
- Server deployment is a protected manual operation until a GitHub Environment with
  Cloudflare secrets and required reviewers is configured.
- MCP may automate read-only inspection, PR creation and workflow dispatch. It must not bypass
  reviews, environment protection or secret handling.

## Rollback and recovery

- Plugin: the downloaded dictionary is validated and cached; the bundled fallback covers an
  unavailable remote.
- Translation data: revert the offending commit, regenerate the manifest and let clients pick
  up the new version.
- Collection data: keep D1 backups; never restore production data into local or test.
- If metrics or health fail, disable text capture first; translation downloads are
  independent of the collection API.

## Security

- Never run destructive scans or stress tests against production.
- Keep `ADMIN_TOKEN`, `PENDING_TOKEN`, `LLM_API_KEY` and Cloudflare credentials in their
  respective secret stores.
- Review dependency updates and workflow changes like code.
