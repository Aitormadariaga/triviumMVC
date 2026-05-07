package com.example.triviumgor.network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.triviumgor.view.LoginActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApiClient {
    private static final String BASE_URL = "http://localhost:8000";
    private static final String PREFS_NAME = "LoginPrefs";

    // Idempotencia: si varios requests responden 401 a la vez, solo uno
    // dispara el flujo de logout. Lo resetea LoginActivity tras login OK.
    public static final AtomicBoolean logoutEnCurso = new AtomicBoolean(false);

    private final Context appContext;
    private final RequestQueue queue;
    private final SharedPreferences prefs;

    public ApiClient(Context context) {
        this.appContext = context.getApplicationContext();
        this.queue = Volley.newRequestQueue(this.appContext);
        this.prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ============================================
    // Token JWT
    // ============================================
    public String getToken() {
        return prefs.getString("jwt_token", "");
    }

    private Map<String, String> getHeadersConToken() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + getToken());
        headers.put("Content-Type", "application/json");
        return headers;
    }

    // ============================================
    // POST /api/login
    // Android envía username y password
    // Recibe: { "token": "eyJ..." }
    // ============================================
    public void login(String username, String password, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            post("/api/login", body, false, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/registro
    // Crear cuenta nueva
    // ============================================
    public void registro(String username, String password,
                         String nombre, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);
            if (nombre != null && !nombre.isEmpty()) {
                body.put("nombre", nombre);
            }

            post("/api/registro", body, false, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/perfil
    // Ver datos del usuario autenticado
    // Recibe: { "id": 1, "username": "...", "nombre": "...", "roles": [...] }
    // ============================================
    public void perfil(ApiCallback callback) {
        get("/api/perfil", callback);
    }

    // ============================================
    // PUT /api/cambiar-password
    // Cambiar contraseña del usuario autenticado
    // ============================================
    public void cambiarPassword(String passwordActual, String passwordNuevo,
                                ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("passwordActual", passwordActual);
            body.put("passwordNuevo", passwordNuevo);

            put("/api/cambiar-password", body, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/pacientes
    // Primera carga — descargar todos los pacientes
    // Recibe: { "total": N, "pacientes": [...] }
    // ============================================
    public void getPacientes(ApiCallback callback) {
        get("/api/pacientes", callback);
    }

    // ============================================
    // GET /api/sesiones
    // Primera carga — descargar todas las sesiones
    // Recibe: { "total": N, "sesiones": [...] }
    // ============================================
    public void getSesiones(ApiCallback callback) {
        get("/api/sesiones", callback);
    }

    // ============================================
    // POST /api/sincronizar
    // Enviar cambios pendientes de la tablet
    //
    // Envía:
    // {
    //   "cambios": [
    //     {
    //       "pacienteId": 5,
    //       "eliminar": false,
    //       "nombre": "Juan",
    //       "apellido1": "García",
    //       "dni": "12345678A",
    //       "sesion": {
    //         "dispositivo": "Tablet-01",
    //         "intensidad": 10,
    //         "tiempo": 30
    //       }
    //     }
    //   ]
    // }
    //
    // Recibe:
    // {
    //   "sincronizados": [5],
    //   "conflictos": [...],
    //   "errores": [...]
    // }
    // ============================================
    public void sincronizar(JSONArray cambios, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("cambios", cambios);

            post("/api/sincronizar", body, true, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/sincronizar/resolver-conflicto
    // El médico decide qué versión mantener
    //
    // Envía:
    // {
    //   "pacienteId": 5,
    //   "decision": "mantener" o "sobreescribir",
    //   "versionTablet": { ... },
    //   "sesion": { ... } (opcional)
    // }
    // ============================================
    public void resolverConflicto(int pacienteId, String decision,
                                  JSONObject versionTablet,
                                  JSONObject sesion,
                                  ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("pacienteId", pacienteId);
            body.put("decision", decision);
            if (versionTablet != null) {
                body.put("versionTablet", versionTablet);
            }
            if (sesion != null) {
                body.put("sesion", sesion);
            }

            post("/api/sincronizar/resolver-conflicto", body, true, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/sincronizar/sesiones
    // Enviar sesiones pendientes de la tablet
    //
    // Envía:
    // {
    //   "sesiones": [
    //     {
    //       "pacienteId": 5,
    //       "dispositivo": "Tablet-01",
    //       "intensidad": 10,
    //       "tiempo": 30
    //     }
    //   ]
    // }
    // ============================================
    public void sincronizarSesiones(JSONArray sesiones, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("sesiones", sesiones);
            post("/api/sincronizar/sesiones", body, true, callback);
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/sincronizar/eliminaciones-rechazadas
    // Comprueba si alguna eliminación no fue
    // confirmada por el admin esta semana
    // ============================================
    public void getEliminacionesRechazadas(ApiCallback callback) {
        get("/api/sincronizar/eliminaciones-rechazadas", callback);
    }

    // ============================================
    // Métodos HTTP base
    // ============================================

    private void get(String endpoint, ApiCallback callback) {
        String url = BASE_URL + endpoint;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.GET, url, null,
                response -> callback.onSuccess(response),
                error -> callback.onError(parsearError(error, true))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getHeadersConToken();
            }
        };

        queue.add(request);
    }

    private void post(String endpoint, JSONObject body,
                      boolean conToken, ApiCallback callback) {
        String url = BASE_URL + endpoint;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST, url, body,
                response -> callback.onSuccess(response),
                error -> callback.onError(parsearError(error, conToken))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                if (conToken) return getHeadersConToken();
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        queue.add(request);
    }

    private void put(String endpoint, JSONObject body, ApiCallback callback) {
        String url = BASE_URL + endpoint;

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.PUT, url, body,
                response -> callback.onSuccess(response),
                error -> callback.onError(parsearError(error, true))
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                return getHeadersConToken();
            }
        };

        queue.add(request);
    }

    // ============================================
    // Parsear errores HTTP
    // conToken=true significa endpoint autenticado:
    // un 401 ahi indica token expirado/invalido y dispara auto-logout.
    // conToken=false (login/registro): un 401 son credenciales malas.
    // ============================================
    private String parsearError(com.android.volley.VolleyError error, boolean conToken) {
        // Si el logout ya esta en curso, los requests cancelados llegan aqui
        // con networkResponse=null. Devolvemos el mismo mensaje en lugar de
        // "Sin conexion" para no confundir tras el toast de sesion caducada.
        if (logoutEnCurso.get() && error.networkResponse == null) {
            return "Sesión caducada, inicia sesión de nuevo";
        }
        if (error.networkResponse == null) {
            return "Sin conexión al servidor";
        }
        int code = error.networkResponse.statusCode;
        if (code == 401) {
            if (conToken) {
                forzarLogoutPorTokenExpirado();
                return "Sesión caducada, inicia sesión de nuevo";
            }
            return "Usuario o contraseña incorrectos";
        }
        switch (code) {
            case 400: return "Datos incorrectos";
            case 404: return "Recurso no encontrado";
            case 409: return "El usuario ya existe";
            case 500: return "Error en el servidor";
            default:  return "Error " + code;
        }
    }

    // Limpia la sesion local y redirige a LoginActivity. Se llama
    // cuando un endpoint autenticado responde 401 (token expirado o
    // invalido). Idempotente via logoutEnCurso para que multiples 401
    // simultaneos no disparen N veces.
    private void forzarLogoutPorTokenExpirado() {
        if (!logoutEnCurso.compareAndSet(false, true)) {
            return;
        }

        // Cancelar cualquier request en vuelo para no encadenar 401s.
        queue.cancelAll(req -> true);

        prefs.edit().clear().apply();

        // Toast Y startActivity dentro del mismo Handler.post para garantizar
        // ejecucion en mainThread. Antes el startActivity quedaba fuera y, si
        // el callback de Volley llegaba en un thread distinto al main (caso
        // raro pero posible con cancelaciones / RetryPolicy), Android 10+
        // bloqueaba silenciosamente el lanzamiento de la Activity desde
        // Application context — el toast aparecia pero el redirect a Login
        // nunca pasaba.
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(appContext,
                    "Sesión caducada, inicia sesión de nuevo",
                    Toast.LENGTH_LONG).show();
            try {
                Intent intent = new Intent(appContext, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                appContext.startActivity(intent);
            } catch (Exception e) {
                android.util.Log.e("ApiClient",
                        "No se pudo lanzar LoginActivity tras 401", e);
            }
        });
    }

    // ============================================
    // Interface de callback
    // ============================================
    public interface ApiCallback {
        void onSuccess(JSONObject response);
        void onError(String mensaje);
    }

}
