# Security Policy

FinTrack handles sensitive financial SMS on-device. We take it seriously.

## Supported versions

| Version | Supported |
|---------|-----------|
| `main` (latest tag) | ✅ |
| `dev` / `staging` | best effort |
| < `1.0.0` | ❌ |

## Reporting a vulnerability

**Do not open a public issue for security bugs.**

Email: **vipin.official.seth@gmail.com** with subject `[FinTrack Security]`

Include: description, steps to reproduce, impacted version/tag, and if possible a redacted SMS sample (replace last 4 digits, amount can stay).

We aim to acknowledge within 48h and ship a fix within 14 days. You’ll be credited in the release notes unless you ask otherwise.

## What we do

- SMS bodies are stored in `fintrack.db` (`smsBody`) in clear SQLite and never leave the device (no network, no analytics).
- `allowBackup=true` means Google auto-backup can copy the DB — disable if you need at-rest encryption (see `README.md#privacy`).
- Logs never include `smsBody` at INFO — only `sender` prefix + `len` at DEBUG (`SmsReceiver.kt`).

## Disclosure

Once fixed, we publish a `SECURITY` entry in the GitHub Release notes and recommend updating via the next tag.
