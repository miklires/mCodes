<div align="center">
  <h1>mCodes</h1>
  <p>Secure promotional campaigns and player referrals for modern Minecraft servers.</p>

  <p>
    <a href="https://papermc.io/software/paper"><img alt="Paper" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/paper_vector.svg"></a>
    <a href="https://purpurmc.org"><img alt="Purpur" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/purpur_vector.svg"></a>
    <a href="https://papermc.io/software/folia"><img alt="Folia" height="56" src="https://raw.githubusercontent.com/miklires/mCommand/main/docs/assets/folia-available.png"></a>
  </p>

  <p>
    <a href="https://github.com/miklires/mCodes"><img alt="GitHub" src="https://tr7zw.github.io/uikit/social_buttons_icon/Github-Button-64.png"></a>
    <a href="https://modrinth.com/project/mcodes"><img alt="Modrinth" src="https://tr7zw.github.io/uikit/social_buttons_icon/Modrinth-Button-64.png"></a>
  </p>

  <p>
    <a href="https://bstats.org/plugin/bukkit/mCodes/27942"><img alt="bStats" src="https://img.shields.io/badge/bStats-27942-2F9BE6?style=for-the-badge"></a>
    <a href="https://github.com/miklires/mCodes/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/miklires/mCodes?style=for-the-badge"></a>
    <img alt="Java 25" src="https://img.shields.io/badge/Java-25-5382A1?style=for-the-badge">
  </p>
</div>

mCodes provides ready-to-use promo codes and two-sided referral rewards. Limits, eligibility and the reward journal are committed in one H2 transaction so concurrent redemptions cannot overspend a campaign.

## What it does

- Creates case-insensitive promo codes with global, per-player and optional network limits.
- Supports delayed activation, expiry, permission requirements and minimum playtime.
- Gives separate command rewards to a referred player and the referral-code owner.
- Prevents self-referrals, repeated referral use and reuse of a network identity across different referrers.
- Stores only a keyed HMAC of an address; raw addresses and the secret are never written to the database.
- Claims every reward transaction before commands run, preventing automatic duplicate delivery after a crash.
- Lists unresolved reward transactions for explicit staff retry or reconciliation.
- Runs database work asynchronously and schedules Bukkit operations safely on Paper, Purpur and Folia.
- Includes English and Russian interfaces, automatic config migration and optional bStats metrics.

## Quick start

1. Put `mCodes-1.1.0.jar` in the server's `plugins` directory and restart.
2. Keep the generated `security.identity-secret` private and unchanged.
3. Adjust the reward commands in `plugins/mCodes/config.yml`.
4. Create a campaign with `/code create WELCOME 100 1 168`.
5. Players redeem it with `/code redeem WELCOME`.

The bundled configuration works immediately: `WELCOME` in the example is limited to 100 total uses, one use per player, and expires after 168 hours.

## Commands

- `/code redeem <code>` — redeem a promo or referral code.
- `/refer` — create or show your personal referral code.
- `/refer <code>` — redeem another player's referral code.
- `/code create <code> <global-limit> [per-player] [expires-hours] [permission|-] [starts-hours]` — create a promo campaign. Use `0` for no global limit or expiry and `-` when no permission is required.
- `/code info <code>` and `/code list` — inspect campaigns.
- `/code enable <code>` and `/code disable <code>` — change availability without deleting history.
- `/code pending` — list pending or claimed reward transactions.
- `/code retry <transaction-uuid>` — retry a transaction that is still pending.
- `/code resolve <transaction-uuid>` — mark a manually reconciled claimed transaction as delivered.
- `/code reload` — reload and validate configuration.

Players receive `mcodes.use` by default. Administrative commands require `mcodes.admin`, which defaults to server operators.

## Language and configuration

English is selected by default. Set `language: ru_RU` and run `/code reload` to switch the interface to Russian.

`anti-abuse.identity-enabled` controls keyed network-identity checks. `referral-per-identity` defaults to one, while promo network limits are disabled until `promo-per-identity` is increased. Minimum playtime and all console reward commands are documented in the generated configuration. Supported placeholders are `{player}`, `{referrer}` and `{redeemer}`.

Changing `security.identity-secret` invalidates comparisons with previously stored identity hashes. Back it up with the database and never publish it.

## Reward recovery

A successful redemption first creates a `PENDING` journal entry. Delivery atomically claims it as `CLAIMED` before any console command runs and then marks it `DELIVERED`. Pending entries can be retried safely. A claimed entry means the process may have stopped while commands were running, so mCodes leaves it for staff review instead of risking duplicate currency or items.

Reward commands should still be designed to be idempotent where possible because Minecraft commands cannot participate in a database transaction.

## Requirements

- Java 25
- Paper, Purpur or Folia 26.2

No economy, permissions or external database plugin is required. Reward commands may call any plugin already installed on the server.

## Storage and privacy

Campaigns, redemptions and the recovery journal are stored in `plugins/mCodes/codes.mv.db`. Schema upgrades run automatically. mCodes uses anonymous [bStats metrics](https://bstats.org/plugin/bukkit/mCodes/27942) when `metrics.enabled` is `true`; disable it in the configuration or through the global bStats setting. Codes, UUIDs, player names, addresses, identity hashes and reward contents are never sent to bStats.

## Build

```bash
./gradlew clean build
```

The release JAR is written to `build/libs/mCodes-1.1.0.jar`.

Licensed under the MIT License.
