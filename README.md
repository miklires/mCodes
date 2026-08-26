<div align="center">

# mCodes

Transactional promo and referral codes for Minecraft servers.

[![Paper](https://img.shields.io/badge/Available_for-Paper-222c31?style=for-the-badge)](https://papermc.io/software/paper)
[![Purpur](https://img.shields.io/badge/Available_for-Purpur-5f2167?style=for-the-badge)](https://purpurmc.org/)
[![Folia](https://img.shields.io/badge/Available_for-Folia-69c535?style=for-the-badge)](https://papermc.io/software/folia)

[![Build](https://img.shields.io/github/actions/workflow/status/miklires/mCodes/build.yml?label=build)](https://github.com/miklires/mCodes/actions)
![Release](https://img.shields.io/badge/release-v1.0.0-0ea5e9)
![Java](https://img.shields.io/badge/Java-25-5382a1)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-62b47a)

</div>

mCodes provides one-time promo codes and player referral codes with atomic limits, self-referral protection and a durable reward journal.

## Features

- Promo and referral codes with global and per-player limits.
- Expiration, disabling and case-insensitive identifiers.
- One referral per player and self-referral protection.
- SHA-256 identity hook without storing raw addresses.
- H2 transactions and retry-safe command rewards.
- Paper, Purpur and Folia 26.2 support.

## Commands

- `/code redeem <code>` — redeem a code.
- `/refer [code]` — show/create your referral code or redeem another player's code.
- `/code create <code> <limit>` — create a promo code.
- `/code delete <code>`, `/code list`, `/code reload` — administration.

## Build

Run `./gradlew clean build`. The release JAR is written to `build/libs`.

MIT licensed.
