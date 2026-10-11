# Becho SaaS — Database Design

MySQL 8.0 · InnoDB · `utf8mb4_unicode_ci` · Flyway migrations in `db/migration/`

## 1. Core decisions

| Decision | Choice | Why |
|---|---|---|
| Multi-tenancy | **Shared schema, `store_id` on every store-scoped row** | One DB to operate; tenant isolation via `WHERE store_id = ?` enforced in a single place (JPA `@Where` / Hibernate filter + code review). Schema-per-tenant would explode migration cost at scale. |
| Primary keys | `BIGINT UNSIGNED AUTO_INCREMENT` | Sequential → index-friendly (no UUID fragmentation). Public IDs (order numbers) are per-store sequences, not raw PKs. |
| Money | `BIGINT` **minor units** (poisha/cents), never FLOAT/DOUBLE | Exact arithmetic; no rounding bugs in totals, taxes, refunds. |
| Timestamps | `DATETIME(3)`, always UTC | No 2038 problem (`TIMESTAMP`), ms precision for ordering. |
| Flexible fields | `JSON` columns (settings, addresses snapshot, metafields) | Schema stays relational where it matters, flexible where it varies. |
| Soft delete | `deleted_at` on major tables | Orders/customers/products are never hard-deleted (legal/audit). |
| Status fields | `VARCHAR(32)` + `CHECK` | Cheaper to evolve than ENUM, still constrained. |

## 2. Migration map (50 tables)

| Version | Domain | Tables |
|---|---|---|
| V1 | Tenancy & access | plans, tenants, stores, users, store_staff, store_sequences |
| V2 | Catalog | products, product_variants, product_images, collections, collection_products, product_metafields |
| V3 | Inventory | locations, inventory_levels, inventory_transfers, inventory_transfer_items, stock_movements |
| V4 | Customers | customers, customer_addresses |
| V5 | Discounts | discounts, discount_usages |
| V6 | Orders | orders, order_lines, order_transactions, fulfillments, fulfillment_lines, refunds, refund_lines |
| V7 | Gift/loyalty/credit | gift_cards, gift_card_transactions, loyalty_accounts, loyalty_transactions, store_credits, store_credit_transactions |
| V8 | Subscriptions | subscription_plans, subscriptions |
| V9 | Shipping & tax | shipping_zones, shipping_rates, tax_rates |
| V10 | Marketing & content | campaigns, abandoned_checkouts, blog_posts, redirects |
| V11 | Platform ops | api_keys, webhooks, webhook_deliveries, activity_log |
| V12 | Themes & pages | themes, pages |
| V13 | Finance | payouts |

## 3. Security (defense in depth)

No system is "unbreakable" — the goal is to make every layer expensive to attack:

1. **Passwords**: Argon2id hashing only (Spring Security `Argon2PasswordEncoder`). Column is `password_hash VARCHAR(255)` — plaintext never touches the DB.
2. **API keys**: store `SHA-256(key_hash)` + visible `key_prefix` only. Plaintext shown **once** at creation, never retrievable.
3. **Webhook secrets**: stored hashed; payloads signed with HMAC-SHA256.
4. **SQL injection**: impossible by construction — JPA/criteria queries with bind parameters; no string-concatenated SQL. (Migrations are the only raw SQL, reviewed.)
5. **Least-privilege DB users**: `app` user gets DML only (no DDL, no `SUPER`); migrations run as separate `migrator` user in CI.
6. **Tenant isolation**: every query scoped by `store_id`; integration tests assert cross-store access returns 404/empty. Staff permissions from `store_staff` are **enforced server-side**, never trusted from the client.
7. **PII**: collect minimum; encrypt backups & disk (RDS/Cloud SQL encryption at rest); TLS everywhere.
8. **Audit**: `activity_log` is append-only (no UPDATE/DELETE grants for app user); `stock_movements` likewise.
9. **Secrets**: gateway keys, OAuth secrets live in a vault/KMS — never in code, env files in git, or DB.
10. **Rate limiting & lockout**: login attempts throttled; API keys have scopes + expiry + revocation.

## 4. Performance (algorithms + caching)

**Indexing strategy**
- Every hot query leads with `store_id`: `idx_orders_created (store_id, created_at)`, `idx_orders_customer (store_id, customer_id, created_at)` — tenant pruning first, then range.
- Covering indexes for list pages; `FULLTEXT` on product titles for search.
- Generated column `inventory_levels.available = on_hand - reserved` — computed once, indexed if needed.

**Query algorithms**
- **Keyset pagination** (`WHERE (created_at, id) < (?, ?) ORDER BY created_at DESC, id DESC LIMIT 50`) for orders/products/customers — O(log n), no `OFFSET` slowdown on page 10,000.
- **Atomic sequences**: `SELECT next_val ... FOR UPDATE` + increment for order numbers — no gaps races, no app-level locks.
- **Inventory reservation** inside the same transaction as order creation (`UPDATE inventory_levels SET reserved = reserved + ? WHERE ... AND available >= ?`) — oversell impossible even under concurrency.
- Money math in minor-unit integers — O(1), exact.

**Caching (Redis)**
| Cache | Key pattern | Invalidation |
|---|---|---|
| Product/catalog reads | `store:{id}:product:{id}` | Write-through on product update |
| Published theme settings | `store:{id}:theme:published` | On theme publish |
| Store settings/shipping rates | `store:{id}:settings` | On settings save |
| Rate-limit counters | `rl:{scope}:{key}` | TTL expiry |
| Analytics aggregates | `store:{id}:analytics:{range}:{date}` | TTL 5–15 min (eventual consistency OK) |

Rules: cache-aside, per-store key prefix (no cross-tenant leaks), short TTLs for money-adjacent data, **never cache** order financial state — always read from DB in the order transaction.

**Connection pooling**: HikariCP (Spring Boot default), pool sized ~ `(2 × cores) + spindles`; read replicas later for analytics queries.

## 5. SOLID mapping (for the Java layer)

The schema is shaped into aggregates so the code can follow SOLID naturally:

- **S**ingle Responsibility: one service per aggregate (`OrderService`, `InventoryService`, `CatalogService`) — no god classes.
- **O**pen/Closed: payment gateways & couriers as `PaymentProvider` / `CourierProvider` interfaces — new provider = new class, zero changes to `OrderService`.
- **L**iskov: all providers honor the interface contract (e.g., `charge()` either returns success or throws `PaymentException`).
- **I**nterface Segregation: narrow ports (`InventoryReader` vs `InventoryWriter`) instead of one fat repository.
- **D**ependency Inversion: services depend on repository *interfaces*; JPA implementations injected. Testable with fakes.

## 6. Running migrations

```bash
# Flyway (recommended) — src/main/resources/db/migration/*.sql
./mvnw flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/becho \
  -Dflyway.user=migrator -Dflyway.password=...

# Manual
mysql -u migrator -p becho < db/migration/V1__tenancy_and_access.sql
# ... V2..V13 in order
```

Seed data (demo tenant/store/users) will be a separate `R__` repeatable migration when the backend scaffold lands.
