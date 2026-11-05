# AllianceTrading Member App (React Native): Project Context

Read this file before writing any code for the member mobile app. It defines what to build, where the design comes from, how to talk to the backend, and the standards to follow. When this file and your own assumptions disagree, this file wins. When this file and the live API disagree, the API (Swagger) wins, so flag the difference.

---

## 1. Product in one paragraph

AllianceTrading is a closed investment club. A member signs in with a mobile OTP, submits KYC (name, DOB, PAN, Aadhaar) and a payout bank account, then waits for an admin to approve them. Once approved, the admin assigns them a company UPI account and a fixed amount (₹10,000 today). The member pays by UPI, uploads a screenshot, and an admin approves the investment. Admins post daily profit/loss to the member's balance. The member can withdraw profit (fees apply) and raise support tickets. All money and identity rules live on the backend. **The app only displays and collects; it never calculates balances, fees or P&L itself.**

---

## 2. Design source: the Figma Make prototype

| Item | Location |
|---|---|
| Member screens | `C:\Users\sr106824\Downloads\TestAnis-main\TestAnis-main\src\App.tsx` |
| Design tokens / global CSS | `C:\Users\sr106824\Downloads\TestAnis-main\TestAnis-main\src\index.css` |
| Original Figma | Figma Make file (password-protected; the exported code above is the source of truth) |

The prototype is a **web React + Tailwind v4 mock with hard-coded data**. Use it for **layout, visual hierarchy, copy, spacing, colours and flow order only**. Do not copy its state logic, fake data, timers or `setScreen` navigation into the app.

### 2.1 Design tokens (from `index.css`)

| Token | Value |
|---|---|
| Font | **Manrope** 400/500/600/700/800 (load via `expo-font` / `@expo-google-fonts/manrope`) |
| `brand` | `#3857ff` |
| `brand-dark` | `#2746eb` |
| `brand-soft` | `#edf0ff` |
| `canvas` (light bg) | `#f7f8fc` |
| `canvas-dark` | `#0b0d14` |
| `surface-dark` | `#12151f` |
| Brand shadow | `0 20px 50px -18px rgb(56 87 255 / 0.48)` |
| Input (`.field`) | height 64, radius 16, 1px slate-200 border, bold 16px text, focus = brand border + brand/10 ring |
| Neutrals | Tailwind `slate` scale |

Dark mode exists in the prototype (`ThemeToggle`, `dark:` variants), so support light and dark from day one, following the system setting with a manual override.

### 2.2 Prototype screen → app screen → API map

| Prototype component (`App.tsx`) | App screen | Backend |
|---|---|---|
| `Splash` | Splash / bootstrap | `GET /api/v1/app/version` (force update), restore session |
| `PortalChoice` | **Drop.** The app is member-only; admins use the web console | — |
| `Onboarding` step `mobile` | Enter mobile | `POST /api/v1/auth/otp/request` |
| `Onboarding` step `otp` | Verify OTP | `POST /api/v1/auth/otp/verify` |
| `Onboarding` steps `name`, `dob`, `pan` (+ Aadhaar) | KYC form | `POST /api/v1/kyc` |
| `Onboarding` steps `bank`, `upi` | Bank details | `POST /api/v1/bank-accounts` |
| `Waitlist` | Under review / rejected | `GET /api/v1/me/profile`, `GET /api/v1/kyc` |
| `Dashboard` | Home | `GET /api/v1/me/summary`, `GET /api/v1/me/profile`, `GET /api/v1/me/ledger?limit=5` |
| `Transactions`, `TransactionRow` | Transactions | `GET /api/v1/me/ledger` (cursor paging) |
| `Invest` | Invest / pay intro | `GET /api/v1/payment/current` |
| `Payment`, `QrCode` | Pay via UPI | `GET /api/v1/payment/current` (`upiUri`), `GET /api/v1/payment/qr` (PNG) |
| `UploadPayment` | Upload screenshot | `POST /api/v1/files` then `POST /api/v1/investments` |
| `PaymentReview` | Payment under review | `GET /api/v1/investments` |
| `Withdraw` | Withdraw profit | `GET /api/v1/me/summary`, `POST /api/v1/withdrawals/quote` |
| `WithdrawReview` | Confirm withdrawal | `POST /api/v1/withdrawals` |
| `WithdrawSuccess` | Withdrawal status | `GET /api/v1/withdrawals` (+ `POST /{id}/cancel` while `REQUESTED`) |
| `Profile` | Profile | `GET /api/v1/me/profile`, `GET /api/v1/bank-accounts` |
| Support card on `Dashboard` | Support tickets (list, thread, new) | `/api/v1/support/tickets` |
| `ThemeToggle` | Settings → Appearance | local only |
| — (add) | Logout | `POST /api/v1/auth/logout` |

### 2.3 Where the prototype and the backend disagree (backend wins)

| Prototype shows | Build this instead |
|---|---|
| 4-digit OTP | **6-digit** OTP (`^\d{6}$`) |
| Bank step asks only account number | Collect **holder name, account number (9–18 digits), IFSC (`^[A-Z]{4}0[A-Z0-9]{6}$`), account type (`SAVINGS`/`CURRENT`/`SALARY`), UPI ID** |
| No Aadhaar step | Add **Aadhaar** (12 digits, checksum validated by the server) |
| Fixed "₹200/day, ₹2,000/month" and monthly progress | **Do not show projected or fixed returns.** Show only real figures from the API (regulatory risk). Hide the "This month" progress card until an API exists |
| Flat 1% withdrawal fee | Show the **fee returned by the quote API** (currently 2% profit / 5% principal; admins can change it) |
| Withdrawal: review → approved → released | Statuses: `REQUESTED`, `APPROVED`, `PAID`, `REJECTED`, `CANCELLED`, `PAYOUT_FAILED` |
| "Edit" buttons on Profile | KYC fields are **read-only** after submission. Bank change = new `POST /bank-accounts` (see 6.4) |
| Change withdrawal bank | Not supported (one verified account) |
| Notifications inbox | Not available yet; push only |

---

## 3. Recommended stack

These are recommendations; deviate only with a reason written in the PR.

- **Expo** (managed workflow) + **React Native** + **TypeScript (strict)**
- Navigation: **expo-router** (file-based) or React Navigation, with separate auth and app stacks
- Styling: **NativeWind** (Tailwind classes) so the prototype's class names translate almost 1:1; put the tokens above in `tailwind.config`
- Server state: **TanStack Query**. No hand-rolled caching. Client state: React context or Zustand (minimal)
- HTTP: **axios** (or fetch wrapper) with the auth interceptor in section 5
- Forms: **react-hook-form + zod** (mirror the server regexes in section 7)
- Secure storage: **expo-secure-store** for the refresh token
- Images/QR: `expo-image` (supports auth headers), `expo-image-picker` / `expo-document-picker` for proofs
- Push: `expo-notifications` (backend FCM delivery is **not built yet**; still register the token)
- Lint/format: ESLint + Prettier; tests: Jest + React Native Testing Library

---

## 4. Environments and backend

| | |
|---|---|
| Base URL | `EXPO_PUBLIC_API_BASE_URL` (local Android emulator: `http://10.0.2.2:8080`; iOS sim: `http://localhost:8080`; device: LAN IP) |
| API prefix | `/api/v1` |
| API docs (source of truth) | `{base}/swagger-ui/index.html`, `{base}/v3/api-docs` |
| Postman | `c:\dev\alliancetrading\postman\AllianceTrading.postman_collection.json`, **User** folder |
| Local OTP | Not sent by SMS locally; printed in the backend console |
| Seeded test mobiles | `9876543210`, `9876543211`, `9876543212` (or any valid new number) |

Never hard-code URLs or secrets in source. Use `EXPO_PUBLIC_*` env vars per build profile (dev / staging / prod).

---

## 5. Authentication and token refresh (read carefully)

### 5.1 Tokens

| Token | Lifetime | Store where |
|---|---|---|
| Access token (JWT) | **15 minutes** | **Memory only** |
| Refresh token (opaque) | **30 days** | **expo-secure-store** (Keychain / Keystore). Never AsyncStorage, never logs |

`POST /auth/otp/verify` and `POST /auth/refresh` both return:

```json
{ "accessToken": "…", "refreshToken": "…", "expiresAt": "2026-11-21T09:00:00Z", "newUser": true }
```

Admin sessions also get an `admin` field; it is absent for members.

`expiresAt` is the **refresh token's** expiry (30 days). It is **not** when the access token expires. Read the access token's expiry from its JWT `exp` claim (decode with `jwt-decode`).

### 5.2 Login flow

1. `POST /auth/otp/request` `{ "mobile": "9876543210" }` → 200 (empty). Show a 30s resend timer. Handle `RATE_LIMITED` (429).
2. `POST /auth/otp/verify` `{ mobile, otp, deviceId, deviceInfo }`
   - `deviceId`: a stable per-install UUID, generated once and kept in secure-store.
   - `deviceInfo`: e.g. `"Pixel 7 · Android 15 · app 1.0.0"`.
   - Errors: `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `UNAUTHENTICATED` (wrong code), `FORBIDDEN` (suspended account).
3. Save the refresh token, keep the access token in memory, then route by `GET /me/profile` status (section 6.1).

### 5.3 Every request

`Authorization: Bearer <accessToken>`. Optionally send `X-Request-Id: <uuid>`; the server echoes it and includes `requestId` in errors, so log it.

### 5.4 Refresh: must be single-flight

The backend **rotates** the refresh token on every refresh and has **reuse detection**. If an old refresh token is presented again, the server **revokes the session** and returns `401 SESSION_REPLACED`. So:

- Allow **only one refresh request at a time**. Queue all other failing requests behind a shared promise and retry them with the new access token.
- Refresh proactively about 60 seconds before the access token's JWT `exp`, and reactively on `401` with code `TOKEN_EXPIRED` or `UNAUTHENTICATED`.
- **Write the new refresh token to secure-store before releasing the queue.** If the app is killed mid-refresh and still holds the old token, the next launch will trigger reuse detection and force a re-login. That's acceptable and expected.
- Never retry a refresh that failed with 401.

```ts
let refreshing: Promise<string> | null = null;
async function getFreshAccessToken() {
  refreshing ??= (async () => {
    const rt = await SecureStore.getItemAsync('refreshToken');
    if (!rt) throw new LoggedOut();
    const r = await raw.post('/api/v1/auth/refresh', { refreshToken: rt });
    await SecureStore.setItemAsync('refreshToken', r.data.refreshToken);
    setAccessToken(r.data.accessToken); // expiry: jwtDecode(accessToken).exp
    return r.data.accessToken;
  })().finally(() => { refreshing = null; });
  return refreshing;
}
```

### 5.5 Single active session

A member can be logged in on **one device only**. Logging in elsewhere revokes this session. Any request then returns **`401` with `code: "SESSION_REPLACED"`**:
- Don't try to refresh.
- Clear tokens and show: "You were signed in on another device."
- Go to the login screen.

The same applies after refresh-token reuse or an admin suspension.

### 5.6 Logout

`POST /auth/logout`, then delete the refresh token, clear the TanStack Query cache and reset navigation. Clear local state even if the call fails.

### 5.7 App start

1. `GET /app/version` → `{ minAppVersion }`. If the installed version is older, show a blocking update screen.
2. If a refresh token exists, refresh it; on success go to the routing rules in 6.1, otherwise go to login.

---

## 6. Business flows and rules

### 6.1 Routing by account status (`GET /me/profile`)

| `status` | Show |
|---|---|
| `REGISTERED` | KYC form, then bank form |
| `KYC_SUBMITTED` | Waitlist / under review |
| `REJECTED` | Rejection reason plus resubmit KYC |
| `APPROVED` | Main app (tabs: Home, Transactions, Invest/Withdraw, Profile) |
| `SUSPENDED` | Login is blocked by the server (`FORBIDDEN`); show a support message |

### 6.2 KYC

- `POST /kyc { fullName, dob: "YYYY-MM-DD", pan: "ABCDE1234F", aadhaar: "123456789012" }`
- Uppercase the PAN on input. The member must be 18 or older.
- Aadhaar has a checksum, so show the server's message on `VALIDATION_FAILED`.
- `DUPLICATE_PAN` / `DUPLICATE_AADHAAR` → "Already registered, contact support."
- **Never** display full PAN or Aadhaar after submission; the API returns masked values only. Mask while typing is optional; don't log these fields.

### 6.3 Invest

1. `GET /payment/current` → `{ amount, vpa, payeeName, upiUri, qrContentUrl }`. A `409 NO_PAYMENT_ASSIGNMENT` means the admin hasn't assigned one yet.
2. "Pay with UPI app": `Linking.openURL(upiUri)`. Also show the QR from `GET /payment/qr` (PNG, needs the auth header) with copy/share for the UPI ID.
3. Upload the proof with `POST /files` as multipart: `file` (JPEG/PNG/PDF, **max 5 MB**) and `docType=PAYMENT_PROOF`. This returns `{ id, docType, originalFilename, mime, sizeBytes, sha256, createdAt }` (`originalFilename` is cleaned and may be null). `GET /files/{id}/content` sends `Content-Disposition: inline; filename="..."; filename*=UTF-8''...` with that name.
4. `POST /investments { proofDocumentId }` with header **`Idempotency-Key: <uuid>`**. Generate the key once per attempt and reuse it on retries.
5. Show the investment as `PENDING_VERIFICATION` until the admin approves it (`ACTIVE`) or rejects it (show the reason).

Only **one live investment** per member.

### 6.4 Bank details change

`POST /bank-accounts` with the new details creates a pending account:
- Payouts keep going to the old verified account until an admin verifies the new one.
- Changing a verified account starts a **48h cooling-off**; withdrawals return `409 COOLING_OFF_ACTIVE` until it ends.
- Explain this before the member submits.

### 6.5 Withdraw

1. `GET /me/summary` → `withdrawable`, `canWithdraw`, `reasonIfNot`. If `canWithdraw` is false, show `reasonIfNot`.
2. `POST /withdrawals/quote { amount, withdrawalType: "PROFIT" }` → `{ quoteId, grossAmount, feePct, feeAmount, netAmount, destinationMasked, … }`. Show the fee and net exactly as returned. Quotes expire (`410 QUOTE_EXPIRED`); re-quote when that happens.
3. `POST /withdrawals { quoteId }` with an `Idempotency-Key`.
4. There is one open withdrawal at a time (`OPEN_WITHDRAWAL_EXISTS`). The member can cancel while it is `REQUESTED`.
5. `PRINCIPAL` withdrawal is behind a feature flag (`FEATURE_DISABLED`). Hide it unless product enables it.

### 6.6 Home and transactions

- Home figures come straight from `/me/summary`: `principal`, `profit`, `currentValue`, `withdrawable`, `held`.
- Ledger: `GET /me/ledger?limit=20&cursor=` returns `{ items, nextCursor }`. Use infinite scroll; `nextCursor: null` means the end.
- Group by **local date** (device time zone) on the client.

### 6.7 Support

`POST /support/tickets { subject, message }`, `GET /support/tickets`, `GET /support/tickets/{id}`, `POST /support/tickets/{id}/messages { message }`.

### 6.8 Push

On login and whenever the push token changes, call `PUT /devices/push-token { pushToken }`. Every account notification (KYC decision, investment decision, withdrawal updates, P&L postings, support replies) is sent by push only. The only SMS a member receives is the login OTP.

---

## 7. API conventions

- **Errors** are RFC 7807 `application/problem+json`:
  `{ "type", "title", "status", "detail", "code": "VALIDATION_FAILED", "requestId": "…" }`.
  **Branch on `code`, never on `detail` text.** Show `detail` to the user only for `VALIDATION_FAILED` and business-rule codes; otherwise use your own friendly copy plus the `requestId` for support.

  Codes the app must handle:
  - Auth: `UNAUTHENTICATED`, `TOKEN_EXPIRED`, `SESSION_REPLACED`, `FORBIDDEN`
  - OTP and rate limits: `RATE_LIMITED`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`
  - KYC: `DUPLICATE_PAN`, `DUPLICATE_AADHAAR`
  - Payment and investment: `NO_PAYMENT_ASSIGNMENT`
  - Withdrawals: `INSUFFICIENT_WITHDRAWABLE`, `OPEN_WITHDRAWAL_EXISTS`, `COOLING_OFF_ACTIVE`, `QUOTE_EXPIRED`, `FEATURE_DISABLED`
  - General: `ILLEGAL_STATE_TRANSITION`, `IDEMPOTENCY_CONFLICT`, `SMS_SEND_FAILED`
  - Uploads: `UNSUPPORTED_MEDIA_TYPE`, `FILE_TOO_LARGE`
  - Server: `INTERNAL_ERROR`
- **Timestamps** (`createdAt`, …) are ISO-8601 **UTC** (`…Z`). Always display them in the **device time zone**, e.g. `new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })`. Never show the raw string.
- **Plain dates** (`dob`, `sessionDate`) are `YYYY-MM-DD` with no time zone. Never convert them through `Date`, or they can shift a day.
- **Money** comes as JSON numbers with 2 decimals (rupees, not paise). Display it with `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })` (e.g. ₹1,00,000.00). **Never do money arithmetic in the app.** All totals, fees and P&L come from the API. Send amounts as numbers with at most 2 decimals.
- **IDs** are UUID strings.
- **Idempotency**: `POST /investments` and `POST /withdrawals` require `Idempotency-Key`.
- **Validation** (mirror these on the client; the server is final):
  - mobile `^[6-9]\d{9}$`
  - OTP `^\d{6}$`
  - PAN `^[A-Z]{5}[0-9]{4}[A-Z]$`
  - Aadhaar `^\d{12}$`
  - account number `^\d{9,18}$`
  - IFSC `^[A-Z]{4}0[A-Z0-9]{6}$`
  - UPI ID `^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$`
- **Enums** to model as TypeScript union types:
  - User status: `REGISTERED | KYC_SUBMITTED | APPROVED | REJECTED | SUSPENDED`
  - Investment: `PENDING_VERIFICATION | ACTIVE | REJECTED | EXIT_REQUESTED | CLOSED`
  - Withdrawal: `REQUESTED | APPROVED | CANCELLED | REJECTED | PAID | PAYOUT_FAILED`
  - Bank: `PENDING_VERIFICATION | VERIFIED | REJECTED | REPLACED`

Generate request/response types from `/v3/api-docs` (e.g. `openapi-typescript`) instead of hand-writing them, and regenerate when the backend changes.

---

## 8. Engineering standards

- **Project layout:**
  - `app/` (routes)
  - `src/api/` (client, interceptors, generated types, one module per backend area)
  - `src/features/<area>/` (screens, hooks, components)
  - `src/ui/` (design-system primitives: Button, Field, Card, AmountText, StatusBadge)
  - `src/lib/` (format, validation, storage)
- **Data fetching:** one TanStack Query hook per endpoint (`useSummary`, `useLedger`, …). Invalidate the related queries after mutations, e.g. after a withdrawal invalidate `summary`, `withdrawals` and `ledger`. Pull-to-refresh on every list and on Home.
- **Every screen** handles loading (skeletons like the prototype's `animate-loading`), empty, error (with retry) and offline states.
- **Security:**
  - No tokens, PAN, Aadhaar or account numbers in logs, analytics or crash reports.
  - Disable screenshots on KYC and payment screens if product agrees (`expo-screen-capture`).
  - Blur app content in the app switcher.
  - Certificate pinning before production.
- **Accessibility:** minimum 44×44 touch targets, labels on icons, support dynamic font size, colour contrast AA.
- **Copy:** English (India) and the ₹ symbol. Keep strings in one place so i18n can be added later.
- **Quality:** TypeScript strict, no `any`. ESLint plus Prettier must pass. Unit test the auth interceptor (single-flight refresh, `SESSION_REPLACED`) and the formatters.
- **Compliance:** never advertise guaranteed or fixed returns. Show past P&L only as reported.

---

## 9. Not available yet (don't fake it)

These are not in the backend. Hide them or show "coming soon", and ask before inventing APIs:
- month-to-date profit or targets, P&L charts and time series
- notifications inbox
- editing profile fields, changing mobile number, deleting the account
- statements and document downloads, referral
- FCM push delivery (the token is stored, delivery is logged only)
