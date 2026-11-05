# AllianceTrading Backend

Record-keeping backend for AllianceTrading: users register via mobile OTP, submit KYC, invest a fixed amount via external UPI payment, and receive profit/loss allocations from trading sessions. Admins verify everything manually. Trading itself happens outside the system.

## Stack

Java 21 · Spring Boot 3.5 · PostgreSQL 16 (Neon) · Flyway · Spring Data JPA · Spring Security (stateless, JWT via Nimbus) · AWS SDK v2 S3 (Cloudflare R2) · Bucket4j · springdoc-openapi · Testcontainers · Maven

## Run locally

Infrastructure is external: **Neon** (Postgres) for the database and **Cloudflare R2** for file
storage — no local services to start. Set `DATABASE_PASSWORD`, `R2_ACCESS_KEY` and
`R2_SECRET_KEY` in your environment first (see the Environment variables table).

```bash
# 1. Run the app (local profile: LoggingSmsSender prints OTPs, seed data created)
#    Quote the -D argument in PowerShell.
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 2. Explore the API
# Swagger UI: http://localhost:8080/swagger-ui/index.html
# OpenAPI:    http://localhost:8080/v3/api-docs
# Postman:    import postman/AllianceTrading.postman_collection.json (User + Admin folders;
#             login requests save tokens, list/create requests save ids into collection variables)
```

## Deploy (Docker / EC2)

`docker-compose.yml` runs just the app against the external Neon + R2 infrastructure.
On the server:

```bash
# 1. Put credentials in .env next to docker-compose.yml (compose reads it automatically;
#    your secrets env file is already in KEY=value format, so a copy works)
cp /path/to/alliancetrading-secrets.env .env

# 2. Build and start (prod profile by default)
docker compose up -d --build
```

The app listens on `:8080`. `.env` is gitignored — never commit it.

### Resetting the database

The schema is a baseline (`V1`–`V9`). After a baseline file changes, recreate the schema
(this deletes all data) by running in the NeonDB SQL Editor, then restart the app:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

## Seeded data (local profile)

| What | Value |
|---|---|
| SUPER_ADMIN | super@alliancetrading.local / SuperSecure1! ("Super Admin") |
| SUPER_ADMIN (checker) | super2@alliancetrading.local / SuperSecure2! ("Super Admin 2") |
| ADMIN | admin@alliancetrading.local / AdminSecure1! ("Ops Admin") |
| Default investment amount | ₹50,000 (payment setting; pre-fills the admin's assignment form) |
| Saved payment account | alliancetrading@upi ("Company UPI (sample)") |
| Fees | 2% profit withdrawal, 5% principal exit |
| Test users | 9876543210, 9876543211, 9876543212 |

Seeding is skipped if the test users already exist. OTP codes are printed to the log by `LoggingSmsSender`.

## Environment variables

| Variable | Purpose | Default (local) |
|---|---|---|
| `DATABASE_URL` | JDBC URL | NeonDB host in application-local/dev/staging.yml |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | DB credentials | `neondb_owner` / **required** (never commit it) |
| `JWT_SECRET` | base64 256-bit+ HMAC key for access tokens | dev value in application-local.yml |
| `OTP_PEPPER` | base64 pepper for OTP HMAC hashing | dev value |
| `MASTER_KEY` | base64 32-byte AES key (envelope + field encryption) | dev value |
| `MASTER_KEY_ID` | key version id for rotation | `k1` |
| `HASH_PEPPER` | base64 pepper for uniqueness hashes (PAN, Aadhaar, account numbers) | dev value |
| `R2_ENDPOINT` / `R2_ACCESS_KEY` / `R2_SECRET_KEY` / `R2_BUCKET` | Cloudflare R2 object storage (all profiles) | — |
| `FAST2SMS_API_KEY` | Fast2SMS API key (staging/prod, `app.sms.provider=fast2sms`) | — |
| `FAST2SMS_ROUTE` | `dlt` (DLT sender + templates; production) or `otp` (Fast2SMS OTP route, OTPs only) | `dlt` |
| `FAST2SMS_SENDER_ID` | DLT-approved sender ID (dlt route) | — |
| `FAST2SMS_TEMPLATE_OTP` | Fast2SMS DLT message ID for the OTP template (dlt route; the only SMS sent) | — |
| `OTP_FIXED_CODE` | Local/dev E2E tests only: every member OTP becomes this 6-digit code; startup fails unless `app.sms.provider=logging` | unset |
| `SMS_PROVIDER` | staging only: `fast2sms` or `logging` | `fast2sms` |
| `CORS_ALLOWED_ORIGINS` | comma-separated browser origins for the admin web console (allows PATCH; exposes `Content-Disposition`) | none (CORS off) |

### File storage

One bucket in Cloudflare R2 (create it in the Cloudflare dashboard, e.g. `alliance-trading-test`). Object keys (files are encrypted before upload; the original filename is never in the key; the document id keeps keys unique so re-uploads never overwrite):

- Member documents: `<folder>/<memberUserId>/<doc_type>_<documentId>.<ext>`, folders `kyc/` (PAN_CARD, AADHAAR_MASKED_FRONT, AADHAAR_MASKED_BACK, BANK_PROOF), `payment-proof/`, `payout-proof/` (e.g. `kyc/<userId>/pan_card_<id>.jpg`). Admin-uploaded payout proofs go under the member.
- Admin-only files (PNL_EVIDENCE, PAYMENT_QR): `admin/<adminId>/<doc_type>_<documentId>.<ext>`.

The cleaned original filename is stored in `documents.original_filename` (directory parts, control chars and `" * : < > ? |` removed, whitespace collapsed, base name max 200 chars, extension replaced by the detected type's; null if nothing usable remains). Uploads return `DocumentResponse { id, docType, originalFilename, mime, sizeBytes, sha256, createdAt }`; content downloads send `Content-Disposition: inline; filename="..."; filename*=UTF-8''...` (fallback `<doc_type>_<id>.<ext>`).

### SMS (Fast2SMS)

SMS is sent for one thing only: the login OTP. Every account notification (KYC and investment
decisions, withdrawal updates, P&L postings, support replies) goes by push through the outbox.
`notification.sms.SmsTemplate` holds the OTP wording; register it on Fast2SMS/DLT with the same text
(`%s` = DLT variable, in order) and set its message ID in `FAST2SMS_TEMPLATE_OTP` (required at startup).
All Fast2SMS request/response details are isolated in `Fast2SmsSender` — confirm them
against your Fast2SMS account docs before going live.

## Architecture

Modular monolith, single deployable. Package layout under `in.ruhmans.alliancetrading`:

| Module | Responsibility |
|---|---|
| `common` | ProblemDetail errors, Clock, correlation ID, idempotency, crypto (AES-256-GCM envelope + field, HMAC hashing), money, cursor + page pagination, PII masking, CORS |
| `auth` | OTP flow, sessions (members: single active device via DB partial unique index; admins: multiple devices allowed), JWT, refresh rotation with reuse detection, admin email + password login with lockout; suspended members cannot sign in |
| `user` | User profile, status state machine, `/me/summary`, `/me/profile`, `/me/ledger`; admin member search/detail/ledger, suspend/reactivate |
| `kyc` | KYC submission (full PAN + full Aadhaar, both AES-encrypted + HMAC-hashed, last-4 for display; Aadhaar Verhoeff check; 18+), admin approval queue, audited full-number view, one-step account approval (assign payment + approve KYC + verify bank) |
| `bank` | Bank accounts (with account type) submitted during onboarding, manual verification, 48h cooloff on changes to a verified account |
| `files` | Upload through backend (magic bytes, EXIF strip, SHA-256, envelope encryption), R2 storage, secure streaming view |
| `payment` | Saved company payment accounts (UPI) and per-user payment assignments (account + amount); generated UPI QR (`upi://pay`) or uploaded QR image |
| `settings` | Payment default / fee / app settings — versioned rows; direct saves by SUPER_ADMIN, effective now or scheduled, cancellable while scheduled |
| `investment` | Investment submission (amount from the user's payment assignment, payment-proof upload), admin approval posts +PRINCIPAL |
| `pnl` | P&L batches applied to each target investment: FIXED (same ₹ each) or PERCENTAGE (each investment gets principal × %); targets ALL active or INVESTMENT; server-computed totals; dry-run preview; maker picks the approving admin and only that admin can approve/reject; per-user allocation view |
| `ledger` | Append-only entries (DB trigger blocks UPDATE/DELETE), user_balances cache, single LedgerPostingService entry point |
| `withdrawal` | Quote → request → approve/second-approve → paid, fee calculation, principal exits behind feature flag |
| `support` | Member support tickets with message threads; admin queue, reply, resolve/reopen, priority |
| `audit` | Append-only audit log for all admin mutations, document/PII views (`AuditService.json` for safe detail payloads) |
| `notification` | Transactional outbox + scheduled dispatcher with backoff (push only); `notification.sms` — OTP SMS via Fast2SMS |
| `admin` | Dashboard (`stats`, `actionCenter` counts incl. P&L awaiting me, `todayDistribution`; two SQL round trips), admin user management with display names, admin directory |

## Main API flows

**Member (mobile app)**

1. `POST /auth/otp/request` → `POST /auth/otp/verify` (creates the user on first login)
2. `POST /kyc` `{fullName, dob, pan, aadhaar}` and `POST /bank-accounts` `{holderName, accountNumber, ifsc, accountType, vpa}` → status `KYC_SUBMITTED` (waitlist)
3. After approval: `GET /payment/current` (amount, UPI ID, `upi://` link) and `GET /payment/qr` (PNG)
4. `POST /files` (docType `PAYMENT_PROOF`) → `POST /investments` `{proofDocumentId}` with `Idempotency-Key`
5. `GET /me/summary`, `GET /me/ledger`, `GET /me/profile`
6. `POST /withdrawals/quote` → `POST /withdrawals` → track via `GET /withdrawals`
7. `POST /support/tickets`, `GET /support/tickets/{id}`, `POST /support/tickets/{id}/messages`

**Admin (web console)**

- Account approvals: `GET /admin/kyc?status=KYC_SUBMITTED` → `POST /admin/users/{id}/kyc/approve` `{payment: {paymentAccountId | newAccount, saveToList, amount}}` or `/kyc/reject` `{reason}`; full numbers via `GET /admin/kyc/{id}/identity/{PAN|AADHAAR}` (audited)
- Payment accounts (dropdown): `GET/POST /admin/payment-accounts`, `POST /admin/payment-accounts/{id}/archive` (409 `PAYMENT_ACCOUNT_IN_USE` if it is the default or in a scheduled default change), `GET /admin/payment-accounts/defaults` → `{defaultAmount, defaultPaymentAccountId}`; edit a member's QR via `POST /admin/users/{id}/payment-assignment`, preview with `GET /admin/users/{id}/payment-assignment/qr`
- Investments: `GET /admin/investments?status=&page=&size=` (rows carry `expectedVpa`/`expectedAmount`), `GET /admin/investments/{id}`, `/approve`, `/reject`
- Members: `GET /admin/users?search=&status=`, `GET /admin/users/{id}`, `GET /admin/users/{id}/ledger`, `/suspend`, `/reactivate`
- P&L: `GET /admin/pnl-batches/approvers` → `POST /admin/pnl-batches/preview` (dry run) → `POST /admin/pnl-batches` → approver sees `GET /admin/pnl-batches?assignedToMe=true` → `GET /admin/pnl-batches/{id}` + `/preview` → `/approve` or `/reject`; `GET /admin/pnl-batches/{id}/allocations`; history `GET /admin/pnl-batches?status=&valueType=&page=&size=`
  - Fixed: `{sessionDate, valueType: "FIXED", approverAdminId, lines: [{targetType: "ALL", amount: 200}]}`: every active investment gets ₹200; the server computes `controlTotal`
  - Percentage of principal: `{sessionDate, valueType: "PERCENTAGE", approverAdminId, lines: [{targetType: "ALL", percentage: 1}]}` — the server computes `controlTotal` (₹10k → ₹100, ₹50k → ₹500). If active investments change before approval, approval is refused; reject and recreate the batch.
- Withdrawals: `GET /admin/withdrawals?status=&page=&size=`, `GET /admin/withdrawals/{id}` (member, bank snapshot, approvers, `awaitingSecondApproval`), `/approve`, `/second-approve`, `/mark-paid` (UTR/proof optional), `/reject`, `/mark-failed`
- Settings (SUPER_ADMIN): `GET /admin/settings` (current values + `scheduled[]`), `POST /admin/settings/{payment|fees|app}` (final save; `effectiveFrom` now or later → `ACTIVE`/`SCHEDULED`), `DELETE /admin/settings/{payment|fees|app}/{id}` (cancel a scheduled change); app keys are lowercase and validated; payment takes `{fixedAmount, effectiveFrom, paymentAccountId | newAccount}` (exactly one; `newAccount` is always saved to the list; seed default: "Company UPI (sample)" `alliancetrading@upi`, 50,000)
- Admin uploads: `POST /admin/documents` (`PAYOUT_PROOF` with `ownerUserId`; `PNL_EVIDENCE`/`PAYMENT_QR` without)
- Support: `GET /admin/support/tickets`, reply, `/resolve`, `/reopen`, `/priority`
- Bank accounts: `GET /admin/bank-accounts?status=&page=&size=`, `GET /admin/bank-accounts/{id}`, `/verify`, `/reject`
- Dashboard: `GET /admin/dashboard` → `{stats: {activeInvestors, fundsInvested, profitCredited, pendingActions}, actionCenter: {kycPending, investmentPending, withdrawalOpen, supportOpen, bankPending, pnlAwaitingMyApproval, total}, todayDistribution: {sessionDate, total, recipients, eligible}}` (IST boundaries)
- Admin users (SUPER_ADMIN): `GET /admin/admin-users`, `POST /admin/admin-users` `{email, password, role, displayName}`, `PATCH /admin/admin-users/{id}` `{displayName}`, `POST /admin/admin-users/{id}/reset-password` `{newPassword}` (204, not on self; clears lockout, revokes sessions); admin login/refresh return `admin {id, email, displayName, role}`
- Admin directory: `GET /admin/admin-directory` (every admin `{id, email, displayName, role, status}`, for id → name lookups; `/pnl-batches/approvers` is only the approver dropdown)
- Admin lists of members, KYC, tickets, investments, bank accounts, withdrawals and P&L batches return `PageResponse { items, page, size, totalItems, totalPages }` (0-based, size 1..100, newest first)

## Profile and bank changes (current behaviour)

- **Name, DOB, PAN, Aadhaar**: not editable once KYC is submitted. A member can resubmit only after an admin rejects the KYC; PAN/Aadhaar already used by another member are refused. There is no change-mobile flow.
- **Bank account / UPI**: `POST /bank-accounts` with the new details creates a `PENDING_VERIFICATION` account (an older pending one becomes `REPLACED`). The verified account keeps receiving payouts until an admin verifies the new one, which then replaces it. Changing an already-verified account starts a cooling-off (`BANK_CHANGE_COOLOFF_HOURS`, default 48h) during which withdrawals are blocked.
- Not built yet: admin-approved profile corrections, step-up OTP and SMS/push alerts on bank changes, name-match warning, change mobile number.

## Key invariants

- **Ledger is append-only** — a DB trigger raises an exception on UPDATE/DELETE of `ledger_entries`; all money movement goes through `LedgerPostingService.post()`.
- **One live investment / one open withdrawal / one active session (members only; admins may use several devices) / one active payment assignment** per user — enforced by partial unique indexes, not application checks.
- **Maker-checker** — approver ≠ creator, enforced in service and via DB CHECK constraints (second withdrawal approval). P&L batches go further: only the admin the maker assigned may decide (DB CHECK `checker_id = assigned_checker_id`).
- **Idempotency** — `Idempotency-Key` header on money-moving POSTs; ledger entries carry derived idempotency keys (`base:BUCKET:TYPE`) with a UNIQUE constraint.
- **PII protection** — PAN, Aadhaar and account numbers encrypted (AES-256-GCM) at rest, HMAC-hashed for uniqueness, shown masked (last 4); decrypted views are audited. Storing full Aadhaar numbers is regulated by UIDAI — obtain compliance sign-off before production.

## Testing

```bash
# Unit tests (no Docker needed)
.\mvnw.cmd test -Dtest="*Test" -DfailIfNoTests=false

# Integration tests (need Docker for Testcontainers/PostgreSQL)
.\mvnw.cmd test
```

181 unit tests pass standalone. Integration tests (session replacement, refresh-token reuse, append-only trigger, concurrent withdrawals, ledger concurrency) require Docker; they also exercise every Flyway migration against PostgreSQL.

## Phases delivered

1. Scaffolding, docker-compose, profiles, common module, Flyway V1
2. Auth (OTP, sessions, JWT, refresh rotation, admin login, rate limiting)
3. Files (upload/encryption/R2), KYC, bank accounts, audit foundation
4. Settings (payment/fee/app, versioned, scheduled or immediate)
5. Ledger + investments (append-only, posting service, one-live-investment)
6. P&L (fixed or percentage per investment, dry-run and approver preview, approve)
7. Withdrawals (quote, request, cancel, second approval, principal-exit gating)
8. Outbox dispatcher, dashboard, seeder
9. OpenAPI polish + this README
10. Prototype alignment (folded into the V1–V9 schema, no ALTER migrations): full PAN/Aadhaar KYC without address or document uploads, bank account type, investments without UTR, per-user payment accounts + UPI QR, P&L assigned approver, Fast2SMS, admin member management, `/me/profile`, support tickets, CORS
