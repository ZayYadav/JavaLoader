# OneCore MyThos KEYpanel Security Notes

This folder is a standalone PHP/MySQL control panel for the JavaLoader encrypted licensing flow.

## Trust boundaries

- The Android signing certificate, panel RSA private key, `.env`, database credentials and Telegram bot token are private production secrets.
- Only the RSA **public** key, TLS SPKI pin, public HTTPS licensing URL, game ID and production certificate digest are copied into the Android build configuration.
- The server remains the final source of truth for key status, expiry, device limits and maintenance mode.

## Mandatory production identity

- Android package: `OneCore.Vip`
- Android production certificate SHA-256: `B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4`
- Production key alias: `onecore-mythos-prod`
- Licensing route: `/api/v2/connect` over HTTPS only

## Controls included

- PHP 8.1+ requirement and production HTTPS enforcement
- Strict, HttpOnly, SameSite session cookies with session rotation, idle expiry and absolute expiry
- CSRF token plus same-origin validation
- Password hashing with Argon2id when available and automatic rehash
- Login throttling and one-time arithmetic CAPTCHA
- Owner/admin/reseller authorization with audit logging
- No directory indexes and web-deny rules for `src/`, `database/`, `tests/`, `tools/`, `runtime/`, `.env`, private keys, dumps and backups
- CSP with scripts disabled, HSTS, frame denial, no-referrer, noindex and cross-origin isolation headers
- Encrypted loader protocol using AES-256-GCM, RSA-OAEP wrapped session keys, nonce replay defense, timestamp validation and canary binding
- Server-side package/certificate/version binding
- TLS SPKI pin support in the Android loader
- Telegram webhook secret-header validation plus Telegram user allowlist and linked panel account authorization

## Never commit

Do not commit or publish any of the following:

- `KEYpanel/.env`
- `KEYpanel/runtime/api-private.pem`
- MySQL exports containing real users/keys
- Telegram bot token/webhook secret
- Android production `.jks`/`.keystore`
- Android keystore/store passwords

The repository `.gitignore` and KEYpanel `.gitignore` are defense-in-depth only; secret handling still depends on operator discipline.

## Deployment checks

Run before production:

```bash
cd KEYpanel
php tools/deploy-check.php
find . -name '*.php' -print0 | xargs -0 -n1 php -l
php tests/run.php
php tests/config.php
```

With an empty test database configured through `DB_*` environment variables, also run:

```bash
php tests/integration.php
```

## Key rotation

Android signing-key rotation must be planned. Do not casually replace the production signing key; a different certificate changes the application identity and the loader/panel signer binding. If a legitimate rotation is performed, temporarily allowlist both approved certificate digests on the server only for the planned migration window, update the Android trust configuration deliberately, test upgrade behavior, then retire the old digest.

RSA transport-key rotation is separate from Android signing. Rotating the panel RSA key requires copying the new public key to the GitHub Actions variable and rebuilding the Android release. Keep the RSA private key only on hosting.

## Backups

Back up together and keep encrypted/offline copies of:

1. MySQL database
2. production `.env`
3. `runtime/api-private.pem`
4. Android production signing keystore

Losing the Android signing keystore can break future upgrade/signature compatibility. Losing the RSA private key requires generating a new pair and rebuilding the loader with the new public key.
