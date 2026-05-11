package com.example.triviumgor.view;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.triviumgor.R;
import com.example.triviumgor.controller.UsuarioController;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Usuario;
import com.example.triviumgor.network.ApiClient;
import com.example.triviumgor.network.SincronizacionManager;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel admin para gestionar usuarios. Patron A1 cliente-delgado: todas las
 * operaciones CRUD van por ApiClient → servidor. La BD local SQLite es solo
 * cache. Sin internet → boton "Crear" deshabilitado, dialogos de cambio de
 * password / activacion bloqueados.
 *
 * Tras cualquier operacion OK se refresca la lista desde el servidor y se
 * actualiza el cache local (upsert por username, sin tocar password_hash).
 */
public class AdminUsuariosActivity extends AppCompatActivity {

    private static final String TAG = "AdminUsuariosActivity";

    private TextInputEditText etNewUsername, etNewPassword, etNombreCompleto;
    private Spinner spinnerRol;
    private Button btnCrearUsuario;
    private RecyclerView recyclerViewUsuarios;
    private UsuarioAdapter usuarioAdapter;

    private UsuarioController usuarioController;
    private PacienteDataManager dataManager;
    private ApiClient apiClient;
    private List<Usuario> usuariosList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        usuarioController = new UsuarioController(this, dataManager);
        apiClient = new ApiClient(this);

        if (!usuarioController.esAdmin()) {
            Toast.makeText(this, "Acceso denegado. Solo administradores.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_usuarios);
        setTitle("Administrar Usuarios");

        usuariosList = new ArrayList<>();

        etNewUsername = findViewById(R.id.etNewUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etNombreCompleto = findViewById(R.id.etNombreCompleto);
        spinnerRol = findViewById(R.id.spinnerRol);
        btnCrearUsuario = findViewById(R.id.btnCrearUsuario);
        recyclerViewUsuarios = findViewById(R.id.recyclerViewUsuarios);

        recyclerViewUsuarios.setLayoutManager(new LinearLayoutManager(this));
        usuarioAdapter = new UsuarioAdapter(usuariosList, this::mostrarOpcionesUsuario);
        recyclerViewUsuarios.setAdapter(usuarioAdapter);

        configurarSpinnerRoles();

        // Carga inicial. Si hay red, lista fresca del server (actualiza cache).
        // Si no, lista de cache local.
        cargarUsuarios();

        btnCrearUsuario.setOnClickListener(v -> crearNuevoUsuario());

        // Reflejar estado de red en la UI al arrancar.
        actualizarUiSegunConexion();
    }

    /**
     * Habilita / deshabilita los controles de escritura segun haya internet.
     * El listado siempre se muestra (con datos cache si no hay red).
     */
    private void actualizarUiSegunConexion() {
        boolean conectado = SincronizacionManager.hayInternet(this);
        btnCrearUsuario.setEnabled(conectado);
        if (!conectado) {
            btnCrearUsuario.setText("Sin conexión");
        } else {
            btnCrearUsuario.setText("Crear usuario");
        }
    }

    private void configurarSpinnerRoles() {
        String[] roles = UsuarioController.Rol.getTextosTodos();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRol.setAdapter(adapter);
    }

    // ============================================
    // Crear
    // ============================================

    private void crearNuevoUsuario() {
        if (!SincronizacionManager.hayInternet(this)) {
            Toast.makeText(this, "Necesitas conexión para crear usuarios",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String username = etNewUsername.getText().toString().trim();
        String password = etNewPassword.getText().toString().trim();
        String nombreCompleto = etNombreCompleto.getText().toString().trim();

        String rolTexto = spinnerRol.getSelectedItem().toString();
        UsuarioController.Rol rol = UsuarioController.Rol.fromTextoFormateado(rolTexto);

        // Validaciones basicas en cliente (el server las repite — son la
        // ultima palabra). Aqui solo evitamos hacer un POST garantizado a
        // fallar para no malgastar red.
        if (username.length() < 4) {
            Toast.makeText(this, "Usuario: mínimo 4 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "Contraseña: mínimo 6 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nombreCompleto.isEmpty()) {
            Toast.makeText(this, "Nombre obligatorio", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCrearUsuario.setEnabled(false);
        apiClient.crearUsuario(username, password, nombreCompleto, rol.getCodigo(),
                new ApiClient.ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject response) {
                        runOnUiThread(() -> {
                            Toast.makeText(AdminUsuariosActivity.this,
                                    "Usuario creado", Toast.LENGTH_SHORT).show();
                            etNewUsername.setText("");
                            etNewPassword.setText("");
                            etNombreCompleto.setText("");
                            spinnerRol.setSelection(0);
                            btnCrearUsuario.setEnabled(true);
                            cargarUsuarios();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            btnCrearUsuario.setEnabled(true);
                            // El server devuelve 409 con {"error":"username_existe"}
                            // que ApiClient.parsearError convierte a "El usuario ya existe".
                            Toast.makeText(AdminUsuariosActivity.this,
                                    mensaje, Toast.LENGTH_LONG).show();
                            // Refrescar lista aunque haya error: cubre el caso en
                            // que el POST tardo mas que el timeout del cliente
                            // pero el server alcanzo a persistir antes. El usuario
                            // habria visto Toast de error y un usuario "fantasma"
                            // creado server-side, ahora visible en la lista.
                            cargarUsuarios();
                        });
                    }
                });
    }

    // ============================================
    // Listar (refresca server + cache local)
    // ============================================

    private void cargarUsuarios() {
        // Si hay red, refrescar desde server y actualizar cache. Si no hay,
        // simplemente leer del cache local — sigue mostrando la ultima lista
        // descargada.
        if (SincronizacionManager.hayInternet(this)) {
            apiClient.getUsuarios(new ApiClient.ApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        JSONArray arr = response.getJSONArray("usuarios");
                        List<Usuario> lista = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject u = arr.getJSONObject(i);
                            int id = u.getInt("id");
                            String username = u.getString("username");
                            String nombre = u.optString("nombre", username);
                            String rol = u.optString("rol", "usuario");
                            int activo = u.optBoolean("activo", true) ? 1 : 0;
                            String fechaCreacion = u.optString("fechaCreacion", null);
                            String ultimoAcceso = u.isNull("ultimoAcceso")
                                    ? null : u.optString("ultimoAcceso", null);

                            // Upsert en cache local. Sin esto los cambios de la
                            // web (creaciones, desactivaciones) no se reflejarian
                            // en SQLite — un usuario desactivado seguiria pudiendo
                            // hacer login offline en esta tablet hasta el proximo
                            // login online suyo. password_hash NO se toca: queda
                            // el del ultimo login online en este dispositivo.
                            dataManager.upsertUsuarioDesdeServer(
                                    username, nombre, rol, activo, fechaCreacion, ultimoAcceso);

                            // El campo hash del objeto in-memory no se usa en la
                            // UI; pasamos "" como placeholder, no contamina SQLite.
                            lista.add(new Usuario(id, username, "", nombre, rol,
                                    activo, fechaCreacion, ultimoAcceso));
                        }
                        runOnUiThread(() -> {
                            usuariosList = lista;
                            usuarioAdapter.actualizarLista(usuariosList);
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Parse /api/usuarios fallido", e);
                        runOnUiThread(() -> cargarUsuariosDesdeCache());
                    }
                }

                @Override
                public void onError(String mensaje) {
                    Log.w(TAG, "GET /api/usuarios fallo: " + mensaje);
                    runOnUiThread(() -> cargarUsuariosDesdeCache());
                }
            });
        } else {
            cargarUsuariosDesdeCache();
        }
    }

    private void cargarUsuariosDesdeCache() {
        usuariosList = usuarioController.obtenerTodosLosUsuarios();
        usuarioAdapter.actualizarLista(usuariosList);
    }

    // ============================================
    // Opciones por usuario
    // ============================================

    private void mostrarOpcionesUsuario(final int position) {
        if (position >= usuariosList.size()) return;

        final Usuario usuario = usuariosList.get(position);
        String usuarioActual = usuarioController.getUsernameActual();

        if (!SincronizacionManager.hayInternet(this)) {
            Toast.makeText(this, "Necesitas conexión para gestionar usuarios",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones para: " + usuario.getUsername());

        boolean esElMismo = usuario.getUsername().equals(usuarioActual);
        String[] opciones = esElMismo
                ? new String[]{"Cambiar mi contraseña", "Cancelar"}
                : new String[]{"Cambiar contraseña", "Activar/Desactivar usuario", "Cancelar"};

        builder.setItems(opciones, (dialog, which) -> {
            if (which == 0) {
                if (esElMismo) {
                    mostrarDialogoAutocambioPassword();
                } else {
                    mostrarDialogoCambiarPassword(usuario);
                }
            } else if (which == 1 && !esElMismo) {
                toggleEstadoUsuario(usuario);
            }
        });

        builder.show();
    }

    /**
     * Auto-cambio: requiere la password ACTUAL como prueba de identidad.
     * Va por /api/cambiar-password (distinto al endpoint admin).
     */
    private void mostrarDialogoAutocambioPassword() {
        View view = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
        final TextInputEditText etActual = new TextInputEditText(this);
        etActual.setHint("Contraseña actual");
        etActual.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final TextInputEditText etNueva = new TextInputEditText(this);
        etNueva.setHint("Nueva contraseña (mín. 6 caracteres)");
        etNueva.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(etActual);
        layout.addView(etNueva);

        new AlertDialog.Builder(this)
                .setTitle("Cambiar mi contraseña")
                .setView(layout)
                .setPositiveButton("Cambiar", (d, w) -> {
                    String actual = etActual.getText().toString();
                    String nueva = etNueva.getText().toString();
                    if (nueva.length() < 6) {
                        Toast.makeText(this, "Mínimo 6 caracteres",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    apiClient.cambiarPassword(actual, nueva, new ApiClient.ApiCallback() {
                        @Override
                        public void onSuccess(JSONObject response) {
                            runOnUiThread(() -> Toast.makeText(AdminUsuariosActivity.this,
                                    "Contraseña cambiada", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onError(String mensaje) {
                            runOnUiThread(() -> Toast.makeText(AdminUsuariosActivity.this,
                                    mensaje, Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarDialogoCambiarPassword(final Usuario usuario) {
        final TextInputEditText etNewPass = new TextInputEditText(this);
        etNewPass.setHint("Nueva contraseña (mín. 6 caracteres)");
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Cambiar contraseña de " + usuario.getUsername())
                .setView(etNewPass)
                .setPositiveButton("Cambiar", (d, w) -> {
                    String newPassword = etNewPass.getText().toString().trim();
                    if (newPassword.length() < 6) {
                        Toast.makeText(this, "Mínimo 6 caracteres",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    apiClient.cambiarPasswordUsuario(usuario.getId(), newPassword,
                            new ApiClient.ApiCallback() {
                                @Override
                                public void onSuccess(JSONObject response) {
                                    runOnUiThread(() -> Toast.makeText(
                                            AdminUsuariosActivity.this,
                                            "Contraseña cambiada",
                                            Toast.LENGTH_SHORT).show());
                                }
                                @Override
                                public void onError(String mensaje) {
                                    runOnUiThread(() -> Toast.makeText(
                                            AdminUsuariosActivity.this,
                                            mensaje, Toast.LENGTH_LONG).show());
                                }
                            });
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toggleEstadoUsuario(final Usuario usuario) {
        final boolean nuevoEstado = (usuario.getActivo() == 0);
        String accion = nuevoEstado ? "activar" : "desactivar";

        new AlertDialog.Builder(this)
                .setTitle("Confirmar")
                .setMessage("¿Deseas " + accion + " al usuario '" +
                        usuario.getUsername() + "'?")
                .setPositiveButton("Sí", (dialog, which) ->
                        apiClient.setActivoUsuario(usuario.getId(), nuevoEstado,
                                new ApiClient.ApiCallback() {
                                    @Override
                                    public void onSuccess(JSONObject response) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(AdminUsuariosActivity.this,
                                                    "Usuario " + (nuevoEstado ? "activado" : "desactivado"),
                                                    Toast.LENGTH_SHORT).show();
                                            cargarUsuarios();
                                        });
                                    }
                                    @Override
                                    public void onError(String mensaje) {
                                        runOnUiThread(() -> Toast.makeText(
                                                AdminUsuariosActivity.this,
                                                mensaje, Toast.LENGTH_LONG).show());
                                    }
                                }))
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Actualizar UI de conexion por si cambio mientras estaba en otra
        // activity. Y refrescar la lista para reflejar cambios hechos en
        // la web por otro admin desde fuera de esta activity. Tambien
        // recupera creaciones cuyo POST cliente vio como fallo (p.ej.
        // por timeout local) pero que el server si persistio.
        actualizarUiSegunConexion();
        if (usuariosList != null) {
            cargarUsuarios();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataManager != null) {
            dataManager.close();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
