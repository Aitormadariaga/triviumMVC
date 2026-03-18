package com.example.triviumgor.network;

import org.json.JSONArray;

public interface SincronizacionListener {
    void onCompletado(int sincronizados, int conflictos);
    void onConflictos(JSONArray conflictos);
    void onEliminacionesRechazadas(JSONArray rechazados);
    void onError(String mensaje);
}
