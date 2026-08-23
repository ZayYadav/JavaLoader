# OneCore MyThos integrity policy

The release build uses a fail-closed, layered APK/signing identity chain.

## Java checks
- Expected package/application ID and UID ownership.
- Canonical installed `base.apk` / `publicSourceDir` agreement.
- Standalone-install policy: unexpected split APKs are rejected.
- `ApkVerifier` cryptographically verifies the exact installed APK.
- Active signer certificate SHA-256 values must match the build-pinned allowlist.
- Android PackageManager active signer and signing-certificate history are cross-checked.
- `hasSigningCertificate(..., CERT_INPUT_SHA256)` is used on Android 9+.
- APK archive entries are scanned for wrapper payload names and hidden executable content magic under `assets/` and `res/raw/`.

## Native checks
- Process/package identity is bound to `OneCore.Vip`.
- `base.apk` is bound to the loaded `libclient.so` installation root and filesystem identity.
- `/proc/self/maps` and `/proc/self/fd` are checked for suspicious external executable payloads and invalid client mappings.
- The exact installed `base.apk` APK Signature Scheme v2 block is parsed directly in C++.
- Native code extracts the raw leaf X.509 signer certificates from the on-disk v2 signing block.
- Java SHA-256 hashes those native-extracted certificates and requires them to match both the build allowlist and the `ApkVerifier` signer set.

## Wrapper payload policy
The app fails closed for common nested wrapper/repack layouts, including `origin.apk`, `original.apk`, `payload.apk`, `shell.apk`, `target.apk`, `backup.apk`, APK/DEX/JAR/ODEX/VDEX/SO/ZIP payloads under `assets/` or `res/raw/`, path traversal entries, malformed APK structure, and hidden ZIP/DEX/ELF content magic in payload directories.

## Runtime re-check
A lightweight native runtime binding/signer check is repeated by the integrity watchdog, while the complete signer chain is periodically repeated. Confirmed failures are routed to the non-cancelable signature-integrity breach response.

No client-only integrity system can be mathematically unpatchable against an attacker with arbitrary kernel/root control. The objective here is layered, independent verification so ordinary resigning, wrapper/origin.apk replacement, and repackaging paths fail closed.
