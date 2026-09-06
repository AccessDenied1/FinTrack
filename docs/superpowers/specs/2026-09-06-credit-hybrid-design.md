# Credit Hybrid Design — Carousel + Statement Deep-Dive + Insights (A+C+D)

**Date:** 2026-09-06
**Status:** Draft — awaiting user review
**Scope:** Hybrid (lightweight now, exact linkage later) — no pay flow (B) in this iteration
**Related:** Precision Ledger theme (`core/ui/theme/*`), tiered CI (`build.yml:3`)

## 1. Goal & Non-Goals

**Goal:** Make Cards on par with CRED/Cheq for A (swipeable card stack), C (tap bill → that statement's transactions), D (utilization, due calendar, spend by category per card) — with rigorous tests.

**Non-Goals:** B pay via UPI + rewards, credit score, offers, SQLCipher.

## 2. Architecture

- Keep existing multi-module: `feature:cards` owns UI, `core:data` owns repo, `core:database` owns Room, `service:parser` owns SMS.
- No new module. New files only in existing modules.

## 3. Data & DB (v4 → v5)

**CardBillEntity.kt:8**
```kotlin
creditLimit: Double? = null          // from SMS or manual
statementStart: Long = 0L            // billing window start, 0 = derived as dueDate - 30d
```
**TransactionEntity.kt:8**
```kotlin
cardId: Long? = null                 // FK credit_cards.id, SET NULL, Index(["cardId"])
```
**CreditCardEntity.kt:8**
```kotlin
creditLimitOverride: Double? = null  // manual limit fallback
```
**Migration:** `MIGRATION_4_5` adds columns `creditLimit REAL`, `statementStart INTEGER NOT NULL DEFAULT 0`, `cardId INTEGER` + `Index cardId`, `creditLimitOverride`. All nullable/default — no backfill. Old 3908 rows stay `cardId = null`, `creditLimit = null`.

**Repo:**
- `CreditCardRepository.kt:28` `suspend fun updateLimit(cardId: Long, limit: Double?)`
- `BankCardDao.kt:28` `updateLimit`, `CardBillDao.kt:22` queries unchanged, `TransactionDao.kt:22` `updateCardId`
- `CreditCardRepositoryImpl.kt:68` `upsertBill` now persists `creditLimit`/`statementStart`; `findCardByBank(bankHint): Long?` helper for lazy linking.

## 4. Parser

**ParserUtils.kt:160**
- `AVAILABLE_LIMIT_PATTERN = "available\\s*limit\\s*(?:is|:)?\\s*Rs\\.?\\s*([\\d,]+)"` → `extractCreditLimit(text): Double?`
- `STATEMENT_PERIOD_PATTERN = "statement\\s*period\\s*(\\d{1,2}[-/\\s][A-Za-z]{3,9})\\s*[−-to]+\\s*(\\d{1,2}[-/\\s][A-Za-z]{3,9})"` → `extractStatementStart(text, smsTime): Long?` else `dueDate - 30*DAY_MILLIS`

**CardSmsParser.kt:29**
- `ParsedCardBill` adds `creditLimit: Double?`, `statementStart: Long`
- `parseBill(sms)` extracts limit/period alongside `totalDue/minDue/dueDate`. If no limit, returns `null` (UI shows manual prompt). If no period, returns `dueDate - 30d`.

**SmsProcessorImpl.kt:31**
- `handleCardSms` after `upsertBill` → if `creditLimit != null` update card limit.
- Ledger path: when building `PendingTransaction`/`Transaction`, `cardId` left `null`; `TransactionRepositoryImpl.kt:54` sets `cardId` lazily during `acceptPending`/`insertTransaction` by `findCardByBank(bank)` + `dateTime in [statementStart, dueDate)` match (if no match, stays `null` — insights fallback to `bank` string).

**hold/block** still excluded from `looksLikeTransactionSms` — future `HOLD` type out of scope.

## 5. VM & UI

**CardsViewModel.kt:34**
```kotlin
data class CardInsights(
  val outstanding: Double, val limit: Double?, val utilization: Float?, // null if limit == null
  val dueCalendar: List<CardBill>, // unpaid sorted by dueDate
  val spendByCategory: Map<ExpenseCategory, Double>, // for selected card's current statement window
  val spendTrend: List<Double> // last 30d debits for selected card
)
data class CardsUiState(
  val cards: List<CreditCard>, val selectedCardId: Long?,
  val insights: Map<Long, CardInsights>, // per card
  ...
)
fun onSelectCard(id: Long)
fun onUpdateLimit(id: Long, limit: Double?)
```
Computes `insights` by combining `getAllCards`, `getAllBills`, `getAllTransactions()` — filters `tx.bank == card.bankName && tx.dateTime in window` (if `tx.cardId != null` prefer exact).

**CardsScreen.kt:50**
- Top: `HorizontalPager` (foundation:pager) — each page card visual: `bankColor(bankHint)` from `Color.kt:5` (HDFC red, Axis maroon, etc.), last4, network dot, `creditLimit` pill. Swipe updates `selectedCardId`.
- Below pager: **Hero** `Outstanding ₹X • Limit ₹Y • Util 42%` + `LinearProgressIndicator` (brass 75%, red 90%+), **Due calendar** horizontal chips, **Spend donut** + **WeeklyBars** per card (reuse `CategoryDonutChart.kt:46` / `WeeklyBarsChart.kt:22`).
- **C deep-dive:** Tap bill → `ModalBottomSheet` `StatementDetail` — transactions for that window, grouped by category.
- Settings: `feature/settings/SettingsScreen.kt:40` add `CardLimitItem` row under DATA to edit limit per card.

## 6. Error Handling

- SMS without limit → `creditLimit = null`, UI shows `Set limit` CTA, utilization hidden (no divide-by-zero).
- No period → `statementStart = dueDate -30d` (documented heuristic, not bank-exact).
- Bank mismatch (tx.bank != card.bankHint) → insights empty list (not crash), fallback to 0.
- Migration nullable → old DB opens, old transactions `cardId = null` still counted via `bank` fallback.
- `limit = 0` or negative → treated as `null`.

## 7. Testing (rigorous, as requested)

- **VM:** `CardsViewModelTest.kt:76` — utilization 42% (42k/100k), spendByCategory bank window filter, dueCalendar sorted, selectCard.
- **Parser:** `CardSmsParserTest.kt:13` — limit `Available limit Rs 1,00,000`, period `15 Oct - 14 Nov` → `statementStart`, no-limit → null, due fallback.
- **Repo (integration):** `CreditCardRepositoryImplTest.kt` — in-mem `Room.inMemoryDatabaseBuilder`, `upsertBill` with limit/period, `findCardByBank` lazy `cardId` set on `acceptPending` (old rows null, new linked).
- **CI:** Tiered as agreed `build.yml:3` — `push: dev` unit only (~35s), `PR → dev` unit+integration, `PR/push → main` full (unit+integration+ui+lint+debug).

## 8. Out of Scope

- Pay flow, rewards, credit score, offers, `transaction.cardId` backfill for 3908 rows (additive later).

## 9. Files Touched

- `core/database/*Entity.kt`, `Migrations.kt`, `FinTrackDatabase.kt`
- `core/data/*Repository*.kt`, `TransactionRepositoryImpl.kt`
- `service/parser/ParserUtils.kt`, `CardSmsParser.kt`, `SmsProcessorImpl.kt`
- `feature/cards/*`, `feature/settings/*`
- Tests: `CardSmsParserTest`, `CardsViewModelTest`, `CreditCardRepositoryImplTest`, `MigrationsTest`
