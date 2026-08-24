<?php

declare(strict_types=1);

namespace ParallaxPanel;

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
Env::load(PANEL_ROOT . '/.env');

$failures = 0;
$warnings = 0;
$line = static function (string $status, string $message): void {
    echo '[' . $status . '] ' . $message . PHP_EOL;
};
$pass = static function (string $message) use ($line): void { $line('PASS', $message); };
$fail = static function (string $message) use ($line, &$failures): void { $failures++; $line('FAIL', $message); };
$warn = static function (string $message) use ($line, &$warnings): void { $warnings++; $line('WARN', $message); };

$expectedPackage = 'OneCore.Vip';
$expectedCert = 'B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4';

echo "OneCore MyThos KEYpanel deployment check\n";
echo str_repeat('=', 48) . "\n";

PHP_VERSION_ID >= 80100 ? $pass('PHP ' . PHP_VERSION . ' is supported.') : $fail('PHP 8.1+ is required.');
foreach (['pdo', 'pdo_mysql', 'openssl', 'json', 'session', 'curl'] as $extension) {
    extension_loaded($extension) ? $pass("PHP extension $extension is loaded.") : $fail("Missing PHP extension: $extension");
}

$envPath = PANEL_ROOT . '/.env';
if (!is_file($envPath)) {
    $fail('.env is missing. Copy .env.example to .env and fill production values.');
} else {
    $pass('.env exists.');
    $mode = @fileperms($envPath);
    if (is_int($mode)) {
        $octal = $mode & 0777;
        if (($octal & 0007) !== 0) {
            $warn('.env is world-readable/writable; prefer 0600 or 0640 on hosting.');
        } else {
            $pass('.env filesystem permissions do not expose it to everyone.');
        }
    }
}

Env::get('APP_ENV') === 'production' ? $pass('APP_ENV=production.') : $fail('APP_ENV must be production.');
$appUrl = Env::get('APP_URL');
if (filter_var($appUrl, FILTER_VALIDATE_URL) !== false && strtolower((string) parse_url($appUrl, PHP_URL_SCHEME)) === 'https') {
    $pass('APP_URL is valid HTTPS.');
} else {
    $fail('APP_URL must be the public HTTPS panel origin.');
}

$setupToken = Env::get('SETUP_TOKEN');
if (strlen($setupToken) >= 32 && !str_contains($setupToken, 'CHANGE_TO_')) {
    $pass('SETUP_TOKEN is non-default and sufficiently long.');
} else {
    $fail('SETUP_TOKEN is missing/default/too short. Run php tools/generate-secrets.php.');
}

foreach (['DB_HOST', 'DB_NAME', 'DB_USER', 'DB_PASSWORD'] as $key) {
    $value = Env::get($key);
    ($value !== '' && !str_contains($value, 'CHANGE_ME')) ? $pass("$key is configured.") : $fail("$key is not configured.");
}

Env::get('EXPECTED_ANDROID_PACKAGE') === $expectedPackage
    ? $pass("Android package binding is $expectedPackage.")
    : $fail("EXPECTED_ANDROID_PACKAGE must be $expectedPackage.");

$certs = array_values(array_filter(array_map(
    static fn(string $value): string => strtoupper(str_replace(':', '', trim($value))),
    explode(',', Env::get('EXPECTED_ANDROID_CERT_SHA256'))
)));
if (in_array($expectedCert, $certs, true)) {
    $pass('Production Android certificate SHA-256 is allowlisted.');
} else {
    $fail('EXPECTED_ANDROID_CERT_SHA256 must include the OneCore MyThos production certificate.');
}
foreach ($certs as $cert) {
    if (preg_match('/^[0-9A-F]{64}$/D', $cert) !== 1) {
        $fail('EXPECTED_ANDROID_CERT_SHA256 contains an invalid digest.');
        break;
    }
}

$minVersion = filter_var(Env::get('MIN_ANDROID_VERSION_CODE', '1'), FILTER_VALIDATE_INT);
($minVersion !== false && $minVersion >= 1) ? $pass('MIN_ANDROID_VERSION_CODE is valid.') : $fail('MIN_ANDROID_VERSION_CODE must be >= 1.');

$runtime = PANEL_ROOT . '/runtime';
is_dir($runtime) ? $pass('runtime/ exists.') : $fail('runtime/ directory is missing.');
is_writable($runtime) ? $pass('runtime/ is writable by PHP.') : $fail('runtime/ must be writable by PHP.');
is_file($runtime . '/.htaccess') ? $pass('runtime/.htaccess deny rule exists.') : $fail('runtime/.htaccess is missing.');

$privateKey = PANEL_ROOT . '/' . ltrim(Env::get('API_PRIVATE_KEY_PATH', 'runtime/api-private.pem'), '/');
$publicKey = PANEL_ROOT . '/' . ltrim(Env::get('API_PUBLIC_KEY_PATH', 'runtime/api-public.b64'), '/');
if (is_file($privateKey)) {
    $pass('RSA private API key exists.');
    $keyMode = @fileperms($privateKey);
    if (is_int($keyMode) && (($keyMode & 0007) !== 0)) {
        $warn('RSA private key is accessible to other users; prefer 0600/0640.');
    }
} else {
    $warn('RSA private API key does not exist yet; /setup will generate it.');
}
is_file($publicKey) ? $pass('RSA public API key exists.') : $warn('RSA public API key does not exist yet; /setup will generate it.');

$trusted = trim(Env::get('TRUSTED_PROXY_IPS'));
if ($trusted === '*' || str_contains($trusted, '0.0.0.0/0')) {
    $fail('TRUSTED_PROXY_IPS must never trust every address.');
} elseif ($trusted === '') {
    $pass('TRUSTED_PROXY_IPS is empty (correct for normal shared hosting).');
} else {
    $warn('TRUSTED_PROXY_IPS is configured; verify every listed proxy is controlled by you.');
}

if ($failures === 0) {
    echo "\nREADY: no blocking configuration errors. Warnings: $warnings\n";
    exit(0);
}

echo "\nNOT READY: $failures blocking issue(s), $warnings warning(s).\n";
exit(1);
