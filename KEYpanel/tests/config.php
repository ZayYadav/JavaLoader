<?php

declare(strict_types=1);

$root = dirname(__DIR__);
$assertions = 0;
$assert = static function (bool $condition, string $message) use (&$assertions): void {
    $assertions++;
    if (!$condition) {
        throw new RuntimeException($message);
    }
};

$env = (string) file_get_contents($root . '/.env.example');
$readme = (string) file_get_contents($root . '/README.md');
$view = (string) file_get_contents($root . '/src/View.php');
$bootstrap = (string) file_get_contents($root . '/src/bootstrap.php');
$htaccess = (string) file_get_contents($root . '/.htaccess');

$assert(str_contains($env, 'EXPECTED_ANDROID_PACKAGE=OneCore.Vip'), 'OneCore.Vip package binding is missing from .env.example.');
$assert(str_contains($env, 'B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4'), 'Production signer digest is missing from .env.example.');
$assert(str_contains($env, 'PANEL_NAME=OneCore MyThos'), 'Panel branding is missing from .env.example.');
$assert(str_contains($readme, 'JAVA_LOADER_LICENSE_URL'), 'Current JavaLoader GitHub variable names are missing from README.');
$assert(str_contains($readme, 'ANDROID_KEYSTORE_BASE64'), 'Production signing secret setup is missing from README.');
$assert(str_contains($view, 'ONECORE <b>MYTHOS</b>'), 'OneCore MyThos branding is missing from View.');
$assert(str_contains($bootstrap, "script-src 'none'"), 'Strict script CSP is missing.');
$assert(str_contains($bootstrap, 'Request body is too large.'), 'Request body size guard is missing.');
$assert(str_contains($htaccess, 'RewriteRule ^(?:src|database|tests|tools|runtime)'), 'Private directory web deny rule is missing.');
$assert(is_file($root . '/tools/deploy-check.php'), 'Deployment self-check tool is missing.');

foreach (['.env', 'api-private.pem', '.jks', '.keystore'] as $secretName) {
    $assert(!str_contains($readme, 'STORE_PASSWORD=' . $secretName), 'README must not embed private secrets.');
}

echo "OneCore MyThos KEYpanel configuration tests passed ($assertions assertions).\n";
