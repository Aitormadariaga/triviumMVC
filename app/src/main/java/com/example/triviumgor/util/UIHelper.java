package com.example.triviumgor.util;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.Button;

import androidx.core.view.ViewCompat;

/**
 * Helper de UI para gestión de colores de botones.
 * Usa ViewCompat para compatibilidad con API 19+.
 */
public class UIHelper {

    // Colores semánticos
    public static final int COLOR_CONECTANDO    = 0xFFFF5733;  // Naranja
    public static final int COLOR_CONECTADO     = Color.YELLOW;
    public static final int COLOR_SESION_ACTIVA = Color.GREEN;
    public static final int COLOR_SESION_PARADA = Color.RED;
    public static final int COLOR_DESHABILITADO = Color.LTGRAY;
    public static final int COLOR_DEFAULT       = Color.parseColor("#6750A4"); // Material 3 primary

    /**
     * Cambia el color de fondo de un botón sin destruir el drawable Material 3.
     */
    public static void setButtonColor(Button btn, int color) {
        ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(color));
    }

    /**
     * Restaura un botón al color por defecto del tema (#6750A4) con texto blanco.
     */
    public static void resetButtonToDefault(Button btn) {
        ViewCompat.setBackgroundTintList(btn, ColorStateList.valueOf(COLOR_DEFAULT));
        btn.setTextColor(Color.WHITE);
    }
}