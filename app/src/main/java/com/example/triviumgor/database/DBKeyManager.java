package com.example.triviumgor.database;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class DBKeyManager {
    private static final String PREFS_NAME = "trivium_db_keystore";
    private static final String KEY_PASSPHRASE = "db_passphrase";
    private static volatile DBKeyManager instance;
    private final SharedPreferences encryptedPrefs;
    private DBKeyManager(Context context) throws GeneralSecurityException, IOException {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        this.encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }
    public static DBKeyManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DBKeyManager.class) {
                if (instance == null) {
                    try {
                        instance = new DBKeyManager(context.getApplicationContext());
                    } catch (Exception e) {
                        Log.e("DBKeyManager", "Fallo crítico al inicializar Keystore", e);
                        throw new RuntimeException(
                                "No se pudo inicializar el almacén seguro. " +
                                        "Si persiste, reinstala la app (perderás la BD local; " +
                                        "los datos están en el servidor).", e);
                    }
                }
            }
        }
        return instance;
    }
    /** Devuelve la passphrase de SQLCipher como char[]. Genera una nueva si no existe. */
    public char[] getPassphrase() {

        String saved = encryptedPrefs.getString(KEY_PASSPHRASE, null);
        if (saved != null) {
            return saved.toCharArray();
        }
// Primera ejecución: generar nueva
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
// Codificamos como hex para almacenarla como String estable
        String passphrase = bytesToHex(random);
        encryptedPrefs.edit().putString(KEY_PASSPHRASE, passphrase).apply();
        return passphrase.toCharArray();
    }
    /**
     * Borra la passphrase. Llamar SOLO en escenarios de recovery: si la BD
     * cifrada está corrupta o el Keystore tras una OTA dejó de poder
     * descifrar, se borra todo y se fuerza a sincronizar de cero.
     */
    public void resetPassphrase() {
        encryptedPrefs.edit().remove(KEY_PASSPHRASE).apply();
    }
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
