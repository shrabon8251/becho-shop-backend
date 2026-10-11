# Becho SaaS patch notes — 2026-10-10

## Applied changes

1. `StoreInventoryController` transfer endpoint now rejects missing/invalid quantities, zero/negative/fractional quantities, duplicate variants, same source/destination, foreign-store locations/variants, insufficient available stock, and destination integer overflow.
2. Transfer execution and manual stock level updates run in a transaction. Transfer number allocation is serialized per store, transfer header/items are persisted using the existing `inventory_transfers` and `inventory_transfer_items` tables, and the history endpoint reads store-scoped records.
3. Inventory level responses now include `qty` as well as `onHand` and `available` to match the frontend `StockLevel` contract.
4. Manual stock adjustments validate quantity and cannot set on-hand below reserved stock.
5. Forgot-password throttling is evaluated before the general auth bucket. If Redis is unavailable, authentication endpoints fail closed with HTTP 503 rather than silently disabling rate limiting.
6. Forwarded IP headers are trusted only when proxy trust is enabled and the remote address matches an explicitly configured trusted proxy.
7. `.env` files were removed from the distributable archives. `.gitignore` patterns now exclude environment files while allowing `.env.example`.

## Verification status

- Archive integrity and static checks: run before release.
- Backend Maven tests: not run in this environment because Maven is not installed.
- Frontend production build: attempted but blocked because dependency installation could not complete (registry package unavailable/time-out; installed `node_modules` is incomplete). This is an environment/dependency-install blocker, not a verified source compile pass.
- Live database transaction, concurrent transfer, and cross-tenant attack tests: still required against a disposable test database before production.

Do not treat this patch as a substitute for integration tests on the target MySQL/TiDB schema.
