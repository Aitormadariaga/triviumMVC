package com.example.triviumgor.controller;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import com.example.triviumgor.R;
import com.example.triviumgor.model.DispositivoState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

/**
 * Controlador Bluetooth.
 * Gestiona: lectura de MACs, diálogo de selección de dispositivo, conexión BT.
 * Usa DispositivoState para almacenar el estado de cada dispositivo.
 */
public class BluetoothController {

    private static final String TAG = "BluetoothController";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * Callback de éxito al conectar.
     */
    public interface OnConnectedCallback {
        void onConnected();
    }

    /**
     * Callback de error al conectar.
     */
    public interface OnErrorCallback {
        void onError(String mensaje);
    }

    private final Context context;
    private BluetoothAdapter btAdapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // MACs leídas del archivo
    private final String[] dirMacs = new String[20];
    private int indiceDirMACs = 0;

    // Referencia a los dos dispositivos para filtrar en el diálogo
    private DispositivoState dispositivo1Ref;
    private DispositivoState dispositivo2Ref;

    public BluetoothController(Context context) {
        this.context = context;
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Guarda referencias a ambos dispositivos para poder filtrar
     * el ya conectado al mostrar el diálogo del otro.
     */
    public void setDispositivoRefs(DispositivoState disp1, DispositivoState disp2) {
        this.dispositivo1Ref = disp1;
        this.dispositivo2Ref = disp2;
    }

    // ========================
    // LECTURA DE MACs
    // ========================

    /**
     * Lee las direcciones MAC desde el archivo raw/dir_macs.
     */
    public void leerArchivoMACs() {
        try {
            InputStream fis = context.getResources().openRawResource(R.raw.dir_macs);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String linea = reader.readLine();
            indiceDirMACs = 0;

            while (linea != null) {
                dirMacs[indiceDirMACs] = linea.trim();
                indiceDirMACs++;
                linea = reader.readLine();
                Log.d(TAG, "MAC leída: " + linea);
            }

            reader.close();
            fis.close();
            Log.d(TAG, "Total MACs leídas: " + indiceDirMACs);
        } catch (IOException e) {
            Log.e(TAG, "Error al leer archivo MACs: " + e.getMessage());
        }
    }

    public String[] getDirMacs() {
        return dirMacs;
    }

    public int getIndiceDirMACs() {
        return indiceDirMACs;
    }

    // ========================
    // CONEXIÓN
    // ========================

    /**
     * Muestra diálogo de dispositivos BT vinculados y conecta al seleccionado.
     * Filtra el dispositivo ya conectado en el otro slot.
     */
    @SuppressLint("MissingPermission")
    public void conectarDispositivo(DispositivoState dispositivo,
                                    OnConnectedCallback onSuccess,
                                    OnErrorCallback onError) {
        if (btAdapter == null) {
            onError.onError("Bluetooth no disponible");
            return;
        }

        if (dispositivo.isConnected()) {
            onError.onError("Dispositivo ya conectado");
            return;
        }

        mostrarDialogoSeleccion(dispositivo, onSuccess, onError);
    }

    /**
     * Muestra un diálogo con la lista de dispositivos BT vinculados.
     */
    @SuppressLint("MissingPermission")
    private void mostrarDialogoSeleccion(DispositivoState dispositivo,
                                         OnConnectedCallback onSuccess,
                                         OnErrorCallback onError) {
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        ArrayList<BluetoothDevice> deviceList = new ArrayList<>();

        if (pairedDevices != null && pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                // Filtrar el dispositivo que ya está conectado en el otro slot
                if (estaConectadoEnOtroSlot(device, dispositivo)) {
                    continue;
                }
                deviceNames.add(device.getName() + "\n" + device.getAddress());
                deviceList.add(device);
            }
        }

        if (deviceNames.isEmpty()) {
            deviceNames.add("No hay dispositivos vinculados");
        }

        // Inflar diálogo
        View dialogView = LayoutInflater.from(context)
                .inflate(R.layout.bluetoothoption_dialog, null);
        ListView listView = dialogView.findViewById(R.id.listaBluetotoh);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, deviceNames);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Dispositivos Bluetooth")
                .setView(dialogView)
                .setPositiveButton("Cerrar", (d, w) -> {
                    d.dismiss();
                    onError.onError("Conexión cancelada");
                })
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (position < deviceList.size()) {
                realizarConexion(deviceList.get(position), dispositivo, onSuccess, onError);
            }
        });

        dialog.show();
    }

    /**
     * Comprueba si un BluetoothDevice ya está conectado en el otro slot.
     */
    @SuppressLint("MissingPermission")
    private boolean estaConectadoEnOtroSlot(BluetoothDevice device, DispositivoState dispositivoActual) {
        if (dispositivo1Ref == null || dispositivo2Ref == null) return false;

        DispositivoState otro = (dispositivoActual.getNumero() == 1) ? dispositivo2Ref : dispositivo1Ref;
        return otro.isConnected() && otro.getBtDevice() != null
                && otro.getBtDevice().getAddress().equals(device.getAddress());
    }

    /**
     * Realiza la conexión BT al dispositivo seleccionado EN UN HILO DE FONDO.
     * Esto evita bloquear el hilo principal (UI) y previene ANR cuando
     * el dispositivo está apagado o fuera de alcance.
     * Los callbacks se ejecutan siempre en el hilo principal.
     */
    @SuppressLint("MissingPermission")
    private void realizarConexion(BluetoothDevice device, DispositivoState dispositivo,
                                  OnConnectedCallback onSuccess, OnErrorCallback onError) {
        new Thread(() -> {
            try {
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(BT_UUID);
                socket.connect();

                // Guardar estado en DispositivoState
                dispositivo.setBtDevice(device);
                dispositivo.setBtSocket(socket);
                dispositivo.setAddress(device.getAddress());
                dispositivo.setOutputStream(socket.getOutputStream());
                dispositivo.setInputStream(socket.getInputStream());
                dispositivo.setConnected(true);
                dispositivo.setBattMon(true);

                Log.d(TAG, "Conectado a: " + device.getName() + " (" + device.getAddress() + ")");

                // Callback de éxito en el hilo principal
                mainHandler.post(onSuccess::onConnected);

            } catch (Exception e) {
                Log.e(TAG, "Error de conexión: " + e.getMessage());
                dispositivo.resetConexion();

                // Callback de error en el hilo principal
                mainHandler.post(() -> onError.onError("Fallo al conectarse: " + e.getMessage()));
            }
        }, "BT-Connect-" + device.getAddress()).start();
    }

    /**
     * Desconecta un dispositivo cerrando su socket y reseteando estado.
     */
    public void desconectar(DispositivoState dispositivo) {
        try {
            if (dispositivo.getBtSocket() != null) {
                dispositivo.getBtSocket().close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error al cerrar socket: " + e.getMessage());
        }
        dispositivo.resetCompleto();
    }
}