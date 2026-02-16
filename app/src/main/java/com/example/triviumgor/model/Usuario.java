package com.example.triviumgor.model;

public class Usuario {
    private int id;
    private String username;
    private String hashPassword;
    private String nombreCompleto;
    private String rol;
    private int activo;
    private String fechaCreacion;
    private String ultimoAcceso;

    public Usuario(int id, String username, String hashPassword, String nombreCompleto, String rol, int activo, String fechaCreacion, String ultimoAcceso) {
        this.id = id;
        this.username = username;
        this.hashPassword = hashPassword;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
        this.ultimoAcceso = ultimoAcceso;
    }

    //sin ID para inserciones

    public Usuario(String username, String hashPassword, String nombreCompleto, String rol, int activo, String fechaCreacion, String ultimoAcceso) {
        this.username = username;
        this.hashPassword = hashPassword;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
        this.fechaCreacion = fechaCreacion;
        this.ultimoAcceso = ultimoAcceso;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHashPassword() {
        return hashPassword;
    }

    public void setHashPassword(String hashPassword) {
        this.hashPassword = hashPassword;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public int getActivo() {
        return activo;
    }

    public void setActivo(int activo) {
        this.activo = activo;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(String ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }
}
