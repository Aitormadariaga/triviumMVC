package com.example.triviumgor.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.text.TextUtils;
import android.util.Log;

import com.example.triviumgor.database.PacienteDBHelper;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Usuario;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UsuarioController {
    private static final String TAG = "UsuarioController";
    private static final String PREFS_NAME = "LoginPrefs";

    // Constantes de validación
    // Dependencias
    private final PacienteDataManager dataManager;
    private final SharedPreferences sharedPreferences;
    private final Context context;

    // ========================
    // ENUM DE ROLES
    // ========================

    /**
     * Enum que representa todos los roles del sistema
     * Incluye emoji, código de DB y descripción
     */
    /**
     * Roles del sistema. Modelo binario alineado con el servidor:
     *   - admin: gestiona usuarios (CRUD via /api/usuarios) y login.
     *   - usuario: todo lo demas (crear pacientes, sesiones, ver historial...).
     *
     * fromCodigo() es defensivo: roles legacy (medico/enfermero/fisioterapeuta/
     * recepcionista) que sigan en la BD local devuelven USUARIO. La migracion
     * de schema en PacienteDBHelper#onUpgrade normaliza la columna 'rol' al
     * arrancar tras la actualizacion.
     */
    public enum Rol {
        ADMIN("admin", "👑", "Administrador"),
        USUARIO("usuario", "👤", "Usuario");

        private final String codigo;
        private final String emoji;
        private final String descripcion;

        Rol(String codigo, String emoji, String descripcion) {
            this.codigo = codigo;
            this.emoji = emoji;
            this.descripcion = descripcion;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getDescripcion() {
            return descripcion;
        }

        public String getTextoCompleto() {
            return emoji + " " + codigo + " - " + descripcion;
        }

        /**
         * "admin" → ADMIN. Cualquier otra cosa (incluido null) → USUARIO.
         * Asi cubrimos datos legacy (medico/enfermero/...) sin crashear.
         */
        public static Rol fromCodigo(String codigo) {
            if (codigo != null && "admin".equalsIgnoreCase(codigo.trim())) {
                return ADMIN;
            }
            return USUARIO;
        }

        public static Rol fromTextoFormateado(String texto) {
            if (texto != null && texto.contains("admin")) {
                return ADMIN;
            }
            return USUARIO;
        }

        public static String[] getTextosTodos() {
            Rol[] roles = values();
            String[] textos = new String[roles.length];
            for (int i = 0; i < roles.length; i++) {
                textos[i] = roles[i].getTextoCompleto();
            }
            return textos;
        }

        public static List<Rol> getTodos() {
            return Arrays.asList(values());
        }
    }

    // ========================
    // CONSTRUCTOR
    // ========================

    public UsuarioController(Context context, PacienteDataManager dataManager) {
        this.context = context;
        this.dataManager = dataManager;
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ========================
    // AUTENTICACIÓN Y SESIÓN
    // ========================

    public ResultadoLogin login(String username, String password) {
        if (TextUtils.isEmpty(username)) {
            return new ResultadoLogin(false, "Por favor, ingresa tu usuario");
        }
        if (TextUtils.isEmpty(password)) {
            return new ResultadoLogin(false, "Por favor, ingresa tu contraseña");
        }

        try {
            boolean credencialesValidas = dataManager.verificarCredenciales(username, password);

            if (!credencialesValidas) {
                return new ResultadoLogin(false, "Usuario o contraseña incorrectos");
            }

            Usuario usuario = obtenerUsuarioPorUsername(username);

            if (usuario == null) {
                Log.e(TAG, "ERROR CRÍTICO: credenciales válidas pero usuario no encontrado");
                return new ResultadoLogin(false, "Error al obtener datos del usuario");
            }

            if (usuario.getActivo() == 0) {
                return new ResultadoLogin(false, "Usuario desactivado. Contacta al administrador");
            }

            guardarSesion(usuario);
            Log.d(TAG, "✓ Login exitoso: " + username + " (rol: " + usuario.getRol() + ")");

            return new ResultadoLogin(true, "Bienvenido, " + username, usuario);

        } catch (Exception e) {
            Log.e(TAG, "Error durante login: " + e.getMessage());
            return new ResultadoLogin(false, "Error al procesar el login");
        }
    }

    public void logout() {
        try {
            String username = getUsernameActual();
            sharedPreferences.edit().clear().apply();
            Log.d(TAG, "✓ Sesión cerrada: " + username);
        } catch (Exception e) {
            Log.e(TAG, "Error durante logout: " + e.getMessage());
        }
    }

    public boolean haySesionActiva() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    public Usuario getUsuarioActual() {
        if (!haySesionActiva()) return null;
        String username = sharedPreferences.getString("username", "");
        return TextUtils.isEmpty(username) ? null : obtenerUsuarioPorUsername(username);
    }

    public String getUsernameActual() {
        return sharedPreferences.getString("username", "");
    }

    public String getRolActual() {
        return sharedPreferences.getString("rol", "");
    }

    /**
     * Obtiene el rol actual como Enum
     */
    public Rol getRolActualEnum() {
        String rolCodigo = getRolActual();
        return Rol.fromCodigo(rolCodigo);
    }

    public String getNombreCompletoActual() {
        return sharedPreferences.getString("nombreCompleto", "");
    }

    // ========================
    // CRUD DE USUARIOS
    // ========================

    /**
     * Inserta o actualiza un usuario en la cache local SQLite a partir de los
     * datos validados por el servidor en un login online. Es lo que cierra el
     * loop offline: tras el primer login OK contra /api/login, este metodo
     * guarda el hash de la password (recien validada por el server) en local,
     * permitiendo logins offline posteriores. Sin esto, un usuario creado por
     * admin en la web (vista por Android via GET /api/usuarios) podria
     * autenticarse online pero nunca offline porque su hash local no existe.
     *
     * @return true si el upsert fue exitoso.
     */
    public boolean cacheUsuarioTrasLoginRemoto(String username, String password,
                                               String nombreCompleto, String rolCodigo) {
        try {
            // Si ya existe localmente, refresh password+nombre+rol. Si no, crea.
            Usuario existente = obtenerUsuarioPorUsername(username);
            if (existente != null) {
                dataManager.cambiarPassword(username, password);
                // No refrescamos nombre/rol aqui — el upsert "full" de cache va
                // por sincronizarUsuariosDesdeServer() al abrir AdminUsuarios.
                return true;
            }
            long id = dataManager.crearUsuario(username, password, nombreCompleto,
                    rolCodigo != null ? rolCodigo : "usuario");
            return id != -1;
        } catch (Exception e) {
            Log.e(TAG, "Error cacheando usuario tras login remoto: " + e.getMessage());
            return false;
        }
    }

    public List<Usuario> obtenerTodosLosUsuarios() {
        List<Usuario> usuarios = new ArrayList<>();
        Cursor cursor = null;

        try {
            cursor = dataManager.obtenerTodosUsuarios();
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    Usuario u = cursorAUsuario(cursor);
                    if (u != null) usuarios.add(u);
                } while (cursor.moveToNext());
            }
            Log.d(TAG, "✓ Obtenidos " + usuarios.size() + " usuarios");
        } catch (Exception e) {
            Log.e(TAG, "Error obtener usuarios: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
        return usuarios;
    }

    public Usuario obtenerUsuarioPorUsername(String username) {
        Cursor cursor = null;
        try {
            cursor = dataManager.obtenerUsuario(username);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorAUsuario(cursor);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obtener usuario " + username + ": " + e.getMessage());
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public Usuario obtenerUsuarioPorId(int id) {
        Cursor cursor = null;
        try {
            cursor = dataManager.obtenerUsuarioPorId(id);
            if (cursor != null && cursor.moveToFirst()) {
                return cursorAUsuario(cursor);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Error obtener usuario " + id + ": " + e.getMessage());
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // ========================
    // CONTROL DE PERMISOS
    // ========================

    public boolean esAdmin() {
        return getRolActualEnum() == Rol.ADMIN;
    }

    /**
     * Verifica si tiene un rol específico
     */
    public boolean tieneRol(Rol rol) {
        return getRolActualEnum() == rol;
    }

    /**
     * Modelo binario: solo ADMINISTRAR_USUARIOS y VER_REPORTES_COMPLETOS son
     * exclusivos de admin. Cualquier otra accion clinica esta abierta a todos
     * los usuarios autenticados — usuarios no-admin pueden crear pacientes,
     * editar, ver estadisticas, etc. Coincide con el modelo de la web donde
     * ROLE_USER tiene todas las capacidades clinicas y ROLE_ADMIN suma solo
     * la gestion del propio panel admin.
     */
    public boolean tienePermiso(AccionPermiso accion) {
        Rol rol = getRolActualEnum();
        if (rol == null) return false;

        switch (accion) {
            case ADMINISTRAR_USUARIOS:
            case VER_REPORTES_COMPLETOS:
                return rol == Rol.ADMIN;

            case CREAR_PACIENTE:
            case EDITAR_PACIENTE:
            case VER_ESTADISTICAS:
            case VER_PACIENTES:
            case CREAR_SESION:
            case VER_HISTORIAL_PACIENTE:
                return true;

            default:
                return false;
        }
    }

    // ========================
    // MÉTODOS AUXILIARES
    // ========================

    private void guardarSesion(Usuario usuario) {
        try {
            sharedPreferences.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("username", usuario.getUsername())
                    .putString("rol", usuario.getRol())
                    .putString("nombreCompleto", usuario.getNombreCompleto())
                    .putInt("userId", usuario.getId())
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Error guardar sesión: " + e.getMessage());
        }
    }

    private Usuario cursorAUsuario(Cursor cursor) {
        try {
            int id = cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_USUARIO_ID));
            String username = cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_USERNAME));
            String hash = cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_PASSWORD_HASH));
            String nombre = cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_NOMBRE_COMPLETO));
            String rol = cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ROL));
            int activo = cursor.getInt(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ACTIVO));
            String fechaCreacion = cursor.getString(cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_FECHA_CREACION));

            int idx = cursor.getColumnIndexOrThrow(PacienteDBHelper.COLUMN_ULTIMO_ACCESO);
            String ultimoAcceso = cursor.isNull(idx) ? null : cursor.getString(idx);

            return new Usuario(id, username, hash, nombre, rol, activo, fechaCreacion, ultimoAcceso);
        } catch (Exception e) {
            Log.e(TAG, "Error convertir cursor: " + e.getMessage());
            return null;
        }
    }

    // ========================
    // CLASES RESULTADO
    // ========================

    public static class ResultadoLogin {
        public final boolean exitoso;
        public final String mensaje;
        public final Usuario usuario;

        public ResultadoLogin(boolean exitoso, String mensaje) {
            this(exitoso, mensaje, null);
        }

        public ResultadoLogin(boolean exitoso, String mensaje, Usuario usuario) {
            this.exitoso = exitoso;
            this.mensaje = mensaje;
            this.usuario = usuario;
        }
    }

    public static class ResultadoOperacion {
        public final boolean exitoso;
        public final String mensaje;

        public ResultadoOperacion(boolean exitoso, String mensaje) {
            this.exitoso = exitoso;
            this.mensaje = mensaje;
        }
    }

    public enum AccionPermiso {
        ADMINISTRAR_USUARIOS,
        VER_REPORTES_COMPLETOS,
        CREAR_PACIENTE,
        EDITAR_PACIENTE,
        VER_PACIENTES,
        CREAR_SESION,
        VER_HISTORIAL_PACIENTE,
        VER_ESTADISTICAS
    }

}
