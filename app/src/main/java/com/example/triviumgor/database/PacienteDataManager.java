package com.example.triviumgor.database;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.triviumgor.model.Sesion;
import com.example.triviumgor.model.Paciente;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class PacienteDataManager {
    private SQLiteDatabase database;
    private final PacienteDBHelper dbHelper;

    public PacienteDataManager(Context context) {
        dbHelper = new PacienteDBHelper(context);
    }

    public boolean open() {
        try {
            database = dbHelper.getWritableDatabase();
            return true;
        } catch (SQLException e) {
            Log.e("ERROR", "Error SQL al abrir la base de datos: " + e.getMessage());
            return false;
        } catch (Exception e) {
            Log.e("ERROR", "Error al abrir la base de datos: " + e.getMessage());
            return false;
        }
    }

    public void close() {
        dbHelper.close();
    }

    // ======= MÉTODOS PARA AUTENTICACIÓN DE USUARIOS =======

    /**
     * Verifica las credenciales de un usuario
     * @param username Nombre de usuario
     * @param password Contraseña en texto plano
     * @return true si las credenciales son válidas, false en caso contrario
     */
    public boolean verificarCredenciales(String username, String password) {
        try {
            String passwordHash = PacienteDBHelper.hashPassword(password);

            Cursor cursor = database.query(
                    PacienteDBHelper.TABLE_USUARIOS,
                    new String[]{PacienteDBHelper.COLUMN_USUARIO_ID, PacienteDBHelper.COLUMN_ACTIVO},
                    PacienteDBHelper.COLUMN_USERNAME + " = ? AND " +
                            PacienteDBHelper.COLUMN_PASSWORD_HASH + " = ?",
                    new String[]{username, passwordHash},
                    null,
                    null,
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                int activo = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_ACTIVO));
                int userId = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_USUARIO_ID));
                cursor.close();

                if (activo == 1) {
                    // Actualizar último acceso
                    actualizarUltimoAcceso(userId);
                    return true;
                }
            }

            if (cursor != null) {
                cursor.close();
            }

            return false;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al verificar credenciales: " + e.getMessage());
            return false;
        }
    }
    /**
     * Obtiene información del usuario por username
     * @param username Nombre de usuario
     * @return Cursor con los datos del usuario o null
     */
    public Cursor obtenerUsuario(String username) {
        return database.query(
                PacienteDBHelper.TABLE_USUARIOS,
                null,
                PacienteDBHelper.COLUMN_USERNAME + " = ?",
                new String[]{username},
                null,
                null,
                null
        );
    }

    /**
     * Obtiene información del usuario por Id
     * @param id Integer que define el id del usuario
     * @return Cursor con los datos del usuario o null
     */
    public Cursor obtenerUsuarioPorId(int id) {
        return database.query(
                PacienteDBHelper.TABLE_USUARIOS,
                null,
                PacienteDBHelper.COLUMN_USUARIO_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null
        );
    }

    /**
     * Crea un nuevo usuario en la base de datos
     * @param username Nombre de usuario (único)
     * @param password Contraseña en texto plano
     * @param nombreCompleto Nombre completo del usuario
     * @param rol Rol del usuario (admin, medico, enfermero, etc.)
     * @return ID del usuario creado o -1 si hubo error
     */
    public long crearUsuario(String username, String password, String nombreCompleto, String rol) {
        try {
            String passwordHash = PacienteDBHelper.hashPassword(password);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String fechaActual = sdf.format(new Date());

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_USERNAME, username);
            values.put(PacienteDBHelper.COLUMN_PASSWORD_HASH, passwordHash);
            values.put(PacienteDBHelper.COLUMN_NOMBRE_COMPLETO, nombreCompleto);
            values.put(PacienteDBHelper.COLUMN_ROL, rol);
            values.put(PacienteDBHelper.COLUMN_ACTIVO, 1);
            values.put(PacienteDBHelper.COLUMN_FECHA_CREACION, fechaActual);

            long id = database.insert(PacienteDBHelper.TABLE_USUARIOS, null, values);

            if (id != -1) {
                Log.d("PacienteDataManager", "Usuario creado: " + username);
            } else {
                Log.e("PacienteDataManager", "Error al crear usuario: " + username);
            }

            return id;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al crear usuario: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Cambia la contraseña de un usuario
     * @param username Nombre de usuario
     * @param newPassword Nueva contraseña
     * @return true si se cambió correctamente, false en caso contrario
     */
    public boolean cambiarPassword(String username, String newPassword) {
        try {
            String passwordHash = PacienteDBHelper.hashPassword(newPassword);

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_PASSWORD_HASH, passwordHash);

            int rowsAffected = database.update(
                    PacienteDBHelper.TABLE_USUARIOS,
                    values,
                    PacienteDBHelper.COLUMN_USERNAME + " = ?",
                    new String[]{username}
            );

            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al cambiar contraseña: " + e.getMessage());
            return false;
        }
    }

    /**
     * Actualiza el timestamp de último acceso del usuario
     */
    private void actualizarUltimoAcceso(int userId) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String fechaActual = sdf.format(new Date());

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_ULTIMO_ACCESO, fechaActual);

            database.update(
                    PacienteDBHelper.TABLE_USUARIOS,
                    values,
                    PacienteDBHelper.COLUMN_USUARIO_ID + " = ?",
                    new String[]{String.valueOf(userId)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al actualizar último acceso: " + e.getMessage());
        }
    }

    /**
     * Activa o desactiva un usuario
     * @param username Nombre de usuario
     * @param activo true para activar, false para desactivar
     * @return true si se actualizó correctamente
     */
    public boolean establecerEstadoUsuario(String username, boolean activo) {
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_ACTIVO, activo ? 1 : 0);

            int rowsAffected = database.update(
                    PacienteDBHelper.TABLE_USUARIOS,
                    values,
                    PacienteDBHelper.COLUMN_USERNAME + " = ?",
                    new String[]{username}
            );

            return rowsAffected > 0;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al cambiar estado de usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene todos los usuarios del sistema
     * @return Lista de usuarios
     */
    public Cursor obtenerTodosUsuarios() {
        return database.query(
                PacienteDBHelper.TABLE_USUARIOS,
                null,
                null,
                null,
                null,
                null,
                PacienteDBHelper.COLUMN_NOMBRE_COMPLETO
        );
    }

    // ======= MÉTODOS PARA USUARIO_PACIENTE =======

    /**
     * Vincula un paciente a un usuario con el rol indicado.
     * Se llama automáticamente al crear un paciente nuevo (rol = "creador"),
     * o manualmente al asignar un paciente existente (rol = "asignado").
     *
     * @param idUsuario  ID del usuario
     * @param idPaciente ID del paciente
     * @param rol        "creador" o "asignado"
     * @return true si se insertó correctamente, false si ya existía o hubo error
     */
    public boolean vincularUsuarioPaciente(int idUsuario, int idPaciente, String rol) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_UP_USUARIO_ID, idUsuario);
            values.put(PacienteDBHelper.COLUMN_UP_PACIENTE_ID, idPaciente);
            values.put(PacienteDBHelper.COLUMN_UP_ROL, rol);
            values.put(PacienteDBHelper.COLUMN_UP_FECHA, sdf.format(new Date()));

            long resultado = database.insert(PacienteDBHelper.TABLE_USUARIO_PACIENTE, null, values);
            return resultado != -1;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al vincular usuario-paciente: " + e.getMessage());
            return false;
        }
    }

    /**
     * Desvincula un usuario de un paciente (elimina la relación).
     *
     * @param idUsuario  ID del usuario
     * @param idPaciente ID del paciente
     * @return true si se eliminó correctamente
     */
    public boolean desvincularUsuarioPaciente(int idUsuario, int idPaciente) {
        try {
            return database.delete(
                    PacienteDBHelper.TABLE_USUARIO_PACIENTE,
                    PacienteDBHelper.COLUMN_UP_USUARIO_ID + " = ? AND " +
                            PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(idUsuario), String.valueOf(idPaciente)}
            ) > 0;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al desvincular usuario-paciente: " + e.getMessage());
            return false;
        }
    }

    /**
     * Comprueba si un usuario ya tiene acceso a un paciente concreto.
     */
    public boolean tieneAccesoPaciente(int idUsuario, int idPaciente) {
        try {
            Cursor cursor = database.query(
                    PacienteDBHelper.TABLE_USUARIO_PACIENTE,
                    new String[]{PacienteDBHelper.COLUMN_UP_ROL},
                    PacienteDBHelper.COLUMN_UP_USUARIO_ID + " = ? AND " +
                            PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(idUsuario), String.valueOf(idPaciente)},
                    null, null, null
            );

            boolean tiene = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();
            return tiene;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al comprobar acceso: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene todos los pacientes visibles para un usuario concreto,
     * usando un JOIN entre pacientes y usuario_paciente.
     *
     * @param idUsuario ID del usuario logueado
     * @return Cursor con las columnas de la tabla pacientes
     */
    public Cursor obtenerPacientesDeUsuario(int idUsuario) {
        String query =
                "SELECT p.* FROM " + PacienteDBHelper.TABLE_PACIENTES + " p " +
                        "INNER JOIN " + PacienteDBHelper.TABLE_USUARIO_PACIENTE + " up " +
                        "ON p." + PacienteDBHelper.COLUMN_ID + " = up." + PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " " +
                        "WHERE up." + PacienteDBHelper.COLUMN_UP_USUARIO_ID + " = ? " +
                        "ORDER BY p." + PacienteDBHelper.COLUMN_NOMBRE;

        return database.rawQuery(query, new String[]{String.valueOf(idUsuario)});
    }

    /**
     * Obtiene los IDs de todos los usuarios que tienen acceso a un paciente.
     * Útil para mostrar la lista de usuarios asignados a un paciente.
     */
    public Cursor obtenerUsuariosDeUnPaciente(int idPaciente) {
        String query =
                "SELECT u.*, up." + PacienteDBHelper.COLUMN_UP_ROL + " AS rol_asignacion " +
                        "FROM " + PacienteDBHelper.TABLE_USUARIOS + " u " +
                        "INNER JOIN " + PacienteDBHelper.TABLE_USUARIO_PACIENTE + " up " +
                        "ON u." + PacienteDBHelper.COLUMN_USUARIO_ID + " = up." + PacienteDBHelper.COLUMN_UP_USUARIO_ID + " " +
                        "WHERE up." + PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " = ?";

        return database.rawQuery(query, new String[]{String.valueOf(idPaciente)});
    }

    /**
     * Obtiene la información del usuario que creó un paciente concreto.
     * Devuelve un Cursor con las columnas del usuario + fecha_asignacion,
     * o null si no se encuentra creador.
     *
     * @param idPaciente ID del paciente
     * @return Cursor con los datos del creador, o null
     */
    public Cursor obtenerCreadorDePaciente(int idPaciente) {
        String query =
                "SELECT u." + PacienteDBHelper.COLUMN_NOMBRE_COMPLETO + ", " +
                        "u." + PacienteDBHelper.COLUMN_USERNAME + ", " +
                        "up." + PacienteDBHelper.COLUMN_UP_FECHA + " " +
                        "FROM " + PacienteDBHelper.TABLE_USUARIO_PACIENTE + " up " +
                        "INNER JOIN " + PacienteDBHelper.TABLE_USUARIOS + " u " +
                        "ON u." + PacienteDBHelper.COLUMN_USUARIO_ID + " = up." + PacienteDBHelper.COLUMN_UP_USUARIO_ID + " " +
                        "WHERE up." + PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " = ? " +
                        "AND up." + PacienteDBHelper.COLUMN_UP_ROL + " = 'creador'";

        return database.rawQuery(query, new String[]{String.valueOf(idPaciente)});
    }

    // ======= MÉTODOS PARA PACIENTES =======

    public long nuevoPaciente(String dni, String nombre, String apellido1, String apellido2, int edad, String genero,
                              String patologia,String medicacion, int intensidad, int tiempo, String cic) {
        ContentValues values = new ContentValues();
        values.put(PacienteDBHelper.COLUMN_DNI, dni);
        values.put(PacienteDBHelper.COLUMN_NOMBRE, nombre);
        values.put(PacienteDBHelper.COLUMN_APELLIDO1, apellido1);
        values.put(PacienteDBHelper.COLUMN_APELLIDO2, apellido2);
        values.put(PacienteDBHelper.COLUMN_EDAD, edad);
        values.put(PacienteDBHelper.COLUMN_GENERO, genero); //no se si cambiarlo
        values.put(PacienteDBHelper.COLUMN_PATOLOGIA, patologia);
        values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, medicacion);
        values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
        values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);
        values.put(PacienteDBHelper.COLUMN_CIC, cic);

        return database.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
    }

    // Obtener todos los pacientes
    public Cursor obtenerTodosPacientes() {

        return database.query(
                PacienteDBHelper.TABLE_PACIENTES,
                null,
                null,
                null,
                null,
                null,
                PacienteDBHelper.COLUMN_NOMBRE // Ordenar por nombre
        );
    }
    public Cursor obtenerPacientePorId(int id) {
        return database.query(
                PacienteDBHelper.TABLE_PACIENTES,
                null,
                PacienteDBHelper.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );
    }
    public int actualizarPaciente(int id, String dni, String nombre, String apellido1, String apellido2, int edad, String genero,
                                  String patologia, String medicacion, int intensidad,
                                  int tiempo, String cic) {
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_DNI, dni);
            values.put(PacienteDBHelper.COLUMN_NOMBRE, nombre);
            values.put(PacienteDBHelper.COLUMN_APELLIDO1, apellido1);
            values.put(PacienteDBHelper.COLUMN_APELLIDO2, apellido2);
            values.put(PacienteDBHelper.COLUMN_EDAD, edad);
            values.put(PacienteDBHelper.COLUMN_GENERO, genero); //no se si cambiarlo
            values.put(PacienteDBHelper.COLUMN_PATOLOGIA, patologia);
            values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, medicacion);
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
            values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);
            values.put(PacienteDBHelper.COLUMN_CIC, cic);

            return database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{ String.valueOf(id) }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar paciente: " + e.getMessage());
            return -1;
        }
    }

    //guardar configuracion
    public int guardarConfiguracion(String dni, int intensidad,
                                    int tiempo){
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
            values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);

            return  database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_DNI + " =?",
                    new String[]{dni}
            );
        }catch(Exception e) {
            Log.e(TAG, "Error al actualizar paciente: " + e.getMessage());
            return -1;
        }

    }
    //para borrar
    public boolean reiniciarAutoIncrement() {
        try {
            if (database != null && database.isOpen()) {
                database.execSQL("DELETE FROM SQLITE_SEQUENCE WHERE name='" +
                        PacienteDBHelper.TABLE_PACIENTES + "'");
                return true;
            }
            return false;
        } catch (SQLException e) {
            Log.e("ERROR", "Error al reiniciar autoincrement: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina permanentemente un paciente y todos sus registros relacionados de la base de datos
     * (sesiones + vínculos usuario_paciente).
     * @param idPaciente ID del paciente a eliminar
     * @return true si se eliminó correctamente, false en caso contrario
     */
    public boolean eliminarPaciente(long idPaciente) {
        database.beginTransaction();
        try {
            // 1. Borrar relaciones usuario_sesion de las sesiones del paciente
            //    (si no, al borrar sesiones quedan huérfanos en usuario_sesion).
            database.execSQL(
                    "DELETE FROM " + PacienteDBHelper.TABLE_USUARIO_SESION +
                            " WHERE " + PacienteDBHelper.COLUMN_US_SESION_ID +
                            " IN (SELECT " + PacienteDBHelper.COLUMN_SESION_ID +
                            " FROM " + PacienteDBHelper.TABLE_SESIONES +
                            " WHERE " + PacienteDBHelper.COLUMN_PACIENTE_ID + " = ?)",
                    new Object[]{idPaciente}
            );

            // 2. Borrar las sesiones del paciente
            database.delete(
                    PacienteDBHelper.TABLE_SESIONES,
                    PacienteDBHelper.COLUMN_PACIENTE_ID + " = ?",
                    new String[] { String.valueOf(idPaciente) }
            );

            // 3. Borrar vínculos usuario_paciente
            database.delete(
                    PacienteDBHelper.TABLE_USUARIO_PACIENTE,
                    PacienteDBHelper.COLUMN_UP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(idPaciente)}
            );

            // 4. Borrar el paciente. Si no existía, rollback (no tocamos nada).
            int filasBorradas = database.delete(
                    PacienteDBHelper.TABLE_PACIENTES,
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[] { String.valueOf(idPaciente) }
            );
            if (filasBorradas <= 0) {
                return false;
            }

            database.setTransactionSuccessful();
            return true;
        } catch (SQLException e) {
            Log.e("ERROR", "Error al eliminar paciente: " + e.getMessage());
            return false;
        } finally {
            database.endTransaction();
        }
    }





    //para 1 paciente 2 dispositivos

    public long nuevoPaciente2disp(String dni, String nombre, String apellido1, String apellido2, int edad, String genero,
                                   String patologia,String medicacion, int intensidad, int tiempo, int intensidad2, int tiempo2, String cic) {
        ContentValues values = new ContentValues();
        values.put(PacienteDBHelper.COLUMN_DNI, dni);
        values.put(PacienteDBHelper.COLUMN_NOMBRE, nombre);
        values.put(PacienteDBHelper.COLUMN_APELLIDO1, apellido1);
        values.put(PacienteDBHelper.COLUMN_APELLIDO2, apellido2);
        values.put(PacienteDBHelper.COLUMN_EDAD, edad);
        values.put(PacienteDBHelper.COLUMN_GENERO, genero); //no se si cambiarlo
        values.put(PacienteDBHelper.COLUMN_PATOLOGIA, patologia);
        values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, medicacion);
        values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
        values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);
        values.put(PacienteDBHelper.COLUMN_INTENSIDAD2, intensidad2);
        values.put(PacienteDBHelper.COLUMN_TIEMPO2, tiempo2);
        values.put(PacienteDBHelper.COLUMN_CIC, cic);

        return database.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
    }
    public int actualizarPaciente2disp(int id, String dni, String nombre, String apellido1, String apellido2, int edad, String genero,
                                       String patologia, String medicacion, int intensidad,
                                       int tiempo,int intensidad2, int tiempo2, String cic) {
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_DNI, dni);
            values.put(PacienteDBHelper.COLUMN_NOMBRE, nombre);
            values.put(PacienteDBHelper.COLUMN_APELLIDO1, apellido1);
            values.put(PacienteDBHelper.COLUMN_APELLIDO2, apellido2);
            values.put(PacienteDBHelper.COLUMN_EDAD, edad);
            values.put(PacienteDBHelper.COLUMN_GENERO, genero); //no se si cambiarlo
            values.put(PacienteDBHelper.COLUMN_PATOLOGIA, patologia);
            values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, medicacion);
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
            values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD2, intensidad2);
            values.put(PacienteDBHelper.COLUMN_TIEMPO2, tiempo2);
            values.put(PacienteDBHelper.COLUMN_CIC, cic);

            return database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{ String.valueOf(id) }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar paciente: " + e.getMessage());
            return -1;
        }
    }

    //guardar configuracion
    public int guardarConfiguracion2disp(String dni, int intensidad, int tiempo, int intensidad2, int tiempo2){
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD, intensidad);
            values.put(PacienteDBHelper.COLUMN_TIEMPO, tiempo);
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD2, intensidad2);
            values.put(PacienteDBHelper.COLUMN_TIEMPO2, tiempo2);

            return  database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_DNI + " =?",
                    new String[]{dni}
            );
        }catch(Exception e) {
            Log.e(TAG, "Error al actualizar paciente: " + e.getMessage());
            return -1;
        }

    }
    // ======= MÉTODOS PARA SESIONES =======

    /**
     * Registra una nueva sesión de tratamiento con notas
     * @param idPaciente ID del paciente
     * @param dispositivo Número de dispositivo (1, 2, o 3 para ambos)
     * @param intensidad Intensidad utilizada
     * @param tiempo Tiempo del tratamiento
     * @return ID de la sesión creada o -1 si hubo error
     */
    public long registrarSesion(int idPaciente, String dispositivo, int intensidad, int tiempo) {
        try {
            // Obtener fecha actual en formato ISO
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String fechaActual = sdf.format(new Date());

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_PACIENTE_ID, idPaciente);
            values.put(PacienteDBHelper.COLUMN_DISPOSITIVO, dispositivo);
            values.put(PacienteDBHelper.COLUMN_FECHA, fechaActual);
            values.put(PacienteDBHelper.COLUMN_INTENSIDAD_SESION, intensidad);
            values.put(PacienteDBHelper.COLUMN_TIEMPO_SESION, tiempo);


            long idSesion = database.insert(PacienteDBHelper.TABLE_SESIONES, null, values);

            if (idSesion != -1) {
                Log.d("PacienteDataManager", "Sesión registrada con éxito, ID: " + idSesion);
            } else {
                Log.e("PacienteDataManager", "Error al registrar sesión");
            }

            return idSesion;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al registrar sesión: " + e.getMessage());
            return -1;
        }
    }
    /**
     * Obtiene todas las sesiones de un paciente
     * @param idPaciente ID del paciente
     * @return Lista de sesiones
     */
    public List<Sesion> obtenerSesionesPaciente(int idPaciente) {
        List<Sesion> sesiones = new ArrayList<>();

        Cursor cursor = database.query(
                PacienteDBHelper.TABLE_SESIONES,
                null,
                PacienteDBHelper.COLUMN_PACIENTE_ID + " = ?",
                new String[]{String.valueOf(idPaciente)},
                null,
                null,
                PacienteDBHelper.COLUMN_FECHA + " DESC" // Ordenar por fecha, más reciente primero
        );

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_SESION_ID));
                String dispositivo = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_DISPOSITIVO));
                String fecha = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_FECHA));
                int intensidad = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD_SESION));
                int tiempo = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO_SESION));


                Sesion sesion = new Sesion(id, idPaciente, dispositivo, fecha, intensidad, tiempo);
                sesiones.add(sesion);
            } while (cursor.moveToNext());

            cursor.close();
        }

        return sesiones;
    }

    /**
     * Obtiene una sesión específica por su ID
     * @param idSesion ID de la sesión
     * @return Objeto Sesion o null si no se encuentra
     */
    public Sesion obtenerSesion(int idSesion) {
        Cursor cursor = database.query(
                PacienteDBHelper.TABLE_SESIONES,
                null,
                PacienteDBHelper.COLUMN_SESION_ID + " = ?",
                new String[]{String.valueOf(idSesion)},
                null,
                null,
                null
        );

        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_SESION_ID));
            int idPaciente = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_PACIENTE_ID));
            String dispositivo = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_DISPOSITIVO));
            String fecha = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_FECHA));
            int intensidad = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD_SESION));
            int tiempo = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO_SESION));


            cursor.close();
            return new Sesion(id, idPaciente, dispositivo, fecha, intensidad, tiempo);
        }

        if (cursor != null) {
            cursor.close();
        }

        return null;
    }

    /**
     * Elimina una sesión de la base de datos
     * @param idSesion ID de la sesión a eliminar
     * @return true si se eliminó correctamente, false en caso contrario
     */
    public boolean eliminarSesion(int idSesion) {
        database.beginTransaction();
        try {
            // 1. Borrar relaciones usuario_sesion antes que la sesión
            //    (evita huérfanos en la tabla intermedia).
            database.delete(
                    PacienteDBHelper.TABLE_USUARIO_SESION,
                    PacienteDBHelper.COLUMN_US_SESION_ID + " = ?",
                    new String[]{String.valueOf(idSesion)}
            );

            // 2. Borrar la sesión. Si no existía, rollback.
            int filasBorradas = database.delete(
                    PacienteDBHelper.TABLE_SESIONES,
                    PacienteDBHelper.COLUMN_SESION_ID + " = ?",
                    new String[]{String.valueOf(idSesion)}
            );
            if (filasBorradas <= 0) {
                return false;
            }

            database.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al eliminar sesión: " + e.getMessage());
            return false;
        } finally {
            database.endTransaction();
        }
    }

    // ======= MÉTODOS PARA USUARIO_SESION =======

    /**
     * Asigna un usuario a una sesión (registra quién realizó/supervisó la sesión)
     * @param idUsuario ID del usuario
     * @param idSesion  ID de la sesión
     * @return true si se insertó correctamente
     */
    public boolean asignarUsuarioSesion(int idUsuario, int idSesion) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String fechaActual = sdf.format(new Date());

            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_US_USUARIO_ID, idUsuario);
            values.put(PacienteDBHelper.COLUMN_US_SESION_ID,  idSesion);
            //values.put(PacienteDBHelper.COLUMN_US_FECHA,      fechaActual);

            long id = database.insertOrThrow(PacienteDBHelper.TABLE_USUARIO_SESION, null, values);
            return id != -1;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al asignar usuario a sesión: " + e.getMessage());
            return false;
        }
    }

    /**
     * Obtiene los IDs de sesión asociados a un usuario
     * @param idUsuario ID del usuario
     * @return Lista de IDs de sesión
     */
    public List<Integer> obtenerSesionesPorUsuario(int idUsuario) {
        List<Integer> sesionIds = new ArrayList<>();
        Cursor cursor = database.query(
                PacienteDBHelper.TABLE_USUARIO_SESION,
                new String[]{PacienteDBHelper.COLUMN_US_SESION_ID},
                PacienteDBHelper.COLUMN_US_USUARIO_ID + " = ?",
                new String[]{String.valueOf(idUsuario)},
                null, null, null
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                sesionIds.add(cursor.getInt(0));
            }
            cursor.close();
        }
        return sesionIds;
    }

    /**
     * Obtiene los IDs de usuario asignados a una sesión
     * @param idSesion ID de la sesión
     * @return Lista de IDs de usuario
     */
    public List<Integer> obtenerUsuariosPorSesion(int idSesion) {
        List<Integer> usuarioIds = new ArrayList<>();
        Cursor cursor = database.query(
                PacienteDBHelper.TABLE_USUARIO_SESION,
                new String[]{PacienteDBHelper.COLUMN_US_USUARIO_ID},
                PacienteDBHelper.COLUMN_US_SESION_ID + " = ?",
                new String[]{String.valueOf(idSesion)},
                null, null, null
        );
        if (cursor != null) {
            while (cursor.moveToNext()) {
                usuarioIds.add(cursor.getInt(0));
            }
            cursor.close();
        }
        return usuarioIds;
    }

    /**
     * Elimina la relación entre un usuario y una sesión
     */
    public boolean eliminarRelacionUsuarioSesion(int idUsuario, int idSesion) {
        try {
            return database.delete(
                    PacienteDBHelper.TABLE_USUARIO_SESION,
                    PacienteDBHelper.COLUMN_US_USUARIO_ID + " = ? AND " +
                            PacienteDBHelper.COLUMN_US_SESION_ID  + " = ?",
                    new String[]{String.valueOf(idUsuario), String.valueOf(idSesion)}
            ) > 0;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al eliminar relación usuario-sesión: " + e.getMessage());
            return false;
        }
    }



    public void eliminarRelacionesPorSesion(int idSesion) {
        try {
            database.delete(
                    PacienteDBHelper.TABLE_USUARIO_SESION,
                    PacienteDBHelper.COLUMN_US_SESION_ID + " = ?",
                    new String[]{String.valueOf(idSesion)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al eliminar relaciones de sesión: " + e.getMessage());
        }
    }

    // ========================================================================
    // Sincronización con la API
    // ------------------------------------------------------------------------
    // Estos métodos los consume network/SincronizacionManager. La filosofía es
    // offline-first: los cambios locales se copian a backup_pendiente (o el ID
    // se marca en eliminaciones_pendientes) y se suben cuando hay red. Tras la
    // respuesta del servidor, las filas correspondientes se limpian.
    // ========================================================================

    /**
     * Guarda en backup_pendiente un snapshot del paciente recién creado,
     * editado o marcado para eliminar. El SincronizacionManager lo enviará
     * al servidor en el próximo push.
     */
    public void guardarCambioPendiente(int pacienteId, boolean eliminar,
                                       JSONObject datosPaciente) {
        try {
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

            database.insert(PacienteDBHelper.TABLE_BACKUP_PENDIENTE, null, values);
            Log.d("PacienteDataManager", "Cambio pendiente guardado para paciente " + pacienteId);

        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al guardar cambio pendiente: " + e.getMessage());
        }
    }

    /**
     * Devuelve todos los cambios pendientes, ordenados por fecha ascendente
     * para respetar el orden cronológico al sincronizar.
     */
    public JSONArray obtenerCambiosPendientes() {
        JSONArray cambios = new JSONArray();
        Cursor cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    null, null, null, null, null,
                    PacienteDBHelper.COLUMN_BP_FECHA + " ASC"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject cambio = new JSONObject();
                    cambio.put("pacienteId", cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_PACIENTE_ID)));
                    cambio.put("eliminar",   cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_ELIMINAR)) == 1);
                    cambio.put("cic",        cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_CIC)));
                    cambio.put("dni",        cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_DNI)));
                    cambio.put("nombre",     cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_NOMBRE)));
                    cambio.put("apellido1",  cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_APELLIDO1)));
                    cambio.put("apellido2",  cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_APELLIDO2)));
                    cambio.put("edad",       cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_EDAD)));
                    cambio.put("genero",     cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_GENERO)));
                    cambio.put("patologia",  cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_PATOLOGIA)));
                    cambio.put("medicacion", cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_MEDICACION)));
                    cambio.put("intensidad", cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_INTENSIDAD)));
                    cambio.put("tiempo",     cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_TIEMPO)));
                    cambio.put("intensidad2",cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_INTENSIDAD2)));
                    cambio.put("tiempo2",    cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_TIEMPO2)));
                    cambios.put(cambio);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return cambios;
    }

    /**
     * Devuelve todas las sesiones en formato JSON, ordenadas por fecha,
     * para subirlas al servidor.
     */
    public JSONArray obtenerTodasLasSesiones() {
        JSONArray sesiones = new JSONArray();
        Cursor cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_SESIONES,
                    null, null, null, null, null,
                    PacienteDBHelper.COLUMN_FECHA + " ASC"
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject sesion = new JSONObject();
                    sesion.put("pacienteId",  cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_PACIENTE_ID)));
                    sesion.put("dispositivo", cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_DISPOSITIVO)));
                    sesion.put("fecha",       cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_FECHA)));
                    sesion.put("intensidad",  cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_INTENSIDAD_SESION)));
                    sesion.put("tiempo",      cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_TIEMPO_SESION)));
                    sesiones.put(sesion);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return sesiones;
    }

    public boolean haySesionesLocales() {
        Cursor cursor = null;
        try {
            cursor = database.rawQuery(
                    "SELECT COUNT(*) FROM " + PacienteDBHelper.TABLE_SESIONES, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0) > 0;
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return false;
    }

    /**
     * Tras sincronizar con éxito un paciente, borramos sus filas en
     * backup_pendiente (puede haber varias si se editó varias veces).
     */
    public void eliminarCambiosPendientesDePaciente(int pacienteId) {
        try {
            database.delete(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    PacienteDBHelper.COLUMN_BP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        }
    }

    /**
     * Descarga masiva: reemplaza todos los pacientes locales por los que envía
     * el servidor. Los pacientes marcados localmente como pendientes de
     * eliminar se saltan para no resucitarlos antes de que el admin confirme.
     */
    public void guardarPacientesDesdeServidor(JSONArray pacientes) throws Exception {
        List<Integer> eliminacionesPendientes = obtenerTodasEliminacionesPendientes();

        database.beginTransaction();
        try {
            database.delete(PacienteDBHelper.TABLE_PACIENTES, null, null);

            for (int i = 0; i < pacientes.length(); i++) {
                JSONObject p = pacientes.getJSONObject(i);
                int id = p.getInt("id");

                if (eliminacionesPendientes.contains(id)) {
                    Log.d("PacienteDataManager", "Saltando paciente " + id + " — eliminación pendiente");
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put(PacienteDBHelper.COLUMN_ID, id);
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
                database.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    /**
     * Descarga masiva de sesiones desde el servidor (reemplaza las locales).
     */
    public void guardarSesionesDesdeServidor(JSONArray sesiones) throws Exception {
        database.beginTransaction();
        try {
            database.delete(PacienteDBHelper.TABLE_SESIONES, null, null);

            for (int i = 0; i < sesiones.length(); i++) {
                JSONObject s = sesiones.getJSONObject(i);
                ContentValues values = new ContentValues();
                values.put(PacienteDBHelper.COLUMN_SESION_ID, s.getInt("id"));
                values.put(PacienteDBHelper.COLUMN_PACIENTE_ID, s.getInt("pacienteId"));
                values.put(PacienteDBHelper.COLUMN_DISPOSITIVO, s.optString("dispositivo"));
                values.put(PacienteDBHelper.COLUMN_FECHA, s.optString("fecha"));
                values.put(PacienteDBHelper.COLUMN_INTENSIDAD_SESION, s.optInt("intensidad"));
                values.put(PacienteDBHelper.COLUMN_TIEMPO_SESION, s.optInt("tiempo"));
                database.insert(PacienteDBHelper.TABLE_SESIONES, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public List<Integer> obtenerTodasEliminacionesPendientes() {
        List<Integer> ids = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES,
                    null, null, null, null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    ids.add(cursor.getInt(cursor.getColumnIndexOrThrow(
                            PacienteDBHelper.COLUMN_EP_PACIENTE_ID)));
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return ids;
    }

    public void eliminarEliminacionPendiente(int pacienteId) {
        try {
            database.delete(
                    PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES,
                    PacienteDBHelper.COLUMN_EP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error: " + e.getMessage());
        }
    }

    public void guardarEliminacionPendiente(int pacienteId) {
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_EP_PACIENTE_ID, pacienteId);
            database.insertOrThrow(
                    PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES, null, values);
        } catch (Exception e) {
            // Ignorar si ya existe (PK duplicada al volver a pulsar eliminar).
        }
    }

    public boolean estaEnEliminacionesPendientes(int pacienteId) {
        Cursor cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES,
                    null,
                    PacienteDBHelper.COLUMN_EP_PACIENTE_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)},
                    null, null, null
            );
            return cursor != null && cursor.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

}