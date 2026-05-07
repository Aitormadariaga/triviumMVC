package com.example.triviumgor.network;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.example.triviumgor.database.PacienteDBHelper;
import com.example.triviumgor.database.PacienteDataManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.example.triviumgor.network.SincronizacionListener;

public class SincronizacionManager {

    private static final String TAG = "SincronizacionManager";

    private final Context context;
    private final ApiClient apiClient;
    private final PacienteDataManager dataManager;

    public SincronizacionManager(Context context, PacienteDataManager dataManager) {
        this.context = context;
        this.apiClient = new ApiClient(context);

        this.dataManager = dataManager;
    }

    // ============================================
    // Comprobar si hay internet
    // ============================================
    public static boolean hayInternet(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }

    // ============================================
    // Guardar cambio de paciente pendiente en SQLite
    // Se llama cuando el médico modifica un paciente
    // sin conexión a internet
    // ============================================
    public void guardarCambioPendiente(int pacienteId, boolean eliminar,
                                       JSONObject datosPaciente) {
        dataManager.guardarCambioPendiente(pacienteId, eliminar, datosPaciente);
    }

    // ============================================
    // Sincronizar — se llama al pulsar el botón
    // Primero sincroniza cambios de pacientes
    // Luego sube todas las sesiones locales
    // ============================================
    public void sincronizar(SincronizacionListener listener) {

        JSONArray cambios = obtenerCambiosPendientes();
        boolean hayCambios = cambios.length() > 0;
        boolean haySesiones = haySesionesLocales();

        // Si no hay nada que sincronizar
        if (!hayCambios && !haySesiones) {
            comprobarEliminacionesRechazadas(listener);
            return;
        }

        Log.d(TAG, "Sincronizando — cambios: " + cambios.length());

        if (hayCambios) {
            Log.d(TAG, "==> ENVIANDO sincronizar: " + cambios.toString());

            // Paso 1 — Sincronizar cambios de pacientes
            apiClient.sincronizar(cambios, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        Log.d(TAG, "<== RESPONSE sincronizar: " + response.toString());

                        JSONArray sincronizados = response.optJSONArray("sincronizados");
                        JSONArray conflictos    = response.optJSONArray("conflictos");

                        Log.d(TAG, "Sincronizados: " + (sincronizados == null ? "null" : sincronizados.length())
                                + " | Conflictos: " + (conflictos == null ? "null" : conflictos.length()));

                        // Procesar sincronizados[]: cada item trae
                        // {pacienteId (server_id), dni, fechaActualizacion}.
                        // Correlacionamos por DNI con la fila local porque el
                        // server_id puede ser nuevo (CREATE recién aceptado)
                        // y aún no estar guardado localmente. Tras encontrar
                        // el _id local:
                        //   - Rellenamos server_id si era CREATE.
                        //   - Refrescamos fechaActualizacion local con el
                        //     timestamp autoritativo del servidor.
                        //   - Limpiamos el cambio pendiente que disparó este
                        //     sincronizado (ya está aplicado en remoto).
                        if (sincronizados != null) {
                            for (int i = 0; i < sincronizados.length(); i++) {
                                Object item = sincronizados.get(i);
                                if (!(item instanceof JSONObject)) {
                                    Log.w(TAG, "Elemento de sincronizados[] con tipo inesperado: " +
                                            (item == null ? "null" : item.getClass().getName()));
                                    continue;
                                }
                                JSONObject obj = (JSONObject) item;
                                int serverId = obj.getInt("pacienteId");
                                String dni = obj.optString("dni", null);
                                String nuevaFecha = obj.optString("fechaActualizacion", null);

                                // Resolvemos el _id local: por server_id si la
                                // fila ya estaba mapeada, o por DNI si era un
                                // CREATE que acaba de recibir su server_id.
                                int idLocal = dataManager.obtenerIdLocalPorServerId(serverId);
                                if (idLocal == -1 && dni != null && !dni.isEmpty()) {
                                    idLocal = dataManager.obtenerIdLocalPorDni(dni);
                                }
                                if (idLocal == -1) {
                                    Log.w(TAG, "Sincronizado sin fila local correlacionable: server_id="
                                            + serverId + " dni=" + dni);
                                    continue;
                                }

                                // Si la fila local todavía no tenía server_id
                                // (caso CREATE), lo rellenamos para que las
                                // próximas sincronizaciones manden el id real.
                                Integer serverIdActual = dataManager.obtenerServerIdPorIdLocal(idLocal);
                                if (serverIdActual == null) {
                                    dataManager.actualizarServerIdDePaciente(idLocal, serverId);
                                }

                                if (nuevaFecha != null) {
                                    dataManager.actualizarFechaActualizacion(idLocal, nuevaFecha);
                                }
                                eliminarCambiosPendientesDePaciente(idLocal);
                                // Si el cambio recién sincronizado era una
                                // eliminación, también limpiamos su entrada
                                // de eliminaciones_pendientes. Es idempotente:
                                // si no había entrada, no hace nada.
                                dataManager.eliminarEliminacionPendiente(idLocal);
                            }
                        }

                        // Procesar errores irrecuperables: limpiamos los cambios
                        // pendientes locales que el servidor rechazó por dni_duplicado,
                        // paciente_no_encontrado o campos_obligatorios. Sin esto, el
                        // cliente reintentaría eternamente cambios que jamás van a
                        // aplicar. Correlacionamos por orden (servidor devuelve los
                        // errores en el mismo orden que los cambios enviados).
                        JSONArray errores = response.optJSONArray("errores");
                        if (errores != null && errores.length() > 0) {
                            for (int i = 0; i < errores.length(); i++) {
                                JSONObject err = errores.getJSONObject(i);
                                String razon = err.optString("razon", "");
                                // Razones irrecuperables: el cambio nunca se va
                                // a aplicar tal cual, por tanto lo limpiamos
                                // localmente para no reintentarlo eternamente.
                                //   - dni_duplicado: ya hay otro paciente con
                                //     ese DNI; el local debería fundirse con
                                //     él (descargarTodo lo hará).
                                //   - paciente_no_encontrado: el server_id que
                                //     enviamos no existe (paciente fantasma o
                                //     borrado a la fuerza).
                                //   - paciente_inactivo: el paciente fue
                                //     soft-deleted en server por otro usuario;
                                //     nuestra edición no aplica. El paciente
                                //     se borrará localmente en el descargarTodo
                                //     (ya no aparece en /api/pacientes).
                                //   - campos_obligatorios: validación dura;
                                //     el médico tendría que reeditar.
                                if (!"dni_duplicado".equals(razon)
                                        && !"paciente_no_encontrado".equals(razon)
                                        && !"paciente_inactivo".equals(razon)
                                        && !"campos_obligatorios".equals(razon)) {
                                    continue;
                                }
                                // Mapeo por índice asumiendo que el server
                                // procesó cambios en orden y produjo un error
                                // por cada cambio fallido. Si en el futuro el
                                // server intercala success/error, este mapeo
                                // pierde precisión y habría que migrar a un
                                // identificador estable (DNI, idCliente, ...).
                                if (i < cambios.length()) {
                                    String dniCambio = cambios.getJSONObject(i).optString("dni", "");
                                    if (!dniCambio.isEmpty()) {
                                        Log.d(TAG, "Limpiando cambio fantasma con razón "
                                                + razon + " para DNI " + dniCambio);
                                        dataManager.limpiarCambiosFantasmaPorDni(dniCambio);
                                    }
                                }
                            }
                        }

                        // Si hay conflictos → el usuario decide primero
                        // Las sesiones se sincronizan después de resolver conflictos
                        if (conflictos != null && conflictos.length() > 0) {
                            // El servidor solo devuelve versionServidor en cada
                            // conflicto. La UI necesita también versionTablet
                            // para mostrar al médico qué cambios quiere imponer.
                            // Enriquecemos copiando los datos que esta tablet
                            // envió en `cambios` (match por pacienteId).
                            for (int i = 0; i < conflictos.length(); i++) {
                                JSONObject conflicto = conflictos.getJSONObject(i);
                                int pid = conflicto.getInt("pacienteId");
                                for (int j = 0; j < cambios.length(); j++) {
                                    JSONObject cambio = cambios.getJSONObject(j);
                                    if (cambio.optInt("pacienteId", -1) == pid) {
                                        conflicto.put("versionTablet", cambio);
                                        break;
                                    }
                                }
                            }
                            listener.onConflictos(conflictos);
                            return;
                        }

                        // Sin conflictos → pasar a sincronizar sesiones
                        sincronizarSesionesYComprobarEliminaciones(listener);

                    } catch (Exception e) {
                        Log.e(TAG, "EXCEPCIÓN al procesar respuesta sincronizar: " + e.getMessage(), e);
                        listener.onError("Error al procesar respuesta del servidor");
                    }
                }

                @Override
                public void onError(String mensaje) {
                    Log.e(TAG, "ERROR HTTP sincronizar: " + mensaje);
                    listener.onError(mensaje);
                }
            });

        } else {
            // Solo hay sesiones que subir
            sincronizarSesionesYComprobarEliminaciones(listener);
        }
    }

    // ============================================
    // Resolver conflicto — después de que el usuario decide
    // Una vez resuelto continúa con las sesiones
    // ============================================
    public void resolverConflicto(int pacienteId, String decision,
                                  JSONObject versionTablet,
                                  SincronizacionListener listener) {

        // pacienteId aquí es el server_id (la UI lo saca del conflicto, que
        // viene del servidor). Lo enviamos tal cual al endpoint y lo
        // mapeamos a _id local solo para limpiar el cambio pendiente que
        // generó este conflicto.
        apiClient.resolverConflicto(pacienteId, decision, versionTablet, null,
                new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        int idLocal = dataManager.obtenerIdLocalPorServerId(pacienteId);
                        if (idLocal != -1) {
                            eliminarCambiosPendientesDePaciente(idLocal);
                        }

                        // Después de resolver el conflicto
                        // comprobar si quedan más cambios pendientes
                        JSONArray cambiosRestantes = obtenerCambiosPendientes();
                        if (cambiosRestantes.length() == 0) {
                            // No quedan cambios → sincronizar sesiones
                            sincronizarSesiones(listener);
                        } else {
                            // Aún quedan cambios pendientes
                            // notificar para que el botón vuelva a activarse
                            listener.onCompletado(1, 0);
                        }
                    }

                    @Override
                    public void onError(String mensaje) {
                        listener.onError(mensaje);
                    }
                });
    }

    // ============================================
    // Primera descarga — descargar todo del servidor
    // Se llama la primera vez que se usa la app
    // ============================================
    public void descargarTodo(DescargaListener listener) {

        // Paso 1 — Descargar pacientes
        apiClient.getPacientes(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray pacientes = response.getJSONArray("pacientes");
                    guardarPacientesEnLocal(pacientes);
                    Log.d(TAG, "Pacientes descargados: " + pacientes.length());

                    // Paso 2 — Descargar sesiones
                    apiClient.getSesiones(new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            try {
                                JSONArray sesiones = response.getJSONArray("sesiones");
                                guardarSesionesEnLocal(sesiones);
                                Log.d(TAG, "Sesiones descargadas: " + sesiones.length());
                                listener.onCompletado(
                                        pacientes.length(), sesiones.length());
                            } catch (Exception e) {
                                listener.onError("Error al guardar sesiones locales");
                            }
                        }

                        @Override
                        public void onError(String mensaje) {
                            listener.onError(mensaje);
                        }
                    });

                } catch (Exception e) {
                    listener.onError("Error al guardar pacientes locales");
                }
            }

            @Override
            public void onError(String mensaje) {
                listener.onError(mensaje);
            }
        });
    }

    // ============================================
    // Método privado — Sincronizar sesiones
    // Sube toda la tabla sesiones local al servidor
    // El servidor ignora las que ya existen
    // ============================================
    private void sincronizarSesiones(SincronizacionListener listener) {
        JSONArray sesiones = obtenerTodasLasSesiones();

        if (sesiones.length() == 0) {
            listener.onCompletado(0, 0);
            return;
        }

        Log.d(TAG, "Subiendo " + sesiones.length() + " sesiones...");

        apiClient.sincronizarSesiones(sesiones, new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                int insertadas = response.optInt("insertadas", 0);
                Log.d(TAG, "Sesiones insertadas: " + insertadas);
                listener.onCompletado(insertadas, 0);
            }

            @Override
            public void onError(String mensaje) {
                // Error al subir sesiones — notificar pero no es crítico
                Log.w(TAG, "Error al subir sesiones: " + mensaje);
                listener.onError(mensaje);
            }
        });
    }

    // ============================================
    // Métodos privados — SQLite lectura
    // ============================================

    private JSONArray obtenerCambiosPendientes() {
        return dataManager.obtenerCambiosPendientes();
    }

    private JSONArray obtenerTodasLasSesiones() {
        return dataManager.obtenerTodasLasSesiones();
    }

    private boolean haySesionesLocales() {
        return dataManager.haySesionesLocales();
    }

    // ============================================
    // Métodos privados — SQLite escritura
    // ============================================

    private void eliminarCambiosPendientesDePaciente(int pacienteId) {
        dataManager.eliminarCambiosPendientesDePaciente(pacienteId);
    }

    private void guardarPacientesEnLocal(JSONArray pacientes) throws Exception {
        dataManager.guardarPacientesDesdeServidor(pacientes);
    }

    private void guardarSesionesEnLocal(JSONArray sesiones) throws Exception {
        dataManager.guardarSesionesDesdeServidor(sesiones);
    }

    private boolean hayEliminacionesPendientes() {
        return !dataManager.obtenerTodasEliminacionesPendientes().isEmpty();
    }

    private void eliminarEliminacionPendiente(int pacienteId) {
        dataManager.eliminarEliminacionPendiente(pacienteId);
    }

    private void comprobarEliminacionesRechazadas(SincronizacionListener listener) {
        if (!hayEliminacionesPendientes()) {
            listener.onCompletado(0, 0);
            return;
        }

        apiClient.getEliminacionesRechazadas(new ApiClient.ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    JSONArray rechazados = response.optJSONArray("rechazados");

                    if (rechazados != null && rechazados.length() > 0) {
                        // Limpiar eliminaciones_pendientes de los rechazados
                        for (int i = 0; i < rechazados.length(); i++) {
                            int pacienteId = rechazados.getJSONObject(i).getInt("pacienteId");
                            eliminarEliminacionPendiente(pacienteId);
                        }
                        listener.onEliminacionesRechazadas(rechazados);
                    } else {
                        listener.onCompletado(0, 0);
                    }
                } catch (Exception e) {
                    listener.onCompletado(0, 0);
                }
            }

            @Override
            public void onError(String mensaje) {
                listener.onCompletado(0, 0);
            }
        });
    }



    // ============================================
    // Interfaces de callback
    // ============================================
    /*
    public interface SincronizacionListener {
        void onCompletado(int sincronizados, int conflictos);
        void onConflictos(JSONArray conflictos);

        void onEliminacionesRechazadas(JSONArray r);

        void onError(String mensaje);
    }*/
    private void sincronizarSesionesYComprobarEliminaciones(SincronizacionListener listener) {
        sincronizarSesiones(new SincronizacionListener() {
            @Override
            public void onCompletado(int sincronizados, int conflictos) {
                comprobarEliminacionesRechazadas(listener);
            }
            @Override
            public void onConflictos(JSONArray c) {}
            @Override
            public void onEliminacionesRechazadas(JSONArray r) {}
            @Override
            public void onError(String msg) {
                // Error en sesiones → aun así comprobar eliminaciones
                comprobarEliminacionesRechazadas(listener);
            }
        });
    }

    public interface DescargaListener {
        void onCompletado(int pacientes, int sesiones);
        void onError(String mensaje);
    }
}
