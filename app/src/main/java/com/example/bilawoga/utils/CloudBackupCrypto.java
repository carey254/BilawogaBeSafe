package com.example.bilawoga.utils;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts/decrypts small fields for Firestore backup using Android Keystore AES-GCM.
 * Structure stored in Firestore:
 * {
 *   enc: true,
 *   v: 1,
 *   username: { iv: base64, ct: base64 },
 *   enum1: { iv: base64, ct: base64 },
 *   enum2: { iv: base64, ct: base64 },
 *   incident_type: { iv: base64, ct: base64 },
 *   backup_time: long
 * }
 */
public class CloudBackupCrypto {
    private static final String TAG = "CloudBackupCrypto";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "BILA_CLOUD_BACKUP_KEY";

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setKeySize(256)
                .build();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(spec);
        return kg.generateKey();
    }

    private static Map<String, String> encryptField(String plaintext) throws Exception {
        if (plaintext == null) plaintext = "";
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] ct = cipher.doFinal(plaintext.getBytes());
        Map<String, String> out = new HashMap<>();
        out.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
        out.put("ct", Base64.encodeToString(ct, Base64.NO_WRAP));
        return out;
    }

    private static String decryptField(Object mapObj) throws Exception {
        if (!(mapObj instanceof Map)) return null;
        @SuppressWarnings("unchecked") Map<String, Object> map = (Map<String, Object>) mapObj;
        String ivB64 = (String) map.get("iv");
        String ctB64 = (String) map.get("ct");
        if (ivB64 == null || ctB64 == null) return null;
        byte[] iv = Base64.decode(ivB64, Base64.NO_WRAP);
        byte[] ct = Base64.decode(ctB64, Base64.NO_WRAP);
        SecretKey key = getOrCreateKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] pt = cipher.doFinal(ct);
        return new String(pt);
    }

    public static Map<String, Object> buildEncryptedPayload(String username, String enum1, String enum2, String incidentType, long backupTime) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("enc", true);
            data.put("v", 1);
            data.put("username", encryptField(username));
            data.put("enum1", encryptField(enum1));
            data.put("enum2", encryptField(enum2));
            data.put("incident_type", encryptField(incidentType));
            data.put("backup_time", backupTime);
            return data;
        } catch (Exception e) {
            Log.e(TAG, "Encrypt payload failed: " + e.getMessage());
            // Fallback to plaintext if encryption fails (still include flag)
            Map<String, Object> data = new HashMap<>();
            data.put("enc", false);
            data.put("username", username);
            data.put("enum1", enum1);
            data.put("enum2", enum2);
            data.put("incident_type", incidentType);
            data.put("backup_time", backupTime);
            return data;
        }
    }

    public static String tryDecryptString(Object fieldObj) {
        try {
            if (fieldObj instanceof Map) {
                return decryptField(fieldObj);
            } else if (fieldObj instanceof String) {
                return (String) fieldObj; // legacy plaintext
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Decrypt field failed: " + e.getMessage());
            return null;
        }
    }
}