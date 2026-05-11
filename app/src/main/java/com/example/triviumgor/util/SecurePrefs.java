package com.example.triviumgor.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Almacenamiento cifrado para los secretos de autenticacion (access JWT y
 * refresh token). El resto de campos de sesion (isLoggedIn, username, rol,
 * nombreCompleto, userId) siguen en SharedPreferences plano "LoginPrefs":
 * no son secretos y migrarlos forzaria tocar muchos call sites sin
 * beneficio.
 *
 * Migracion one-shot: en la primera construccion tras update, si el
 * jwt_token estaba en LoginPrefs plano, lo movemos aqui y lo borramos del
 * plano. Usamos un flag explicito "secure_prefs_migration_v1_done" en
 * LoginPrefs en lugar de inferir el estado por la ausencia de jwt_token,
 * porque hay usuarios con sesion offline (login local sin internet) que
 * nunca tuvieron jwt_token aunque isLoggedIn=true.
 */
public final class SecurePrefs {

    private static final String TAG = "SecurePrefs";

    private static final String FILE = "SecureLoginPrefs";
    private static final String LEGACY_FILE = "LoginPrefs";

    private static final String KEY_ACCESS = "jwt_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_MIGRATION_DONE = "secure_prefs_migration_v1_done";

    private final SharedPreferences prefs;

    public SecurePrefs(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = abrir(app);
        migrarDesdeLegacySiHaceFalta(app);
    }

    private static SharedPreferences abrir(Context app) {
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    app,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            // Fallback: si EncryptedSharedPreferences no se puede inicializar
            // (corrupcion de la master key, p.ej. tras restaurar backup),
            // volvemos a SharedPreferences plano para no romper la app. El
            // token sigue siendo necesario para que las peticiones funcionen.
            Log.e(TAG, "EncryptedSharedPreferences no disponible, fallback a plano", e);
            return app.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE);
        }
    }

    private void migrarDesdeLegacySiHaceFalta(Context app) {
        SharedPreferences legacy = app.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE);
        if (legacy.getBoolean(KEY_MIGRATION_DONE, false)) {
            return;
        }
        String legacyToken = legacy.getString(KEY_ACCESS, "");
        if (legacyToken != null && !legacyToken.isEmpty()) {
            prefs.edit().putString(KEY_ACCESS, legacyToken).apply();
            legacy.edit().remove(KEY_ACCESS).apply();
        }
        legacy.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS, "");
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH, "");
    }

    /**
     * Persiste ambos tokens. Si alguno viene vacio o null no se sobreescribe
     * el valor existente: protege el caso de fallback offline que podria
     * intentar guardar tokens vacios sobre unos validos previos.
     */
    public void setTokens(String access, String refresh) {
        SharedPreferences.Editor e = prefs.edit();
        if (access != null && !access.isEmpty()) {
            e.putString(KEY_ACCESS, access);
        }
        if (refresh != null && !refresh.isEmpty()) {
            e.putString(KEY_REFRESH, refresh);
        }
        e.apply();
    }

    public void setAccessToken(String access) {
        if (access == null || access.isEmpty()) return;
        prefs.edit().putString(KEY_ACCESS, access).apply();
    }

    public void clearTokens() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).apply();
    }
}
