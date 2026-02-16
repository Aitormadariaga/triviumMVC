package com.example.triviumgor.view;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.triviumgor.R;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.controller.UsuarioController;
import com.google.android.material.textfield.TextInputEditText;

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

    private void attemptLogin() {
        tvError.setVisibility(View.GONE);

        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        UsuarioController.ResultadoLogin resultado = usuarioController.login(username, password);

        if (resultado.exitoso) {
            Toast.makeText(this, resultado.mensaje, Toast.LENGTH_SHORT).show();
            navigateToMain();
        } else {
            tvError.setText(resultado.mensaje);
            tvError.setVisibility(View.VISIBLE);
            etPassword.setText("");
            etPassword.requestFocus();
        }
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