package com.example.triviumgor.view;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.triviumgor.R;
import com.example.triviumgor.database.PacienteDBHelper;
import com.example.triviumgor.database.PacienteDataManager;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvError;
    private SharedPreferences sharedPreferences;
    private PacienteDataManager dataManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE);

        // Inicializar gestor de base de datos
        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Verificar si ya hay una sesión activa
        if (isLoggedIn()) {
            navigateToMain();
            return;
        }

        // Inicializar vistas
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);

        // Configurar el botón de login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
    }

    private void attemptLogin() {
        // Ocultar mensaje de error previo
        tvError.setVisibility(View.GONE);

        // Obtener valores de los campos
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Validar que los campos no estén vacíos
        if (TextUtils.isEmpty(username)) {
            tvError.setText("Por favor, ingresa tu usuario");
            tvError.setVisibility(View.VISIBLE);
            etUsername.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            tvError.setText("Por favor, ingresa tu contraseña");
            tvError.setVisibility(View.VISIBLE);
            etPassword.requestFocus();
            return;
        }

        // Verificar credenciales en la base de datos
        if (dataManager.verificarCredenciales(username, password)) {
            // Obtener información completa del usuario
            Cursor cursor = dataManager.obtenerUsuario(username);

            if (cursor != null && cursor.moveToFirst()) {
                String rol = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_ROL));
                String nombreCompleto = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_NOMBRE_COMPLETO));
                int userId = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_USUARIO_ID));
                cursor.close();

                // Login exitoso - Guardar toda la información
                saveLoginState(true, username, rol, nombreCompleto, userId);
                Toast.makeText(this, "Bienvenido, " + nombreCompleto, Toast.LENGTH_SHORT).show();
                navigateToMain();
            }
        } else {
            // Login fallido
            tvError.setText("Usuario o contraseña incorrectos");
            tvError.setVisibility(View.VISIBLE);
            etPassword.setText(""); // Limpiar contraseña
            etPassword.requestFocus();
        }
    }

    private boolean isLoggedIn() {
        return sharedPreferences.getBoolean("isLoggedIn", false);
    }

    private void saveLoginState(boolean isLoggedIn, String username, String rol,
                                String nombreCompleto, int userId) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", isLoggedIn);
        editor.putString("username", username);
        editor.putString("rol", rol);
        editor.putString("nombreCompleto", nombreCompleto);
        editor.putInt("userId", userId);
        editor.apply();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Deshabilitar el botón de atrás en la pantalla de login
        moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataManager != null) {
            dataManager.close();
        }
    }
}