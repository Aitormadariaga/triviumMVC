package com.example.triviumgor.controller;

import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Sesion;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controlador de Sesiones.
 * Encapsula la lógica de registro, consulta, eliminación y agrupación de sesiones.
 * NO contiene ninguna referencia a Views ni Android UI.
 */
public class SesionController {

    private static final SimpleDateFormat FORMATO_DB =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat FORMATO_FECHA =
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private static final SimpleDateFormat FORMATO_HORA =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    /**
     * Representa un grupo de sesiones agrupadas por día.
     */
    public static class GrupoDia {
        public final String fechaFormateada; // "dd/MM/yyyy"
        public final int indiceInicio;       // índice en la lista original
        public final int indiceFin;          // índice fin (exclusivo) para subList()

        public GrupoDia(String fechaFormateada, int indiceInicio, int indiceFin) {
            this.fechaFormateada = fechaFormateada;
            this.indiceInicio = indiceInicio;
            this.indiceFin = indiceFin;
        }
    }

    private final PacienteDataManager dataManager;

    public SesionController(PacienteDataManager dataManager) {
        this.dataManager = dataManager;
    }

    // ========================
    // REGISTRO
    // ========================

    /**
     * Registra una nueva sesión de tratamiento.
     * @return ID de la sesión creada, o -1 si hubo error
     */
    public long registrarSesion(int idPaciente, String dispositivo, int intensidad, int tiempo) {
        return dataManager.registrarSesion(idPaciente, dispositivo, intensidad, tiempo);
    }

    // ========================
    // CONSULTAS
    // ========================

    /**
     * Obtiene todas las sesiones de un paciente (ordenadas por fecha DESC).
     */
    public List<Sesion> obtenerSesionesPaciente(int idPaciente) {
        return dataManager.obtenerSesionesPaciente(idPaciente);
    }

    /**
     * Obtiene una sesión por su ID.
     */
    public Sesion obtenerSesion(int idSesion) {
        return dataManager.obtenerSesion(idSesion);
    }

    // ========================
    // ELIMINACIÓN
    // ========================

    /**
     * Elimina una sesión por su ID.
     */
    public boolean eliminarSesion(int idSesion) {
        return dataManager.eliminarSesion(idSesion);
    }

    // ========================
    // AGRUPACIÓN POR DÍA
    // ========================

    /**
     * Agrupa una lista de sesiones por día.
     * Reemplaza la lógica que estaba inline en HistorialSesionesActivity.agruparPorDiaLista().
     *
     * @param sesiones lista de sesiones (ya ordenada por fecha DESC desde la DB)
     * @return lista de GrupoDia con fecha formateada e índices para subList()
     */
    public List<GrupoDia> agruparPorDia(List<Sesion> sesiones) {
        List<GrupoDia> grupos = new ArrayList<>();

        if (sesiones == null || sesiones.isEmpty()) {
            return grupos;
        }

        if (sesiones.size() == 1) {
            String fecha = formatearFecha(sesiones.get(0).getFecha());
            grupos.add(new GrupoDia(fecha, 0, 1));
            return grupos;
        }

        int inicioGrupo = 0;
        String fechaGrupoActual = formatearFecha(sesiones.get(0).getFecha());

        for (int i = 1; i < sesiones.size(); i++) {
            String fechaActual = formatearFecha(sesiones.get(i).getFecha());

            if (!fechaActual.equals(fechaGrupoActual)) {
                // Cerrar grupo anterior
                grupos.add(new GrupoDia(fechaGrupoActual, inicioGrupo, i));
                // Abrir nuevo grupo
                inicioGrupo = i;
                fechaGrupoActual = fechaActual;
            }
        }

        // Cerrar último grupo
        grupos.add(new GrupoDia(fechaGrupoActual, inicioGrupo, sesiones.size()));

        return grupos;
    }

    /**
     * Obtiene la lista de fechas formateadas para mostrar en ListView.
     */
    public List<String> obtenerFechasAgrupadas(List<GrupoDia> grupos) {
        List<String> fechas = new ArrayList<>();
        for (GrupoDia grupo : grupos) {
            fechas.add(grupo.fechaFormateada);
        }
        return fechas;
    }

    // ========================
    // FORMATEO
    // ========================

    /**
     * Formatea fecha de DB ("yyyy-MM-dd HH:mm:ss") a "dd/MM/yyyy".
     */
    public String formatearFecha(String fechaDB) {
        try {
            Date fecha = FORMATO_DB.parse(fechaDB);
            return FORMATO_FECHA.format(fecha);
        } catch (ParseException e) {
            return fechaDB;
        }
    }

    /**
     * Extrae la hora de una fecha DB ("yyyy-MM-dd HH:mm:ss") a "HH:mm:ss".
     */
    public String formatearHora(String fechaDB) {
        try {
            Date fecha = FORMATO_DB.parse(fechaDB);
            return FORMATO_HORA.format(fecha);
        } catch (ParseException e) {
            return "--:--:--";
        }
    }
}