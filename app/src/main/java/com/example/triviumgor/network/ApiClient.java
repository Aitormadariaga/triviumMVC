package com.example.triviumgor.network;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.triviumgor.util.SecurePrefs;
import com.example.triviumgor.view.LoginActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApiClient {
    private static final String TAG = "ApiClient";

    // Convencion del proyecto: usar localhost:8000 contra `adb reverse tcp:8000 tcp:8000`.
    // Antes de probar en el emulador hay que configurar el reverse desde Terminal:
    //   adb reverse tcp:8000 tcp:8000
    // (se pierde al reiniciar el emulador, hay que rehacerlo). En dispositivo
    // fisico hay que cambiar esto por la IP del PC en la red local.
    private static final String BASE_URL = "http://localhost:8000";
    private static final String PREFS_NAME = "LoginPrefs";

    // Timeout estandar: 6s. Suficiente margen para latencia real, lo bastante
    // corto para que el fallback offline triggeree rapido si no hay red. Sin
    // reintentos automaticos en endpoints con token: si un 401 entra, queremos
    // un solo disparo de refresh, no que Volley vuelva a lanzar la misma
    // request con el token caducado.
    private static final int REQUEST_TIMEOUT_MS = 6_000;

    // ============================================
    // Estado static (compartido entre todas las instancias del proceso)
    // ============================================
    // Cada Activity/Manager hace `new ApiClient(context)`, pero la cola
    // Volley, el flag de logout-en-curso, el flag de refresh-en-curso y la
    // lista de peticiones esperando al refresh deben ser unicos por proceso.
    // Si no, dos refresh paralelos lanzados desde Activities distintas
    // invalidarian el refresh_token rotado mutuamente y matarian la sesion.

    private static volatile RequestQueue sharedQueue;

    /** Idempotencia del auto-logout: varios 401 simultaneos no disparan N
     *  veces el flujo. LoginActivity lo resetea tras login OK. */
    public static final AtomicBoolean logoutEnCurso = new AtomicBoolean(false);

    /** Solo una llamada a /api/token/refresh puede estar en vuelo a la vez. */
    private static final AtomicBoolean refreshEnCurso = new AtomicBoolean(false);

    /** Requests esperando a que termine el refresh para reintentarse. */
    private static final ConcurrentLinkedQueue<PendingRequest> pendientes =
            new ConcurrentLinkedQueue<>();

    // ============================================
    // Estado de instancia
    // ============================================
    private final Context appContext;
    private final SharedPreferences prefs;
    private final SecurePrefs secure;

    public ApiClient(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secure = new SecurePrefs(this.appContext);
        ensureQueue(this.appContext);
    }

    private static void ensureQueue(Context app) {
        if (sharedQueue == null) {
            synchronized (ApiClient.class) {
                if (sharedQueue == null) {
                    sharedQueue = Volley.newRequestQueue(app);
                }
            }
        }
    }

    // ============================================
    // Token JWT
    // ============================================
    public String getToken() {
        return secure.getAccessToken();
    }

    public String getRefreshToken() {
        return secure.getRefreshToken();
    }

    public void setTokens(String access, String refresh) {
        secure.setTokens(access, refresh);
    }

    public void clearTokens() {
        secure.clearTokens();
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
    // Recibe: { "token": "eyJ...", "refresh_token": "..." }
    // ============================================
    public void login(String username, String password, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("username", username);
            body.put("password", password);

            enqueue(new RequestSpec(Request.Method.POST, "/api/login", body, false, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/token/refresh
    // Envia el refresh_token, recibe {token, refresh_token} (rotation).
    // NO usa Bearer: el access ha caducado.
    // ============================================
    public void refresh(String refreshToken, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            enqueue(new RequestSpec(Request.Method.POST, "/api/token/refresh", body, false, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/logout
    // Invalida el refresh_token en servidor antes de limpiar local.
    // Se autentica por el refresh_token del body, NO por Bearer
    // (mismo patron que /api/token/refresh). Esto es deliberado:
    // el llamador limpia tokens locales en paralelo a esta request
    // por motivos de UX, asi que cuando Volley evalua getHeaders() ya
    // no habria access token disponible. Si exigieramos Bearer, el
    // server respondria 401 y dispararia el flujo de auto-logout
    // creando un Toast falso "Sesion caducada" tras un logout
    // voluntario.
    // ============================================
    public void logout(String refreshToken, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("refresh_token", refreshToken);
            enqueue(new RequestSpec(Request.Method.POST, "/api/logout", body, false, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/registro
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

            enqueue(new RequestSpec(Request.Method.POST, "/api/registro", body, false, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/perfil
    // ============================================
    public void perfil(ApiCallback callback) {
        enqueue(new RequestSpec(Request.Method.GET, "/api/perfil", null, true, callback));
    }

    // ============================================
    // PUT /api/cambiar-password
    // ============================================
    public void cambiarPassword(String passwordActual, String passwordNuevo,
                                ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("passwordActual", passwordActual);
            body.put("passwordNuevo", passwordNuevo);

            enqueue(new RequestSpec(Request.Method.PUT, "/api/cambiar-password", body, true, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/pacientes
    // ============================================
    public void getPacientes(ApiCallback callback) {
        enqueue(new RequestSpec(Request.Method.GET, "/api/pacientes", null, true, callback));
    }

    // ============================================
    // GET /api/sesiones
    // ============================================
    public void getSesiones(ApiCallback callback) {
        enqueue(new RequestSpec(Request.Method.GET, "/api/sesiones", null, true, callback));
    }

    // ============================================
    // POST /api/sincronizar
    // ============================================
    public void sincronizar(JSONArray cambios, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("cambios", cambios);

            enqueue(new RequestSpec(Request.Method.POST, "/api/sincronizar", body, true, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/sincronizar/resolver-conflicto
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

            enqueue(new RequestSpec(Request.Method.POST, "/api/sincronizar/resolver-conflicto",
                    body, true, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // POST /api/sincronizar/sesiones
    // ============================================
    public void sincronizarSesiones(JSONArray sesiones, ApiCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("sesiones", sesiones);
            enqueue(new RequestSpec(Request.Method.POST, "/api/sincronizar/sesiones",
                    body, true, callback));
        } catch (Exception e) {
            callback.onError("Error al preparar la petición");
        }
    }

    // ============================================
    // GET /api/sincronizar/eliminaciones-rechazadas
    // ============================================
    public void getEliminacionesRechazadas(ApiCallback callback) {
        enqueue(new RequestSpec(Request.Method.GET, "/api/sincronizar/eliminaciones-rechazadas",
                null, true, callback));
    }

    // ============================================
    // Encolado central
    // ============================================

    /**
     * Especificacion declarativa de una request. Permite reencolarla tras
     * un refresh exitoso sin tener que conservar el JsonObjectRequest
     * original (que ya habia consumido sus callbacks).
     */
    private static final class RequestSpec {
        final int method;
        final String endpoint;
        final JSONObject body;       // null para GET
        final boolean conToken;
        final ApiCallback callback;

        RequestSpec(int method, String endpoint, JSONObject body,
                    boolean conToken, ApiCallback callback) {
            this.method = method;
            this.endpoint = endpoint;
            this.body = body;
            this.conToken = conToken;
            this.callback = callback;
        }
    }

    private static final class PendingRequest {
        final ApiClient client;
        final RequestSpec spec;

        PendingRequest(ApiClient client, RequestSpec spec) {
            this.client = client;
            this.spec = spec;
        }
    }

    private void enqueue(final RequestSpec spec) {
        final String url = BASE_URL + spec.endpoint;

        JsonObjectRequest request = new JsonObjectRequest(
                spec.method, url, spec.body,
                response -> spec.callback.onSuccess(response),
                error -> manejarError(error, spec)
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                if (spec.conToken) return getHeadersConToken();
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                return headers;
            }
        };

        // 0 reintentos: si llega 401, queremos que el flujo de refresh lo
        // gestione, no que Volley reintente la misma request con el access
        // caducado y dispare dos veces nuestra logica.
        request.setRetryPolicy(new DefaultRetryPolicy(
                REQUEST_TIMEOUT_MS,
                0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        sharedQueue.add(request);
    }

    // ============================================
    // Manejo de errores y flujo de refresh
    // ============================================

    private void manejarError(VolleyError error, RequestSpec spec) {
        NetworkResponse nr = error.networkResponse;

        // 401 en endpoint autenticado: intentar refresh antes de auto-logout.
        if (nr != null && nr.statusCode == 401 && spec.conToken) {
            manejar401ConRefresh(spec);
            return;
        }

        // Si el logout ya esta en curso y el request fue cancelado (nr==null
        // y networkResponse vacio), evitamos el mensaje engañoso "Sin conexion"
        // tras el toast de sesion caducada.
        if (logoutEnCurso.get() && nr == null) {
            spec.callback.onError("Sesión caducada, inicia sesión de nuevo");
            return;
        }

        spec.callback.onError(parsearError(nr, spec.conToken));
    }

    private String parsearError(NetworkResponse nr, boolean conToken) {
        if (nr == null) {
            return "Sin conexión al servidor";
        }
        int code = nr.statusCode;
        if (code == 401) {
            // Llegamos aqui solo si NO es un endpoint con token (login,
            // registro, refresh). Para login/registro un 401 son credenciales
            // malas; para refresh significa refresh_token invalido/caducado.
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

    /**
     * Punto de entrada cuando un endpoint autenticado responde 401. Encola
     * la request fallida y, si no hay ya un refresh en curso, lo dispara.
     * Cuando termine, drena la cola: si refresh OK → reintentar todas; si
     * KO → auto-logout y notificar fallo a todas.
     */
    private void manejar401ConRefresh(RequestSpec spec) {
        pendientes.add(new PendingRequest(this, spec));
        Log.d(TAG, "401 en " + spec.endpoint + " — encolado (pendientes=" + pendientes.size() + ")");

        if (!refreshEnCurso.compareAndSet(false, true)) {
            // Ya hay un refresh en vuelo: el spec quedara esperando en la
            // cola y se procesara cuando el refresh termine.
            Log.d(TAG, "Refresh ya en curso, esperando");
            return;
        }

        final String refreshToken = getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            // Sin refresh_token no hay forma de renovar: caer directamente
            // al flujo de logout.
            Log.w(TAG, "Sin refresh_token guardado — forzando logout");
            refreshEnCurso.set(false);
            fallarPendientes("Sesión caducada, inicia sesión de nuevo");
            forzarLogoutPorTokenExpirado();
            return;
        }

        Log.d(TAG, "Disparando POST /api/token/refresh");
        refresh(refreshToken, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                String nuevoAccess = response.optString("token", "");
                String nuevoRefresh = response.optString("refresh_token", "");

                if (nuevoAccess.isEmpty()) {
                    // Respuesta inesperada: tratamos como fallo.
                    onError("Respuesta de refresh sin token");
                    return;
                }

                secure.setTokens(nuevoAccess, nuevoRefresh);
                refreshEnCurso.set(false);
                int reencoladas = pendientes.size();
                Log.d(TAG, "Refresh OK — reencolando " + reencoladas + " requests");

                // Drenar cola reencolando con el nuevo token. Cada spec
                // vuelve a pasar por enqueue() y getHeadersConToken() leera
                // el access recien guardado.
                PendingRequest p;
                while ((p = pendientes.poll()) != null) {
                    p.client.enqueue(p.spec);
                }
            }

            @Override
            public void onError(String mensaje) {
                Log.w(TAG, "Refresh fallido: " + mensaje + " — forzando logout");
                // IMPORTANTE: fallar los pendientes ANTES de
                // forzarLogoutPorTokenExpirado(), porque ese metodo cancela
                // la RequestQueue y dejaria specs huerfanos sin notificar.
                refreshEnCurso.set(false);
                fallarPendientes("Sesión caducada, inicia sesión de nuevo");
                forzarLogoutPorTokenExpirado();
            }
        });
    }

    private void fallarPendientes(String mensaje) {
        PendingRequest p;
        while ((p = pendientes.poll()) != null) {
            final ApiCallback cb = p.spec.callback;
            new Handler(Looper.getMainLooper()).post(() -> cb.onError(mensaje));
        }
    }

    /**
     * Limpia la sesion local y redirige a LoginActivity. Se llama cuando un
     * refresh falla (token expirado o invalido) o cuando no hay refresh_token
     * disponible. Idempotente via logoutEnCurso para que multiples 401
     * simultaneos no disparen N veces.
     */
    private void forzarLogoutPorTokenExpirado() {
        if (!logoutEnCurso.compareAndSet(false, true)) {
            return;
        }

        // Cancelar cualquier request en vuelo para no encadenar 401s.
        if (sharedQueue != null) {
            sharedQueue.cancelAll(req -> true);
        }

        // Limpiar tokens (SecurePrefs) y sesion plana (LoginPrefs).
        secure.clearTokens();
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
                Log.e(TAG, "No se pudo lanzar LoginActivity tras 401", e);
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
