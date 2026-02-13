package com.example.triviumgor.view;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.triviumgor.R;
import com.example.triviumgor.database.PacienteDBHelper;
import com.example.triviumgor.database.PacienteDataManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

public class AdminUsuariosActivity extends AppCompatActivity {

    private TextInputEditText etNewUsername, etNewPassword, etNombreCompleto;
    private Spinner spinnerRol;
    private Button btnCrearUsuario;
    private ListView listViewUsuarios;
    private PacienteDataManager dataManager;
    
    private List<String> usernamesList; // Para mantener track de los usernames

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 🔒 VERIFICACIÓN DE SEGURIDAD - SOLO ADMIN PUEDE ACCEDER
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        String rolUsuario = prefs.getString("rol", "");
        
        if (!"admin".equals(rolUsuario)) {
            Toast.makeText(this, "⛔ Acceso denegado. Solo administradores pueden acceder.", 
                          Toast.LENGTH_LONG).show();
            finish(); // Cerrar la activity inmediatamente
            return;
        }
        
        setContentView(R.layout.activity_admin_usuarios);
        
        // Configurar título
        setTitle("Administrar Usuarios");
        
        // Inicializar lista de usernames
        usernamesList = new ArrayList<>();

        // Inicializar vistas
        etNewUsername = findViewById(R.id.etNewUsername);
        etNewPassword = findViewById(R.id.etNewPassword);
        etNombreCompleto = findViewById(R.id.etNombreCompleto);
        spinnerRol = findViewById(R.id.spinnerRol);
        btnCrearUsuario = findViewById(R.id.btnCrearUsuario);
        listViewUsuarios = findViewById(R.id.listViewUsuarios);

        // Configurar spinner de roles con emojis
        String[] roles = {
            "👑 admin - Administrador",
            "👨‍⚕️ medico - Médico",
            "👩‍⚕️ enfermero - Enfermero/a",
            "💪 fisioterapeuta - Fisioterapeuta",
            "📋 recepcionista - Recepcionista"
        };
        ArrayAdapter<String> adapterRoles = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, roles);
        adapterRoles.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRol.setAdapter(adapterRoles);

        // Abrir base de datos
        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Cargar usuarios existentes
        cargarUsuarios();

        // Configurar botón crear
        btnCrearUsuario.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                crearNuevoUsuario();
            }
        });
        
        // Configurar click en lista para opciones (activar/desactivar, cambiar contraseña)
        listViewUsuarios.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mostrarOpcionesUsuario(position);
            }
        });
    }

    private void crearNuevoUsuario() {
        String username = etNewUsername.getText().toString().trim();
        String password = etNewPassword.getText().toString().trim();
        String nombreCompleto = etNombreCompleto.getText().toString().trim();
        
        // Obtener rol sin emoji
        String rolSeleccionado = spinnerRol.getSelectedItem().toString();
        String rol = extraerRolDeTexto(rolSeleccionado);

        // ===== VALIDACIONES =====
        
        // Validar username
        if (TextUtils.isEmpty(username)) {
            Toast.makeText(this, "❌ Ingresa un nombre de usuario", Toast.LENGTH_SHORT).show();
            etNewUsername.requestFocus();
            return;
        }
        
        if (username.contains(" ")) {
            Toast.makeText(this, "❌ El usuario no puede contener espacios", Toast.LENGTH_SHORT).show();
            etNewUsername.requestFocus();
            return;
        }
        
        if (username.length() < 4) {
            Toast.makeText(this, "❌ El usuario debe tener al menos 4 caracteres", 
                          Toast.LENGTH_SHORT).show();
            etNewUsername.requestFocus();
            return;
        }

        // Validar password
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, "❌ Ingresa una contraseña", Toast.LENGTH_SHORT).show();
            etNewPassword.requestFocus();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, "❌ La contraseña debe tener al menos 6 caracteres", 
                          Toast.LENGTH_SHORT).show();
            etNewPassword.requestFocus();
            return;
        }

        // Validar nombre completo
        if (TextUtils.isEmpty(nombreCompleto)) {
            Toast.makeText(this, "❌ Ingresa el nombre completo", Toast.LENGTH_SHORT).show();
            etNombreCompleto.requestFocus();
            return;
        }

        // ===== CREAR USUARIO =====
        long result = dataManager.crearUsuario(username, password, nombreCompleto, rol);

        if (result != -1) {
            Toast.makeText(this, "✅ Usuario '" + username + "' creado exitosamente", 
                          Toast.LENGTH_SHORT).show();
            
            // Limpiar campos
            etNewUsername.setText("");
            etNewPassword.setText("");
            etNombreCompleto.setText("");
            spinnerRol.setSelection(0);
            
            // Recargar lista
            cargarUsuarios();
            
            // Hacer scroll al final de la lista
            listViewUsuarios.smoothScrollToPosition(usernamesList.size() - 1);
        } else {
            Toast.makeText(this, "❌ Error: El usuario '" + username + "' ya existe", 
                          Toast.LENGTH_LONG).show();
        }
    }

    private void cargarUsuarios() {
        List<String> listaUsuarios = new ArrayList<>();
        usernamesList.clear();
        
        Cursor cursor = dataManager.obtenerTodosUsuarios();
        
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String username = cursor.getString(
                    cursor.getColumnIndex(PacienteDBHelper.COLUMN_USERNAME));
                String nombreCompleto = cursor.getString(
                    cursor.getColumnIndex(PacienteDBHelper.COLUMN_NOMBRE_COMPLETO));
                String rol = cursor.getString(
                    cursor.getColumnIndex(PacienteDBHelper.COLUMN_ROL));
                int activo = cursor.getInt(
                    cursor.getColumnIndex(PacienteDBHelper.COLUMN_ACTIVO));
                
                // Guardar username para referencia
                usernamesList.add(username);
                
                // Crear texto para mostrar
                String estado = (activo == 1) ? "✓" : "✗";
                String emoji = getEmojiPorRol(rol);
                
                String textoUsuario = estado + " " + emoji + " " + username + "\n" +
                                     "   " + nombreCompleto + " [" + rol + "]";
                
                listaUsuarios.add(textoUsuario);
            } while (cursor.moveToNext());
            
            cursor.close();
        }
        
        if (listaUsuarios.isEmpty()) {
            listaUsuarios.add("No hay usuarios registrados");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, listaUsuarios);
        listViewUsuarios.setAdapter(adapter);
    }
    
    private void mostrarOpcionesUsuario(final int position) {
        if (position >= usernamesList.size()) {
            return; // Por si acaso
        }
        
        final String username = usernamesList.get(position);
        
        // No permitir modificar el usuario actual
        SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
        String usuarioActual = prefs.getString("username", "");
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Opciones para: " + username);
        
        String[] opciones;
        if (username.equals(usuarioActual)) {
            // El usuario actual solo puede cambiar su contraseña
            opciones = new String[]{
                "🔑 Cambiar mi contraseña",
                "❌ Cancelar"
            };
        } else {
            // Otros usuarios pueden ser activados/desactivados
            opciones = new String[]{
                "🔑 Cambiar contraseña",
                "🔄 Activar/Desactivar usuario",
                "❌ Cancelar"
            };
        }
        
        builder.setItems(opciones, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    // Cambiar contraseña
                    mostrarDialogoCambiarPassword(username);
                } else if (which == 1 && !username.equals(usuarioActual)) {
                    // Activar/Desactivar (solo si no es el usuario actual)
                    toggleEstadoUsuario(username);
                }
            }
        });
        
        builder.show();
    }
    
    private void mostrarDialogoCambiarPassword(final String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cambiar contraseña de " + username);
        
        // Crear EditText para la nueva contraseña
        final TextInputEditText etNewPass = new TextInputEditText(this);
        etNewPass.setHint("Nueva contraseña (mín. 6 caracteres)");
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                               android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        
        builder.setView(etNewPass);
        
        builder.setPositiveButton("✓ Cambiar", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newPassword = etNewPass.getText().toString().trim();
                
                if (newPassword.length() < 6) {
                    Toast.makeText(AdminUsuariosActivity.this, 
                                  "❌ La contraseña debe tener al menos 6 caracteres", 
                                  Toast.LENGTH_SHORT).show();
                    return;
                }
                
                if (dataManager.cambiarPassword(username, newPassword)) {
                    Toast.makeText(AdminUsuariosActivity.this, 
                                  "✅ Contraseña cambiada exitosamente", 
                                  Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminUsuariosActivity.this, 
                                  "❌ Error al cambiar la contraseña", 
                                  Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }
    
    private void toggleEstadoUsuario(final String username) {
        // Obtener estado actual
        Cursor cursor = dataManager.obtenerUsuario(username);
        if (cursor != null && cursor.moveToFirst()) {
            int activoActual = cursor.getInt(
                cursor.getColumnIndex(PacienteDBHelper.COLUMN_ACTIVO));
            cursor.close();
            
            final boolean nuevoEstado = (activoActual == 0); // Invertir estado
            String mensaje = nuevoEstado ? "activar" : "desactivar";
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Confirmar");
            builder.setMessage("¿Deseas " + mensaje + " al usuario '" + username + "'?");
            
            builder.setPositiveButton("Sí", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (dataManager.establecerEstadoUsuario(username, nuevoEstado)) {
                        String estado = nuevoEstado ? "activado" : "desactivado";
                        Toast.makeText(AdminUsuariosActivity.this, 
                                      "✅ Usuario " + estado, 
                                      Toast.LENGTH_SHORT).show();
                        cargarUsuarios();
                    } else {
                        Toast.makeText(AdminUsuariosActivity.this, 
                                      "❌ Error al cambiar estado", 
                                      Toast.LENGTH_SHORT).show();
                    }
                }
            });
            
            builder.setNegativeButton("No", null);
            builder.show();
        }
    }
    
    private String extraerRolDeTexto(String textoConEmoji) {
        // "👑 admin - Administrador" -> "admin"
        if (textoConEmoji.contains("admin")) return "admin";
        if (textoConEmoji.contains("medico")) return "medico";
        if (textoConEmoji.contains("enfermero")) return "enfermero";
        if (textoConEmoji.contains("fisioterapeuta")) return "fisioterapeuta";
        if (textoConEmoji.contains("recepcionista")) return "recepcionista";
        return "medico"; // Por defecto
    }
    
    private String getEmojiPorRol(String rol) {
        switch (rol) {
            case "admin": return "👑";
            case "medico": return "👨‍⚕️";
            case "enfermero": return "👩‍⚕️";
            case "fisioterapeuta": return "💪";
            case "recepcionista": return "📋";
            default: return "👤";
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
        // Permitir volver atrás con el botón de la barra
        finish();
        return true;
    }
}
