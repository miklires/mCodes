# Changelog

## 1.1.0 - 2026-08-29

- Added delayed activation, permission requirements, minimum playtime and per-identity campaign limits.
- Added keyed HMAC identity storage and generated a private per-server secret.
- Prevented one network identity from redeeming different referral codes.
- Added an atomic PENDING/CLAIMED/DELIVERED reward journal with staff recovery commands.
- Added code inspection, enable/disable controls, clearer localized failures and configuration validation.
- Added English-by-default and Russian interfaces, Folia-safe scheduling and storage-failure shutdown.
- Expanded tests, documentation, metadata and automated GitHub/Modrinth release publishing.

## 1.0.0 - 2026-08-26

- Added transactional promo and referral redemptions.
- Added durable command-reward delivery and H2 persistence.
- Added limits, expiration, self-referral protection and hashed identity hook.
- Added Paper, Purpur and Folia 26.2 support.
