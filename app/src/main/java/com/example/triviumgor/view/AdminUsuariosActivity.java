package com.example.triviumgor.view;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AdminUsuariosActivity extends AppCompatActivity {

    private TextInputEditText etNewUsername, etNewPassword, etNombreCompleto;
    private Spinner spinnerRol;
    private Button btnCrearUsuario;
    private RecyclerView recyclerViewUsuarios;
    private UsuarioAdapter usuarioAdapter;

    // Controller y datos
    private UsuarioController usuarioController;
    private PacienteDataManager dataManager;
    private List<Usuario> usuariosList;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inicializar DataManager
        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Inicializar Controller
        usuarioController = new UsuarioController(this, dataManager);

        // VERIFICACIÓN DE SEGURIDAD - SOLO ADMIN PUEDE ACCEDER
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        String rolUsuario = prefs.getString("rol", "");

        if (!usuarioController.esAdmin()) {
            Toast.makeText(this, "Acceso denegado. Solo administradores.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(R.layout.activity_admin_usuarios);

        // Configurar título
        setTitle("Administrar Usuarios");

        // Inicializar lista de usuarios
        usuariosList = new ArrayList<>();

        // Inicializar vistas
        etNewUsername = findViewById(R.id.etNewUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etNombreCompleto = findViewById(R.id.etNombreCompleto);
        spinnerRol = findViewById(R.id.spinnerRol);
        btnCrearUsuario = findViewById(R.id.btnCrearUsuario);
        recyclerViewUsuarios = findViewById(R.id.recyclerViewUsuarios);

        // Configurar RecyclerView
        recyclerViewUsuarios.setLayoutManager(new LinearLayoutManager(this));
        usuarioAdapter = new UsuarioAdapter(usuariosList, new UsuarioAdapter.OnUsuarioClickListener() {
            @Override
            public void onUsuarioClick(int position) {
                mostrarOpcionesUsuario(position);
            }
        });
        recyclerViewUsuarios.setAdapter(usuarioAdapter);

        // Configurar spinner
        configurarSpinnerRoles();

        // Cargar usuarios existentes
        cargarUsuarios();

        // Configurar botón crear
        btnCrearUsuario.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crearNuevoUsuario();
            }
        });
    }

    /**
     * Configurar spinner usando métodos estáticos del controller
     */
    private void configurarSpinnerRoles() {
        String[] roles = UsuarioController.Rol.getTextosTodos();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRol.setAdapter(adapter);
    }

    private void crearNuevoUsuario() {
        String username = etNewUsername.getText().toString().trim();
        String password = etNewPassword.getText().toString().trim();
        String nombreCompleto = etNombreCompleto.getText().toString().trim();

        // Extraer rol usando método del controller
        String rolTexto = spinnerRol.getSelectedItem().toString();
        UsuarioController.Rol rol = UsuarioController.Rol.fromTextoFormateado(rolTexto);

        // TODAS las validaciones las hace el controller
        UsuarioController.ResultadoOperacion resultado =
                usuarioController.crearUsuario(username, password, nombreCompleto, rol);

        if (resultado.exitoso) {
            Toast.makeText(this, resultado.mensaje, Toast.LENGTH_SHORT).show();

            // Limpiar formulario
            etNewUsername.setText("");
            etNewPassword.setText("");
            etNombreCompleto.setText("");
            spinnerRol.setSelection(0);

            // Recargar lista
            cargarUsuarios();
            recyclerViewUsuarios.smoothScrollToPosition(
                    usuarioAdapter.getItemCount() > 0 ? usuarioAdapter.getItemCount() - 1 : 0);
        } else {
            Toast.makeText(this, resultado.mensaje, Toast.LENGTH_LONG).show();
        }
    }

    private void cargarUsuarios() {
        // Obtener usuarios del controller
        usuariosList = usuarioController.obtenerTodosLosUsuarios();
        usuarioAdapter.actualizarLista(usuariosList);
    }


    /**
     * Mostrar opciones para gestionar usuario
     */
    private void mostrarOpcionesUsuario(final int position) {
        if (position >= usuariosList.size()) return;

        final Usuario usuario = usuariosList.get(position);
        String usuarioActual = usuarioController.getUsernameActual();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones para: " + usuario.getUsername());

        String[] opciones;
        if (usuario.getUsername().equals(usuarioActual)) {
            opciones = new String[]{
                    "Cambiar mi contraseña",
                    "Cancelar"
            };
        } else {
            opciones = new String[]{
                    "Cambiar contraseña",
                    "Activar/Desactivar usuario",
                    "Cancelar"
            };
        }

        builder.setItems(opciones, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    mostrarDialogoCambiarPassword(usuario);
                } else if (which == 1 && !usuario.getUsername().equals(usuarioActual)) {
                    toggleEstadoUsuario(usuario);
                }
            }
        });

        builder.show();
    }

    private void mostrarDialogoCambiarPassword(final Usuario usuario) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cambiar contraseña de " + usuario.getUsername());

        final TextInputEditText etNewPass = new TextInputEditText(this);
        etNewPass.setHint("Nueva contraseña (mín. 6 caracteres)");
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        builder.setView(etNewPass);

        builder.setPositiveButton("Cambiar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newPassword = etNewPass.getText().toString().trim();

                UsuarioController.ResultadoOperacion resultado =
                        usuarioController.cambiarPassword(usuario.getUsername(), newPassword);

                Toast.makeText(AdminUsuariosActivity.this, resultado.mensaje, Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void toggleEstadoUsuario(final Usuario usuario) {

        final boolean nuevoEstado = (usuario.getActivo() == 0);
        String accion = nuevoEstado ? "activar" : "desactivar";

        new AlertDialog.Builder(this)
                .setTitle("Confirmar")
                .setMessage("¿Deseas " + accion + " al usuario '" + usuario.getUsername() + "'?")
                .setPositiveButton("Sí", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UsuarioController.ResultadoOperacion resultado =
                                usuarioController.toggleEstadoUsuario(usuario.getUsername(), nuevoEstado);

                        if (resultado.exitoso) {
                            Toast.makeText(AdminUsuariosActivity.this,
                                    resultado.mensaje,
                                    Toast.LENGTH_SHORT).show();
                            cargarUsuarios();
                        } else {
                            Toast.makeText(AdminUsuariosActivity.this,
                                    resultado.mensaje,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("No", null)
                .show();
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