# ADR-0001: Cloudflare-first hosting; AWS is optional

- **Status:** Accepted
- **Date:** 2026-11-10

## Context

The project needs a free, low-maintenance runtime for an opt-in collection API and static
translation dictionaries. The current deployment is a Cloudflare Worker with D1, GitHub raw
for dictionaries and GitHub Actions for automation. The plugin itself runs on the player's
machine.

The development guidelines ask for AWS free tier combined with Cloudflare. Adding a second
runtime for the same domain would create duplicate state, extra credentials and a distributed
system that the project does not need.

## Decision

Keep Cloudflare Workers + D1 as the production edge and GitHub as the data/automation plane.
Use AWS free tier only for auxiliary, non-runtime workloads (for example a backup or an
analytics sandbox) after a separate ADR. AWS must not become a second source of truth for the
queue or a second business service.

## Consequences

- One production runtime, one queue and one set of credentials.
- The free tier remains sufficient.
- If AWS is introduced later, it is an adapter with an explicit migration and rollback plan,
  not a microservice split.
- This ADR records the deliberate deviation from the generic AWS+Cloudflare guideline.
