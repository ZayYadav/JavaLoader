// ---------- Login.java ----------
package com.pubgm;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.os.Build;
import android.util.Base64;
import com.pubgm.utils.FLog;
import java.net.NetworkInterface;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.security.MessageDigest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

// ADD BlackBoxCore IMPORT
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;

@Obfuscate
public class Login {
    
    static {
        try {
            System.loadLibrary("client");
        } catch(UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
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
    private static int retry = 0;
    
    public static String getAndroidID(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
    
    public static String getDeviceModel() {
        return Build.MODEL;
    }
    
    public static String getDeviceBrand() {
        return Build.BRAND;
    }
    
    public static String getUUID(String hwid) {
        return UUID.nameUUIDFromBytes(hwid.getBytes()).toString();
    }
    
    private static boolean isUnsafeNetwork(Context ctx) {
		try {
			ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
			if (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return true;
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				String n = ni.getName().toLowerCase();
				if (ni.isUp() && !ni.isLoopback() && (n.contains("tun") || n.contains("ppp") || n.contains("tap") || n.contains("vpn"))) return true;
			}
			if (System.getProperty("http.proxyHost") != null || android.net.Proxy.getHost(ctx) != null) return true;
		} catch (Exception ignored) {}
		return false;
	}
    
    public static long getExpiryTimestamp() {
        try {
            if (EXP == null || EXP.isEmpty()) return 0;
            
            FLog.info("Parsing EXP: " + EXP);

            String cleanExp = EXP.replace("\"", "").trim();
            
            if (cleanExp.matches("^[0-9.]+$")) {
                double val = Double.parseDouble(cleanExp);
                if (val > 1000000000000.0) {
                    return (long) (val / 1000.0);
                } else if (val > 1000000000.0) {
                    return (long) val;
                } else if (val > 0) {
                    return (System.currentTimeMillis() / 1000) + (long)(val * 86400.0);
                }
            }

            String expLower = cleanExp.toLowerCase();
            long now = System.currentTimeMillis() / 1000;
            
            if (expLower.contains("life")) {
                return now + (365 * 10 * 86400L);
            }

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
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                    java.util.Date date = sdf.parse(cleanExp);
                    if (date != null) return date.getTime() / 1000;
                } catch (Exception ignored) {}
            }

            return 0;
        } catch (Exception e) {
            FLog.error("EXP Parse Error: " + e.getMessage());
            return 0;
        }
    }

    public static String check(Context context, String userKey) {
        try {
            retry = 0;
            while (isUnsafeNetwork(context)) {
                retry++;
                Thread.sleep(2000);
                if (retry > 15) {
                    return "Disable your VPN and Http Canary, or access will be blocked.";
                }
            }
            String androidId = getAndroidID(context);
            String model = getDeviceModel();
            String brand = getDeviceBrand();
            String hwid = userKey + androidId + model + brand;
            String uuid = getUUID(hwid);
            
            ArrayList<String> headerData = getheaders(context);
            String baseUrl = getbaseurl(context);
            String gameName = headerData.get(8);
            String userKeyParam = headerData.get(9);
            String serialParam = headerData.get(10);
            String authSecret = headerData.get(11);
            String postData = "game=" + gameName + "&" + userKeyParam + "=" + userKey + "&" + serialParam + "=" + uuid;
            String response = sendHttpRequest(context, baseUrl, postData);
            if (response == null) {
                return "Connection failed";
            }
            JSONObject result = new JSONObject(response);
            if (!result.getBoolean("status")) {
                return result.getString("reason");
            }
            JSONObject data = result.getJSONObject("data");
            g_Token = data.getString("token");
            EXP = data.getString("EXP");
            rng = data.getLong("rng");
            if (rng + 30 > System.currentTimeMillis() / 1000) {
                String auth = gameName + "-" + userKey + "-" + uuid + "-" + authSecret;
                g_Auth = getMD5(auth);
                bValid = g_Token.equals(g_Auth);
                
                if (bValid) {
                    setAuth(g_Token, g_Auth);
                    setExpire(EXP);
                    return "OK";
                }
            }
            
            return "Invalid";
            
        } catch (Exception e) {
            return "PLEASE WAIT: " + e.getMessage();
        }
    }
    
    private static String sendHttpRequest(Context context, String urlStr, String postData) {
        HttpURLConnection conn = null;
        try {
            URL llllll = new URL(urlStr);
            ArrayList<String> headerData = getheaders(context);
            conn = (HttpURLConnection) llllll.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty(headerData.get(0), headerData.get(1));
            conn.setRequestProperty(headerData.get(2), headerData.get(3));
            conn.setRequestProperty(headerData.get(4), headerData.get(5));
            conn.setRequestProperty(headerData.get(6), headerData.get(7));
            conn.setDoOutput(true);
            OutputStream os = conn.getOutputStream();
            os.write(postData.getBytes("utf-8"));
            os.close();
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
                br.close();
                return response.toString();
            }
            
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }
    
    private static SSLSocketFactory getPinnedFactory() throws Exception {
        TrustManager tm = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                try { 
                    verifyPin(chain[0]);
                }
                catch (Exception e) { throw new CertificateException(e); }
            }
            public X509Certificate[] getAcceptedIssuers() { 
               return new X509Certificate[0]; 
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{tm}, new SecureRandom());
        return ctx.getSocketFactory();
    }

    private static void verifyPin(X509Certificate cert) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] pubKey = cert.getPublicKey().getEncoded();
        byte[] digest = md.digest(pubKey);
        String pin = "sha256/" + Base64.encodeToString(digest, Base64.NO_WRAP);
    }
    
    private static String getMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}