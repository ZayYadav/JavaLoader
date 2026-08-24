<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

define('PANEL_ROOT', dirname(__DIR__));
require PANEL_ROOT . '/src/Support.php';
\ParallaxPanel\Env::load(PANEL_ROOT . '/.env');

$token = \ParallaxPanel\Env::get('TELEGRAM_BOT_TOKEN');
if (preg_match('/^[0-9]{6,12}:[A-Za-z0-9_-]{30,}$/D', $token) !== 1) {
    fwrite(STDERR, "Put TELEGRAM_BOT_TOKEN in .env first.\n");
    exit(1);
}
if (!extension_loaded('curl')) {
    fwrite(STDERR, "PHP cURL extension is required.\n");
    exit(1);
}
$curl = curl_init('https://api.telegram.org/bot' . $token . '/getUpdates?limit=20&timeout=0');
curl_setopt_array($curl, [
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_CONNECTTIMEOUT => 5,
    CURLOPT_TIMEOUT => 12,
    CURLOPT_SSL_VERIFYPEER => true,
    CURLOPT_SSL_VERIFYHOST => 2,
    CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
]);
$raw = curl_exec($curl);
$status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
$error = curl_error($curl);
curl_close($curl);
$response = is_string($raw) ? json_decode($raw, true) : null;
if ($status !== 200 || !is_array($response) || !($response['ok'] ?? false)) {
    fwrite(STDERR, 'Telegram getUpdates failed: ' . ($error ?: 'HTTP ' . $status) . PHP_EOL);
    exit(1);
}
$ids = [];
foreach ($response['result'] ?? [] as $update) {
    foreach (['message', 'callback_query'] as $type) {
        $item = $update[$type] ?? null;
        if (!is_array($item)) continue;
        $from = $item['from'] ?? null;
        if (is_array($from) && isset($from['id'])) {
            $id = (string) $from['id'];
            $name = trim((string) (($from['first_name'] ?? '') . ' ' . ($from['last_name'] ?? '')));
            $ids[$id] = $name !== '' ? $name : ($from['username'] ?? 'unknown');
        }
    }
}
if ($ids === []) {
    echo "No user IDs found. Disable webhook if already configured, send /start to the bot, then retry.\n";
    exit(0);
}
foreach ($ids as $id => $name) {
    echo $id . '  ' . $name . PHP_EOL;
}
