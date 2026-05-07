package com.example.triviumgor.view;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.example.triviumgor.R;
import com.example.triviumgor.database.DBKeyManager;

public class SplashActivity extends AppCompatActivity {
    private static final int SPLASH_DURATION = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        try {
            DBKeyManager.getInstance(this); // pre-carga / genera passphrase
        } catch (Exception e) {
            Log.e("SplashActivity", "Fallo al inicializar BD segura", e);
            Toast.makeText(this,
                    "Error al inicializar la base de datos. Reinstala la app.",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        }, SPLASH_DURATION);
    }
}