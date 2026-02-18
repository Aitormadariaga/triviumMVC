package com.example.triviumgor.model;

/**
        * Clase que representa la relación N:M entre Usuario y Paciente.
        * Un usuario puede tener varios pacientes, y un paciente puede estar
        * asignado a varios usuarios.
        *
        * Rol posibles: "creador" (quien registró el paciente) o "asignado" (asignado posteriormente)
        */
public class UsuarioPaciente {
    private int idUsuario;
    private int idPaciente;
    private String rol; //por ahora es "creador" o "asignado"

    public UsuarioPaciente(int idUsuario, int idPaciente, String rol) {
        this.idUsuario = idUsuario;
        this.idPaciente = idPaciente;
        this.rol = rol;
    }

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public int getIdPaciente() {
        return idPaciente;
    }

    public void setIdPaciente(int idPaciente) {
        this.idPaciente = idPaciente;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    /**
     * Indica si este usuario fue quien creó al paciente
     */
    public boolean esCreador() {
        return "creador".equals(rol);
    }
}
