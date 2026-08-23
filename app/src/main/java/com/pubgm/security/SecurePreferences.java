package com.pubgm.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Small fail-closed Android Keystore backed preference store for license state. */
public final class SecurePreferences {
    private static final String PREFS = "javaloader_secure_auth_v2";
    private static final String KEY_ALIAS = "javaloader.auth.aes.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;

    private final SharedPreferences preferences;

    public SecurePreferences(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void putString(String key, String value) {
        try {
            byte[] plaintext = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plaintext);
            ByteBuffer packed = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            packed.put((byte) iv.length);
            packed.put(iv);
            packed.put(ciphertext);
            preferences.edit().putString(key,
                    Base64.encodeToString(packed.array(), Base64.NO_WRAP)).apply();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to protect local license state", error);
        }
    }

    public String getString(String key, String defaultValue) {
        String encoded = preferences.getString(key, null);
        if (encoded == null || encoded.isEmpty()) return defaultValue;
        try {
            byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer input = ByteBuffer.wrap(packed);
            int ivLength = input.get() & 0xff;
            if (ivLength < 12 || ivLength > 32 || input.remaining() <= ivLength) {
                throw new IllegalStateException("Invalid encrypted preference");
            }
            byte[] iv = new byte[ivLength];
            input.get(iv);
            byte[] ciphertext = new byte[input.remaining()];
            input.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception error) {
            preferences.edit().remove(key).apply();
            return defaultValue;
        }
    }

    public void putLong(String key, long value) {
        putString(key, Long.toString(value));
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(getString(key, Long.toString(defaultValue)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public void putBoolean(String key, boolean value) {
        putString(key, value ? "1" : "0");
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        return "1".equals(getString(key, defaultValue ? "1" : "0"));
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key key = store.getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey) return (SecretKey) key;

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}