<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$setupToken = bin2hex(random_bytes(32));
$webhookSecret = rtrim(strtr(base64_encode(random_bytes(36)), '+/', '_-'), '=');

echo "SETUP_TOKEN=$setupToken\n";
echo "TELEGRAM_WEBHOOK_SECRET=$webhookSecret\n";
echo "\nCopy these into your private .env. Do not commit or post them publicly.\n";
