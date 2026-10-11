# Becho backend → Railway deploy (demo + live QA)

সংক্ষেপে: Railway-তে backend + MySQL চালু করলেই live QA-র 14টা smoke test চালানো
যাবে — sandbox-এ Spring Boot boot হয় না, তাই এটাই একমাত্র পথ। Varsity demo-তেও
একই deploy ব্যবহার করা যাবে।

## খরচ (যাচাই করা, অক্টোবর ২০২৬)

- নতুন account-এ **$5 one-time trial credit, কার্ড লাগে না**, ৩০ দিন বা শেষ হওয়া
  পর্যন্ত। তারপর Free plan-এ প্রতি মাসে $1 credit।
- Trial limit: 1 GB RAM, shared vCPU, প্রতি project-এ ৫টা service — আমাদের জন্য
  (backend + MySQL, চাইলে Redis) যথেষ্ট।
- ⚠️ GitHub account **unverified** থাকলে outbound network restricted থাকে
  ("Limited Trial") — Maven dependency download আটকে যাবে। আগে
  railway.com/verify থেকে verify করে নিন।

## ধাপ ১ — Code GitHub-এ তুলুন (private repo হলেও চলবে)

Repo root-এ থাকতে হবে: `pom.xml`, `src/`, `deploy/Dockerfile.railway`।

```bash
cd ~/workspace/shop-saas-backend
git init && git add -A && git commit -m "becho backend"
# GitHub-এ private repo বানিয়ে push করুন
```

## ধাপ ২ — Railway project + MySQL

1. railway.app → New Project → **Deploy from GitHub repo** → repo select করুন।
2. Service-এর Settings → Build → **Dockerfile Path**: `deploy/Dockerfile.railway`
   (Root Directory খালি রাখুন — context repo root)।
3. Project-এ **+ New → Database → MySQL** যোগ করুন।
4. MySQL service → Variables tab থেকে এগুলোর value নিন:
   `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, `MYSQLPASSWORD`, `MYSQLDATABASE`

## ধাপ ৩ — Backend service-এ Environment Variables

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:mysql://<MYSQLHOST>:<MYSQLPORT>/<MYSQLDATABASE>?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8&allowPublicKeyRetrieval=true` |
| `DB_USER` | MySQL plugin-এর `MYSQLUSER` |
| `DB_PASSWORD` | MySQL plugin-এর `MYSQLPASSWORD` |
| `JWT_SECRET` | নিজে বানান: `openssl rand -base64 48` (কোডে/ফাইলে রাখবেন না) |
| `CORS_ORIGINS` | QA-র জন্য আপাতত দরকার নেই; frontend deploy হলে সেটার URL |

Flyway চালু আছে (`spring.flyway.enabled=true`) — প্রথম start-এ V1–V21
migration নিজে নিজে apply হবে, আলাদা কিছু করতে হবে না।

ঐচ্ছিক:
- **Redis plugin** (+ New → Database → Redis) যোগ করলে cache নিরাপদ থাকে।
  না থাকলেও app চালু হবে — শুধু storefront-domain cache lookup-এ Redis
  লাগে (`StorefrontResolver`)।
- **Mail (MAIL_HOST/PORT/USER/PASS)** না দিলেও signup ভাঙবে না —
  `MailService` best-effort; verification code Railway **Logs**-এ দেখা যাবে
  (`DEV-ONLY: email verification code for ...`)। Demo-তে ওখান থেকে code নিন।

## ধাপ ৪ — Deploy + URL

Deploy শেষে service-এর **Settings → Networking → Generate Domain** থেকে
public URL নিন, যেমন `https://becho-backend.up.railway.app`।

## ধাপ ৫ — আমাকে URL-টা দিন

URL পেলেই আমি:
1. 14টা smoke test (auth, tenant isolation, CRUD, billing, contact) চালাবো,
2. তারপর full adversarial QA (24-section report) শুরু করবো।

## জানা সীমা

- Railway trial credit শেষ হলে service থেমে যাবে — launch-এর VPS plan
  আলাদা (`deploy/` দেখুন), এটা শুধু demo/QA-র জন্য।
- Build-এ ~5–10 মিনিট লাগতে পারে (প্রথমবার Maven dependency download)।
