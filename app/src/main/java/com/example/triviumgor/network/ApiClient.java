package com.example.triviumgor.network;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
public class ApiClient {
    private static final String BASE_URL = "http://TU_IP_GOOGLE_CLOUD:8000";
    private static final String PREFS_NAME = "LoginPrefs";

    private final RequestQueue queue;
    private final SharedPreferences prefs;

    public ApiClient(Context context) {
        this.queue = Volley.newRequestQueue(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
                error -> callback.onError(parsearError(error))
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
                error -> callback.onError(parsearError(error))
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
                error -> callback.onError(parsearError(error))
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
    // ============================================
    private String parsearError(com.android.volley.VolleyError error) {
        if (error.networkResponse == null) {
            return "Sin conexión al servidor";
        }
        switch (error.networkResponse.statusCode) {
            case 400: return "Datos incorrectos";
            case 401: return "Sesión expirada, vuelve a iniciar sesión";
            case 404: return "Recurso no encontrado";
            case 409: return "El usuario ya existe";
            case 500: return "Error en el servidor";
            default:  return "Error " + error.networkResponse.statusCode;
        }
    }

    // ============================================
    // Interface de callback
    // ============================================
    public interface ApiCallback {
        void onSuccess(JSONObject response);
        void onError(String mensaje);
    }

}
