// ---------- Login.java ----------
package com.pubgm;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;

import com.pubgm.security.AppIntegrity;
import com.pubgm.utils.FLog;

import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

@Obfuscate
public class Login {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final int MAX_RESPONSE_CHARS = 64 * 1024;

    static {
        try {
            System.loadLibrary("client");
        } catch (UnsatisfiedLinkError error) {
            FLog.error(error.getMessage());
        }
    }

    public static native ArrayList<String> getheaders(Context context);
    public static native String getbaseurl(Context context);
    public static native void setAuth(String token, String auth);
    public static native void setExpire(String exp);
    public static native String FixCrash();
    public static native void setAuthToken(String token);

    private static String g_Token = "";
    private static String g_Auth = "";
    private static boolean bValid = false;
    public static String EXP = "";
    private static long rng = 0;

    public static String getAndroidID(Context context) {
        String value = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return value == null ? "" : value;
    }

    public static String getDeviceModel() {
        return Build.MODEL == null ? "" : Build.MODEL;
    }

    public static String getDeviceBrand() {
        return Build.BRAND == null ? "" : Build.BRAND;
    }

    public static String getUUID(String hwid) {
        return UUID.nameUUIDFromBytes(hwid.getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static long getExpiryTimestamp() {
        try {
            if (EXP == null || EXP.isEmpty()) return 0;

            String cleanExp = EXP.replace("\"", "").trim();
            if (cleanExp.matches("^[0-9.]+$")) {
                double val = Double.parseDouble(cleanExp);
                if (val > 1000000000000.0) return (long) (val / 1000.0);
                if (val > 1000000000.0) return (long) val;
                if (val > 0) return (System.currentTimeMillis() / 1000L) + (long) (val * 86400.0);
            }

            String expLower = cleanExp.toLowerCase(Locale.US);
            long now = System.currentTimeMillis() / 1000L;
            if (expLower.contains("life")) return now + (365L * 10L * 86400L);

            String numericPart = expLower.replaceAll("[^0-9]", "");
            if (!numericPart.isEmpty()) {
                long value = Long.parseLong(numericPart);
                if (expLower.contains("d")) return now + (value * 86400L);
                if (expLower.contains("h")) return now + (value * 3600L);
                if (expLower.contains("m")) return now + (value * 60L);
                if (value < 10000) return now + (value * 86400L);
            }

            String[] patterns = {
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy/MM/dd HH:mm:ss",
                    "dd-MM-yyyy HH:mm:ss",
                    "yyyy-MM-dd"
            };
            for (String pattern : patterns) {
                try {
                    java.text.SimpleDateFormat sdf =
                            new java.text.SimpleDateFormat(pattern, Locale.US);
                    java.util.Date date = sdf.parse(cleanExp);
                    if (date != null) return date.getTime() / 1000L;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception error) {
            FLog.error("EXP Parse Error: " + error.getMessage());
        }
        return 0;
    }

    public static String check(Context context, String userKey) {
        try {
            if (!AppIntegrity.verify(context)) {
                return "Application signature verification failed";
            }

            String normalizedKey = userKey == null ? "" : userKey.trim();
            if (!normalizedKey.matches("^[A-Za-z0-9_-]{4,64}$")) {
                return "Invalid license key";
            }

            String androidId = getAndroidID(context);
            String model = getDeviceModel();
            String brand = getDeviceBrand();
            String hwid = normalizedKey + androidId + model + brand;
            String uuid = getUUID(hwid);

            ArrayList<String> headerData = getheaders(context);
            if (headerData == null || headerData.size() < 12) {
                return "Client configuration invalid";
            }
            String baseUrl = getbaseurl(context);
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                return "Licensing server is not configured";
            }

            String gameName = headerData.get(8);
            String userKeyParam = headerData.get(9);
            String serialParam = headerData.get(10);
            String authSecret = headerData.get(11);
            String postData = "game=" + gameName + "&" + userKeyParam + "="
                    + normalizedKey + "&" + serialParam + "=" + uuid;

            String response = sendHttpRequest(context, baseUrl, postData, headerData);
            if (response == null) return "Secure connection failed";

            JSONObject result = new JSONObject(response);
            if (!result.optBoolean("status", false)) {
                return result.optString("reason", "License rejected");
            }

            JSONObject data = result.optJSONObject("data");
            if (data == null) return "Licensing response invalid";
            g_Token = data.optString("token", "");
            EXP = data.optString("EXP", "");
            rng = data.optLong("rng", 0L);

            long now = System.currentTimeMillis() / 1000L;
            if (rng <= 0L || Math.abs(now - rng) > 30L) {
                return "Licensing response expired";
            }

            // Retain the legacy server token calculation for backend compatibility.
            // Transport, APK identity and local state are independently hardened.
            String auth = gameName + "-" + normalizedKey + "-" + uuid + "-" + authSecret;
            g_Auth = getMD5(auth);
            bValid = constantTimeEquals(g_Token, g_Auth);
            if (!bValid) return "License verification failed";

            setAuth(g_Token, g_Auth);
            setExpire(EXP);
            return "OK";
        } catch (SSLPeerUnverifiedException error) {
            return "Secure server identity validation failed";
        } catch (Exception error) {
            FLog.error("Login error: " + error.getMessage());
            return "Secure license verification is temporarily unavailable";
        }
    }

    private static String sendHttpRequest(
            Context context,
            String urlString,
            String postData,
            ArrayList<String> headerData) throws Exception {
        URL url = new URL(urlString);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new SSLPeerUnverifiedException("HTTPS is required for licensing");
        }

        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Cache-Control", "no-store");
            connection.setRequestProperty(headerData.get(0), headerData.get(1));
            connection.setRequestProperty(headerData.get(2), headerData.get(3));
            connection.setRequestProperty(headerData.get(4), headerData.get(5));
            connection.setRequestProperty(headerData.get(6), headerData.get(7));
            connection.setRequestProperty(
                    "X-App-Certificate-SHA256",
                    AppIntegrity.currentSigningCertificateSha256(context));

            connection.connect();
            verifyConfiguredPin(connection);

            byte[] payload = postData.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }

            int status = connection.getResponseCode();
            if (status != HttpsURLConnection.HTTP_OK) return null;

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (response.length() + line.length() > MAX_RESPONSE_CHARS) {
                        throw new IllegalStateException("Licensing response is too large");
                    }
                    response.append(line);
                }
            }
            return response.toString();
        } finally {
            connection.disconnect();
        }
    }

    private static void verifyConfiguredPin(HttpsURLConnection connection) throws Exception {
        String configured = BuildConfig.JAVA_LOADER_TLS_PINS;
        if (configured == null || configured.trim().isEmpty()) {
            // Platform CA and hostname validation still apply. Production can additionally set SPKI pins.
            return;
        }

        Certificate[] certificates = connection.getServerCertificates();
        if (certificates == null || certificates.length == 0
                || !(certificates[0] instanceof X509Certificate)) {
            throw new SSLPeerUnverifiedException("No peer certificate");
        }
        X509Certificate certificate = (X509Certificate) certificates[0];
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(certificate.getPublicKey().getEncoded());
        String actual = "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);

        for (String candidate : configured.split(",")) {
            if (constantTimeEquals(actual, candidate.trim())) return;
        }
        throw new SSLPeerUnverifiedException("TLS public-key pin mismatch");
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}