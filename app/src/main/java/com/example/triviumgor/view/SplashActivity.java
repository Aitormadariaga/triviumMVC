package com.example.triviumgor.view;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

import com.example.triviumgor.R;

public class SplashActivity extends AppCompatActivity {
    // Duración del splash en milisegundos (3 segundos)
    private static final int SPLASH_DURATION = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Handler para mostrar el splash y luego ir a MainActivity
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // Iniciar MainActivity
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);
                finish(); // Cerrar SplashActivity
            }
        }, SPLASH_DURATION);
    }
}