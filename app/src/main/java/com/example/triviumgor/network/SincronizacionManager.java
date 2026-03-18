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
public class SincronizacionManager {

    private static final String TAG = "SincronizacionManager";

    private final Context context;
    private final ApiClient apiClient;
    private final PacienteDBHelper dbHelper;

    public SincronizacionManager(Context context, PacienteDataManager dataManager) {
        this.context = context;
        this.apiClient = new ApiClient(context);
        this.dbHelper = new PacienteDBHelper(context);
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
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();

            values.put(PacienteDBHelper.COLUMN_BP_PACIENTE_ID, pacienteId);
            values.put(PacienteDBHelper.COLUMN_BP_ELIMINAR, eliminar ? 1 : 0);
            values.put(PacienteDBHelper.COLUMN_BP_FECHA,
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                            Locale.getDefault()).format(new Date()));

            if (datosPaciente != null) {
                values.put(PacienteDBHelper.COLUMN_BP_CIC,
                        datosPaciente.optString("cic", null));
                values.put(PacienteDBHelper.COLUMN_BP_DNI,
                        datosPaciente.optString("dni", null));
                values.put(PacienteDBHelper.COLUMN_BP_NOMBRE,
                        datosPaciente.optString("nombre", null));
                values.put(PacienteDBHelper.COLUMN_BP_APELLIDO1,
                        datosPaciente.optString("apellido1", null));
                values.put(PacienteDBHelper.COLUMN_BP_APELLIDO2,
                        datosPaciente.optString("apellido2", null));
                values.put(PacienteDBHelper.COLUMN_BP_EDAD,
                        datosPaciente.optInt("edad", 0));
                values.put(PacienteDBHelper.COLUMN_BP_GENERO,
                        datosPaciente.optString("genero", null));
                values.put(PacienteDBHelper.COLUMN_BP_PATOLOGIA,
                        datosPaciente.optString("patologia", null));
                values.put(PacienteDBHelper.COLUMN_BP_MEDICACION,
                        datosPaciente.optString("medicacion", null));
                values.put(PacienteDBHelper.COLUMN_BP_INTENSIDAD,
                        datosPaciente.optInt("intensidad", 0));
                values.put(PacienteDBHelper.COLUMN_BP_TIEMPO,
                        datosPaciente.optInt("tiempo", 0));
                values.put(PacienteDBHelper.COLUMN_BP_INTENSIDAD2,
                        datosPaciente.optInt("intensidad2", 0));
                values.put(PacienteDBHelper.COLUMN_BP_TIEMPO2,
                        datosPaciente.optInt("tiempo2", 0));
            }

            db.insert(PacienteDBHelper.TABLE_BACKUP_PENDIENTE, null, values);
            Log.d(TAG, "Cambio pendiente guardado para paciente " + pacienteId);

        } catch (Exception e) {
            Log.e(TAG, "Error al guardar cambio pendiente: " + e.getMessage());
        }
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
            listener.onCompletado(0, 0);
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
                        sincronizarSesiones(listener);

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
            sincronizarSesiones(listener);
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
        JSONArray cambios = new JSONArray();
        Cursor cursor = null;

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.query(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    null, null, null, null, null,
                    PacienteDBHelper.COLUMN_BP_FECHA + " ASC"
                    // ↑ Los más antiguos primero
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject cambio = new JSONObject();
                    cambio.put("pacienteId", cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_BP_PACIENTE_ID)));
                    cambio.put("eliminar", cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_BP_ELIMINAR)) == 1);
                    cambio.put("cic", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_CIC)));
                    cambio.put("dni", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_DNI)));
                    cambio.put("nombre", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_NOMBRE)));
                    cambio.put("apellido1", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_APELLIDO1)));
                    cambio.put("apellido2", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_APELLIDO2)));
                    cambio.put("edad", cursor.getInt(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_EDAD)));
                    cambio.put("genero", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_GENERO)));
                    cambio.put("patologia", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_PATOLOGIA)));
                    cambio.put("medicacion", cursor.getString(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_MEDICACION)));
                    cambio.put("intensidad", cursor.getInt(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_INTENSIDAD)));
                    cambio.put("tiempo", cursor.getInt(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_TIEMPO)));
                    cambio.put("intensidad2", cursor.getInt(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_INTENSIDAD2)));
                    cambio.put("tiempo2", cursor.getInt(
                            cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_TIEMPO2)));

                    cambios.put(cambio);

                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al obtener cambios pendientes: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return cambios;
    }

    private JSONArray obtenerTodasLasSesiones() {
        JSONArray sesiones = new JSONArray();
        Cursor cursor = null;

        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.query(
                    PacienteDBHelper.TABLE_SESIONES,
                    null, null, null, null, null,
                    PacienteDBHelper.COLUMN_FECHA + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject sesion = new JSONObject();
                    sesion.put("pacienteId", cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_PACIENTE_ID)));
                    sesion.put("dispositivo", cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_DISPOSITIVO)));
                    sesion.put("fecha", cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_FECHA)));
                    sesion.put("intensidad", cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_INTENSIDAD_SESION)));
                    sesion.put("tiempo", cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                    PacienteDBHelper.COLUMN_TIEMPO_SESION)));
                    sesiones.put(sesion);
                } while (cursor.moveToNext());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al obtener sesiones: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return sesiones;
    }

    private boolean haySesionesLocales() {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM " + PacienteDBHelper.TABLE_SESIONES,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al contar sesiones: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return false;
    }

    // ============================================
    // Métodos privados — SQLite escritura
    // ============================================

    private void eliminarCambiosPendientesDePaciente(int pacienteId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.delete(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    PacienteDBHelper.COLUMN_BP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)}
            );
            Log.d(TAG, "Cambios eliminados para paciente " + pacienteId);
        } catch (Exception e) {
            Log.e(TAG, "Error al eliminar cambios: " + e.getMessage());
        }
    }

    private void guardarPacientesEnLocal(JSONArray pacientes) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            // Borrar todos antes de insertar los del servidor
            db.delete(PacienteDBHelper.TABLE_PACIENTES, null, null);

            for (int i = 0; i < pacientes.length(); i++) {
                JSONObject p = pacientes.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put(PacienteDBHelper.COLUMN_ID, p.getInt("id"));
                values.put(PacienteDBHelper.COLUMN_CIC, p.optString("cic"));
                values.put(PacienteDBHelper.COLUMN_DNI, p.optString("dni"));
                values.put(PacienteDBHelper.COLUMN_NOMBRE, p.optString("nombre"));
                values.put(PacienteDBHelper.COLUMN_APELLIDO1, p.optString("apellido1"));
                values.put(PacienteDBHelper.COLUMN_APELLIDO2, p.optString("apellido2"));
                values.put(PacienteDBHelper.COLUMN_EDAD, p.optInt("edad"));
                values.put(PacienteDBHelper.COLUMN_GENERO, p.optString("genero"));
                values.put(PacienteDBHelper.COLUMN_PATOLOGIA, p.optString("patologia"));
                values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, p.optString("medicacion"));
                values.put(PacienteDBHelper.COLUMN_INTENSIDAD, p.optInt("intensidad"));
                values.put(PacienteDBHelper.COLUMN_TIEMPO, p.optInt("tiempo"));
                values.put(PacienteDBHelper.COLUMN_INTENSIDAD2, p.optInt("intensidad2"));
                values.put(PacienteDBHelper.COLUMN_TIEMPO2, p.optInt("tiempo2"));
                db.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void guardarSesionesEnLocal(JSONArray sesiones) throws Exception {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(PacienteDBHelper.TABLE_SESIONES, null, null);

            for (int i = 0; i < sesiones.length(); i++) {
                JSONObject s = sesiones.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put(PacienteDBHelper.COLUMN_SESION_ID, s.getInt("id"));
                values.put(PacienteDBHelper.COLUMN_PACIENTE_ID, s.getInt("pacienteId"));
                values.put(PacienteDBHelper.COLUMN_DISPOSITIVO, s.optString("dispositivo"));
                values.put(PacienteDBHelper.COLUMN_FECHA, s.optString("fecha"));
                values.put(PacienteDBHelper.COLUMN_INTENSIDAD_SESION, s.optInt("intensidad"));
                values.put(PacienteDBHelper.COLUMN_TIEMPO_SESION, s.optInt("tiempo"));
                db.insert(PacienteDBHelper.TABLE_SESIONES, null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    // ============================================
    // Interfaces de callback
    // ============================================
    public interface SincronizacionListener {
        void onCompletado(int sincronizados, int conflictos);
        void onConflictos(JSONArray conflictos);
        void onError(String mensaje);
    }

    public interface DescargaListener {
        void onCompletado(int pacientes, int sesiones);
        void onError(String mensaje);
    }
}
