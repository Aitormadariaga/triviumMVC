package com.example.triviumgor.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.triviumgor.R;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.controller.UsuarioController;
import com.example.triviumgor.network.ApiClient;
import com.example.triviumgor.network.SincronizacionManager;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final int REQUEST_LEGACY_STORAGE = 101;
    private static final int REQUEST_MANAGE_STORAGE = 100;

    private TextInputEditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvError;

    // Controller y DataManager
    private UsuarioController usuarioController;
    private PacienteDataManager dataManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Primero verificar permisos, luego abrir BD
        checkStoragePermissions();
    }
    //GESTOR DE PERMISOS
    private void checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requiere MANAGE_EXTERNAL_STORAGE
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.fromParts("package", getPackageName(), null));
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                } catch (Exception e) {
                    // Fallback si el intent no está disponible en este dispositivo
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                }
            } else {
                initApp();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6–10 requiere permisos en runtime
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_LEGACY_STORAGE);
            } else {
                initApp();
            }
        } else {
            // Android 5 o inferior: permisos concedidos en instalación
            initApp();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && Environment.isExternalStorageManager()) {
                initApp();
            } else {
                Toast.makeText(this,
                        "Se necesita permiso de almacenamiento para continuar",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LEGACY_STORAGE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initApp();
            } else {
                Toast.makeText(this,
                        "Se necesita permiso de almacenamiento para continuar",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
    //INICIALIZAR PROCESO
    private void initApp() {
        // Abrir base de datos
        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Inicializar Controller
        usuarioController = new UsuarioController(this, dataManager);

        // Verificar sesión activa
        if (usuarioController.haySesionActiva()) {
            navigateToMain();
            return;
        }

        // Inicializar vistas
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });
    }

    //LOGIN
    private void attemptLogin() {
        tvError.setVisibility(View.GONE);

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            tvError.setText("Introduce usuario y contraseña");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Conectando...");

        // ── Comprobar internet PRIMERO ──
        if (!SincronizacionManager.hayInternet(this)) {
            // Sin internet → login local directamente
            UsuarioController.ResultadoLogin resultado =
                    usuarioController.login(username, password);

            btnLogin.setEnabled(true);
            btnLogin.setText("Iniciar sesión");

            if (resultado.exitoso) {
                Toast.makeText(this,
                        "Sin conexión — modo offline",
                        Toast.LENGTH_SHORT).show();
                navigateToMain();
            } else {
                mostrarError("Usuario o contraseña incorrectos");
            }
            return;
        }

        // ── Con internet → login contra la API ──
        ApiClient apiClient = new ApiClient(this);

        apiClient.login(username, password, new ApiClient.ApiCallback() {

            @Override
            public void onSuccess(JSONObject response) {
                try {
                    String token = response.getString("token");

                    // Guardar token en SharedPreferences
                    getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("username", username)
                            .putString("jwt_token", token)
                            .apply();

                    // También hacer login local para mantener la sesión offline
                    usuarioController.login(username, password);

                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Iniciar sesión");
                        Toast.makeText(LoginActivity.this,
                                "Bienvenido, " + username,
                                Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Iniciar sesión");
                        mostrarError("Error al procesar la respuesta");
                    });
                }
            }

            @Override
            public void onError(String mensaje) {
                runOnUiThread(() -> {
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Iniciar sesión");

                    // Hay internet pero falló la API
                    // → las credenciales son incorrectas o el servidor está caído
                    if (mensaje.equals("Sin conexión al servidor")) {
                        // Servidor caído → intentar login local
                        UsuarioController.ResultadoLogin resultado =
                                usuarioController.login(username, password);

                        if (resultado.exitoso) {
                            Toast.makeText(LoginActivity.this,
                                    "Servidor no disponible — modo offline",
                                    Toast.LENGTH_SHORT).show();
                            navigateToMain();
                        } else {
                            mostrarError("Servidor no disponible y sin sesión local");
                        }
                    } else {
                        // Credenciales incorrectas → mostrar error directamente
                        // NO intentar login local
                        mostrarError(mensaje);
                    }
                });
            }
        });
    }

    private void mostrarError(String mensaje) {
        tvError.setText(mensaje);
        tvError.setVisibility(View.VISIBLE);
        etPassword.setText("");
        etPassword.requestFocus();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
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