package com.example.triviumgor.model;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Encapsula el estado completo de un dispositivo Bluetooth.
 * Elimina la duplicación de variables (IsConnected/IsConnected3, address/address3, etc.)
 * Cada instancia representa un dispositivo (1 o 2).
 */
public class DispositivoState {

    private final int numero; // 1 o 2

    // Bluetooth
    private BluetoothDevice btDevice;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private String address = "";

    // Conexión
    private boolean connected = false;
    private boolean battMon = false;
    private boolean battMonSent = false;

    // Sesión
    private boolean clockStopped = true;
    private int minutoTranscurrido = 0;

    // Paciente asignado
    private int pacienteId = -1;
    private String pacienteDNI = "";
    private String pacienteNombre = "";
    private int intensidad = 0;
    private int duracionMin = 0;

    public DispositivoState(int numero) {
        this.numero = numero;
    }

    /**
     * Resetea todo el estado de conexión (al desconectar).
     */
    public void resetConexion() {
        connected = false;
        battMon = false;
        battMonSent = false;
        address = "";
        btDevice = null;
        btSocket = null;
        inputStream = null;
        outputStream = null;
    }

    /**
     * Resetea el estado de sesión (al finalizar o desconectar).
     */
    public void resetSesion() {
        clockStopped = true;
        minutoTranscurrido = 0;
    }

    /**
     * Resetea el paciente asignado.
     */
    public void resetPaciente() {
        pacienteId = -1;
        pacienteDNI = "";
        pacienteNombre = "";
        intensidad = 0;
        duracionMin = 0;
    }

    /**
     * Resetea todo (conexión + sesión + paciente).
     */
    public void resetCompleto() {
        resetConexion();
        resetSesion();
        resetPaciente();
    }

    /**
     * Indica si hay una sesión activa (conectado y reloj corriendo).
     */
    public boolean isSesionActiva() {
        return connected && !clockStopped;
    }

    /**
     * Indica si la sesión ha terminado por tiempo.
     */
    public boolean isTiempoAgotado() {
        return minutoTranscurrido >= duracionMin && duracionMin > 0;
    }

    // ========================
    // GETTERS / SETTERS
    // ========================

    public int getNumero() { return numero; }

    // Bluetooth
    public BluetoothDevice getBtDevice() { return btDevice; }
    public void setBtDevice(BluetoothDevice btDevice) { this.btDevice = btDevice; }

    public BluetoothSocket getBtSocket() { return btSocket; }
    public void setBtSocket(BluetoothSocket btSocket) { this.btSocket = btSocket; }

    public InputStream getInputStream() { return inputStream; }
    public void setInputStream(InputStream inputStream) { this.inputStream = inputStream; }

    public OutputStream getOutputStream() { return outputStream; }
    public void setOutputStream(OutputStream outputStream) { this.outputStream = outputStream; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    // Conexión
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public boolean isBattMon() { return battMon; }
    public void setBattMon(boolean battMon) { this.battMon = battMon; }

    public boolean isBattMonSent() { return battMonSent; }
    public void setBattMonSent(boolean battMonSent) { this.battMonSent = battMonSent; }

    // Sesión
    public boolean isClockStopped() { return clockStopped; }
    public void setClockStopped(boolean clockStopped) { this.clockStopped = clockStopped; }

    public int getMinutoTranscurrido() { return minutoTranscurrido; }
    public void setMinutoTranscurrido(int minutoTranscurrido) { this.minutoTranscurrido = minutoTranscurrido; }
    public void incrementarMinuto() { this.minutoTranscurrido++; }

    // Paciente
    public int getPacienteId() { return pacienteId; }
    public void setPacienteId(int pacienteId) { this.pacienteId = pacienteId; }

    public String getPacienteDNI() { return pacienteDNI; }
    public void setPacienteDNI(String pacienteDNI) { this.pacienteDNI = pacienteDNI; }

    public String getPacienteNombre() { return pacienteNombre; }
    public void setPacienteNombre(String pacienteNombre) { this.pacienteNombre = pacienteNombre; }

    public int getIntensidad() { return intensidad; }
    public void setIntensidad(int intensidad) { this.intensidad = intensidad; }

    public int getDuracionMin() { return duracionMin; }
    public void setDuracionMin(int duracionMin) { this.duracionMin = duracionMin; }
}