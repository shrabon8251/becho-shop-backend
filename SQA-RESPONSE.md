# SQA Audit Report — Response (2026-10-09 night)

Checked every finding from the other AI's report against the fixed builds.

## Fixed in this pass
- **H2** — Impersonation: "Log in as owner" button hidden in SuperAdmin.tsx
  (backend endpoint is still a stub — implement a real short-lived token later).
- **H3** — Deleted stale top-level `db/migration/` (was V10/V11/V12...).
- **H4** — `deploy.sh` now sources `.env` (`set -a; source .env; set +a`).
- **L1** — `mockCharge` deleted from payments.ts; PaymentSheet fail-closed for
  non-manual providers (was dead code — SSLCommerz goes via /payments/intents).
- **L6** — Removed unused `bcprov-jdk18on` dependency from pom.xml.

## Verified
- Frontend: tsc --noEmit 0 errors, vite build PASS.
- Backend: javac 222 files, 0 errors.

## Left for him (decisions, not code)
- M1 — V33/V34 demo seeds: keep (idempotent INSERT IGNORE) or move to seed script.
- H1 — Frontend has zero automated tests. A Playwright smoke (login → create
  product → checkout → logout) is the cheapest high-value addition.
- M2 — Webhook SSRF guard missing (unchanged).
- M3 — No lockout on shopper login (merchant login has 5-strike lockout).
- M4 — Rate limiter fails open when Redis down (accepted tradeoff — documented).
- 9 integration tests need MySQL reachable (will pass on his PC).
- L2/L3/L5/L7 — cosmetic/benign, skipped.

## Sweep pass 2 (2026-10-09 night) — "ar kisu fix korar ase ni"
- **Mock fallback removed (architecture rule):** `src/api/index.ts` no longer falls
  back to the 833-line fake `mockApi` when `VITE_API_URL` is unset — it now throws
  a loud `[becho] VITE_API_URL is not set` error instead of silently serving fake
  products/orders as a real store. `src/api/mock.ts` deleted. `main.tsx` catches
  the boot error and shows a readable "Configuration error" screen (no white
  screen). `isMock` kept as `false` so old call sites compile.
- **useShopData hardened:** `Promise.all` → `Promise.allSettled`. products /
  settings / theme stay critical (null on failure, as before); discounts / apps /
  shipping-profiles now degrade gracefully (empty/off fallbacks) instead of
  blanking the whole shop. Failures are surfaced via the new `error` field and a
  dismissible warning banner in ShopLayout.
- Verified: `tsc --noEmit` 0 errors, `vite build` pass.
- Checked, no action: no `console.log`/`debug` noise; SmsService TODO is the
  known "choose SMS provider" decision; impersonation stub already hidden (H2);
  trial auto-expiry + public storefront endpoints remain backend-chat work.
