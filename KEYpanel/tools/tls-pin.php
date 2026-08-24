<?php

declare(strict_types=1);

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$host = trim((string) ($argv[1] ?? ''));
if ($host === '' || preg_match('/^[A-Za-z0-9.-]+$/D', $host) !== 1) {
    fwrite(STDERR, "Usage: php tools/tls-pin.php panel.example.com\n");
    exit(2);
}

$context = stream_context_create(['ssl' => [
    'capture_peer_cert' => true,
    'verify_peer' => true,
    'verify_peer_name' => true,
    'peer_name' => $host,
    'SNI_enabled' => true,
    'disable_compression' => true,
]]);
$client = @stream_socket_client(
    'ssl://' . $host . ':443',
    $errno,
    $error,
    12,
    STREAM_CLIENT_CONNECT,
    $context
);
if (!is_resource($client)) {
    fwrite(STDERR, "TLS connection failed: $error ($errno)\n");
    exit(1);
}
$params = stream_context_get_params($client);
fclose($client);
$certificate = $params['options']['ssl']['peer_certificate'] ?? null;
if ($certificate === null) {
    fwrite(STDERR, "Peer certificate was not captured.\n");
    exit(1);
}
$publicKey = openssl_pkey_get_public($certificate);
$details = $publicKey === false ? false : openssl_pkey_get_details($publicKey);
$pem = is_array($details) ? (string) ($details['key'] ?? '') : '';
$base64 = preg_replace('/-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s+/', '', $pem);
$der = base64_decode((string) $base64, true);
if ($der === false || $der === '') {
    fwrite(STDERR, "Could not extract certificate SPKI.\n");
    exit(1);
}
echo 'sha256/' . base64_encode(hash('sha256', $der, true)) . PHP_EOL;
