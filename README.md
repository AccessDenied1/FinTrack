# FinTrack — Precision Ledger

**The finance app that reads your bank SMS. No manual entry. No cloud. Just your ledger, on your device.**

<p>
  <img src="https://img.shields.io/badge/version-1.1.0-0D7A4C?style=flat-square&labelColor=0C1411" alt="version 1.1.0" />
  <img src="https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?style=flat-square&labelColor=0C1411" alt="Kotlin 2.0" />
  <img src="https://img.shields.io/badge/Compose%20BOM-2024.06-4285F4?style=flat-square&labelColor=0C1411" alt="Compose" />
  <img src="https://img.shields.io/badge/targetSdk-35-0D7A4C?style=flat-square&labelColor=0C1411" alt="targetSdk 35" />
  <img src="https://img.shields.io/badge/license-MIT-E5E3DF?style=flat-square&labelColor=0C1411&color=1A1E1C" alt="MIT" />
  <a href="https://github.com/AccessDenied1/FinTrack/actions"><img src="https://img.shields.io/github/actions/workflow/status/AccessDenied1/FinTrack/Build%20APK?label=build&style=flat-square&labelColor=0C1411" alt="build" /></a>
</p>

```
┌─────────────────────────────────────────────┐
│  FINTRACK  ·  LEDGER                        │
│  ₹ 1,42,380.00  SPENDING THIS MONTH         │
│  Today ₹1,240  ·  Per day ₹4,746  ·  Biggest ₹8,200  ·  Projected ₹1,47,000   │
│  ▁▂▃▅▃▂▁  M T W T F S S  — Daily spending   │
└─────────────────────────────────────────────┘
```

> FinTrack turns the SMS your bank already sends you into a quiet, offline ledger. Debits, credits, UPI, card statements — parsed on-device, queued for your review, never leaving your phone.

---

### Why it exists

Indian banking SMS is messy: `Rs.`, `INR`, `₹`, `a/c XX1234`, `UPI/123/…`, `Avl limit Rs. 50,000 Spent Rs. 2,000`. Existing expense apps either miss the amount (picking up a balance instead of the transaction) or mis-categorize `more ⊂ "for more details"`.

FinTrack does three things with obsessive correctness:

1. **Parses** — verb-anchored amount extraction, balance-context exclusion, card-aware due-date parsing
2. **Dedupes** — minute-bucketed fingerprint (`smsBody` + `dateTime/60000`) tolerates SMSC vs handset clock skew
3. **Queues** — persist-before-notify, `withTransaction` guard against double-accept, fuzzy duplicate warnings for you to decide

---

### Features

| Area | What it does |
|------|--------------|
| **SMS ingest** | Live `SmsReceiver` (`goAsync` 8.5s) + historical rescan of `content://sms/inbox` |
| **Parsers** | `HDFC` / `SBI` / `ICICI` / `Axis` / `GenericUPI` (requires `UPI`) / `GenericTransaction` (fallback) — ordered in `CompositeSmsParser` |
| **Card ledger** | Statements auto-upserted (±6d window), payments settle earliest bill (+1% tolerance) or credit as partial/prepay; never re-delivered |
| **Review queue** | Single + bulk `Accept All` (atomic), `Skip All`, fuzzy duplicate chip (`amount+merchant+type+same day`), haptics, 2-tap delete confirm |
| **Home** | Month navigator (24mo), like-for-like MoM delta, 7-day bars, category donut, upcoming bill radar (≤7d), recent 5 with staggered entrance |
| **Net Worth** | `initial + credits − debits` with `starting balance` prompt; `Expenses` list grouped by month, filter chips, search, detail sheet |
| **Manual** | `+` FAB → `Add Transaction` (amount/merchant/type/category/date at noon/notes) for SMS misses |
| **Privacy** | `Delete all data` (overflow menu → atomic `withTransaction` across 5 tables) on `Home` |

---

### Design — Precision Ledger

Not purple gradients. Not glass blur. **Paper and ink.**

- **Palette** — Warm paper `#F9F7F2` vs ink `#0C1411`; emerald `#0D7A4C` (money in), slate `#3A5B75` (structure), brass `#8A6E3A` (highlight). Debit is muted brick `#A12B2F`, not traffic red.
- **Type** — Tight display (`-1.5sp` Black 52sp) for hero numbers, mono tabular (`FontFamily.Monospace`) for every `₹` so columns align like a ledger.
- **Shape** — `8 / 14 / 20 / pill` — not `24` everywhere. `OutlinedCard` + `0.5dp` hairline, never heavy shadow.
- **Motion** — Spring, not tween 260ms. Staggered 40ms rise for lists.

```
Color.kt:5  GreenPrimary #0D7A4C   Theme.kt:12  Light paper vs  Dark ink
Type.kt:10  displayLarge Black -1.5sp   Spacing.kt:7  4pt grid + Hairline 0.5dp
```

---

### Architecture

```
app                 — MainActivity (singleTop deep-links), FinTrackApp (bottom nav), NavHost
core:common         — DispatcherModule, TimeModule (Clock), Format (canonical ₹)
core:model          — Transaction, PendingTransaction, CardBill, CreditCard, NetWorthState
core:database       — Room v4, 5 entities, Migrations 1→2→3→4, indices on dateTime/status/cardId
core:data           — Repositories (atomic acceptPending, bulk acceptAllPending, upsertBill ±6d)
core:ui             — Theme + TransactionItem 40dp hairline square, AnimatedCurrency, Donut 18dp, WeeklyBars
feature:home        — HomeViewModel (Clock-injected month windows, midnight tick)
feature:expense     — Review / PendingReview / ExpenseList / AddTransaction
feature:cards       — CardsViewModel urgency (OVERDUE/TODAY/3D/WEEK)
feature:networth    — NetWorthViewModel
service:sms         — SmsReceiver, SmsProcessorImpl, HistoricalSmsReader/Processor, SmsFingerprint
service:parser      — Composite + 6 bank parsers + CardSmsParser + ParserUtils (card last4, due dates)
service:categorizer — KeywordBasedCategorizer (word-boundary, merchant-first)
service:notification— TransactionNotifierImpl (channels transaction_alerts / card_alerts)
build-logic         — convention plugins (androidLibrary 35, compose, hilt)
```

Dependency rule: `feature → core:data → core:database` (`api` so `FinTrackDatabase` visible for `withTransaction`). `service:notification` depends on `core:common` for `Format`.

---

### Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Kotlin 2.0.0 |
| UI | Jetpack Compose BOM 2024.06, Material3 1.2.1, Navigation 2.7.7 |
| DI | Hilt 2.51.1 + KSP 2.0.0-1.0.21 |
| DB | Room 2.6.1 + `withTransaction` |
| Async | Coroutines 1.8.1 + `WhileSubscribed(5000)` |
| Min / Target | 26 / 35 (AGP 8.7.3, Gradle 8.9, JDK 17) |
| CI | `testDebugUnitTest` → `assembleDebug` → `assembleRelease` (R8) |

---

### Getting Started

**Prerequisites:** JDK 17, Android SDK 35, `local.properties` with `sdk.dir`.

```bash
# clone
git clone https://github.com/AccessDenied1/FinTrack.git
cd FinTrack

# debug APK (installable, debug keystore)
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# release bundle (requires signingConfigs — add your keystore to gradle.properties)
./gradlew bundleRelease

# tests (all modules)
./gradlew testDebugUnitTest

# single module
./gradlew :feature:home:testDebugUnitTest --tests "*HomeViewModelTest*"
```

Install:

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

### Permissions — why each is needed

| Permission | Where | Why |
|------------|-------|-----|
| `RECEIVE_SMS` | `app/AndroidManifest.xml:4` | Live `SMS_RECEIVED` broadcast |
| `READ_SMS` | `:5` | Historical rescan `content://sms/inbox` |
| `POST_NOTIFICATIONS` | `:6` | Android 13+ review / card bill alerts |

Rationale is shown via `PermissionCard` on `HomeScreen.kt:92` before system dialog. `SmsReceiver` is `exported=true` with `permission="BROADCAST_SMS"` and `priority="999"` (`app/AndroidManifest.xml:29`). No SMS body is logged — only `sender` prefix + `len`.

---

### Privacy

- **On-device only.** No network, no analytics, no cloud. SMS bodies are stored in `fintrack.db` (`transactions.smsBody`, `pending_transactions.smsBody`) in clear SQLite — intentionally, so you can see the source SMS on `Review`.
- **Exported?** `allowBackup="true"` lets Google auto-backup include the DB (so you can restore on a new phone). If that concerns you, set `allowBackup="false"` or add `dataExtractionRules`.
- **Erase:** `Home → ⋮ → Delete all data` (`HomeViewModel.kt:115` `withTransaction { deleteAll() x5 }`) wipes everything atomically.

---

### SMS Coverage (tested)

```
Debited:  "Rs. 1,500 debited from a/c XX1234 to SWIGGY on 10-Aug-26"
Credited: "INR 9,000 credited to a/c XX1234 from SALARY"
UPI:      "Paid Rs. 250 to ZOMATO via UPI ref 123..."
Card:     "HDFC Card XX4521 Total Due Rs. 45,000 Min Due Rs. 2,250 Due Date 15-Aug-26"
Payment:  "Payment of Rs. 45,000 received for HDFC Card XX4521 — thank you"
```

Amount extraction is verb-anchored and `balance/limit/outstanding` aware (`ParserUtils.kt:55` `isBalanceIntroduced` nearest-keyword wins). Re-delivered card statements update the same bill (`±6d`), next month inserts fresh.

---

### Versioning & Releases

`app/build.gradle.kts:14` holds the truth (`versionCode` int + `versionName` string). Tag must match `versionName`.

```bash
# bump
# edit app/build.gradle.kts → versionCode 3, versionName "1.2.0"
git add app/build.gradle.kts
git commit -m "chore: bump version to 1.2.0"
git tag -a v1.2.0 -m "v1.2.0 — hold/block parser + dropdown"
git push && git push origin v1.2.0
# GitHub → Releases → Draft from tag v1.2.0 → Publish
```

Current: `v1.1.0` (`versionCode 2`) — Precision Ledger + market hardening (`dd81deb`→`51371f0`). CI builds `app-debug.apk` on every push; for Play, `bundleRelease` → `.aab` is required.

---

### Roadmap

- [ ] Hold/lien SMS (`Rs. 2000 on hold` / `Hold released`) as `HOLD` type (excluded from monthly debits until released)
- [ ] Category dropdown (`CategoryPicker.kt:16` `FlowRow` → `ExposedDropdownMenuBox`) — saves vertical space
- [ ] Card payment disambiguation (UPI to `Card XX1234` vs ledger debit)
- [ ] Encrypted DB option (SQLCipher) for sensitive ledgers

---

### License

MIT — do what you want, keep the notice.

<p align="center"><sub>Built with care in India. For people who actually check their SMS.</sub></p>
