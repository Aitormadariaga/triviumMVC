package com.example.triviumgor.controller;

import android.database.Cursor;

import com.example.triviumgor.database.PacienteDBHelper;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Paciente;

import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de Pacientes.
 * Encapsula toda la lógica de negocio: validación, CRUD y búsqueda.
 * NO contiene ninguna referencia a Views ni Android UI.
 */
public class PacienteController {

    // Resultado de operaciones para comunicar a la Vista
    public static class Resultado {
        public final boolean exito;
        public final String mensaje;
        public final long id; // ID del registro afectado (-1 si no aplica)

        private Resultado(boolean exito, String mensaje, long id) {
            this.exito = exito;
            this.mensaje = mensaje;
            this.id = id;
        }

        public static Resultado ok(String mensaje) {
            return new Resultado(true, mensaje, -1);
        }

        public static Resultado ok(String mensaje, long id) {
            return new Resultado(true, mensaje, id);
        }

        public static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, -1);
        }
    }

    private final PacienteDataManager dataManager;

    public PacienteController(PacienteDataManager dataManager) {
        this.dataManager = dataManager;
    }

    // ========================
    // VALIDACIÓN
    // ========================

    /**
     * Valida que los campos obligatorios no estén vacíos.
     */
    public Resultado validarCamposObligatorios(String dni, String nombre, String apellido1) {
        if (dni == null || dni.trim().isEmpty()) {
            return Resultado.error("El DNI es obligatorio");
        }
        if (nombre == null || nombre.trim().isEmpty()) {
            return Resultado.error("El nombre es obligatorio");
        }
        if (apellido1 == null || apellido1.trim().isEmpty()) {
            return Resultado.error("El primer apellido es obligatorio");
        }
        return Resultado.ok("Campos válidos");
    }

    /**
     * Valida el formato del DNI (8 números + 1 letra).
     */
    public Resultado validarDNI(String dni) {
        if (dni == null || dni.length() != 9) {
            return Resultado.error("El DNI debe tener 8 números y 1 letra");
        }

        for (int i = 0; i < 8; i++) {
            if (!Character.isDigit(dni.charAt(i))) {
                return Resultado.error("Formato de DNI incorrecto (8 números + 1 letra)");
            }
        }

        if (!Character.isLetter(dni.charAt(8))) {
            return Resultado.error("Formato de DNI incorrecto (8 números + 1 letra)");
        }

        return Resultado.ok("DNI válido");
    }

    /**
     * Comprueba si ya existe un paciente con ese DNI en la base de datos.
     * @param dni DNI a comprobar
     * @param excluirId ID del paciente a excluir (para edición), -1 si es nuevo
     */
    public boolean existeDNI(String dni, int excluirId) {
        List<Paciente> todos = obtenerTodosPacientes();
        for (Paciente p : todos) {
            if (p.getDNI().equalsIgnoreCase(dni) && p.getID() != excluirId) {
                return true;
            }
        }
        return false;
    }

    // ========================
    // CRUD
    // ========================

    /**
     * Guarda un paciente nuevo validando todos los campos.
     */
    public Resultado guardarPaciente(String dni, String nombre, String apellido1, String apellido2,
                                     String patologia, String medicacion,
                                     String intensidadStr, String tiempoStr, String cic) {
        // Validar campos obligatorios
        Resultado validacion = validarCamposObligatorios(dni, nombre, apellido1);
        if (!validacion.exito) return validacion;

        // Validar formato DNI
        validacion = validarDNI(dni);
        if (!validacion.exito) return validacion;

        // Comprobar DNI duplicado
        if (existeDNI(dni, -1)) {
            return Resultado.error("Ya existe un paciente con ese DNI");
        }

        // Parsear intensidad y tiempo
        int intensidad, tiempo;
        try {
            intensidad = Integer.parseInt(intensidadStr.trim());
            tiempo = Integer.parseInt(tiempoStr.trim());
        } catch (NumberFormatException e) {
            return Resultado.error("La intensidad y el tiempo deben ser números");
        }

        long resultado = dataManager.nuevoPaciente(dni, nombre, apellido1, apellido2,
                patologia, medicacion, intensidad, tiempo, cic);

        if (resultado != -1) {
            return Resultado.ok("Paciente guardado correctamente", resultado);
        } else {
            return Resultado.error("Error al guardar el paciente");
        }
    }

    /**
     * Guarda un paciente nuevo con datos para 2 dispositivos.
     */
    public Resultado guardarPaciente2disp(String dni, String nombre, String apellido1, String apellido2,
                                          String patologia, String medicacion,
                                          String intensidadStr, String tiempoStr,
                                          String intensidadStr2, String tiempoStr2, String cic) {
        // Validar campos obligatorios
        Resultado validacion = validarCamposObligatorios(dni, nombre, apellido1);
        if (!validacion.exito) return validacion;

        // Validar formato DNI
        validacion = validarDNI(dni);
        if (!validacion.exito) return validacion;

        // Comprobar DNI duplicado
        if (existeDNI(dni, -1)) {
            return Resultado.error("Ya existe un paciente con ese DNI");
        }

        int intensidad, tiempo, intensidad2, tiempo2;
        try {
            intensidad = Integer.parseInt(intensidadStr.trim());
            tiempo = Integer.parseInt(tiempoStr.trim());
            intensidad2 = Integer.parseInt(intensidadStr2.trim());
            tiempo2 = Integer.parseInt(tiempoStr2.trim());
        } catch (NumberFormatException e) {
            return Resultado.error("La intensidad y el tiempo deben ser números");
        }

        long resultado = dataManager.nuevoPaciente2disp(dni, nombre, apellido1, apellido2,
                patologia, medicacion, intensidad, tiempo, intensidad2, tiempo2, cic);

        if (resultado != -1) {
            return Resultado.ok("Paciente guardado correctamente", resultado);
        } else {
            return Resultado.error("Error al guardar el paciente");
        }
    }

    /**
     * Actualiza un paciente existente.
     */
    public Resultado actualizarPaciente(int id, String dni, String nombre, String apellido1,
                                        String apellido2, String patologia, String medicacion,
                                        String intensidadStr, String tiempoStr, String cic) {
        Resultado validacion = validarCamposObligatorios(dni, nombre, apellido1);
        if (!validacion.exito) return validacion;

        validacion = validarDNI(dni);
        if (!validacion.exito) return validacion;

        // Comprobar DNI duplicado excluyendo al propio paciente
        if (existeDNI(dni, id)) {
            return Resultado.error("Ya existe otro paciente con ese DNI");
        }

        int intensidad, tiempo;
        try {
            intensidad = Integer.parseInt(intensidadStr.trim());
            tiempo = Integer.parseInt(tiempoStr.trim());
        } catch (NumberFormatException e) {
            return Resultado.error("La intensidad y el tiempo deben ser números");
        }

        int resultado = dataManager.actualizarPaciente(id, dni, nombre, apellido1, apellido2,
                patologia, medicacion, intensidad, tiempo, cic);

        if (resultado != -1) {
            return Resultado.ok("Paciente actualizado correctamente", id);
        } else {
            return Resultado.error("Error al actualizar el paciente");
        }
    }

    /**
     * Actualiza un paciente existente con datos de 2 dispositivos.
     */
    public Resultado actualizarPaciente2disp(int id, String dni, String nombre, String apellido1,
                                             String apellido2, String patologia, String medicacion,
                                             String intensidadStr, String tiempoStr,
                                             String intensidadStr2, String tiempoStr2, String cic) {
        Resultado validacion = validarCamposObligatorios(dni, nombre, apellido1);
        if (!validacion.exito) return validacion;

        validacion = validarDNI(dni);
        if (!validacion.exito) return validacion;

        if (existeDNI(dni, id)) {
            return Resultado.error("Ya existe otro paciente con ese DNI");
        }

        int intensidad, tiempo, intensidad2, tiempo2;
        try {
            intensidad = Integer.parseInt(intensidadStr.trim());
            tiempo = Integer.parseInt(tiempoStr.trim());
            intensidad2 = Integer.parseInt(intensidadStr2.trim());
            tiempo2 = Integer.parseInt(tiempoStr2.trim());
        } catch (NumberFormatException e) {
            return Resultado.error("La intensidad y el tiempo deben ser números");
        }

        int resultado = dataManager.actualizarPaciente2disp(id, dni, nombre, apellido1, apellido2,
                patologia, medicacion, intensidad, tiempo, intensidad2, tiempo2, cic);

        if (resultado != -1) {
            return Resultado.ok("Paciente actualizado correctamente", id);
        } else {
            return Resultado.error("Error al actualizar el paciente");
        }
    }

    /**
     * Elimina un paciente y reinicia el autoincrement.
     */
    public Resultado eliminarPaciente(int id) {
        boolean eliminado = dataManager.eliminarPaciente(id);
        if (!eliminado) {
            return Resultado.error("Error al borrar el paciente");
        }

        boolean reiniciado = dataManager.reiniciarAutoIncrement();
        if (!reiniciado) {
            return Resultado.error("Paciente borrado, pero error al reiniciar ID");
        }

        return Resultado.ok("Paciente borrado correctamente");
    }

    // ========================
    // CONSULTAS
    // ========================

    /**
     * Obtiene un paciente por su ID.
     * @param opcionDis opción de dispositivo (3 = dos dispositivos)
     */
    public Paciente obtenerPacientePorId(int id, int opcionDis) {
        Cursor cursor = dataManager.obtenerPacientePorId(id);

        if (cursor != null && cursor.moveToFirst()) {
            String cic = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_CIC));
            String dni = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_DNI));
            String nombre = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_NOMBRE));
            String ap1 = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_APELLIDO1));
            String ap2 = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_APELLIDO2));
            String patologia = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_PATOLOGIA));
            String medicacion = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_MEDICACIÓN));
            int intensidad = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD));
            int tiempo = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO));

            if (opcionDis == 3) {
                int intensidad2 = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD2));
                int tiempo2 = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO2));
                cursor.close();
                return new Paciente(id, cic, dni, nombre, ap1, ap2, patologia, medicacion,
                        intensidad, tiempo, intensidad2, tiempo2);
            }

            cursor.close();
            return new Paciente(id, cic, dni, nombre, ap1, ap2, patologia, medicacion,
                    intensidad, tiempo);
        }

        if (cursor != null) cursor.close();
        return null;
    }

    /**
     * Obtiene la lista completa de pacientes.
     */
    public List<Paciente> obtenerTodosPacientes() {
        List<Paciente> lista = new ArrayList<>();
        Cursor cursor = dataManager.obtenerTodosPacientes();

        if (cursor != null && cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_ID));
                String cic = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_CIC));
                String dni = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_DNI));
                String nombre = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_NOMBRE));
                String ap1 = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_APELLIDO1));
                String ap2 = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_APELLIDO2));
                String patologia = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_PATOLOGIA));
                String medicacion = cursor.getString(cursor.getColumnIndex(PacienteDBHelper.COLUMN_MEDICACIÓN));
                int intensidad = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD));
                int tiempo = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO));
                int intensidad2 = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_INTENSIDAD2));
                int tiempo2 = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_TIEMPO2));

                lista.add(new Paciente(id, cic, dni, nombre, ap1, ap2, patologia, medicacion,
                        intensidad, tiempo, intensidad2, tiempo2));
            } while (cursor.moveToNext());
            cursor.close();
        }

        return lista;
    }

    /**
     * Obtiene la lista de pacientes formateada para mostrar en ListView.
     * Formato: "DNI: XXXXXXXXX - Nombre Apellido1 Apellido2"
     */
    public String[] obtenerListaPacientesFormateada() {
        List<Paciente> pacientes = obtenerTodosPacientes();

        if (pacientes.isEmpty()) {
            return new String[]{"No hay pacientes registrados"};
        }

        String[] nombres = new String[pacientes.size()];
        for (int i = 0; i < pacientes.size(); i++) {
            Paciente p = pacientes.get(i);
            nombres[i] = "DNI: " + p.getDNI() + " - " + p.getNombre() + " " + p.getAp1();
            if (p.getAp2() != null && !p.getAp2().isEmpty()) {
                nombres[i] += " " + p.getAp2();
            }
        }
        return nombres;
    }

    /**
     * Obtiene el ID de un paciente por su posición en la lista.
     */
    public int obtenerIdPorPosicion(int posicion) {
        Cursor cursor = dataManager.obtenerTodosPacientes();

        if (cursor != null && cursor.moveToPosition(posicion)) {
            int id = cursor.getInt(cursor.getColumnIndex(PacienteDBHelper.COLUMN_ID));
            cursor.close();
            return id;
        }

        if (cursor != null) cursor.close();
        return -1;
    }

    // ========================
    // BÚSQUEDA / FILTRADO
    // ========================

    /**
     * Filtra la lista de pacientes por un campo y texto de búsqueda.
     * @param filtro texto a buscar (en minúsculas)
     * @param campo campo por el que filtrar: "Nombre", "Apellidos", "DNI", "CIC", "Patologia"
     * @return lista filtrada de strings formateados para ListView
     */
    public List<String> filtrarPacientes(String filtro, String campo) {
        List<String> resultado = new ArrayList<>();
        List<Paciente> todos = obtenerTodosPacientes();
        String[] formateados = obtenerListaPacientesFormateada();

        String filtroLower = (filtro != null) ? filtro.toLowerCase().trim() : "";

        for (int i = 0; i < todos.size(); i++) {
            Paciente p = todos.get(i);
            boolean coincide = false;

            switch (campo) {
                case "Nombre":
                    coincide = p.getNombre().toLowerCase().contains(filtroLower);
                    break;
                case "DNI":
                    coincide = p.getDNI().toLowerCase().contains(filtroLower);
                    break;
                case "Apellidos":
                    String apellidos = p.getAp1() + " " + p.getAp2();
                    coincide = apellidos.toLowerCase().contains(filtroLower);
                    break;
                case "CIC":
                    coincide = p.getCIC().toLowerCase().contains(filtroLower);
                    break;
                case "Patologia":
                    coincide = p.getPatologia().toLowerCase().contains(filtroLower);
                    break;
                default:
                    coincide = formateados[i].toLowerCase().contains(filtroLower);
                    break;
            }

            if (coincide || filtroLower.isEmpty()) {
                resultado.add(formateados[i]);
            }
        }

        return resultado;
    }

    // ========================
    // CONFIGURACIÓN
    // ========================

    /**
     * Guarda la configuración (intensidad/tiempo) de un paciente por DNI.
     */
    public Resultado guardarConfiguracion(String dni, int intensidad, int tiempo) {
        int resultado = dataManager.guardarConfiguracion(dni, intensidad, tiempo);
        if (resultado != -1) {
            return Resultado.ok("Configuración guardada correctamente");
        } else {
            return Resultado.error("Error al guardar la configuración");
        }
    }

    /**
     * Guarda la configuración para 2 dispositivos.
     */
    public Resultado guardarConfiguracion2disp(String dni, int intensidad, int tiempo,
                                               int intensidad2, int tiempo2) {
        int resultado = dataManager.guardarConfiguracion2disp(dni, intensidad, tiempo,
                intensidad2, tiempo2);
        if (resultado != -1) {
            return Resultado.ok("Configuración guardada correctamente");
        } else {
            return Resultado.error("Error al guardar la configuración");
        }
    }
}