package com.example.triviumgor.model;

public class Sesion {

    private int id;
    private int idPaciente;
    private String dispositivo;
    private String fecha;
    private int intensidad;
    private int tiempo;

    // Constructor completo
    public Sesion(int id, int idPaciente, String dispositivo, String fecha,
                  int intensidad, int tiempo) {
        this.id = id;
        this.idPaciente = idPaciente;
        this.dispositivo = dispositivo;
        this.fecha = fecha;
        this.intensidad = intensidad;
        this.tiempo = tiempo;
    }

    // Constructor sin ID (para inserciones)
    public Sesion(int idPaciente, String dispositivo, String fecha,
                  int intensidad, int tiempo) {
        this.idPaciente = idPaciente;
        this.dispositivo = dispositivo;
        this.fecha = fecha;
        this.intensidad = intensidad;
        this.tiempo = tiempo;
    }

    // Getters y Setters

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getIdPaciente() { return idPaciente; }
    public void setIdPaciente(int idPaciente) { this.idPaciente = idPaciente; }

    public String getDispositivo() { return dispositivo; }
    public void setDispositivo(String dispositivo) { this.dispositivo = dispositivo; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public int getIntensidad() { return intensidad; }
    public void setIntensidad(int intensidad) { this.intensidad = intensidad; }

    public int getTiempo() { return tiempo; }
    public void setTiempo(int tiempo) { this.tiempo = tiempo; }
}