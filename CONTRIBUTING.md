# Contributing to FinTrack

Thanks for considering a contribution — FinTrack is built for real Indian banking SMS, and every edge case you fix helps someone track their money.

## Branching model (main + dev only)

```
feature/*  ─┐
fix/*      ─┼─►  dev  ──►  main
chore/*    ─┘      ▲         ▲
              integration  production (protected, tagged v*.*.*)
```

- **`main`** — stable, always releasable. Protected. Only merges from `dev` via PR. Tags `v1.1.0` → GitHub Release + signed `bundleRelease`. **CI runs here only** (`push`/`PR` to `main` → tests + lint + debug).
- **`dev`** — integration. All feature branches target `dev`. **No CI on push to `dev`** — keeps noise low; you get CI when you open `PR dev → main`.
- **`feature/<name>`**, **`fix/<name>`** — branch off `dev`, PR back to `dev` (no CI), then `dev → main` PR triggers CI.

**Flow for a new feature:**
```bash
git checkout dev; git pull
git checkout -b feature/hold-parser
# ... work, commit ...
git push -u origin feature/hold-parser
# PR: feature/hold-parser → dev (no CI — fast)
git checkout dev; git merge feature/hold-parser; git push
# PR: dev → main (CI runs: test + lint + debug)
# After green: merge → tag v1.2.0 → Release workflow builds AAB/APK
```

**Versioning:** `app/build.gradle.kts` `versionCode` (int, ever-increasing) + `versionName` (`1.1.0`). Tag `v1.1.0` must match `versionName`.

## Local setup

```bash
# JDK 17 + Android SDK 35
./gradlew testDebugUnitTest      # fast, no device
./gradlew assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease          # → app/build/outputs/bundle/release/app-release.aab (needs keystore for signing)
```

## CI/CD

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `CI` (`.github/workflows/build.yml`) | `push`/`PR` to `main` only + manual | `testDebugUnitTest` → `lintDebug` → `assembleDebug` → artifact `FinTrack-debug-main-<sha>` (30d) |
| `Release` (`.github/workflows/release.yml`) | tag `v*.*.*` | gate tests → `assembleRelease` + `bundleRelease` → artifacts + GitHub Release with changelog |

**Debug APK:** Open `PR dev → main` or push to `main` or manual `Actions → CI → Run workflow` → download debug APK. Install via `adb install`. No builds on `dev` pushes (quiet dev).

**Release APK/AAB:** Push a tag:
```bash
git tag -a v1.2.0 -m "v1.2.0 — hold parser"
git push origin v1.2.0  # triggers Release workflow
```
For Play, add `FINTRACK_KEYSTORE_*` secrets and enable `signingConfigs` in `app/build.gradle.kts`.

## Code style

- Kotlin `official` style (`gradle.properties: kotlin.code.style=official`)
- Single `Format.currency` (`core/common/util/Format.kt`) — never `DecimalFormat` inline
- Room indices on `dateTime/status/cardId` — add migration for new entities
- Hilt + `Clock` injected (testability via `Fixed` clock)

## Commit messages

`feat:`, `fix:`, `chore:`, `docs:`, `refactor:` — e.g. `feat: add hold/lien SMS parsing`

## Branch protection (maintainer)

Enable in GitHub → Settings → Branches:
- `main` → Require PR from `dev`, Require `test` + `lint` status, Dismiss stale, No force push.
- `dev` → No protection (fast pushes).

Questions? Open an issue or discussion.
