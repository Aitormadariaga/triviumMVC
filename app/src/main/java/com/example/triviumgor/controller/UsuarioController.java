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
    private static final int MIN_USERNAME_LENGTH = 4;
    private static final int MIN_PASSWORD_LENGTH = 6;

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
    public enum Rol {
        ADMIN("admin", "👑", "Administrador"),
        MEDICO("medico", "👨‍⚕️", "Médico"),
        ENFERMERO("enfermero", "👩‍⚕️", "Enfermero/a"),
        FISIOTERAPEUTA("fisioterapeuta", "💪", "Fisioterapeuta"),
        RECEPCIONISTA("recepcionista", "📋", "Recepcionista");

        private final String codigo;      // Código en DB
        private final String emoji;       // Emoji visual
        private final String descripcion; // Descripción legible

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

        /**
         * Texto completo formateado: "👑 admin - Administrador"
         */
        public String getTextoCompleto() {
            return emoji + " " + codigo + " - " + descripcion;
        }

        /**
         * Busca un rol por su código de DB
         * @param codigo Código del rol (ej: "admin")
         * @return Rol correspondiente o null
         */
        public static Rol fromCodigo(String codigo) {
            if (codigo == null) return null;
            for (Rol rol : values()) {
                if (rol.codigo.equals(codigo)) {
                    return rol;
                }
            }
            return null;
        }

        /**
         * Extrae el rol de un texto formateado
         * Ejemplo: "👑 admin - Administrador" → ADMIN
         */
        public static Rol fromTextoFormateado(String texto) {
            if (texto == null) return null;
            for (Rol rol : values()) {
                if (texto.contains(rol.codigo)) {
                    return rol;
                }
            }
            return MEDICO; // Por defecto
        }

        /**
         * Obtiene todos los roles como array de textos formateados
         * Para usar en Spinners
         */
        public static String[] getTextosTodos() {
            Rol[] roles = values();
            String[] textos = new String[roles.length];
            for (int i = 0; i < roles.length; i++) {
                textos[i] = roles[i].getTextoCompleto();
            }
            return textos;
        }

        /**
         * Obtiene todos los roles como lista
         */
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
     * Crear usuario usando Rol enum
     */
    public ResultadoOperacion crearUsuario(String username, String password,
                                           String nombreCompleto, Rol rol) {
        try {
            ResultadoValidacion vUsername = validarUsername(username);
            if (!vUsername.esValido) return new ResultadoOperacion(false, vUsername.mensaje);

            ResultadoValidacion vPassword = validarPassword(password);
            if (!vPassword.esValido) return new ResultadoOperacion(false, vPassword.mensaje);

            if (TextUtils.isEmpty(nombreCompleto)) {
                return new ResultadoOperacion(false, "Ingresa el nombre completo");
            }

            if (rol == null) {
                return new ResultadoOperacion(false, "Rol no válido");
            }

            // Usar el código del enum para la DB
            long resultado = dataManager.crearUsuario(username, password, nombreCompleto, rol.getCodigo());

            if (resultado != -1) {
                Log.d(TAG, "✓ Usuario creado: " + username + " (ID: " + resultado + ")");
                return new ResultadoOperacion(true, "Usuario '" + username + "' creado exitosamente");
            } else {
                Log.w(TAG, "Usuario duplicado: " + username);
                return new ResultadoOperacion(false, "El usuario '" + username + "' ya existe");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error al crear usuario: " + e.getMessage());
            return new ResultadoOperacion(false, "Error al crear el usuario");
        }
    }

    public ResultadoOperacion cambiarPassword(String username, String nuevaPassword) {
        try {
            ResultadoValidacion validacion = validarPassword(nuevaPassword);
            if (!validacion.esValido) {
                return new ResultadoOperacion(false, validacion.mensaje);
            }

            boolean ok = dataManager.cambiarPassword(username, nuevaPassword);

            if (ok) {
                Log.d(TAG, "✓ Contraseña cambiada: " + username);
                return new ResultadoOperacion(true, "Contraseña cambiada exitosamente");
            } else {
                return new ResultadoOperacion(false, "Error al cambiar la contraseña");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cambiar password: " + e.getMessage());
            return new ResultadoOperacion(false, "Error al cambiar la contraseña");
        }
    }

    public ResultadoOperacion toggleEstadoUsuario(String username, boolean activar) {
        try {
            if (username.equals(getUsernameActual()) && !activar) {
                return new ResultadoOperacion(false, "No puedes desactivar tu propio usuario");
            }

            boolean ok = dataManager.establecerEstadoUsuario(username, activar);

            if (ok) {
                String accion = activar ? "activado" : "desactivado";
                Log.d(TAG, "✓ Usuario " + accion + ": " + username);
                return new ResultadoOperacion(true, "Usuario " + accion);
            } else {
                return new ResultadoOperacion(false, "Error al cambiar estado");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cambiar estado: " + e.getMessage());
            return new ResultadoOperacion(false, "Error al cambiar el estado");
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

    // ========================
    // CONTROL DE PERMISOS
    // ========================

    public boolean esAdmin() {
        return getRolActualEnum() == Rol.ADMIN;
    }

    public boolean esMedico() {
        return getRolActualEnum() == Rol.MEDICO;
    }

    public boolean esEnfermero() {
        return getRolActualEnum() == Rol.ENFERMERO;
    }

    public boolean esFisioterapeuta() {
        return getRolActualEnum() == Rol.FISIOTERAPEUTA;
    }

    public boolean esRecepcionista() {
        return getRolActualEnum() == Rol.RECEPCIONISTA;
    }

    public boolean esAdminOMedico() {
        Rol rol = getRolActualEnum();
        return rol == Rol.ADMIN || rol == Rol.MEDICO;
    }

    /**
     * Verifica si tiene un rol específico
     */
    public boolean tieneRol(Rol rol) {
        return getRolActualEnum() == rol;
    }

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
                return rol == Rol.ADMIN || rol == Rol.MEDICO;

            case VER_PACIENTES:
            case CREAR_SESION:
            case VER_HISTORIAL_PACIENTE:
                return true;

            default:
                return false;
        }
    }

    // ========================
    // VALIDACIONES PRIVADAS
    // ========================

    private ResultadoValidacion validarUsername(String username) {
        if (TextUtils.isEmpty(username)) {
            return new ResultadoValidacion(false, "Ingresa un nombre de usuario");
        }
        if (username.contains(" ")) {
            return new ResultadoValidacion(false, "El usuario no puede contener espacios");
        }
        if (username.length() < MIN_USERNAME_LENGTH) {
            return new ResultadoValidacion(false,
                    "El usuario debe tener al menos " + MIN_USERNAME_LENGTH + " caracteres");
        }
        if (!username.matches("^[a-zA-Z0-9._-]+$")) {
            return new ResultadoValidacion(false,
                    "Solo letras, números, puntos, guiones y guiones bajos");
        }
        return new ResultadoValidacion(true, "");
    }

    private ResultadoValidacion validarPassword(String password) {
        if (TextUtils.isEmpty(password)) {
            return new ResultadoValidacion(false, "Ingresa una contraseña");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return new ResultadoValidacion(false,
                    "La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        return new ResultadoValidacion(true, "");
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

    private static class ResultadoValidacion {
        public final boolean esValido;
        public final String mensaje;

        public ResultadoValidacion(boolean esValido, String mensaje) {
            this.esValido = esValido;
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
