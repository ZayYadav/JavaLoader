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
- A canonical ZIP map covers every `classes*.dex`, every `assets/*` file, `AndroidManifest.xml`, `resources.arsc`, and `lib/arm64-v8a/libclient.so`.
- Java records each critical entry's CRC32, uncompressed size, compressed size and compression method, rejects duplicates/traversal, and periodically recomputes CRC32 from the actual uncompressed bytes.

## Native checks
- Process/package identity is bound to `OneCore.Vip`.
- `base.apk` is bound to the loaded `libclient.so` installation root and filesystem identity.
- `/proc/self/maps` and `/proc/self/fd` are checked for suspicious external executable payloads and invalid client mappings.
- The exact installed `base.apk` APK Signature Scheme v2 block is parsed directly in C++.
- Native code extracts the raw leaf X.509 signer certificates from the on-disk v2 signing block.
- Native-read signer certificates are checked against the build-pinned SHA-256 allowlist by the independent native SHA-256 implementation, and Java also requires the same signer set to match `ApkVerifier` and PackageManager.
- Native code independently parses ZIP central-directory and local-header metadata for the same critical-entry set and its canonical map must exactly match Java's map.
- Periodic native CRC validation reads STORED data directly and raw-inflates DEFLATED entries with zlib before comparing the resulting CRC32 and uncompressed byte count to the signed archive metadata.

## Wrapper payload policy
The app fails closed for common nested wrapper/repack layouts, including `origin.apk`, `original.apk`, `payload.apk`, `shell.apk`, `target.apk`, `backup.apk`, APK/DEX/JAR/ODEX/VDEX/SO/ZIP payloads under `assets/` or `res/raw/`, path traversal entries, duplicate ZIP entry names, malformed APK structure, and hidden ZIP/DEX/ELF content magic in payload directories.

## Runtime re-check
The integrity watchdog repeats process/base.apk binding continuously. The Java/native ZIP metadata map is cross-checked on each wrapper-policy pass, full entry CRC bytes are periodically re-read, and the complete signer chain is periodically repeated. Confirmed failures are routed to the existing non-cancelable OneCore security cinematic/video breach response and then terminate the task/process.

No client-only integrity system can be mathematically unpatchable against an attacker with arbitrary kernel/root control. The objective here is layered, independent verification so ordinary resigning, wrapper/origin.apk replacement, DEX/asset replacement, and common repackaging paths fail closed.
