<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
\ParallaxPanel\Env::load(PANEL_ROOT . '/.env');

use ParallaxPanel\Env;

$token = Env::get('TELEGRAM_BOT_TOKEN');
$secret = Env::get('TELEGRAM_WEBHOOK_SECRET');
$appUrl = rtrim(Env::get('APP_URL'), '/');
$base = '/' . trim(Env::get('APP_BASE_PATH'), '/');
$base = $base === '/' ? '' : $base;
$webhook = $appUrl . $base . '/telegram/webhook';

if (preg_match('/^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$/D', $token) !== 1) {
    fwrite(STDERR, "TELEGRAM_BOT_TOKEN is missing/invalid in .env\n");
    exit(1);
}
if (strlen($secret) < 32 || preg_match('/^[A-Za-z0-9_-]+$/D', $secret) !== 1) {
    fwrite(STDERR, "TELEGRAM_WEBHOOK_SECRET must be 32+ URL-safe characters.\n");
    exit(1);
}
if (filter_var($webhook, FILTER_VALIDATE_URL) === false || !str_starts_with(strtolower($webhook), 'https://')) {
    fwrite(STDERR, "APP_URL/APP_BASE_PATH must produce an HTTPS webhook URL.\n");
    exit(1);
}
if (!extension_loaded('curl')) {
    fwrite(STDERR, "PHP cURL extension is required.\n");
    exit(1);
}

$payload = [
    'url' => $webhook,
    'secret_token' => $secret,
    'allowed_updates' => ['message', 'callback_query'],
    'drop_pending_updates' => true,
    'max_connections' => 20,
];
$curl = curl_init('https://api.telegram.org/bot' . $token . '/setWebhook');
curl_setopt_array($curl, [
    CURLOPT_POST => true,
    CURLOPT_POSTFIELDS => json_encode($payload, JSON_UNESCAPED_SLASHES | JSON_THROW_ON_ERROR),
    CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 5,
    CURLOPT_TIMEOUT => 15,
    CURLOPT_SSL_VERIFYPEER => true,
    CURLOPT_SSL_VERIFYHOST => 2,
    CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
]);
$raw = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
$error = curl_error($curl);
curl_close($curl);
$response = is_string($raw) ? json_decode($raw, true) : null;
if ($status < 200 || $status >= 300 || !is_array($response) || !($response['ok'] ?? false)) {
    fwrite(STDERR, 'Telegram setWebhook failed: ' . ($error ?: ($raw ?: 'HTTP ' . $status)) . PHP_EOL);
    exit(1);
}
echo "Telegram webhook configured:\n$webhook\n";
