package com.example.triviumgor.database;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
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
    private PacienteDBHelper dbHelper;
    private Context context;

    public PacienteDataManager(Context context) {
        //dbHelper = new PacienteDBHelper(context);
        this.context = context;

    }

    public boolean open() {
        try {
            char[] charPass = DBKeyManager.getInstance(context).getPassphrase();
            // SQLCipher 4.x acepta byte[] — convertimos UTF-8.
            byte[] passphrase = new String(charPass).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.util.Arrays.fill(charPass, '\0');

            // El helper se construye CON la passphrase. Si ya estaba creado de
            // un open() previo, lo reusamos (su super ya tiene la pass).
            if (dbHelper == null) {
                dbHelper = new PacienteDBHelper(context, passphrase);
            }
            database = dbHelper.getWritableDatabase();   // SIN argumentos

            java.util.Arrays.fill(passphrase, (byte) 0);
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
        // Excluimos pacientes con eliminación pendiente: la fila local sigue
        // existiendo (preservamos server_id para el sync) pero el médico no
        // debe verla porque ya pulsó "Borrar". Tras un sync exitoso la fila
        // se borra de verdad por el soft-delete propagado, así que esta
        // exclusión es transitoria.
        return database.query(
                PacienteDBHelper.TABLE_PACIENTES,
                null,
                PacienteDBHelper.COLUMN_ID + " NOT IN (SELECT "
                        + PacienteDBHelper.COLUMN_EP_PACIENTE_ID + " FROM "
                        + PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES + ")",
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

            // 4. NO borramos físicamente la fila pacientes. Si lo hiciéramos,
            // perderíamos su server_id y el sync no podría enviar la
            // eliminación al servidor (el cambio pendiente quedaría con
            // pacienteId=null y el server lo interpretaría como CREATE).
            // En su lugar el caller registra una entrada en
            // eliminaciones_pendientes y filtramos la lista visible para
            // ocultarlo. El soft-delete propagado en guardarPacientesDesdeServidor
            // borra la fila localmente cuando el servidor confirme la
            // eliminación (deja de devolver al paciente).
            // Verificamos que la fila exista para devolver false como antes.
            Cursor c = null;
            int existe;
            try {
                c = database.query(
                        PacienteDBHelper.TABLE_PACIENTES,
                        new String[]{PacienteDBHelper.COLUMN_ID},
                        PacienteDBHelper.COLUMN_ID + " = ?",
                        new String[]{String.valueOf(idPaciente)},
                        null, null, null);
                existe = (c != null && c.moveToFirst()) ? 1 : 0;
            } finally {
                if (c != null) c.close();
            }
            if (existe == 0) {
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

            // Fotografía del fecha_actualizacion para detección de conflictos:
            // si ya hay otro backup_pendiente para el mismo paciente con un
            // valor preservado, lo reutilizamos para mantener el timestamp
            // ORIGINAL (el del primer toque offline). Si no, lo leemos de la
            // tabla pacientes. Puede ser null para pacientes sin sincronizar
            // o legacy → el servidor cae a last-write-wins.
            String fechaActualizacionLocal = resolverFechaActualizacionLocal(pacienteId);
            if (fechaActualizacionLocal != null) {
                values.put(PacienteDBHelper.COLUMN_BP_FECHA_ACTUALIZACION_LOCAL,
                        fechaActualizacionLocal);
            }

            database.insert(PacienteDBHelper.TABLE_BACKUP_PENDIENTE, null, values);
            Log.d("PacienteDataManager", "Cambio pendiente guardado para paciente " + pacienteId);

        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al guardar cambio pendiente: " + e.getMessage());
        }
    }

    /**
     * Resuelve el server_id de un paciente a partir de su _id local.
     * Devuelve null si el paciente todavía no tiene server_id (creado
     * offline y aún sin subir) o si el _id no existe en la tabla.
     *
     * Es la función clave para enviar el id correcto al servidor en cada
     * sync: el _id local es solo de uso interno de SQLite, mientras que
     * el server_id es la identidad del paciente en MariaDB.
     */
    public Integer obtenerServerIdPorIdLocal(int idLocal) {
        Cursor c = null;
        try {
            c = database.query(
                    PacienteDBHelper.TABLE_PACIENTES,
                    new String[]{PacienteDBHelper.COLUMN_SERVER_ID},
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(idLocal)},
                    null, null, null
            );
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_SERVER_ID);
                if (c.isNull(idx)) return null;
                return c.getInt(idx);
            }
            return null;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error obtenerServerIdPorIdLocal: " + e.getMessage());
            return null;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Resuelve el _id local a partir de un server_id. Inversa de
     * obtenerServerIdPorIdLocal. Se usa al recibir respuestas del servidor
     * (sincronizados[]) para encontrar la fila local que hay que actualizar.
     * Devuelve -1 si no hay correspondencia (ej. paciente creado por otra
     * tablet que la nuestra todavía no ha descargado).
     */
    public int obtenerIdLocalPorServerId(int serverId) {
        Cursor c = null;
        try {
            c = database.query(
                    PacienteDBHelper.TABLE_PACIENTES,
                    new String[]{PacienteDBHelper.COLUMN_ID},
                    PacienteDBHelper.COLUMN_SERVER_ID + " = ?",
                    new String[]{String.valueOf(serverId)},
                    null, null, null
            );
            if (c != null && c.moveToFirst()) {
                return c.getInt(c.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ID));
            }
            return -1;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error obtenerIdLocalPorServerId: " + e.getMessage());
            return -1;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Resuelve el _id local a partir de un DNI. Útil cuando un CREATE
     * recién subido vuelve del servidor con su nuevo server_id; usamos el
     * DNI (estable, único) para localizar la fila local que envió ese
     * cambio y rellenarle el server_id que el servidor acaba de asignar.
     */
    public int obtenerIdLocalPorDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) return -1;
        Cursor c = null;
        try {
            c = database.query(
                    PacienteDBHelper.TABLE_PACIENTES,
                    new String[]{PacienteDBHelper.COLUMN_ID},
                    PacienteDBHelper.COLUMN_DNI + " = ?",
                    new String[]{dni},
                    null, null, null
            );
            if (c != null && c.moveToFirst()) {
                return c.getInt(c.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ID));
            }
            return -1;
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error obtenerIdLocalPorDni: " + e.getMessage());
            return -1;
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Construye el ContentValues para upsert de un paciente venido del
     * servidor (formato JSON de /api/pacientes y de versionServidor en
     * conflictos). Centraliza el mapeo campo a campo y el manejo
     * defensivo de JSONObject.NULL → SQLite null. Usado tanto por la
     * descarga masiva (guardarPacientesDesdeServidor) como por el
     * upsert quirúrgico de un solo paciente (aplicarPacienteDesdeServidor).
     */
    private ContentValues construirValuesPacienteServidor(JSONObject p) throws org.json.JSONException {
        ContentValues values = new ContentValues();
        values.put(PacienteDBHelper.COLUMN_SERVER_ID, p.getInt("id"));
        // OJO: optString("x") devuelve la cadena literal "null" si la value
        // es JSONObject.NULL en lugar de null Java. Por eso isNull() defensivo
        // en todos los strings.
        values.put(PacienteDBHelper.COLUMN_CIC,        p.isNull("cic")        ? null : p.optString("cic"));
        values.put(PacienteDBHelper.COLUMN_DNI,        p.isNull("dni")        ? null : p.optString("dni"));
        values.put(PacienteDBHelper.COLUMN_NOMBRE,     p.isNull("nombre")     ? null : p.optString("nombre"));
        values.put(PacienteDBHelper.COLUMN_APELLIDO1,  p.isNull("apellido1")  ? null : p.optString("apellido1"));
        values.put(PacienteDBHelper.COLUMN_APELLIDO2,  p.isNull("apellido2")  ? null : p.optString("apellido2"));
        values.put(PacienteDBHelper.COLUMN_EDAD, p.optInt("edad"));
        values.put(PacienteDBHelper.COLUMN_GENERO,     p.isNull("genero")     ? null : p.optString("genero"));
        values.put(PacienteDBHelper.COLUMN_PATOLOGIA,  p.isNull("patologia")  ? null : p.optString("patologia"));
        values.put(PacienteDBHelper.COLUMN_MEDICACIÓN, p.isNull("medicacion") ? null : p.optString("medicacion"));
        values.put(PacienteDBHelper.COLUMN_INTENSIDAD, p.optInt("intensidad"));
        values.put(PacienteDBHelper.COLUMN_TIEMPO, p.optInt("tiempo"));
        values.put(PacienteDBHelper.COLUMN_INTENSIDAD2, p.optInt("intensidad2"));
        values.put(PacienteDBHelper.COLUMN_TIEMPO2, p.optInt("tiempo2"));
        values.put(PacienteDBHelper.COLUMN_FECHA_ACTUALIZACION,
                p.isNull("fechaActualizacion") ? null : p.optString("fechaActualizacion"));
        return values;
    }

    /**
     * Upsert quirúrgico de UN solo paciente. A diferencia de
     * guardarPacientesDesdeServidor (que toma la lista completa y propaga
     * soft-delete a los locales que ya no están en server), este método NO
     * borra nada — solo crea o actualiza la fila del paciente recibido.
     *
     * Se usa al resolver un conflicto con "mantener": el cliente ya tiene
     * la versionServidor cargada y solo quiere refrescar esa fila local
     * sin tocar al resto de pacientes.
     */
    public void aplicarPacienteDesdeServidor(JSONObject p) throws Exception {
        ContentValues values = construirValuesPacienteServidor(p);
        int serverId = p.getInt("id");
        int filasActualizadas = database.update(
                PacienteDBHelper.TABLE_PACIENTES,
                values,
                PacienteDBHelper.COLUMN_SERVER_ID + " = ?",
                new String[]{String.valueOf(serverId)});
        if (filasActualizadas == 0) {
            database.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
        }
    }

    /**
     * Asigna o actualiza el server_id de una fila local. Se llama tras un
     * CREATE exitoso para que la próxima sincronización envíe el id real
     * y el servidor pueda hacer UPDATE en lugar de tratar el cambio como
     * un CREATE nuevo.
     */
    public void actualizarServerIdDePaciente(int idLocal, int serverId) {
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_SERVER_ID, serverId);
            database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(idLocal)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error actualizarServerIdDePaciente: " + e.getMessage());
        }
    }

    /**
     * Devuelve el fecha_actualizacion_local que se debe asociar a un nuevo
     * backup_pendiente del paciente indicado. Preserva el valor del primer
     * toque offline si ya hay backups pendientes anteriores; en caso
     * contrario lee el último timestamp conocido del servidor desde la
     * tabla pacientes. Puede devolver null (paciente sin sincronizar nunca).
     */
    private String resolverFechaActualizacionLocal(int pacienteId) {
        Cursor cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    new String[]{PacienteDBHelper.COLUMN_BP_FECHA_ACTUALIZACION_LOCAL},
                    PacienteDBHelper.COLUMN_BP_PACIENTE_ID + " = ? AND " +
                            PacienteDBHelper.COLUMN_BP_FECHA_ACTUALIZACION_LOCAL + " IS NOT NULL",
                    new String[]{String.valueOf(pacienteId)},
                    null, null,
                    PacienteDBHelper.COLUMN_BP_FECHA + " ASC",
                    "1"
            );
            if (cursor != null && cursor.moveToFirst()) {
                String preservado = cursor.getString(0);
                if (preservado != null) {
                    return preservado;
                }
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al buscar fecha_actualizacion_local previa: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        cursor = null;
        try {
            cursor = database.query(
                    PacienteDBHelper.TABLE_PACIENTES,
                    new String[]{PacienteDBHelper.COLUMN_FECHA_ACTUALIZACION},
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)},
                    null, null, null
            );
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0); // puede ser null
            }
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al leer fecha_actualizacion del paciente: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }

        return null;
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
                    int pacienteIdLocal = cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_PACIENTE_ID));
                    boolean eliminar = cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_ELIMINAR)) == 1;
                    // El servidor identifica pacientes por server_id, no por
                    // nuestro _id local. Resolvemos el server_id de la fila
                    // local y lo enviamos como pacienteId. Si la fila aún no
                    // tiene server_id (paciente creado offline) enviamos null:
                    //   - Para CREATE → el endpoint hace INSERT y nos devuelve
                    //     el server_id real en la respuesta.
                    //   - Para DELETE de un paciente que nunca llegó al server
                    //     enviamos null igualmente; el endpoint responde
                    //     "campos_obligatorios" o similar y el cliente limpia
                    //     el cambio fantasma localmente. Antes mandábamos el
                    //     _id local en este caso, pero podía colisionar con
                    //     un id de servidor distinto y borrar el paciente
                    //     equivocado en remoto.
                    Integer serverId = obtenerServerIdPorIdLocal(pacienteIdLocal);
                    if (serverId == null) {
                        cambio.put("pacienteId", JSONObject.NULL);
                    } else {
                        cambio.put("pacienteId", serverId.intValue());
                    }
                    cambio.put("eliminar", eliminar);
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
                    // Solo se incluye si hay valor: si es NULL, el servidor
                    // no puede comparar y aplica last-write-wins.
                    int idxFA = cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_BP_FECHA_ACTUALIZACION_LOCAL);
                    if (!cursor.isNull(idxFA)) {
                        cambio.put("fechaActualizacionLocal", cursor.getString(idxFA));
                    }
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
                    int pacienteIdLocal = cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_PACIENTE_ID));
                    // Las sesiones referencian al paciente por su _id local
                    // (FK interna de SQLite). Al subirlas al servidor hay que
                    // traducir ese _id a server_id, igual que con los cambios
                    // pendientes. Las sesiones de un paciente que aún no se
                    // ha subido se quedan en cola: el endpoint las rechazaría
                    // con paciente_no_encontrado y volaríamos sesiones reales.
                    Integer serverId = obtenerServerIdPorIdLocal(pacienteIdLocal);
                    if (serverId == null) {
                        Log.d("PacienteDataManager", "Saltando sesión local id_paciente=" + pacienteIdLocal
                                + ": paciente aún sin server_id, se subirá tras crear el paciente en server");
                        continue;
                    }
                    JSONObject sesion = new JSONObject();
                    sesion.put("pacienteId",  serverId.intValue());
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
     * Refresca el timestamp del servidor para un paciente. Lo llama el
     * SincronizacionManager tras un push exitoso, con la fecha que el
     * servidor devuelve en sincronizados[]. Asi la siguiente edicion
     * offline arrancara con el snapshot actualizado.
     */
    public void actualizarFechaActualizacion(int pacienteId, String fechaActualizacion) {
        if (fechaActualizacion == null) return;
        try {
            ContentValues values = new ContentValues();
            values.put(PacienteDBHelper.COLUMN_FECHA_ACTUALIZACION, fechaActualizacion);
            database.update(
                    PacienteDBHelper.TABLE_PACIENTES,
                    values,
                    PacienteDBHelper.COLUMN_ID + " = ?",
                    new String[]{String.valueOf(pacienteId)}
            );
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error al actualizar fecha_actualizacion: " + e.getMessage());
        }
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
     * Borra cambios pendientes y eliminaciones pendientes locales asociados
     * a un DNI. Se invoca cuando el servidor responde con un error
     * irrecuperable (dni_duplicado, paciente_no_encontrado, campos
     * obligatorios) para que el cliente no insista eternamente con un
     * cambio que el servidor jamás va a aceptar. También borra el paciente
     * local "fantasma" con ese DNI si existe, para que el siguiente
     * descargarTodo lo recree con el id real del servidor.
     */
    public void limpiarCambiosFantasmaPorDni(String dni) {
        if (dni == null || dni.trim().isEmpty()) return;
        try {
            int borradosBp = database.delete(
                    PacienteDBHelper.TABLE_BACKUP_PENDIENTE,
                    PacienteDBHelper.COLUMN_BP_DNI + " = ?",
                    new String[]{dni}
            );
            // Las eliminaciones pendientes están indexadas por pacienteId
            // local. Buscamos primero esos ids a partir del DNI en la tabla
            // pacientes y luego limpiamos.
            Cursor cP = null;
            try {
                cP = database.query(
                        PacienteDBHelper.TABLE_PACIENTES,
                        new String[]{PacienteDBHelper.COLUMN_ID},
                        PacienteDBHelper.COLUMN_DNI + " = ?",
                        new String[]{dni},
                        null, null, null);
                if (cP != null && cP.moveToFirst()) {
                    int idxId = cP.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ID);
                    do {
                        int idLocal = cP.getInt(idxId);
                        database.delete(
                                PacienteDBHelper.TABLE_ELIMINACIONES_PENDIENTES,
                                PacienteDBHelper.COLUMN_EP_PACIENTE_ID + " = ?",
                                new String[]{String.valueOf(idLocal)}
                        );
                        // Borramos también el registro fantasma de pacientes
                        // que nunca llegó al servidor (server_id NULL) para
                        // que descargarTodo pueda traer el real con su id
                        // correcto. Si la fila tiene server_id NO la tocamos
                        // — está sincronizada y borrarla destruiría datos.
                        database.delete(
                                PacienteDBHelper.TABLE_PACIENTES,
                                PacienteDBHelper.COLUMN_ID + " = ? AND " +
                                        PacienteDBHelper.COLUMN_SERVER_ID + " IS NULL",
                                new String[]{String.valueOf(idLocal)}
                        );
                    } while (cP.moveToNext());
                }
            } finally {
                if (cP != null) cP.close();
            }
            Log.d("PacienteDataManager", "Limpieza fantasma por DNI " + dni
                    + ": " + borradosBp + " cambios pendientes borrados");
        } catch (Exception e) {
            Log.e("PacienteDataManager", "Error limpiarCambiosFantasmaPorDni: " + e.getMessage());
        }
    }

    /**
     * Descarga masiva del servidor con UPSERT por server_id (no destructivo).
     *
     * Antes del refactor v8 esta función borraba toda la tabla y la repoblaba
     * con la lista del servidor; eso obligaba a parchear "preserva pacientes
     * con cambios pendientes" para no perder creaciones offline. El refactor
     * separa el _id local autoincrement del server_id real, y la función
     * pasa a operar quirúrgicamente:
     *
     *   - Para cada paciente del JSON, buscamos fila local con server_id = id
     *     servidor. Si existe → UPDATE conservando el _id local. Si no →
     *     INSERT nuevo con server_id rellenado.
     *   - Borramos las filas locales con server_id no nulo que YA NO estén
     *     en la respuesta del servidor (porque el server las soft-deleteó).
     *   - Las filas con server_id IS NULL son creaciones offline pendientes
     *     de subir; no se tocan jamás aquí.
     *
     * También limpiamos eliminaciones_pendientes huérfanas (id de servidor
     * que el servidor sigue devolviendo activo → la eliminación local nunca
     * se confirmó).
     */
    public void guardarPacientesDesdeServidor(JSONArray pacientes) throws Exception {
        // Conjunto de server_ids que el servidor sigue devolviendo activos.
        java.util.Set<Integer> idsEnServer = new java.util.HashSet<>();
        for (int i = 0; i < pacientes.length(); i++) {
            idsEnServer.add(pacientes.getJSONObject(i).getInt("id"));
        }

        // Las eliminaciones pendientes están indexadas por _id local. Para
        // compararlas con la lista del servidor (server_ids) tenemos que
        // mapearlas. Si una eliminación apunta a una fila local sin
        // server_id (paciente nunca subido) no sirve para sincronizar y la
        // dejamos en paz; la limpia el flujo de cambios pendientes.
        java.util.Set<Integer> serverIdsConEliminacionPendiente = new java.util.HashSet<>();
        java.util.Map<Integer, Integer> elimLocalAServer = new java.util.HashMap<>();
        for (Integer idLocalElim : obtenerTodasEliminacionesPendientes()) {
            Integer serverIdElim = obtenerServerIdPorIdLocal(idLocalElim);
            if (serverIdElim != null) {
                serverIdsConEliminacionPendiente.add(serverIdElim);
                elimLocalAServer.put(idLocalElim, serverIdElim);
            }
        }

        // Limpieza de eliminaciones huérfanas: si una eliminación pendiente
        // local apunta a un server_id que el server sigue devolviendo activo,
        // significa que el server nunca aplicó esa eliminación (caso típico
        // tras restauración manual). La limpiamos para que el paciente no
        // quede oculto localmente para siempre.
        for (java.util.Map.Entry<Integer, Integer> e : elimLocalAServer.entrySet()) {
            int idLocal = e.getKey();
            int serverId = e.getValue();
            if (idsEnServer.contains(serverId)) {
                eliminarEliminacionPendiente(idLocal);
                serverIdsConEliminacionPendiente.remove(serverId);
                Log.d("PacienteDataManager",
                        "Limpiando eliminación pendiente huérfana _id=" + idLocal
                                + " server_id=" + serverId
                                + " (servidor lo devolvió activo, no se borró)");
            }
        }

        database.beginTransaction();
        try {
            for (int i = 0; i < pacientes.length(); i++) {
                JSONObject p = pacientes.getJSONObject(i);
                int serverId = p.getInt("id");

                // Si la tablet aún quiere borrar este paciente, no lo
                // resucitamos antes de que el admin confirme la eliminación.
                // El bloque de limpieza de huérfanas ya quitó del set las
                // eliminaciones para pacientes que el server devuelve
                // activos, así que en este punto solo quedan las que
                // realmente debemos respetar (sería raro: el server tendría
                // soft-deleted al paciente y la app aún espera confirmación).
                if (serverIdsConEliminacionPendiente.contains(serverId)) {
                    Log.d("PacienteDataManager",
                            "Saltando paciente server_id=" + serverId + " — eliminación pendiente");
                    continue;
                }

                ContentValues values = construirValuesPacienteServidor(p);

                int filasActualizadas = database.update(
                        PacienteDBHelper.TABLE_PACIENTES,
                        values,
                        PacienteDBHelper.COLUMN_SERVER_ID + " = ?",
                        new String[]{String.valueOf(serverId)});

                if (filasActualizadas == 0) {
                    // No había fila local con ese server_id → INSERT nuevo.
                    // SQLite asigna _id autoincrement, server_id queda con
                    // el valor que ya pusimos en `values`.
                    database.insert(PacienteDBHelper.TABLE_PACIENTES, null, values);
                }
            }

            // Soft-delete local: pacientes con server_id NOT NULL que YA NO
            // están en la lista del servidor (porque el server los borró).
            // No tocamos los server_id IS NULL: son creaciones offline
            // pendientes de subir, deben permanecer hasta el próximo sync.
            if (!idsEnServer.isEmpty()) {
                StringBuilder placeholders = new StringBuilder();
                String[] args = new String[idsEnServer.size()];
                int k = 0;
                for (Integer id : idsEnServer) {
                    if (k > 0) placeholders.append(",");
                    placeholders.append("?");
                    args[k++] = String.valueOf(id);
                }
                int borrados = database.delete(
                        PacienteDBHelper.TABLE_PACIENTES,
                        PacienteDBHelper.COLUMN_SERVER_ID + " IS NOT NULL AND " +
                                PacienteDBHelper.COLUMN_SERVER_ID + " NOT IN (" + placeholders + ")",
                        args);
                if (borrados > 0) {
                    Log.d("PacienteDataManager",
                            "Soft-delete propagado: " + borrados + " pacientes locales eliminados (no estaban en servidor)");
                }
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