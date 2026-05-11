package com.example.triviumgor.view;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.os.Build;
import android.os.Bundle;
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
import com.example.triviumgor.util.JwtUtils;
import com.example.triviumgor.util.SecurePrefs;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {



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
        initApp();
        // Primero verificar permisos, luego abrir BD

    }
    //GESTOR DE PERMISOS


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

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

        // Verificar sesión activa. Si el JWT guardado caduco mientras la app
        // estaba cerrada, expulsamos al form sin pasar por MainActivity en
        // lugar de esperar a que la primera request reciba 401 (auto-logout
        // reactivo). Token vacio no se considera expirado: usuarios con
        // sesion offline (login local sin internet) pueden tener
        // isLoggedIn=true sin jwt_token guardado.
        if (usuarioController.haySesionActiva()) {
            SecurePrefs secure = new SecurePrefs(this);
            String token = secure.getAccessToken();
            // Si el access caduco mientras la app estaba cerrada PERO tenemos
            // refresh_token guardado, dejamos pasar a MainActivity: el primer
            // request 401 disparara el flujo de refresh automatico. Solo
            // expulsamos a Login si no hay refresh_token (o token vacio y
            // expirado, es decir sin sesion API recuperable).
            String refreshToken = secure.getRefreshToken();
            boolean accessCaducado = JwtUtils.isExpired(token);
            boolean podemosRefrescar = refreshToken != null && !refreshToken.isEmpty();

            if (accessCaducado && !podemosRefrescar) {
                SharedPreferences prefs = getSharedPreferences("LoginPrefs", MODE_PRIVATE);
                prefs.edit().clear().apply();
                secure.clearTokens();
                ApiClient.logoutEnCurso.set(false);
                Toast.makeText(this,
                        "Sesión caducada, vuelve a iniciar sesión",
                        Toast.LENGTH_LONG).show();
                // Sin return: cae al codigo de inicializar vistas para que
                // el usuario vea el form de login.
            } else {
                navigateToMain();
                return;
            }
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
                    String refreshToken = response.optString("refresh_token", "");

                    // Decodificar el payload del JWT para obtener el rol del
                    // usuario sin tener que llamar a /api/perfil. El payload
                    // está en el segundo segmento, codificado en base64url.
                    String rolDerivado = "USER";
                    try {
                        String[] parts = token.split("\\.");
                        if (parts.length >= 2) {
                            byte[] payloadBytes = android.util.Base64.decode(parts[1],
                                    android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);
                            JSONObject payload = new JSONObject(new String(payloadBytes, "UTF-8"));
                            org.json.JSONArray roles = payload.optJSONArray("roles");
                            if (roles != null) {
                                for (int i = 0; i < roles.length(); i++) {
                                    String r = roles.getString(i);
                                    if ("ROLE_ADMIN".equals(r)) { rolDerivado = "ADMIN"; break; }
                                    if ("ROLE_MEDICO".equals(r)) { rolDerivado = "MEDICO"; }
                                }
                            }
                        }
                    } catch (Exception ignore) {
                        // Si falla el decode mantenemos USER como fallback seguro.
                    }

                    // Tokens (secretos) → almacen cifrado. Resto de campos
                    // (isLoggedIn/username/rol) → SharedPreferences plano.
                    new SecurePrefs(LoginActivity.this).setTokens(token, refreshToken);

                    getSharedPreferences("LoginPrefs", MODE_PRIVATE)
                            .edit()
                            .putBoolean("isLoggedIn", true)
                            .putString("username", username)
                            .putString("rol", rolDerivado)
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
        // Permitir que un futuro 401 vuelva a disparar auto-logout. Cubre
        // tanto el login online como el offline.
        ApiClient.logoutEnCurso.set(false);

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