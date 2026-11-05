# AllianceTrading Admin Console (Web): Project Context

Read this file before writing any code for the admin web console. It defines what to build, where the design comes from, how to talk to the backend, and the standards to follow. When this file and your own assumptions disagree, this file wins. When this file and the live API disagree, the API (Swagger) wins, so flag the difference.

---

## 1. Product in one paragraph

AllianceTrading is a closed investment club. Members use a mobile app: they sign in by OTP, submit KYC and a bank account, pay a fixed amount by UPI and upload a proof, receive daily P&L, and withdraw profit. **Admins use this console** to:
- approve members and assign each one a company UPI account and amount
- verify payments
- post P&L, with a second admin approving each batch
- process withdrawals and answer support tickets
- manage settings

Every money movement is recorded in an append-only ledger on the backend, and sensitive actions require a second admin. **The console never calculates money itself.** It shows what the API returns and sends the admin's decisions.

---

## 2. Design source: the Figma Make prototype

| Item | Location |
|---|---|
| Admin screens | `C:\Users\sr106824\Downloads\TestAnis-main\TestAnis-main\src\AdminDashboard.tsx` |
| Design tokens / global CSS | `C:\Users\sr106824\Downloads\TestAnis-main\TestAnis-main\src\index.css` |
| Prototype stack | React 19 + Vite + Tailwind CSS v4 + TypeScript |

The prototype uses the **same stack** recommended here, so you can lift its JSX and Tailwind classes for layout and visuals. **Strip all mock data, local `useState` "databases" and fake timers**, and wire everything to the API. Keep `index.css` (tokens, `.field`, `.nav-item`, `animate-loading`) as the base stylesheet.

### 2.1 Design tokens (from `index.css`)

| Token | Value |
|---|---|
| Font | **Manrope** 400–800 (Google Fonts) |
| `brand` / `brand-dark` / `brand-soft` | `#3857ff` / `#2746eb` / `#edf0ff` |
| `canvas` / `canvas-dark` / `surface-dark` | `#f7f8fc` / `#0b0d14` / `#12151f` |
| `shadow-brand` | `0 20px 50px -18px rgb(56 87 255 / 0.48)` |
| Dark mode | `@custom-variant dark (&:where(.dark, .dark *))`, toggled with a `.dark` class |
| Neutrals | Tailwind `slate` |

### 2.2 Prototype section → console page → API map

All paths are under `/api/v1`.

| Prototype section (`AdminDashboard.tsx`) | Console page | Backend |
|---|---|---|
| — (none in prototype; **add**) | Login | `POST /admin/auth/login` |
| `overview` | Dashboard (stat cards, action center, today's distribution; see 6.8) | `GET /admin/dashboard` |
| `accounts` | Account approvals | `GET /admin/kyc?status=KYC_SUBMITTED&page=&size=`, `GET /admin/kyc/{userId}/identity/{PAN\|AADHAAR}`, `POST /admin/users/{userId}/kyc/approve`, `POST /admin/users/{userId}/kyc/reject` |
| Approval drawer (`approvalUpiId`, `approvalAmount`) | Approve modal: pick a saved UPI account or enter a new one, plus the amount; pre-select `defaultPaymentAccountId` and pre-fill `defaultAmount` | `GET /admin/payment-accounts`, `GET /admin/payment-accounts/defaults` (`{ defaultAmount, defaultPaymentAccountId }`, either may be null) |
| `investments` | Payment proofs | `GET /admin/investments?status=PENDING_VERIFICATION&page=&size=` (rows include member `fullName`, `mobile`, and `expectedVpa`/`expectedAmount` from the payment assignment; don't fetch members per row), `GET /admin/investments/{id}` (side panel / deep link), `GET /admin/documents/{id}/content`, `POST /admin/investments/{id}/approve`, `POST /admin/investments/{id}/reject` |
| `users` | Members (search, detail, pause) | `GET /admin/users?search=&status=&page=&size=`, `GET /admin/users/{userId}`, `GET /admin/users/{userId}/ledger`, `POST /admin/users/{userId}/suspend`, `POST /admin/users/{userId}/reactivate` |
| `profits` | Post P&L (maker) | `GET /admin/pnl-batches/approvers`, `POST /admin/pnl-batches/preview` (dry run, live totals), `POST /admin/pnl-batches` |
| (new) | P&L approvals (checker) | `GET /admin/pnl-batches?assignedToMe=true&page=&size=`, `GET /admin/pnl-batches/{id}`, `GET /admin/pnl-batches/{id}/preview`, `POST /admin/pnl-batches/{id}/approve`, `POST /admin/pnl-batches/{id}/reject` |
| `ledger` | P&L history | `GET /admin/pnl-batches?status=&valueType=&page=&size=`, `GET /admin/pnl-batches/{id}`, `GET /admin/pnl-batches/{id}/allocations` |
| `withdrawals` | Withdrawals | `GET /admin/withdrawals?status=&page=&size=` (status optional; member name/mobile, bank snapshot, `bankAccountId`, approvers, `secondApprovalRequired`, `awaitingSecondApproval`), `GET /admin/withdrawals/{id}`, `POST /admin/withdrawals/{id}/approve`, `/second-approve`, `/reject`, `/mark-paid`, `/mark-failed`; full account number via `GET /admin/bank-accounts/{bankAccountId}/account-number`; payout proof via `POST /admin/documents` |
| `support` | Support | `GET /admin/support/tickets?status=&priority=&page=&size=`, `GET /admin/support/tickets/{id}`, `POST /admin/support/tickets/{id}/messages`, `/resolve`, `/reopen`, `/priority` |
| — (add) | Bank accounts (changes, re-verification) | `GET /admin/bank-accounts?status=PENDING_VERIFICATION&page=&size=` (each row has `userId`, member `fullName` and `mobile`; compare `fullName` with `holderName`), `GET /admin/bank-accounts/{id}`, `POST /admin/bank-accounts/{id}/verify`, `/reject` |
| — (add) | Payment accounts (UPI list) | `GET /admin/payment-accounts`, `POST /admin/payment-accounts`, `POST /admin/payment-accounts/{id}/archive` (`409 PAYMENT_ACCOUNT_IN_USE` if it is the default or in a scheduled default change: tell the admin to choose another default in Settings first), `GET\|POST /admin/users/{userId}/payment-assignment`, `GET /admin/users/{userId}/payment-assignment/qr` |
| — (add, SUPER_ADMIN only) | Settings (direct save, optional scheduling) | `GET /admin/settings` (current values + `scheduled[]`; payment default references a saved payment account), `POST /admin/settings/{payment\|fees\|app}`, `DELETE /admin/settings/{payment\|fees\|app}/{id}` (cancel a scheduled change) |
| — (add) | Audit log | `GET /admin/audit-log?entityType=&entityId=&limit=` |
| — (add, SUPER_ADMIN only) | Admin users | `GET\|POST /admin/admin-users`, `PATCH /admin/admin-users/{id}` (rename), `POST /admin/admin-users/{id}/reset-password` |
| — (add, admin menu) | Account / change password | `POST /admin/auth/change-password` |
| (all pages) | Admin id → name | `GET /admin/admin-directory` (see 6.1) |

### 2.3 Where the prototype and the backend disagree (backend wins)

| Prototype | Build this instead |
|---|---|
| No login | Email + password login (no TOTP / 2FA) |
| Posting P&L is a single click | **Maker-checker**: the maker creates a batch and picks an approver; **only that admin** can approve or reject it. Two screens: "Post P&L" and "Awaiting my approval" |
| Per-user amounts typed by hand | Two modes, both applied to **each** selected member: **Fixed** (the same ₹ amount each, e.g. ₹200) and **Percentage of principal** (e.g. +1%; each member gets their own principal × %). The server computes the totals |
| Withdrawal: approve → release | `approve`; then **second-approve** by a *different* admin when the amount is at or above the `withdrawal_second_approval_threshold` setting (the list row says so in `secondApprovalRequired` / `awaitingSecondApproval`); then `mark-paid` (UTR / proof optional) or `mark-failed`. Members can also cancel while `REQUESTED` |
| Default amount ₹10,000 | Pre-fill amount and pre-select the account from `GET /admin/payment-accounts/defaults` (seed: ₹50,000, "Company UPI (sample)"); the admin can change both per member |
| 1% fee | Fees come from settings (seeded 2% profit / 5% principal) |
| "Today's distribution" progress, month deltas | Use `stats` and `todayDistribution` from `/admin/dashboard` (6.8); no other deltas exist |
| Global ledger | Use P&L batches + allocations, or a per-member ledger |
| No settings, audit, admin users | Add these pages |

---

## 3. Recommended stack

- **React 19 + Vite + TypeScript (strict) + Tailwind CSS v4**, the same as the prototype
- Routing: **React Router** (data router) or TanStack Router, with protected routes
- Server state: **TanStack Query**; tables: **TanStack Table** (server-side paging, sorting, filters)
- Forms: **react-hook-form + zod**; UI primitives: Radix UI / shadcn-style components styled with the tokens above
- HTTP: fetch or axios wrapper with the auth logic in section 5
- Types: generate from `/v3/api-docs` with `openapi-typescript`
- Lint/test: ESLint + Prettier, Vitest + React Testing Library, Playwright for the critical flows

---

## 4. Environments and backend

| | |
|---|---|
| Base URL | `VITE_API_BASE_URL` (local `http://localhost:8080`) |
| API prefix | `/api/v1` |
| API docs | `{base}/swagger-ui/index.html`, `{base}/v3/api-docs` |
| Postman | `c:\dev\alliancetrading\postman\AllianceTrading.postman_collection.json`, **Admin** folder |
| **CORS** | The backend blocks browsers unless the console origin is listed in `CORS_ALLOWED_ORIGINS` (e.g. `http://localhost:5173`). Allowed headers: `Authorization`, `Content-Type`, `Idempotency-Key`, `X-Request-Id`. Methods include PATCH. Exposed response header: `Content-Disposition` |
| Seeded admins (local) | `super@alliancetrading.local` / `SuperSecure1!` (SUPER_ADMIN), `super2@alliancetrading.local` / `SuperSecure2!` (SUPER_ADMIN), `admin@alliancetrading.local` / `AdminSecure1!` (ADMIN). Display names "Super Admin", "Super Admin 2", "Ops Admin" |

In development you can avoid CORS with a Vite dev proxy (`server.proxy['/api'] → http://localhost:8080`). Production still needs the origin configured.

---

## 5. Authentication and token refresh (read carefully)

### 5.1 Login

`POST /admin/auth/login { email, password }` returns:

```json
{
  "accessToken": "…", "refreshToken": "…", "expiresAt": "2026-11-21T09:00:00Z", "newUser": false,
  "admin": { "id": "…", "email": "super@alliancetrading.local", "displayName": "Super Admin", "role": "SUPER_ADMIN" }
}
```

`expiresAt` is the **refresh token's** expiry (30 days). It is **not** when the access token expires. Read the access token's expiry from its JWT `exp` claim (`jwt-decode`).

- Wrong credentials: `401 UNAUTHENTICATED`.
- **Five failures lock the account for 15 minutes:** `429 ACCOUNT_LOCKED`. Show a clear message and don't auto-retry.
- A disabled admin gets `401`.
- Show `admin.displayName` in the header. Keep `admin` in memory only (not `sessionStorage`); `POST /auth/refresh` returns it again, so restore it from there after a reload.

### 5.2 Tokens and storage

| Token | Lifetime | Store |
|---|---|---|
| Access token (JWT, `type: ADMIN`, `role: ADMIN \| SUPER_ADMIN`) | **30 minutes** | **Memory only** (module variable or React context) |
| Refresh token | **30 days** server-side | **`sessionStorage`**, so it is cleared when the tab/browser closes. Never `localStorage`, never cookies set by JS, never URLs or logs |

Read `role` from the JWT payload (base64-decode the middle segment) **only for showing or hiding UI**. The server enforces all permissions.

Because a refresh token is readable by JavaScript, **XSS is the main risk**:
- strict Content Security Policy
- no `dangerouslySetInnerHTML`
- sanitize any rendered user text (support messages, names)
- keep dependencies minimal and audited

Moving the refresh token to an httpOnly cookie is a planned backend improvement.

### 5.3 Refresh: must be single-flight

`POST /auth/refresh { refreshToken }` (the same endpoint members use) returns a **new access token and a new refresh token**, plus the same `admin` object as login (admin sessions only). The backend has **reuse detection**: presenting an already-used refresh token **revokes the session** (`401 SESSION_REPLACED`).

- Allow **only one in-flight refresh per tab**. Queue other 401'd requests behind the same promise and retry them once.
- Refresh about 60 seconds before the access token's JWT `exp`, and on a `401` with `TOKEN_EXPIRED` or `UNAUTHENTICATED`.
- **Multiple tabs in the same browser:** tabs share one session, because they share the refresh token in `sessionStorage` (copied to new tabs via BroadcastChannel). Coordinate with a `BroadcastChannel('auth')` or the Web Locks API (`navigator.locks.request('refresh', …)`) so only one tab refreshes, then broadcast the new tokens to the others. Without this, two tabs refreshing together trigger reuse detection and log the admin out. Separate devices and browsers have separate sessions and don't need coordination.

### 5.4 Multiple devices are allowed

An admin can be signed in on **several devices and browsers at the same time**. Each login creates its own independent session, with its own refresh token, and logging in elsewhere does **not** sign out the other sessions.

You can still get `401 SESSION_REPLACED`, for these reasons:
- refresh-token reuse was detected (for example, two tabs refreshed with the same token)
- the session was revoked on the server

On that response: don't refresh, clear tokens, and redirect to login with "Your session ended, please sign in again".

Logout ends **only the current session**. There is no "log out all devices" endpoint yet (changing your password does sign out every other device, see 5.4a).

### 5.4a Change password

`POST /admin/auth/change-password { currentPassword, newPassword }` (any signed-in admin).

- `newPassword`: 10–100 characters and different from the current one, else `400 VALIDATION_FAILED`.
- Wrong current password: `400 INVALID_CREDENTIALS` (deliberately not 401, so the refresh/logout logic doesn't fire). Failures count towards the login lockout (5 → `429 ACCOUNT_LOCKED`, 15 minutes).
- Success revokes **every session of that admin on every device**, writes audit `ADMIN_PASSWORD_CHANGED`, and returns a fresh `AuthResponse` (same shape as login). **Replace the in-memory access token and the refresh token immediately** (the old ones stop working) and broadcast them to other tabs. Other devices get `401 SESSION_REPLACED`.
- UI: "Change password" in the admin menu with current, new and confirm-new fields; check client-side that new == confirm and length is 10–100.

### 5.5 Idle timeout (client-side)

Admins handle PII and money. **Log out after 30 minutes without user activity** (mouse, keyboard, visibility), and warn 2 minutes before. The backend does not enforce idle time yet, so the console must.

### 5.6 Logout

`POST /auth/logout`, then clear tokens, the query cache and the BroadcastChannel state, and redirect. Clear local state even if the call fails.

### 5.7 Roles

| Role | Can |
|---|---|
| `ADMIN` | Everything except admin-user management (can read `/admin/admin-directory`) |
| `SUPER_ADMIN` | Also `/admin/admin-users`: list (rows include `displayName`, sorted by email), create (`displayName` required, 1–100 chars, trimmed; password at least 10 characters), rename via `PATCH /admin/admin-users/{id} { displayName }` (returns the updated admin), reset password via `POST /admin/admin-users/{id}/reset-password { newPassword }` (10–100 chars, `204`; clears lockout, revokes all their sessions, audit `ADMIN_PASSWORD_RESET`; `403 FORBIDDEN` on your own account, use change-password). Use it for lockouts or forgotten passwords and give the new password out of band. UI: "Reset password" row action, hidden on your own row |

`403 FORBIDDEN` means hide or disable the action. Don't retry.

---

## 6. Business rules the UI must respect

### 6.1 Maker-checker (core concept)

- The **approver can never be the creator**. Enforced by the server with `409 MAKER_CHECKER_VIOLATION`.
- **P&L batch:**
  - The maker picks an approver from `GET /admin/pnl-batches/approvers` (excludes the caller and disabled admins; options include `displayName`; use it only for this dropdown).
  - Only that admin sees it in "Awaiting my approval" (`?assignedToMe=true`) and can approve or reject it.
  - Show the dashboard counter `actionCenter.pnlAwaitingMyApproval`.
- **Settings** (payment default, fees, app settings; **SUPER_ADMIN only**, 403 otherwise) are **not** maker-checker: a save is final, no second admin. Each POST takes `effectiveFrom` (required, now or later; up to 5 min in the past counts as now, earlier → `400 VALIDATION_FAILED`) and returns `{ id, status, effectiveFrom }` with `status` `ACTIVE` or `SCHEDULED`. `GET /admin/settings` returns `{ payment, fees, app, scheduled[] }`; `payment` is `{ id, fixedAmount, paymentAccount: { id, label, vpa, payeeName, hasUploadedQr }, effectiveFrom }`; `scheduled[]` items are `{ kind: PAYMENT|FEE|APP, id, fixedAmount, paymentAccount, withdrawalType, feePct, key, value, effectiveFrom, createdBy, createdAt }`, soonest first. `DELETE /admin/settings/{payment|fees|app}/{id}` cancels a scheduled change (`204`; `409 ILLEGAL_STATE_TRANSITION` if already active; `404`). Audit: `SETTING_CHANGED` (old/new value, `effectiveFrom`), `SETTING_SCHEDULE_CANCELLED`. **Default payment** (`POST /admin/settings/payment`): send `{ fixedAmount, effectiveFrom }` plus exactly one of `paymentAccountId` or `newAccount: { label?, vpa, payeeName, qrDocumentId? }` (both/neither → `400 VALIDATION_FAILED`; archived account → `409 ILLEGAL_STATE_TRANSITION`; `newAccount` UPI ID already saved → `400`, "select it from the list"). A `newAccount` is always saved to the account list; the account's QR (uploaded, or generated from its UPI ID) is used. Form: amount, a "Payment account" dropdown of ACTIVE accounts from `GET /admin/payment-accounts` with an "Add new account" option that shows label / UPI ID / payee name / QR upload fields, and the effective-from picker. Show the current default as label + UPI ID; scheduled PAYMENT rows show the account. Audit records old/new `paymentAccountId` and `vpa`. Suggested UI: one form per setting with an "Effective from" picker defaulting to now, a "Save" button with a confirm dialog showing old → new value, and a "Scheduled changes" list from `scheduled[]` with a "Cancel" action. App setting keys are lowercase: `min_profit_withdrawal`, `principal_withdrawal_enabled`, `bank_change_cooloff_hours`, `withdrawal_second_approval_threshold`, `min_app_version`; the server rejects unknown keys and mistyped values.
- **Withdrawals above the threshold** need a `second-approve` by a different admin.
- **Admin names:** fetch `GET /admin/admin-directory` once (`[{ id, email, displayName, role, status }]`, every admin incl. the caller and disabled ones) and cache it (TanStack Query, long `staleTime`); invalidate it after a rename. Show `displayName` (email as tooltip) for every admin id lookup: withdrawal `approvedBy`/`secondApprovedBy`/`paidBy`, P&L `makerId`/`checkerId`/`assignedCheckerId`, settings `createdBy`, audit `actorId`.

### 6.2 Account approval

1. **Queue:** `GET /admin/kyc` returns a `PageResponse` of members with masked PAN/Aadhaar, the bank account and any payment assignment.
2. **Full numbers:** the "Reveal PAN" and "Reveal Aadhaar" buttons call `/admin/kyc/{userId}/identity/{field}`. **Every reveal is audited.** Reveal only on click and auto-hide after about 30 seconds; never prefetch.
3. **Approve:**
   ```json
   POST /admin/users/{userId}/kyc/approve
   { "payment": { "paymentAccountId": "…", "amount": 10000 } }
   ```
   Alternatively send `newAccount: { label, vpa, payeeName }` plus `saveToList: true` instead of `paymentAccountId`. One call approves the KYC, verifies the pending bank account and assigns the payment. Open the dialog with the account dropdown pre-selected to `defaultPaymentAccountId` and the amount pre-filled with `defaultAmount` from `GET /admin/payment-accounts/defaults` (either may be null).
4. **Reject:** `{ reason }`, which is shown to the member.

### 6.3 Payments and investments

- Show the proof image or PDF from `/admin/documents/{id}/content` (authenticated; fetch as a blob, then use an object URL). Show the file name in the viewer: read `Content-Disposition` (parse `filename*=UTF-8''` first, then `filename`); it falls back to `<doc_type>_<id>.<ext>`. The response is `inline`; for a Download button use `<a download="{name}" href={blobUrl}>`.
- **Side panel:** compare the proof with the row's `expectedVpa` and `expectedAmount` (null when there is no payment assignment); no `GET /admin/users/{userId}` needed. Deep links open `GET /admin/investments/{id}`.
- **Approve** posts the deposit to the ledger. **Reject** requires a reason.

### 6.4 P&L posting

The "Post P&L" form has:
- session date
- **Type** toggle: **Fixed ₹ each** / **% of principal**
- recipients: **All active members**, or selected members (one `INVESTMENT` line per selected member's investment)
- value (₹ or %, negative for a loss)
- approver
- notes

| | Fixed (`valueType: "FIXED"`) | Percentage (`valueType: "PERCENTAGE"`) |
|---|---|---|
| Line | `{ targetType: "ALL" \| "INVESTMENT", investmentId?, amount: 200 }` | `{ targetType: "ALL" \| "INVESTMENT", investmentId?, percentage: 1 }` |
| Each target gets | exactly `amount` | its own principal × `percentage` / 100, rounded to the paisa |
| `controlTotal` | optional: the server computes it; if sent, it must match | same |

- **Live totals while typing:**
  - **Fixed:** the form may show "₹200 × 12 members = ₹2,400". This is display arithmetic on what the admin typed and selected, and is allowed.
  - **Percentage:** principals differ, so call **`POST /admin/pnl-batches/preview`**. Send the same body as create (`approverAdminId` optional), debounced about 400 ms. It saves nothing and returns `controlTotal`, each investment's amount and resulting balance, and warnings.
  - Use the same dry run for Fixed too, to show loss-floor errors and large-change flags before posting.
- **Allocation rows** in both previews include `fullName` (null until KYC is filled) and `mobile`, so no per-member lookups are needed.
- **`allocations[].largeChange`** marks a change of more than 10% of principal. The threshold comes back as `largeChangeThresholdPct` (10), so don't hard-code it. Highlight those rows.
- **`warnings`** holds batch-level messages only, for example "active investments changed since the batch was created".
- **Approver preview:** `GET /admin/pnl-batches/{id}` for the batch header and `GET /admin/pnl-batches/{id}/preview` for the lines; don't list all batches to find one. The approver must review it; put Approve and Reject on that screen.
- **Guardrails** (enforced by the server):
  - one REGULAR posting per investment per day
  - loss floor: a member's value can't go below 0
  - a batch is refused at approval if active investments changed since creation; reject it and create it again
- After approval, `/allocations` lists each recipient with name and mobile (header from `GET /admin/pnl-batches/{id}`).
- Lists (`GET /admin/pnl-batches?status=&valueType=&assignedToMe=`) are paged, newest first.
- There is no CSV upload; build the lines in the form.
### 6.5 Withdrawals

- **List filter tabs:** All (no `status`), REQUESTED, APPROVED, PAID, REJECTED, PAYOUT_FAILED, CANCELLED. Paged, newest first.
- **Side panel / deep link:** `GET /admin/withdrawals/{id}`; don't search the loaded page. Show approver/payer emails via the admin directory.
- **Show:** gross, fee and net amounts, and a bank snapshot (holder, last 4 digits, IFSC, UPI ID). Use "Reveal account number" (audited) for the payout.
- **Actions:**
  - `approve`, then `second-approve` (a different admin, when needed)
  - `mark-paid { payoutUtr?, payoutProofDocumentId? }`
  - `mark-failed { failureReason }`
  - `reject { reason }`

### 6.6 Members and bank changes

- **Search** by mobile or name, filter by status.
- **Detail** shows KYC, bank accounts, investments, payment assignment and balances.
- **Suspend** `{ reason }` blocks login and revokes sessions; **reactivate** restores the previous status.
- **A bank change creates a new pending account.** Verify or reject it on the Bank Accounts page. The old account keeps paying out until then, and a 48h cooling-off blocks the member's withdrawals.
- Flag a mismatch between the bank holder name and the KYC name for the reviewer (client-side comparison).

### 6.7 Support

- **Queue:** `PageResponse` filtered by status (`OPEN`/`RESOLVED`) and priority (`NORMAL`/`HIGH`).
- **Actions:** reply, resolve, reopen, set priority.
- **Render message bodies as plain text only.**

### 6.8 Dashboard

`GET /admin/dashboard` (day/month boundaries are IST):

```json
{
  "stats": {
    "activeInvestors": { "value": 100, "newThisMonth": 6 },
    "fundsInvested":   { "value": 1000000.00, "thisMonth": 60000.00 },
    "profitCredited":  { "value": 184000.00, "today": 18400.00 },
    "pendingActions":  { "value": 86 }
  },
  "actionCenter": { "kycPending": 20, "investmentPending": 20, "withdrawalOpen": 25, "supportOpen": 15, "bankPending": 3, "pnlAwaitingMyApproval": 3, "total": 86 },
  "todayDistribution": { "sessionDate": "2026-10-29", "total": 18400.00, "recipients": 92, "eligible": 128 }
}
```

- `activeInvestors`: distinct members with an `ACTIVE` investment; `newThisMonth` = those approved since the 1st of this month.
- `fundsInvested`: sum of `ACTIVE` investment amounts; `thisMonth` = the part approved this month.
- `profitCredited`: net of all posted P&L allocations (approved batches, incl. losses and adjustments; can be negative); `today` = today's session date.
- `pendingActions.value` = `actionCenter.total` = sum of the six counts. `withdrawalOpen` = `REQUESTED` + `APPROVED` + `PAYOUT_FAILED`; `supportOpen` = `OPEN` tickets. `pnlAwaitingMyApproval` is for the signed-in admin, so totals differ between admins.
- `todayDistribution`: today's posted P&L `total`; `recipients` = investments credited today, `eligible` = `ACTIVE` investments (progress = recipients / eligible).
- Use the `actionCenter` counts for sidebar badges too; there is no separate count endpoint.

---

## 7. API conventions

- **Errors** are RFC 7807 `application/problem+json`: `{ type, title, status, detail, code, requestId }`.
  - **Branch on `code`.**
  - Show `detail` for `VALIDATION_FAILED`, `ILLEGAL_STATE_TRANSITION`, `MAKER_CHECKER_VIOLATION`, `INVALID_APPROVER`, `NO_PAYMENT_ASSIGNMENT`, `PAYMENT_ACCOUNT_IN_USE`, `INVALID_CREDENTIALS` and the other business codes; they are written for operators.
  - Always show the `requestId` in error toasts so it can be traced in logs.
- **Pagination:**
  - Admin lists return `PageResponse { items, page (0-based), size (max 100), totalItems, totalPages }`.
  - Ledgers return `CursorPage { items, nextCursor }`, where `null` means the end.
  - This includes investments, withdrawals, bank accounts and P&L batches (no longer plain arrays): newest first, `size` default 20, clamped to 1..100. The shared table uses server-side paging.
  - Open single rows by id (`/admin/{investments|withdrawals|bank-accounts|pnl-batches}/{id}`) instead of searching the loaded page.
- **Timestamps** are ISO-8601 UTC. **Display them in IST for every admin**, so all operators see the same clock:
  `new Intl.DateTimeFormat('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))`.
  Plain dates (`sessionDate`, `dob`) are `YYYY-MM-DD`; don't convert them through `Date`.
- **Money:** JSON numbers in rupees with 2 decimals. Format with `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })`. **No client-side money maths** beyond showing what the API returned. P&L totals come from preview; fees come from the withdrawal record.
- **Percentages:** P&L `percentage` is in percent units (1 = 1%). Fee settings `feePct` is a fraction (0.02 = 2%). Label inputs clearly.
- **Idempotency:** send `Idempotency-Key: <uuid>` on money-moving POSTs. Disable buttons while a mutation is pending to avoid double submits.
- **IDs** are UUIDs. **Enums:** model them as TS unions from the generated OpenAPI types.

---

## 8. Engineering standards

- **Layout:**
  - `src/app/` (router, providers, layout shell with sidebar from the prototype's `.nav-item`)
  - `src/api/` (client, auth, generated types, one module per backend area)
  - `src/features/<area>/` (pages, tables, forms, hooks)
  - `src/ui/` (primitives: Button, Field, Table, Drawer, Modal, StatusBadge, Money, DateTime, ConfirmDialog)
- **Destructive or money actions** (approve, reject, mark paid, suspend, post P&L, reveal PII) always go through a **confirm dialog** that restates the key numbers (member, amount, %). Rejections require a reason field.
- **Tables:** server-side paging and filters kept in the URL query string (shareable, back-button safe), plus empty, loading and error states and manual refresh. Use TanStack Query `refetchOnWindowFocus` for queues.
- **Security:**
  - No PII in console logs, analytics or error trackers.
  - Mask PAN, Aadhaar and account numbers by default.
  - Strict CSP, `X-Frame-Options: DENY` from the host.
  - Use the idle logout from section 5.5.
- **Accessibility:** keyboard navigable tables and dialogs, focus traps in modals, AA contrast in light and dark.
- **Quality:**
  - TypeScript strict, no `any`.
  - ESLint and Prettier must pass.
  - Unit test the auth layer (single-flight refresh, multi-tab lock, `SESSION_REPLACED`) and the formatters.
  - Playwright tests for: login → approve KYC; maker posts a % P&L → checker previews and approves; withdrawal approve → mark paid.

---

## 8a. Admin uploads and test data

- **Uploads:** `POST /admin/documents` (multipart: `file`, `docType`, optional `ownerUserId`). Use `PAYOUT_PROOF` with the member's `ownerUserId`. Use `PNL_EVIDENCE` and `PAYMENT_QR` without it. The response `id` goes into `mark-paid.payoutProofDocumentId`, `evidenceDocumentId` or `qrDocumentId`. Response: `{ id, docType, originalFilename, mime, sizeBytes, sha256, createdAt }`; `originalFilename` is cleaned (extension matches the detected type) and may be null. Use it as the display/download name.
- **Test OTP (local/dev only):** start the backend with `OTP_FIXED_CODE=123456`. Every member OTP is then `123456`, so E2E tests can create members through `/auth/otp/request` and `/auth/otp/verify`. The backend refuses to start with it when real SMS is enabled. Use a fresh mobile per test run, because each mobile is limited to 3 OTP requests per 15 minutes and each IP to 20 per hour.

## 9. Not available yet (don't fake it)

No backend support yet:
- month-over-month deltas or distribution history beyond what `/admin/dashboard` returns
- a global ledger across all members
- CSV or report exports
- admin profile corrections for members (name / DOB)
- change-mobile approvals
- bank-change alerts and name-match checks on the server
- TOTP / 2FA and server-side idle timeout

Hide these, or ask before inventing endpoints.
