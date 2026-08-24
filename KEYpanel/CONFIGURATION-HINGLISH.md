# OneCore MyThos KEYpanel — Full Configuration & Setup (Hinglish)

Ye guide `JavaLoader/KEYpanel` ko shared hosting/VPS par deploy karke `OneCore.Vip` Android app ke saath encrypted key-login flow connect karne ke liye hai.

## 1. Final identity values

In values ko random change mat karna:

- Android package: `OneCore.Vip`
- Production signing alias: `onecore-mythos-prod`
- Production certificate SHA-256: `B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4`
- Loader route: `/api/v2/connect`
- Default game ID: `PUBG`

Production JKS password private secret hai. Usko repo, PDF, screenshot, Telegram ya source code me mat daalna.

## 2. Hosting requirements

Panel ke liye:

- PHP 8.1 ya newer
- MySQL 8.0+ ya MariaDB 10.5+
- `PDO`, `pdo_mysql`, `session`, `json`, `openssl`, `curl` PHP extensions
- Apache ya LiteSpeed with `.htaccess` and rewrite enabled
- Public HTTPS domain/subdomain
- `runtime/` directory PHP ke liye writable

Example panel URL:

```text
https://key.example.com
```

## 3. KEYpanel upload

`JavaLoader/KEYpanel/` ke andar jo files hain unke **contents** domain document-root me upload karo.

Expected hosting structure:

```text
public_html/
├── .env
├── .htaccess
├── index.php
├── assets/
├── database/
├── runtime/
├── src/
├── tests/
└── tools/
```

`src`, `database`, `tests`, `tools`, `runtime`, `.env`, private key, SQL dump aur backup files web se deny hote hain.

## 4. MySQL database setup

cPanel me MySQL Databases open karo:

1. Empty database banao.
2. Dedicated database user banao.
3. Strong random password set karo.
4. User ko us database par ALL PRIVILEGES do.

Example only:

```text
DB name: onecore_panel
DB user: onecore_user
```

Real DB password private rahega.

## 5. `.env` create karo

Hosting terminal:

```bash
cd /path/to/panel
cp .env.example .env
```

Phir secret generator:

```bash
php tools/generate-secrets.php
```

Generated `SETUP_TOKEN` aur Telegram webhook secret ko `.env` me daalo.

Recommended production `.env` skeleton:

```dotenv
APP_ENV=production
APP_URL=https://YOUR-PANEL-DOMAIN
APP_BASE_PATH=
APP_TIMEZONE=UTC
PANEL_NAME=OneCore MyThos
SESSION_NAME=onecore_mythos_panel
SETUP_TOKEN=YOUR_GENERATED_RANDOM_SETUP_TOKEN
TRUSTED_PROXY_IPS=

DB_HOST=localhost
DB_PORT=3306
DB_NAME=YOUR_DB_NAME
DB_USER=YOUR_DB_USER
DB_PASSWORD=YOUR_DB_PASSWORD

TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_WEBHOOK_SECRET=YOUR_GENERATED_WEBHOOK_SECRET
TELEGRAM_ALLOWED_USER_IDS=

API_PRIVATE_KEY_PATH=runtime/api-private.pem
API_PUBLIC_KEY_PATH=runtime/api-public.b64

EXPECTED_ANDROID_PACKAGE=OneCore.Vip
EXPECTED_ANDROID_CERT_SHA256=B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4
MIN_ANDROID_VERSION_CODE=1
```

### APP_URL

Agar direct subdomain:

```dotenv
APP_URL=https://key.example.com
APP_BASE_PATH=
```

Agar `https://example.com/panel`:

```dotenv
APP_URL=https://example.com
APP_BASE_PATH=/panel
```

Production me plain HTTP allowed nahi hai.

### TRUSTED_PROXY_IPS

Normal cPanel/shared hosting me blank rakho:

```dotenv
TRUSTED_PROXY_IPS=
```

Sirf tab fill karo jab TLS kisi reverse proxy par terminate hota ho jo tum control karte ho. `*` ya all-address trust kabhi mat use karna.

## 6. First setup

Browser me:

```text
https://YOUR-PANEL-DOMAIN/setup
```

Fill:

- `SETUP_TOKEN`
- owner username
- 12+ character owner password
- numeric Telegram ID

Setup:

- database schema install karta hai
- first owner create karta hai
- 3072-bit RSA transport key pair generate karta hai

Files:

```text
runtime/api-private.pem
runtime/api-public.b64
```

`api-private.pem` sirf hosting par rahega. GitHub me kabhi upload nahi karna.

Setup complete hone ke baad `SETUP_TOKEN` rotate/remove karo.

## 7. Deployment self-check

Hosting terminal:

```bash
cd /path/to/panel
php tools/deploy-check.php
```

Tool check karta hai:

- PHP version/extensions
- `.env`
- HTTPS APP_URL
- DB fields
- non-default SETUP_TOKEN
- `OneCore.Vip` package binding
- production cert `B343...53D4`
- runtime permissions
- RSA key presence/permissions
- proxy trust configuration

Blocking errors ko production se pehle fix karo.

## 8. Panel game/duration lists

Owner login -> `Settings -> Key generation lists`.

Current JavaLoader `PUBG` submit karta hai, isliye list me `PUBG` rehna chahiye.

Example games:

```text
PUBG|PUBG Mobile
BGMI|Battlegrounds Mobile India
```

Example durations:

```text
2|2 Hours
5|5 Hours
24|1 Day
72|3 Days
168|7 Days
336|14 Days
```

## 9. GitHub production signing Secrets

GitHub:

`ZayYadav/JavaLoader -> Settings -> Secrets and variables -> Actions -> Secrets`

Create exactly:

### `ANDROID_KEYSTORE_BASE64`

Production `.jks` ka Base64.

Termux/Linux:

```bash
base64 -w0 OneCore_MyThos_Production.jks > key-base64.txt
```

Safer GitHub CLI method:

```bash
base64 -w0 OneCore_MyThos_Production.jks \
| gh secret set ANDROID_KEYSTORE_BASE64 --repo ZayYadav/JavaLoader
```

### `ANDROID_KEYSTORE_PASSWORD`

Production JKS store password. Private secret.

```bash
gh secret set ANDROID_KEYSTORE_PASSWORD --repo ZayYadav/JavaLoader
```

Prompt par password paste karo. Password command line/history me mat likho.

### `ANDROID_KEY_ALIAS`

Value:

```text
onecore-mythos-prod
```

CLI:

```bash
gh secret set ANDROID_KEY_ALIAS \
--body "onecore-mythos-prod" \
--repo ZayYadav/JavaLoader
```

### `ANDROID_KEY_PASSWORD`

Private-key password.

```bash
gh secret set ANDROID_KEY_PASSWORD --repo ZayYadav/JavaLoader
```

## 10. Production JKS verify karo

```bash
keytool -list -v \
-keystore OneCore_MyThos_Production.jks \
-alias onecore-mythos-prod
```

Certificate SHA-256 exactly ye hona chahiye:

```text
B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4
```

Different hash hua to production workflow artifact publish nahi karega.

## 11. GitHub repository Variables

GitHub:

`Settings -> Secrets and variables -> Actions -> Variables`

Create exactly:

### `JAVA_LOADER_LICENSE_URL`

Value:

```text
https://YOUR-PANEL-DOMAIN/api/v2/connect
```

Example:

```text
https://key.example.com/api/v2/connect
```

### `JAVA_LOADER_API_PUBLIC_KEY_B64`

First setup ke baad hosting se:

```bash
cat runtime/api-public.b64
```

Jo single-line Base64 public key mile wahi value paste karo.

Private `api-private.pem` kabhi GitHub me mat daalna.

### `JAVA_LOADER_TLS_PINS`

Hosting:

```bash
php tools/tls-pin.php YOUR-PANEL-DOMAIN
```

Output format:

```text
sha256/XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX=
```

Wahi full string variable me paste karo.

Planned TLS certificate rotation ke liye multiple pins comma-separated ho sakte hain:

```text
sha256/PIN1=,sha256/PIN2=
```

### `JAVA_LOADER_GAME_ID`

Current value:

```text
PUBG
```

## 12. GitHub CLI se variables

```bash
gh variable set JAVA_LOADER_LICENSE_URL \
--body "https://YOUR-PANEL-DOMAIN/api/v2/connect" \
--repo ZayYadav/JavaLoader

gh variable set JAVA_LOADER_GAME_ID \
--body "PUBG" \
--repo ZayYadav/JavaLoader

gh variable set JAVA_LOADER_API_PUBLIC_KEY_B64 \
--body "$(cat runtime/api-public.b64)" \
--repo ZayYadav/JavaLoader
```

TLS pin:

```bash
PIN="$(php tools/tls-pin.php YOUR-PANEL-DOMAIN)"
gh variable set JAVA_LOADER_TLS_PINS \
--body "$PIN" \
--repo ZayYadav/JavaLoader
```

## 13. Telegram bot optional setup

BotFather me `/newbot` run karo.

`.env`:

```dotenv
TELEGRAM_BOT_TOKEN=BOTFATHER_TOKEN
TELEGRAM_BOT_USERNAME=BotUsernameWithoutAt
TELEGRAM_ALLOWED_USER_IDS=YOUR_NUMERIC_ID
TELEGRAM_WEBHOOK_SECRET=GENERATED_SECRET
```

Numeric Telegram ID:

1. Bot ko `/start` bhejo.
2. Hosting par:

```bash
php tools/telegram-user-id.php
```

Webhook configure:

```bash
php tools/configure-telegram.php
```

Allowed Telegram ID active owner/admin account me bhi linked hona chahiye.

## 14. Security behavior

Panel includes:

- HTTPS-only production mode
- HSTS
- CSP with scripts disabled
- frame denial
- no-referrer/noindex
- protected internal directories
- strict/HttpOnly/SameSite session cookies
- session ID rotation, idle timeout, absolute timeout
- CSRF + same-origin validation
- password hashing/rehash
- login throttling
- one-time CAPTCHA
- audit log
- AES-256-GCM encrypted request and response
- RSA-OAEP session-key wrapping
- replay nonce defense
- timestamp freshness checks
- encrypted canary binding
- package/certificate/version server checks
- TLS SPKI pin on Android side
- Telegram secret-header + allowlist authorization

## 15. Tests

PHP syntax:

```bash
find . -name '*.php' -print0 | xargs -0 -n1 php -l
```

Unit/security/config tests:

```bash
php tests/run.php
php tests/config.php
```

Empty MySQL test DB configure karke:

```bash
php tests/integration.php
```

## 16. Production release build

Normal PR/main push distributable APK publish nahi karta.

Real production build:

GitHub -> `Actions` -> `OneCore MyThos Release` -> `Run workflow` -> `main`.

CLI:

```bash
gh workflow run android-build.yml \
--repo ZayYadav/JavaLoader \
--ref main
```

Production workflow:

1. JKS secrets require karta hai.
2. alias `onecore-mythos-prod` verify karta hai.
3. JKS cert SHA-256 `B343...53D4` verify karta hai.
4. HTTPS license URL/RSA public key/TLS pin require karta hai.
5. Release APK build karta hai.
6. `apksigner` se final APK verify karta hai.
7. APK Signature Scheme v2 require karta hai.
8. Final signer SHA-256 dobara production cert se match karta hai.
9. Sirf tab production artifact upload hota hai.

## 17. Final APK signer verification

Downloaded production APK:

```bash
apksigner verify --verbose --print-certs app-release.apk
```

Expected signer SHA-256:

```text
B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4
```

Agar `JavaLoader CI Compile Only` ya koi different hash dikhe to APK distribute/install mat karo.

## 18. Panel-to-loader flow

```text
OneCore MyThos / OneCore.Vip
        |
        | HTTPS + TLS SPKI pin
        v
/api/v2/connect
        |
        | RSA-OAEP wrapped AES session key
        | AES-256-GCM encrypted payload
        v
KEYpanel
        |
        | package = OneCore.Vip
        | cert = B343...53D4
        | version >= MIN_ANDROID_VERSION_CODE
        | game = PUBG
        | key status / expiry / device limits
        v
AES-256-GCM encrypted response
        |
        | nonce + canary + timestamp validation
        v
Login accepted / rejected
```

## 19. Backup — ye 4 cheezein saath backup karo

1. MySQL database
2. production `.env`
3. `runtime/api-private.pem`
4. Android production signing `.jks`

Encrypted offline backup best hai.

## 20. Kya public ho sakta hai

Public/config values:

- panel HTTPS URL
- `OneCore.Vip`
- production certificate SHA-256
- production alias
- RSA public key
- TLS SPKI pin
- game ID

Private values:

- production `.jks`
- keystore/key passwords
- panel `.env`
- DB password
- RSA private key
- Telegram bot token/webhook secret
- real user/license database backup

## 21. Rotation warning

Android production signing key casually replace mat karna. New signer Android app identity aur signature pinning ko change karega.

Panel RSA transport key rotate ki ja sakti hai, lekin new public key GitHub Variable me copy karke Android production release rebuild karna padega.

TLS certificate rotate ho to new SPKI pin planned way me add karo, loader rebuild/test karo, phir old pin retire karo.
