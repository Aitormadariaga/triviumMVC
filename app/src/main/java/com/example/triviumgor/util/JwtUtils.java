package com.example.triviumgor.util;

import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

/**
 * Helpers para manipular JWT (decodificacion + validacion local de exp).
 *
 * NO valida la firma del token: eso es responsabilidad del servidor en
 * cada request. Aqui solo leemos el payload y comprobamos exp para
 * cosas como decidir si pedir login al arrancar la app sin esperar a
 * que falle un 401 reactivo.
 */
public final class JwtUtils {

    private static final String TAG = "JwtUtils";

    private JwtUtils() {}

    /**
     * Decodifica el payload (segundo segmento) de un JWT. El segmento esta
     * codificado en base64url sin padding y contiene un JSON con las claims.
     *
     * @throws Exception si el token no tiene 3 partes, o el segmento 2 no
     *                   se puede decodificar como JSON.
     */
    public static JSONObject decodePayload(String token) throws Exception {
        if (token == null) {
            throw new IllegalArgumentException("Token nulo");
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Token sin payload");
        }
        byte[] bytes = Base64.decode(parts[1],
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        return new JSONObject(new String(bytes, "UTF-8"));
    }

    /**
     * Devuelve true SOLO si el token esta presente y esta caducado o
     * corrupto. La ausencia de token (null o cadena vacia) NO se considera
     * expirado: si no hay token simplemente no hay nada que validar y la
     * decision recae en el resto de mecanismos (haySesionActiva, etc).
     * Este matiz es importante para usuarios con sesion offline previa
     * (login local sin internet) que pueden tener isLoggedIn=true sin
     * jwt_token guardado.
     *
     * Comportamiento:
     *   - null o ""        → false (no hay token; dejar pasar).
     *   - corrupto         → true  (fail-safe, mejor pedir login).
     *   - exp < now        → true  (caducado).
     *   - exp >= now       → false (vigente).
     *   - sin claim exp    → true  (fail-safe).
     */
    public static boolean isExpired(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        try {
            JSONObject payload = decodePayload(token);
            if (!payload.has("exp")) {
                Log.w(TAG, "JWT sin claim 'exp' — tratado como expirado por seguridad");
                return true;
            }
            long expSeconds = payload.getLong("exp");
            long nowSeconds = System.currentTimeMillis() / 1000L;
            return expSeconds < nowSeconds;
        } catch (Exception e) {
            Log.w(TAG, "JWT no parseable — tratado como expirado por seguridad: " + e.getMessage());
            return true;
        }
    }
}
