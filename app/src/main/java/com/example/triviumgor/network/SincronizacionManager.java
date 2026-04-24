package com.example.triviumgor.network;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
            // Paso 1 — Sincronizar cambios de pacientes
            apiClient.sincronizar(cambios, new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray sincronizados = response.optJSONArray("sincronizados");
                        JSONArray conflictos    = response.optJSONArray("conflictos");

                        // Eliminar de SQLite los que se sincronizaron sin conflicto
                        if (sincronizados != null) {
                            for (int i = 0; i < sincronizados.length(); i++) {
                                eliminarCambiosPendientesDePaciente(
                                        sincronizados.getInt(i));
                            }
                        }

                        // Si hay conflictos → el usuario decide primero
                        // Las sesiones se sincronizan después de resolver conflictos
                        if (conflictos != null && conflictos.length() > 0) {
                            listener.onConflictos(conflictos);
                            return;
                        }

                        // Sin conflictos → pasar a sincronizar sesiones
                        sincronizarSesionesYComprobarEliminaciones(listener);

                    } catch (Exception e) {
                        listener.onError("Error al procesar respuesta del servidor");
                    }
                }

                @Override
                public void onError(String mensaje) {
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

        apiClient.resolverConflicto(pacienteId, decision, versionTablet, null,
                new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        eliminarCambiosPendientesDePaciente(pacienteId);

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
