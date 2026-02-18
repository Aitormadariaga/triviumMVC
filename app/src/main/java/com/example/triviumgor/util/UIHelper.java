package com.example.triviumgor.util;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Button;

import androidx.core.view.ViewCompat;

/**
 * Helper de UI para gestión de colores de botones.
 * Usa ViewCompat para compatibilidad con API 19+.
 * Colores alineados con la paleta M3 Azul Médico.
 */
public class UIHelper {

    // Colores semánticos — alineados con colors.xml
    public static final int COLOR_CONECTANDO    = 0xFFF57F17;  // Ámbar (warning)
    public static final int COLOR_CONECTADO     = 0xFF2E7D32;  // Verde (success)
    public static final int COLOR_SESION_ACTIVA = 0xFF1565C0;  // Azul (primary)
    public static final int COLOR_SESION_PARADA = 0xFFBA1A1A;  // Rojo (error)
    public static final int COLOR_DESHABILITADO = 0xFFC3C6CF;  // Gris (outlineVariant)
    public static final int COLOR_DEFAULT       = 0xFF1565C0;  // Azul (primary)

    /**
     * Cambia el color de fondo de un botón sin destruir el drawable Material 3.
     */
    public static void setButtonColor(Button btn, int color) {
        ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(color));
        // Texto blanco excepto para deshabilitado
        if (color == COLOR_DESHABILITADO) {
            btn.setTextColor(0xFF43474E); // onSurfaceVariant
        } else {
            btn.setTextColor(Color.WHITE);
        }
    }

    /**
     * Restaura un botón al color por defecto del tema con texto blanco.
     */
    public static void resetButtonToDefault(Button btn) {
        ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(COLOR_DEFAULT));
        btn.setTextColor(Color.WHITE);
    }
}