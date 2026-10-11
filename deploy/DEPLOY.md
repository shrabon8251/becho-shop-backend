# Becho — VPS Deploy Guide (Production)

এই folder-এ production deploy-এর সব file আছে। ধাপে ধাপে follow করো।

## আগে যা লাগবে (তোমাকে করতে হবে)

1. **VPS কেনা** — Hetzner CX22 (2 vCPU, 4GB RAM) বা সমান। Ubuntu 24.04 select করো।
2. **Domain** — যেমন `becho.com.bd`। DNS-এ ২টা A record:
   - `api.becho.com.bd` → তোমার VPS-এর IP
   - (frontend Cloudflare Pages/Vercel-এ থাকবে, আলাদা)
3. **Cloudflare R2** — dashboard থেকে bucket বানাও (যেমন `becho-uploads`) + API token। Public access (r2.dev URL বা custom domain) চালু করো।

## Step 1 — Server ready করো

VPS-এ SSH দিয়ে ঢুকে (root হিসেবে):

```bash
# deploy folder-টা server-এ copy করো (নিজের PC থেকে):
scp -r deploy/ root@<VPS-IP>:/opt/becho-deploy

# server-এ:
bash /opt/becho-deploy/server-setup.sh
```

এটা Docker install করবে, firewall-এ শুধু 22/80/443 খুলবে, fail2ban চালু করবে।

## Step 2 — `.env` বানাও

```bash
mkdir -p /opt/becho
cp /opt/becho-deploy/.env.example /opt/becho/.env
nano /opt/becho/.env
```

সব `CHANGE_ME` value বদলাও। Secret বানাতে:

```bash
openssl rand -hex 32   # DB password-এর জন্য
openssl rand -hex 64   # JWT_SECRET-এর জন্য
```

## Step 3 — Backend JAR build করো

নিজের PC-তে (Java 25 + Maven লাগবে):

```bash
cd shop-saas-backend
mvn -DskipTests package
# target/becho-saas-*.jar তৈরি হবে
```

তারপর পুরো project folder server-এ copy করো:

```bash
scp -r ~/workspace/shop-saas-backend root@<VPS-IP>:/opt/becho/
```

(`deploy/` folder-টা `/opt/becho/deploy/`-এর ভেতরে যাবে।)

## Step 4 — Deploy চালাও

Server-এ:

```bash
cd /opt/becho/deploy
bash deploy.sh
```

প্রথম run-এ Flyway সব migration (V1–V18) চালিয়ে database তৈরি করে দেবে।

## Step 5 — Check করো

- `https://api.becho.com.bd/auth/login` — Caddy নিজে free SSL নিয়ে নেবে (১-২ মিনিট লাগতে পারে)
- `docker compose logs -f app` — error দেখলে এখানে

## Step 6 — Nightly backup চালু করো

```bash
# awscli-তে R2 configure (একবার):
aws configure set aws_access_key_id "<R2_ACCESS_KEY_ID>"
aws configure set aws_secret_access_key "<R2_SECRET_ACCESS_KEY>"
aws configure set default.region auto

# cron-এ দাও:
(crontab -l 2>/dev/null; echo "0 2 * * * /opt/becho/deploy/backup.sh >> /var/log/becho-backup.log 2>&1") | crontab -
```

প্রতিদিন রাত ২টায় DB dump R2-তে যাবে, ১৪ দিনের পুরনো backup auto-delete হবে।

## Step 7 — Frontend deploy

নিজের PC-তে:

```bash
cd shop-saas-frontend
VITE_API_URL=https://api.becho.com.bd npm run build
```

`dist/` folder-টা Cloudflare Pages বা Vercel-এ upload করো, domain `becho.com.bd` connect করো।

## পরে update দিতে হলে

```bash
# PC-তে: mvn -DskipTests package, তারপর server-এ copy
# Server-এ:
cd /opt/becho/deploy && bash deploy.sh
```

Zero-downtime না, ১-২ মিনিট downtime হবে — ছোট দোকানের জন্য ঠিক আছে।

## জরুরি নিয়ম

- `.env` কখনো git-এ commit করবে না
- `.env`-এর একটা copy নিজের কাছে নিরাপদে রাখো (password manager-এ)
- Backup ঠিকমতো হচ্ছে কিনা মাসে একবার R2-তে ঢুকে দেখো
