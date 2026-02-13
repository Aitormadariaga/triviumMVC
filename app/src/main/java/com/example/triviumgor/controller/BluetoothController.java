package com.example.triviumgor.controller;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.triviumgor.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class BluetoothController    {
    private static final String TAG = "BluetoothController";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;

    //Context y Adapter
    private Context context;
    private BluetoothAdapter btAdapter;

    //Device(Dispositivos) y sus Sockets (solo necesitamos 2)
    private  BluetoothDevice btDevice;
    private BluetoothDevice btDevice2;

    private BluetoothSocket btSocket;
    private BluetoothSocket btSocket2;

    // Streams de entrada/salida
    private OutputStream outputStream;
    private InputStream inputStream;
    private OutputStream outputStream2;
    private InputStream inputStream2;

    // Estados de conexión
    private boolean isConnected = false;
    private boolean isConnected2 = false;

    // Información de dispositivos
    private String nameDevice = "";
    private String nameDevice2 = "";
    private String address = "";
    private String address2 = "";

    // Handler para UI
    private Handler handler;

    // Listener de callbacks
    private BluetoothConnectionListener listener;

    /**
     * Interface para callbacks de conexión
     */
    public interface BluetoothConnectionListener {
        void onDeviceConnected(int dispositivoNum);
        void onDeviceDisconnected(int dispositivoNum);
        void onConnectionFailed(int dispositivoNum, String error);
        void onDeviceFound(BluetoothDevice device);
        void onDiscoveryFinished();
    }

    public BluetoothController(Context context, BluetoothConnectionListener listener){
        this.context = context;
        this.listener = listener;
        this.handler = new Handler();
        this.btAdapter = BluetoothAdapter.getDefaultAdapter();

        //Hacer/Registrar el BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        context.registerReceiver(bluetoothReceiver, filter);

    }

    /**
     * BroadcastReceiver para eventos Bluetooth
     */
    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Dispositivo encontrado durante el descubrimiento
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && listener != null) {
                    listener.onDeviceFound(device);
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                // Descubrimiento finalizado
                if (listener != null) {
                    listener.onDiscoveryFinished();
                }

            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                // Estado de emparejamiento cambió
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Dispositivo emparejado: " + device.getName());
                } else if (bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "Dispositivo desemparejado: " + device.getName());
                }
            }
        }
    };

    /**
     * Verificar si el Bluetooth está disponible
     */
    public boolean isBluetoothAvailable() {
        return btAdapter != null;
    }

    /**
     * Verificar si el Bluetooth está habilitado
     */
    public boolean isBluetoothEnabled() {
        return btAdapter != null && btAdapter.isEnabled();
    }

    /**
     * Verificar permisos de Bluetooth
     */
    public boolean checkBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                            == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
                    == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
                            == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Obtener dispositivos emparejados
     */
    public Set<BluetoothDevice> getPairedDevices() {
        if (btAdapter != null && checkBluetoothPermissions()) {
            return btAdapter.getBondedDevices(); //antes de llegar aqui se hace los permisos
        }
        return null;
    }

    /**
     * Mostrar diálogo con lista de dispositivos Bluetooth
     */
    public void mostrarDialogoListaBluetooth(final int dispositivoNum) {
        if (!checkBluetoothPermissions()) {
            Toast.makeText(context, "Se requieren permisos de Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> pairedDevices = getPairedDevices();
        if (pairedDevices == null || pairedDevices.isEmpty()) {
            Toast.makeText(context, "No hay dispositivos emparejados", Toast.LENGTH_SHORT).show();
            return;
        }

        final ArrayList<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
        final String[] deviceNames = new String[deviceList.size()];

        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice device = deviceList.get(i);
            // Formato: "NombreDispositivo (últimos 5 chars de MAC)"
            String macAddress = device.getAddress();
            String shortMac = macAddress.substring(macAddress.length() - 5, macAddress.length());
            deviceNames[i] = device.getName() + " (" + shortMac + ")";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Seleccionar dispositivo " + dispositivoNum);
        builder.setItems(deviceNames, (dialog, which) -> {
            BluetoothDevice selectedDevice = deviceList.get(which);
            conectarDevice(selectedDevice, dispositivoNum);
        });
        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }
    /**
     * Conectar a un dispositivo Bluetooth
     */
    public void conectarDevice(BluetoothDevice device, final int dispositivoNum) {
        if (device == null) {
            if (listener != null) {
                listener.onConnectionFailed(dispositivoNum, "Dispositivo nulo");
            }
            return;
        }

        new Thread(() -> {
            try {
                // Cancelar descubrimiento si está activo
                if (btAdapter.isDiscovering() && checkBluetoothPermissions()) {
                    btAdapter.cancelDiscovery();
                }

                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();

                OutputStream outStream = socket.getOutputStream();
                InputStream inStream = socket.getInputStream();

                // Asignar según el número de dispositivo
                handler.post(() -> {
                    switch (dispositivoNum) {
                        case 1:
                            btDevice = device;
                            btSocket = socket;
                            outputStream = outStream;
                            inputStream = inStream;
                            isConnected = true;
                            nameDevice = device.getName();
                            address = device.getAddress();
                            break;

                        case 2:
                            btDevice2 = device;
                            btSocket2 = socket;
                            outputStream2 = outStream;
                            inputStream2 = inStream;
                            isConnected2 = true;
                            nameDevice2 = device.getName();
                            address2 = device.getAddress();
                            break;


                    }

                    Log.d(TAG, "Conectado al dispositivo " + dispositivoNum + ": " + device.getName());

                    if (listener != null) {
                        listener.onDeviceConnected(dispositivoNum);
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error conectando dispositivo " + dispositivoNum, e);
                handler.post(() -> {
                    if (listener != null) {
                        listener.onConnectionFailed(dispositivoNum, e.getMessage());
                    }
                });
            }
        }).start();
    }
    /**
     * Desconectar dispositivo
     */
    public void desconectarDevice(final int dispositivoNum) {
        new Thread(() -> {
            try {
                BluetoothSocket socket = null;
                OutputStream outStream = null;
                InputStream inStream = null;

                switch (dispositivoNum) {
                    case 1:
                        socket = btSocket;
                        outStream = outputStream;
                        inStream = inputStream;
                        break;
                    case 2:
                        socket = btSocket2;
                        outStream = outputStream2;
                        inStream = inputStream2;
                        break;

                }

                if (outStream != null) {
                    outStream.close();
                }
                if (inStream != null) {
                    inStream.close();
                }
                if (socket != null) {
                    socket.close();
                }

                handler.post(() -> {
                    switch (dispositivoNum) {
                        case 1:
                            btSocket = null;
                            outputStream = null;
                            inputStream = null;
                            isConnected = false;
                            nameDevice = "";
                            address = "";
                            break;
                        case 2:
                            btSocket2 = null;
                            outputStream2 = null;
                            inputStream2 = null;
                            isConnected2 = false;
                            nameDevice2 = "";
                            address2 = "";
                            break;

                    }

                    Log.d(TAG, "Desconectado dispositivo " + dispositivoNum);

                    if (listener != null) {
                        listener.onDeviceDisconnected(dispositivoNum);
                    }
                });

            } catch (IOException e) {
                Log.e(TAG, "Error desconectando dispositivo " + dispositivoNum, e);
            }
        }).start();
    }
    public void desconectarDeviceConComando(final int dispositivoNum, byte comando) {
        new Thread(() -> {
            try {
                OutputStream stream = getOutputStream(dispositivoNum);
                if (stream != null) {
                    stream.write(comando);
                    stream.flush();
                    Thread.sleep(100); // Esperar a que se envíe
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Error enviando comando antes de desconectar", e);
            } finally {
                desconectarDevice(dispositivoNum);
            }
        }).start();
    }
    /**
     * Enviar datos a un dispositivo
     */
    public void enviarDatos(byte[] datos, int dispositivoNum) {
        try {
            OutputStream stream = null;

            switch (dispositivoNum) {
                case 1:
                    if (isConnected && outputStream != null) {
                        stream = outputStream;
                    }
                    break;
                case 2:
                    if (isConnected2 && outputStream2 != null) {
                        stream = outputStream2;
                    }
                    break;
            }

            if (stream != null) {
                stream.write(datos);
                stream.flush();
                Log.d(TAG, "Datos enviados al dispositivo " + dispositivoNum);
            } else {
                Log.w(TAG, "No se puede enviar: dispositivo " + dispositivoNum + " no conectado");
            }

        } catch (IOException e) {
            Log.e(TAG, "Error enviando datos al dispositivo " + dispositivoNum, e);
            desconectarDevice(dispositivoNum);
        }
    }

    /**
     * Leer datos de un dispositivo
     */
    public byte[] leerDatos(int dispositivoNum, int numBytes) {
        try {
            InputStream stream = null;

            switch (dispositivoNum) {
                case 1:
                    if (isConnected && inputStream != null) {
                        stream = inputStream;
                    }
                    break;
                case 2:
                    if (isConnected2 && inputStream2 != null) {
                        stream = inputStream2;
                    }
                    break;
            }

            if (stream != null) {
                byte[] buffer = new byte[numBytes];
                int bytesRead = stream.read(buffer);
                if (bytesRead > 0) {
                    byte[] result = new byte[bytesRead];
                    System.arraycopy(buffer, 0, result, 0, bytesRead);
                    return result;
                }
            }

        } catch (IOException e) {
            Log.e(TAG, "Error leyendo datos del dispositivo " + dispositivoNum, e);
            desconectarDevice(dispositivoNum);
        }

        return null;
    }
    /**
     * Verificar si un dispositivo está conectado
     */
    public boolean isConnected(int dispositivoNum) {
        switch (dispositivoNum) {
            case 1: return isConnected;
            case 2: return isConnected2;
            default: return false;
        }
    }

    /**
     * Obtener nombre del dispositivo
     */
    public String getDeviceName(int dispositivoNum) {
        switch (dispositivoNum) {
            case 1: return nameDevice;
            case 2: return nameDevice2;
            default: return "";
        }
    }
    /**
     * Obtener OutputStream de un dispositivo
     */
    public OutputStream getOutputStream(int dispositivoNum) {
        switch (dispositivoNum) {
            case 1: return outputStream;
            case 2: return outputStream2;
            default: return null;
        }
    }

    /**
     * Obtener InputStream de un dispositivo
     */
    public InputStream getInputStream(int dispositivoNum) {
        switch (dispositivoNum) {
            case 1: return inputStream;
            case 2: return inputStream2;
            default: return null;
        }
    }
    /**
     * Liberar recursos
     */
    public void cleanup() {
        // Desconectar todos los dispositivos
        desconectarDevice(1);
        desconectarDevice(2);

        // Desregistrar el BroadcastReceiver
        try {
            context.unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException e) {
            // Ya estaba desregistrado
        }
    }

}
