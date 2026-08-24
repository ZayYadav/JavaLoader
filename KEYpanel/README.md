# OneCore MyThos KEYpanel

Standalone PHP/MySQL key control panel for the JavaLoader repository.

This panel is based on the hardened `ParallaxSDK/panels/key-panel` core and is aligned to the current JavaLoader production identity:

- Package: `OneCore.Vip`
- Production certificate SHA-256: `B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4`
- Production signing alias: `onecore-mythos-prod`
- Loader API route: `/api/v2/connect`
- Default game ID: `PUBG`

## Features

- Owner/admin/reseller accounts
- Shared `keys_code` inventory used by web panel, Telegram bot and JavaLoader
- Key generation, expiry, device limits, block/enable/reset/delete
- Owner-managed game and duration lists
- Audit trail and maintenance control
- Telegram control bot with webhook-secret validation and numeric-user allowlist
- PHP/MySQL first-run installer
- AES-256-GCM encrypted request/response channel
- RSA-OAEP wrapped session key
- Request nonce replay defense, timestamp validation and canary binding
- Server-side package, certificate SHA-256 and minimum-version binding
- HTTPS enforcement and Android TLS SPKI pin workflow
- CSRF + same-origin checks, strict sessions, login rate limits, CAPTCHA and password hashing
- CSP, HSTS, frame denial, no-referrer and protected private directories
- `tools/deploy-check.php` production self-check
- Unit/config/integration tests

## Hosting requirements

- PHP 8.1+
- MySQL 8.0+ or MariaDB 10.5+
- PHP extensions: `PDO`, `pdo_mysql`, `session`, `json`, `openssl`, `curl`
- Apache/LiteSpeed with `.htaccess` + rewrite support
- HTTPS domain/subdomain
- Writable `runtime/` directory

## Quick deploy

1. Upload the **contents** of `KEYpanel/` into your panel domain document root.
2. Create a MySQL database/user and grant that user all privileges on the panel database.
3. Copy `.env.example` to `.env`.
4. Run:

   ```bash
   php tools/generate-secrets.php
   ```

5. Fill `.env` with the real domain/database values. Keep these Android bindings unchanged unless you deliberately rotate the production identity:

   ```dotenv
   EXPECTED_ANDROID_PACKAGE=OneCore.Vip
   EXPECTED_ANDROID_CERT_SHA256=B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4
   MIN_ANDROID_VERSION_CODE=1
   ```

6. Open `https://YOUR-PANEL-DOMAIN/setup` and create the first owner. Setup installs the schema and generates a 3072-bit RSA API key pair.
7. Run:

   ```bash
   php tools/deploy-check.php
   ```

8. Sign in and keep `PUBG` in **Settings -> Key generation lists** while JavaLoader submits `PUBG`.

## GitHub Actions configuration

In `ZayYadav/JavaLoader -> Settings -> Secrets and variables -> Actions` configure:

### Repository secrets

- `ANDROID_KEYSTORE_BASE64` — Base64 of the production `.jks`
- `ANDROID_KEYSTORE_PASSWORD` — production keystore password
- `ANDROID_KEY_ALIAS` — `onecore-mythos-prod`
- `ANDROID_KEY_PASSWORD` — production key password

Never store the `.jks` or passwords in this repository.

### Repository variables

- `JAVA_LOADER_LICENSE_URL` — `https://YOUR-PANEL-DOMAIN/api/v2/connect`
- `JAVA_LOADER_API_PUBLIC_KEY_B64` — content of `runtime/api-public.b64` after setup
- `JAVA_LOADER_TLS_PINS` — current `sha256/...=` SPKI pin for the panel HTTPS host
- `JAVA_LOADER_GAME_ID` — `PUBG`

Get the TLS pin from hosting:

```bash
php tools/tls-pin.php YOUR-PANEL-DOMAIN
```

The Android Gradle configuration also accepts legacy `PARALLAX_*` environment names, but the current JavaLoader production workflow uses the `JAVA_LOADER_*` names above.

## Production build

Normal PRs and normal `main` pushes only run a non-distributable release compile check. The temporary APK is deleted.

A real production APK is generated only by manually running the `OneCore MyThos Release` workflow. The production lane refuses to publish unless the supplied keystore alias and certificate digest match the pinned production identity.

Expected production certificate:

```text
B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4
```

After downloading the production artifact, verify it again with `apksigner --print-certs` before installation/distribution.

## Telegram controls

Create a bot with BotFather, put the token/username plus allowed numeric Telegram user IDs in `.env`, then run:

```bash
php tools/configure-telegram.php
```

The Telegram ID must also be linked to an active owner/admin panel account.

## Testing

```bash
find . -name '*.php' -print0 | xargs -0 -n1 php -l
php tests/run.php
php tests/config.php
```

With `DB_*` pointing at an empty test database:

```bash
php tests/integration.php
```

## Documentation

See `CONFIGURATION-HINGLISH.md` for the complete A-to-Z setup guide. The PDF version is generated from the same deployment contract.

See `SECURITY.md` for trust boundaries, backups and rotation guidance.
