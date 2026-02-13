package com.example.triviumgor.view;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.triviumgor.R;
import com.example.triviumgor.controller.BluetoothController;
import com.example.triviumgor.controller.PacienteController;
import com.example.triviumgor.controller.SesionController;
import com.example.triviumgor.controller.TratamientoController;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.DispositivoState;
import com.example.triviumgor.model.Paciente;
import com.example.triviumgor.util.UIHelper;

import java.util.UUID;

/**
 * MainActivity - Solo UI y delegación a controllers.
 * NO contiene lógica de negocio, protocolo, ni acceso a DB.
 */
public class MainActivity extends AppCompatActivity
        implements TratamientoController.TratamientoListener {

    private static final String TAG = "MainActivity";
    private static final int PACIENTE_REQUEST_CODE = 100;
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // ========================
    // DISPOSITIVOS (Estado)
    // ========================
    private DispositivoState dispositivo1;
    private DispositivoState dispositivo2;

    // ========================
    // CONTROLLERS
    // ========================
    private BluetoothController bluetoothController;
    private TratamientoController tratamientoController;
    private PacienteController pacienteController;
    private SesionController sesionController;
    private PacienteDataManager dataManager;

    // ========================
    // VISTAS - Dispositivo 1
    // ========================
    private Button Conexion, InicioPulsos, FinPulsos;
    private Button AmpliInc1, AmpliDec1, AmpliInc2, AmpliDec2;
    private EditText Param3, Param4, Param5;
    private EditText NivelBatt;
    private TextView OtroTexto6;
    private TextView dispBluetoothNom1;

    // ========================
    // VISTAS - Dispositivo 2
    // ========================
    private Button Conexion2, InicioPulsos2, FinPulsos2;
    private Button AmpliInc3, AmpliDec3, AmpliInc4, AmpliDec4;
    private EditText Param10, Param12, Param13;
    private EditText NivelBatt2;
    private TextView OtroTexto7;
    private TextView dispBluetoothNom2;

    // ========================
    // VISTAS - Comunes
    // ========================
    private Button Disconnect;
    private Button ConectarPaciente, ventanaPacienteBtn, btnVerLista;
    private TextView nombreDispositivoPac1, nombreDispositivoPac2;
    private TextView Otrotexto1, Otrotexto2, Otrotexto3, Otrotexto4, OtroTexto5;
    private Switch MACSwitch;
    private SharedPreferences sharedPreferences;
    private Toolbar toolbar;

    // ========================
    // DATOS PACIENTE
    // ========================
    private String nombrePaciente, DNIpaciente;
    private String nombrePaciente2, DNIpaciente2;
    private int opcionDispositivoInt = -1;

    // ========================
    // BLUETOOTH RECEIVER
    // ========================
    private final BroadcastReceiver bluetoothDisconnectReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(intent.getAction())) return;

            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) return;

            String mac = device.getAddress();

            // Dispositivo 1
            if (mac.equals(dispositivo1.getAddress()) && dispositivo1.isConnected()) {
                tratamientoController.finalizarSesion(dispositivo1);
                dispositivo1.resetCompleto();

                runOnUiThread(() -> {
                    UIHelper.resetButtonToDefault(Conexion);
                    Conexion.setText("Conectar");
                    dispBluetoothNom1.setText("");
                    dispBluetoothNom1.setVisibility(View.GONE);
                    InicioPulsos.setEnabled(false);
                    UIHelper.setButtonColor(InicioPulsos, UIHelper.COLOR_DESHABILITADO);
                    InicioPulsos.setText("Iniciar");
                    UIHelper.resetButtonToDefault(FinPulsos);
                });
            }
            // Dispositivo 2
            else if (mac.equals(dispositivo2.getAddress()) && dispositivo2.isConnected()) {
                tratamientoController.finalizarSesion(dispositivo2);
                dispositivo2.resetCompleto();

                runOnUiThread(() -> {
                    UIHelper.resetButtonToDefault(Conexion2);
                    Conexion2.setText("Conectar");
                    dispBluetoothNom2.setText("");
                    dispBluetoothNom2.setVisibility(View.GONE);
                    InicioPulsos2.setEnabled(false);
                    UIHelper.setButtonColor(InicioPulsos2, UIHelper.COLOR_DESHABILITADO);
                    InicioPulsos2.setText("Iniciar");
                    UIHelper.resetButtonToDefault(FinPulsos2);
                });
            }
        }
    };

    // ========================
    // LIFECYCLE
    // ========================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar estado
        dispositivo1 = new DispositivoState(1);
        dispositivo2 = new DispositivoState(2);

        // Inicializar DB y controllers
        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bluetoothController = new BluetoothController(this);
        tratamientoController = new TratamientoController(this);
        pacienteController = new PacienteController(dataManager);
        sesionController = new SesionController(dataManager);

        sharedPreferences = getSharedPreferences("LoginPrefs", MODE_PRIVATE);

        // Registrar receiver Bluetooth
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(bluetoothDisconnectReceiver, filter);

        // Inicializar vistas
        inicializarVistas();
        configurarListeners();

        // Leer MACs
        bluetoothController.leerArchivoMACs();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(bluetoothDisconnectReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }

        tratamientoController.destroy();

        if (dispositivo1.isConnected() && dispositivo1.getBtSocket() != null) {
            try { dispositivo1.getBtSocket().close(); } catch (Exception e) { }
        }
        if (dispositivo2.isConnected() && dispositivo2.getBtSocket() != null) {
            try { dispositivo2.getBtSocket().close(); } catch (Exception e) { }
        }

        if (dataManager != null) dataManager.close();
        super.onDestroy();
    }

    // ========================
    // INICIALIZACIÓN DE VISTAS
    // ========================

    private void inicializarVistas() {
        // Dispositivo 1
        Conexion = findViewById(R.id.button_conectar);
        InicioPulsos = findViewById(R.id.button_IniPulsos);
        FinPulsos = findViewById(R.id.button_FinPulsos);
        Param3 = findViewById(R.id.editTextNumberDecimal3);
        Param4 = findViewById(R.id.editTextNumberDecimal4);
        Param5 = findViewById(R.id.editTextNumberDecimal5);
        NivelBatt = findViewById(R.id.editTextNivelBatt);
        OtroTexto6 = findViewById(R.id.OtroTexto6);
        dispBluetoothNom1 = findViewById(R.id.dispBluetoothNom1);

        // Dispositivo 2
        Conexion2 = findViewById(R.id.button_conectar2);
        InicioPulsos2 = findViewById(R.id.button_IniPulsos2);
        FinPulsos2 = findViewById(R.id.button_FinPulsos2);
        Param10 = findViewById(R.id.editTextNumberDecimal10);
        Param12 = findViewById(R.id.editTextNumberDecimal12);
        Param13 = findViewById(R.id.editTextNumberDecimal13);
        NivelBatt2 = findViewById(R.id.editTextNivelBatt2);
        OtroTexto7 = findViewById(R.id.OtroTexto7);
        dispBluetoothNom2 = findViewById(R.id.dispBluetoothNom2);

        // Comunes
        Disconnect = findViewById(R.id.buttonDisconnect);
        ConectarPaciente = findViewById(R.id.btnGuardarConf);
        ventanaPacienteBtn = findViewById(R.id.btnPaciente);
        btnVerLista = findViewById(R.id.btnVerLista);
        nombreDispositivoPac1 = findViewById(R.id.nombreDispositivoPac1);
        nombreDispositivoPac2 = findViewById(R.id.nombreDispositivoPac2);

        // Botones de amplitud (dispositivo 1)
        AmpliInc1 = findViewById(R.id.buttonAmpliInc);
        AmpliDec1 = findViewById(R.id.buttonAmpliDec);

        // Estado inicial
        InicioPulsos.setEnabled(false);
        InicioPulsos2.setEnabled(false);
        UIHelper.setButtonColor(InicioPulsos, UIHelper.COLOR_DESHABILITADO);
        UIHelper.setButtonColor(InicioPulsos2, UIHelper.COLOR_DESHABILITADO);
    }

    // ========================
    // LISTENERS
    // ========================

    private void configurarListeners() {
        // CONECTAR Dispositivo 1
        Conexion.setOnClickListener(v -> {
            UIHelper.setButtonColor(Conexion, UIHelper.COLOR_CONECTANDO);
            Conexion.setText("conectando...");

            bluetoothController.conectarDispositivo(dispositivo1, () -> {
                runOnUiThread(() -> {
                    UIHelper.setButtonColor(Conexion, UIHelper.COLOR_CONECTADO);
                    Conexion.setTextColor(Color.BLACK);
                    Conexion.setText("conectado");
                    InicioPulsos.setEnabled(true);
                    UIHelper.resetButtonToDefault(InicioPulsos);

                    String nombre = dispositivo1.getBtDevice().getName();
                    String addr = dispositivo1.getAddress();
                    String nomBT = nombre + "(" + addr.substring(addr.length() - 5) + ")";
                    dispBluetoothNom1.setText(nomBT);
                    dispBluetoothNom1.setVisibility(View.VISIBLE);
                });
            }, error -> {
                runOnUiThread(() -> {
                    UIHelper.resetButtonToDefault(Conexion);
                    Conexion.setText("Conectar");
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                });
            });
        });

        // CONECTAR Dispositivo 2
        Conexion2.setOnClickListener(v -> {
            UIHelper.setButtonColor(Conexion2, UIHelper.COLOR_CONECTANDO);
            Conexion2.setText("conectando...");

            bluetoothController.conectarDispositivo(dispositivo2, () -> {
                runOnUiThread(() -> {
                    UIHelper.setButtonColor(Conexion2, UIHelper.COLOR_CONECTADO);
                    Conexion2.setTextColor(Color.BLACK);
                    Conexion2.setText("conectado");
                    InicioPulsos2.setEnabled(true);
                    UIHelper.resetButtonToDefault(InicioPulsos2);

                    String nombre = dispositivo2.getBtDevice().getName();
                    String addr = dispositivo2.getAddress();
                    String nomBT = nombre + "(" + addr.substring(addr.length() - 5) + ")";
                    dispBluetoothNom2.setText(nomBT);
                    dispBluetoothNom2.setVisibility(View.VISIBLE);
                });
            }, error -> {
                runOnUiThread(() -> {
                    UIHelper.resetButtonToDefault(Conexion2);
                    Conexion2.setText("Conectar");
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                });
            });
        });

        // INICIAR Dispositivo 1
        InicioPulsos.setOnClickListener(v -> {
            if (!dispositivo1.isConnected()) return;

            int intensidad = Integer.parseInt(Param3.getText().toString());
            int duracion = Integer.parseInt(Param4.getText().toString());

            // Registrar sesión si es nueva
            if (dispositivo1.isClockStopped() && DNIpaciente != null && !DNIpaciente.isEmpty()) {
                registrarSesionEnDB(DNIpaciente, dispBluetoothNom1.getText().toString(),
                        intensidad, duracion);
            }

            boolean esNueva = tratamientoController.iniciarOActualizarSesion(
                    dispositivo1, intensidad, duracion);

            UIHelper.setButtonColor(InicioPulsos, UIHelper.COLOR_SESION_ACTIVA);
            if (esNueva) {
                InicioPulsos.setText("Actualizar");
            }
        });

        // INICIAR Dispositivo 2
        InicioPulsos2.setOnClickListener(v -> {
            if (!dispositivo2.isConnected()) return;

            int intensidad = Integer.parseInt(Param10.getText().toString());
            int duracion = Integer.parseInt(Param12.getText().toString());

            if (dispositivo2.isClockStopped() && DNIpaciente2 != null && !DNIpaciente2.isEmpty()) {
                registrarSesionEnDB(DNIpaciente2, dispBluetoothNom2.getText().toString(),
                        intensidad, duracion);
            }

            boolean esNueva = tratamientoController.iniciarOActualizarSesion(
                    dispositivo2, intensidad, duracion);

            UIHelper.setButtonColor(InicioPulsos2, UIHelper.COLOR_SESION_ACTIVA);
            if (esNueva) {
                InicioPulsos2.setText("Actualizar");
            }
        });

        // FINALIZAR Dispositivo 1
        FinPulsos.setOnClickListener(v -> {
            if (!dispositivo1.isConnected()) return;
            tratamientoController.finalizarSesion(dispositivo1);
            InicioPulsos.setText("INICIAR");
            UIHelper.resetButtonToDefault(InicioPulsos);
            UIHelper.setButtonColor(FinPulsos, UIHelper.COLOR_SESION_PARADA);
        });

        // FINALIZAR Dispositivo 2
        FinPulsos2.setOnClickListener(v -> {
            if (!dispositivo2.isConnected()) return;
            tratamientoController.finalizarSesion(dispositivo2);
            InicioPulsos2.setText("Iniciar");
            UIHelper.resetButtonToDefault(InicioPulsos2);
            UIHelper.setButtonColor(FinPulsos2, UIHelper.COLOR_SESION_PARADA);
        });

        // DESCONECTAR
        Disconnect.setOnClickListener(v -> {
            if (dispositivo1.isConnected() || dispositivo2.isConnected()) {
                mostrarDialogoDesconectar();
            } else {
                Toast.makeText(this, "No hay ningún Dispositivo conectado", Toast.LENGTH_SHORT).show();
            }
        });

        // AMPLITUD +/- Dispositivo 1
        AmpliInc1.setOnClickListener(v -> ajustarIntensidad(Param3, 1, 19));
        AmpliDec1.setOnClickListener(v -> ajustarIntensidad(Param3, -1, 1));

        // PACIENTE
        ventanaPacienteBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, VentanaPacienteActivity.class);
            mostrarDialogoSeleccionarPacienteADispositivo(intent);
        });

        // GUARDAR CONFIG
        ConectarPaciente.setOnClickListener(v -> {
            guardarConfiguracionPaciente();
        });


        // VER LISTA
        btnVerLista.setOnClickListener(v -> {
            Intent intent = new Intent(this, VentanaPacienteActivity.class);
            intent.putExtra("verSoloLista", true);
            intent.putExtra("DISPOSITIVO_ELEC", opcionDispositivoInt);
            startActivity(intent);
        });
    }

    // ========================
    // TRATAMIENTO LISTENER (callbacks del controller)
    // ========================

    @Override
    public void onTiempoRestanteActualizado(int dispositivoNum, int minutosRestantes) {
        runOnUiThread(() -> {
            if (dispositivoNum == 1) {
                Param5.setText(String.valueOf(minutosRestantes));
            } else {
                Param13.setText(String.valueOf(minutosRestantes));
            }
        });
    }

    @Override
    public void onSesionFinalizada(int dispositivoNum) {
        runOnUiThread(() -> {
            if (dispositivoNum == 1) {
                InicioPulsos.setText("INICIAR");
                UIHelper.resetButtonToDefault(InicioPulsos);
            } else {
                InicioPulsos2.setText("Iniciar");
                UIHelper.resetButtonToDefault(InicioPulsos2);
            }
            Toast.makeText(this, "Sesión finalizada (Dispositivo " + dispositivoNum + ")",
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onBateriaActualizada(int dispositivoNum, int valorCarga, int nivel, String textoNivel) {
        runOnUiThread(() -> {
            int color;
            switch (nivel) {
                case TratamientoController.NIVEL_ALTA:
                    color = Color.GREEN;
                    break;
                case TratamientoController.NIVEL_MEDIA:
                    color = Color.YELLOW;
                    break;
                default:
                    color = Color.parseColor("#FF0000");
                    break;
            }

            if (dispositivoNum == 1) {
                NivelBatt.setTextColor(color);
                NivelBatt.setText(textoNivel);
                OtroTexto6.setText(String.valueOf(valorCarga));
            } else {
                NivelBatt2.setTextColor(color);
                NivelBatt2.setText(textoNivel);
                OtroTexto7.setText(String.valueOf(valorCarga));
            }
        });
    }

    @Override
    public void onError(int dispositivoNum, String mensaje) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Disp." + dispositivoNum + ": " + mensaje,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onSesionIniciada(int dispositivoNum) {
        Log.d(TAG, "Sesión iniciada en dispositivo " + dispositivoNum);
    }

    @Override
    public void onIntensidadActualizada(int dispositivoNum, int intensidad) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Intensidad actualizada a " + intensidad,
                    Toast.LENGTH_SHORT).show();
        });
    }

    // ========================
    // HELPERS
    // ========================

    private void ajustarIntensidad(EditText param, int incremento, int limite) {
        int valor = Integer.parseInt(param.getText().toString());
        valor += incremento;
        if (incremento > 0 && valor > limite) valor = limite;
        if (incremento < 0 && valor < limite) valor = limite;
        param.setText(String.valueOf(valor));
    }

    private void registrarSesionEnDB(String dniPaciente, String nombreDisp,
                                     int intensidad, int duracion) {
        Paciente pac = buscarPacientePorDNI(dniPaciente);
        if (pac != null) {
            long error = sesionController.registrarSesion(pac.getID(), nombreDisp, intensidad, duracion);
            if (error == (long)-1){
                Toast.makeText(this, "Hubo error al guardar la Sesion en la BBDD", Toast.LENGTH_LONG);
            }
        }else{
            Toast.makeText(this,"No se a podido detectar al Paciente", Toast.LENGTH_SHORT);
        }
    }

    private Paciente buscarPacientePorDNI(String dni) {
        for (Paciente p : pacienteController.obtenerTodosPacientes()) {
            if (p.getDNI().equals(dni)) return p;
        }
        return null;
    }

    private void guardarConfiguracionPaciente() {
        if (opcionDispositivoInt == 3) {
            mostrarDialogoGuardar2Dispositivos();
        } else {
            if ((nombrePaciente != null && !nombrePaciente.isEmpty()) ||
                    (nombrePaciente2 != null && !nombrePaciente2.isEmpty())) {
                guardarConfigActual();
            }
        }
    }

    private void guardarConfigActual() {
        if (DNIpaciente != null && !DNIpaciente.isEmpty()) {
            int intensidad = Integer.parseInt(Param3.getText().toString());
            int tiempo = Integer.parseInt(Param4.getText().toString());
            pacienteController.guardarConfiguracion(DNIpaciente, intensidad, tiempo);
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show();
        }
    }

    // ========================
    // DIÁLOGOS
    // ========================

    private void mostrarDialogoSeleccionarPacienteADispositivo(Intent intent) {
        LayoutInflater inflater = getLayoutInflater();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("A que dispositivo conectar el Paciente?");
        builder.setPositiveButton("Dispositivo1", (dialog, which) -> {
            if (opcionDispositivoInt == 3) {
                DNIpaciente2 = "";
                nombreDispositivoPac2.setVisibility(View.GONE);
                nombrePaciente2 = "";
                Param10.setText("10");
                Param12.setText("30");
                Param13.setText("30");
            }

            //opcionDispositivoInt = 1;
            if (Param3 != null) {
                intent.putExtra("INTENSIDAD", Param3.getText());
            }
            if (Param4 != null) {
                intent.putExtra("TIEMPO", Param4.getText());
            }


            intent.putExtra("NOMBRE_PACIENTE", nombrePaciente);
            intent.putExtra("DNI_PACIENTE", DNIpaciente);
            intent.putExtra("DISPOSITIVO_ELEC", 1);

            if (DNIpaciente2 != null && !DNIpaciente2.isEmpty()) {
                intent.putExtra("DNI_PAC_OTRODISP", DNIpaciente2);
            }

            // Iniciar actividad esperando resultado
            startActivityForResult(intent, PACIENTE_REQUEST_CODE);
        });

        builder.setNegativeButton("Dispositivo2", (dialog, which) -> {
            if (opcionDispositivoInt == 3) {
                DNIpaciente = "";
                nombreDispositivoPac1.setVisibility(View.GONE);
                nombrePaciente = "";
                Param3.setText("10");
                Param4.setText("30");
                Param5.setText("30");
            }
            //opcionDispositivoInt = 2;
            if (Param10 != null) {
                intent.putExtra("INTENSIDAD", Param10.getText());
            }
            if (Param12 != null) {
                intent.putExtra("TIEMPO", Param12.getText());
            }


            intent.putExtra("NOMBRE_PACIENTE", nombrePaciente2);
            intent.putExtra("DNI_PACIENTE", DNIpaciente2);
            intent.putExtra("DISPOSITIVO_ELEC", 2);


            if (DNIpaciente != null && !DNIpaciente.isEmpty()) {
                intent.putExtra("DNI_PAC_OTRODISP", DNIpaciente);
            }

            // Iniciar actividad esperando resultado
            startActivityForResult(intent, PACIENTE_REQUEST_CODE);

        });
        builder.setNeutralButton("Todos los dispositivos", (dialog, which) -> {
            //opcionDispositivoInt = 3; //esto despues causa problemas

            if (Param3 != null) {
                intent.putExtra("INTENSIDAD", Param3.getText());
            }
            if (Param4 != null) {
                intent.putExtra("TIEMPO", Param4.getText());
            }

            if (nombrePaciente != null && !nombrePaciente.isEmpty() &&
                    DNIpaciente != null && !DNIpaciente.isEmpty()) {
                intent.putExtra("NOMBRE_PACIENTE", nombrePaciente);
                intent.putExtra("DNI_PACIENTE", DNIpaciente);
            }

            if (nombrePaciente2 != null && !nombrePaciente2.isEmpty() &&
                    DNIpaciente2 != null && !DNIpaciente2.isEmpty()) {
                intent.putExtra("NOMBRE_PACIENTE", nombrePaciente2);
                intent.putExtra("DNI_PACIENTE", DNIpaciente2);


            }
            intent.putExtra("DISPOSITIVO_ELEC", 3);
            // Iniciar actividad esperando resultado
            startActivityForResult(intent, PACIENTE_REQUEST_CODE);
        });

        builder.create().show();
    }
    private void mostrarDialogoDesconectar() {
        String[] opciones;
        if (dispositivo1.isConnected() && dispositivo2.isConnected()) {
            opciones = new String[]{"Dispositivo 1", "Dispositivo 2"};
        } else if (dispositivo1.isConnected()) {
            opciones = new String[]{"Dispositivo 1"};
        } else {
            opciones = new String[]{"Dispositivo 2"};
        }

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar dispositivo a desconectar")
                .setItems(opciones, (dialog, which) -> {
                    String seleccion = opciones[which];
                    if (seleccion.equals("Dispositivo 1")) {
                        desconectarDispositivo(dispositivo1, Conexion, InicioPulsos,
                                FinPulsos, dispBluetoothNom1);
                    } else {
                        desconectarDispositivo(dispositivo2, Conexion2, InicioPulsos2,
                                FinPulsos2, dispBluetoothNom2);
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void desconectarDispositivo(DispositivoState disp, Button btnConexion,
                                        Button btnInicio, Button btnFin, TextView lblNombre) {
        tratamientoController.finalizarSesion(disp);
        try {
            if (disp.getBtSocket() != null) disp.getBtSocket().close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        disp.resetCompleto();

        UIHelper.resetButtonToDefault(btnConexion);
        btnConexion.setText("Conectar");
        btnInicio.setEnabled(false);
        UIHelper.setButtonColor(btnInicio, UIHelper.COLOR_DESHABILITADO);
        btnInicio.setText("Iniciar");
        UIHelper.resetButtonToDefault(btnFin);
        lblNombre.setText("");
        lblNombre.setVisibility(View.GONE);
    }

    private void mostrarDialogoGuardar2Dispositivos() {
        // TODO: Implementar diálogo para guardar config de 2 dispositivos
        Toast.makeText(this, "Guardar config 2 dispositivos", Toast.LENGTH_SHORT).show();
    }

    // ========================
    // ACTIVITY RESULT (datos de VentanaPaciente)
    // ========================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PACIENTE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            int opcionNuevo = data.getIntExtra("DISPOSITIVO_ELEC", 0);

            // Cargar datos del paciente según dispositivo
            if (opcionNuevo == 1 || opcionNuevo == 3) {
                int intensidad = data.getIntExtra("INTENSIDAD", 0);
                int tiempo = data.getIntExtra("TIEMPO", 0);
                nombrePaciente = data.getStringExtra("NOMBRE_PACIENTE");
                DNIpaciente = data.getStringExtra("DNI_PACIENTE");

                if (Param3 != null) Param3.setText(String.valueOf(intensidad));
                if (Param4 != null) Param4.setText(String.valueOf(tiempo));
                if (Param5 != null) Param5.setText(String.valueOf(tiempo));
                if (nombreDispositivoPac1 != null){
                    nombreDispositivoPac1.setText(nombrePaciente);
                    nombreDispositivoPac1.setVisibility(View.VISIBLE);
                }
            }

            if (opcionNuevo == 2 || opcionNuevo == 3) {
                int intensidad2 = data.getIntExtra("INTENSIDAD2", 0);
                int tiempo2 = data.getIntExtra("TIEMPO2", 0);
                if (opcionNuevo == 2) {
                    nombrePaciente2 = data.getStringExtra("NOMBRE_PACIENTE");
                    DNIpaciente2 = data.getStringExtra("DNI_PACIENTE");
                } else {
                    nombrePaciente2 = nombrePaciente;
                    DNIpaciente2 = DNIpaciente;
                }

                if (Param10 != null) Param10.setText(String.valueOf(intensidad2 > 0 ? intensidad2 : data.getIntExtra("INTENSIDAD", 0)));
                if (Param12 != null) Param12.setText(String.valueOf(tiempo2 > 0 ? tiempo2 : data.getIntExtra("TIEMPO", 0)));
                if (Param13 != null) Param13.setText(String.valueOf(tiempo2 > 0 ? tiempo2 : data.getIntExtra("TIEMPO", 0)));
                if (nombreDispositivoPac2 != null){
                    nombreDispositivoPac2.setText(nombrePaciente2);
                    nombreDispositivoPac2.setVisibility(View.VISIBLE);
                }
            }

            if (opcionNuevo > 0) {
                opcionDispositivoInt = opcionNuevo;
            }
        }
    }

    // ========================
    // LOGOUT
    // ========================

    public void logout() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isLoggedIn", false);
        editor.putString("username", "");
        editor.apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}