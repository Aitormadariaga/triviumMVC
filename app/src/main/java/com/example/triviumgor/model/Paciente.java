package com.example.triviumgor.model;

public class Paciente {

    private int ID;
    private String CIC;
    private String DNI;
    private String nombre;
    private String ap1;
    private String ap2;
    private String patologia;
    private String medicacion;
    private int intensidad;
    private int tiempoM;
    private int intensidad2;
    private int tiempoM2;

    // Constructor sin ID (para inserciones)
    public Paciente(String CIC, String DNI, String nombre, String ap1, String ap2,
                    String patologia, String medicacion, int intensidad, int tiempoM) {
        this.CIC = CIC;
        this.DNI = DNI;
        this.nombre = nombre;
        this.ap1 = ap1;
        this.ap2 = ap2;
        this.patologia = patologia;
        this.medicacion = medicacion;
        this.intensidad = intensidad;
        this.tiempoM = tiempoM;
        this.intensidad2 = 0;
        this.tiempoM2 = 0;
    }

    // Constructor con ID (1 dispositivo)
    public Paciente(int ID, String CIC, String DNI, String nombre, String ap1, String ap2,
                    String patologia, String medicacion, int intensidad, int tiempoM) {
        this(CIC, DNI, nombre, ap1, ap2, patologia, medicacion, intensidad, tiempoM);
        this.ID = ID;
    }

    // Constructor con ID (2 dispositivos)
    public Paciente(int ID, String CIC, String DNI, String nombre, String ap1, String ap2,
                    String patologia, String medicacion, int intensidad, int tiempoM,
                    int intensidad2, int tiempoM2) {
        this(ID, CIC, DNI, nombre, ap1, ap2, patologia, medicacion, intensidad, tiempoM);
        this.intensidad2 = intensidad2;
        this.tiempoM2 = tiempoM2;
    }

    /**
     * Devuelve el nombre completo: "Nombre Apellido1 Apellido2"
     */
    public String getNombreCompleto() {
        String completo = nombre + " " + ap1;
        if (ap2 != null && !ap2.isEmpty()) {
            completo += " " + ap2;
        }
        return completo;
    }

    // Getters y Setters

    public int getID() { return ID; }
    public void setID(int ID) { this.ID = ID; }

    public String getCIC() { return CIC; }
    public void setCIC(String CIC) { this.CIC = CIC; }

    public String getDNI() { return DNI; }
    public void setDNI(String DNI) { this.DNI = DNI; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getAp1() { return ap1; }
    public void setAp1(String ap1) { this.ap1 = ap1; }

    public String getAp2() { return ap2; }
    public void setAp2(String ap2) { this.ap2 = ap2; }

    public String getPatologia() { return patologia; }
    public void setPatologia(String patologia) { this.patologia = patologia; }

    public String getMedicacion() { return medicacion; }
    public void setMedicacion(String medicacion) { this.medicacion = medicacion; }

    public int getIntensidad() { return intensidad; }
    public void setIntensidad(int intensidad) { this.intensidad = intensidad; }

    public int getTiempoM() { return tiempoM; }
    public void setTiempoM(int tiempoM) { this.tiempoM = tiempoM; }

    public int getIntensidad2() { return intensidad2; }
    public void setIntensidad2(int intensidad2) { this.intensidad2 = intensidad2; }

    public int getTiempoM2() { return tiempoM2; }
    public void setTiempoM2(int tiempoM2) { this.tiempoM2 = tiempoM2; }
}