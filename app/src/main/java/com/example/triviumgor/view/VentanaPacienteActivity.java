package com.example.triviumgor.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.triviumgor.R;
import com.example.triviumgor.controller.PacienteController;
import com.example.triviumgor.controller.UsuarioController;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Paciente;

import java.util.List;

import android.os.Handler;

public class VentanaPacienteActivity extends AppCompatActivity {

    // Estado de edición
    private boolean editando = false;
    private int idEditando = -1;

    // Vistas principales
    private Button btnVerHistorico, verList, crearPac;
    private TextView nomSelPaciente;

    // Lista
    private ListView vieLista;
    private RelativeLayout verLista;
    private Button btnBuscar;

    // Formulario editable
    private ScrollView pacienteScrollView;
    private EditText editDNI, editNombre, editApellido1, editApellido2, editEdad;
    private RadioGroup radioGroupGenero;
    RadioButton radioMasculino;
    RadioButton radioFemenino;
    private EditText editPatologia, editMedicacion, editCIC;
    private EditText editIntensidad, editTiempo;
    private EditText editIntensidad2, editTiempo2;
    private View tilIntensidad2, tilTiempo2;
    private Button btnGuardar;

    // Detalles paciente
    private ScrollView detallesScrollView;
    private RelativeLayout detallesPacienteLayout;
    private TextView tvDNI, tvCIC, tvNombreCompleto, tvPatologia, tvMedicacion, tvEdad, tvGenero;
    private TextView tvIntensidad, tvTiempo, tvIntensidad2, tvTiempo2;
    private TextView tvCreadoPor;
    private View dividerCreador;
    private Button btnEditar, btnBorrar, btnIniciarTratamiento;

    private int pacienteSeleccionadoId = -1;
    private String[] pacientesNombres;
    private int[] pacientesIds;
    private boolean filtrado = false;

    // Datos recibidos de MainActivity
    private String nomPacienteDado;
    private String DNIpacienteDado;
    private int optionDis = -1;
    private String DNI_otroDisp;

    // Controller y DataManager
    private PacienteDataManager dataManager;
    private PacienteController pacienteController;
    private UsuarioController usuarioController;

    // Usuario logueado
    private int idUsuarioActual = -1;  // Para listar pacientes (-1 = todos, usado por admin)
    private int idUsuarioReal = -1;    // ID real del usuario, siempre el verdadero (para vincular creador)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ventana_paciente);

        // Inicializar base de datos
        try {
            dataManager = new PacienteDataManager(this);
            if (!dataManager.open()) {
                Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        } catch (Exception e) {
            Log.e("VentanaPaciente", "Error al inicializar dataManager: " + e.getMessage());
            finish();
            return;
        }

        // Inicializar controller
        pacienteController = new PacienteController(dataManager);
        usuarioController = new UsuarioController(this, dataManager);

        // Obtener el ID del usuario logueado y si es admin, sera -1 (para leer todos los pacientes)
        idUsuarioReal = getSharedPreferences("LoginPrefs", MODE_PRIVATE).getInt("userId", -1);
        idUsuarioActual = usuarioController.esAdmin()
                ? -1
                : idUsuarioReal;

        // Inicializar vistas básicas
        nomSelPaciente = findViewById(R.id.nombrePaciente);
        verLista = findViewById(R.id.listaLayout);
        vieLista = findViewById(R.id.listaPacientes);
        pacienteScrollView = findViewById(R.id.PacienteScrollView);
        crearPac = findViewById(R.id.crear_paciente);
        detallesScrollView = findViewById(R.id.detallesScrollView);

        // Configurar botones principales
        verList = findViewById(R.id.boton_verLista);
        verList.setOnClickListener(v -> {
            actualizarListaPacientes();
            detallesScrollView.setVisibility(View.GONE);
            verLista.setVisibility(View.VISIBLE);
            pacienteScrollView.setVisibility(View.GONE);
        });

        crearPac.setOnClickListener(v -> {
            limpiarCampos();
            editando = false;
            idEditando = -1;
            nomSelPaciente.setText("");
            detallesScrollView.setVisibility(View.GONE);
            verLista.setVisibility(View.GONE);
            pacienteScrollView.setVisibility(View.VISIBLE);
            configurarVisibilidadDisp2Editable();
        });

        // Inicialización diferida
        new Handler().post(() -> {
            try {
                inicializarComponentes();
            } catch (Exception e) {
                Log.e("VentanaPaciente", "Error en inicialización: " + e.getMessage());
                Toast.makeText(this, "Error al cargar datos de pacientes", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void inicializarComponentes() {
        inicializarCampos();
        inicializarDetallesPaciente();

        btnBuscar = findViewById(R.id.botonBuscar);
        btnBuscar.setOnClickListener(v -> mostrarDialogoFiltrar());

        // Adaptador inicial
        pacientesNombres = new String[]{"Cargando pacientes..."};
        vieLista.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, pacientesNombres));

        // Click en paciente de la lista
        vieLista.setOnItemClickListener((parent, view, position, id) -> {
            if (pacientesNombres.length == 1 && pacientesNombres[0].equals("No hay pacientes registrados")) {
                return;
            }

            String nombrePac = vieLista.getAdapter().getItem(position).toString();
            nombrePac = nombrePac.substring(nombrePac.indexOf("-") + 1);
            Toast.makeText(this, "Seleccionaste: " + nombrePac, Toast.LENGTH_SHORT).show();
            nomSelPaciente.setText(nombrePac);
            verLista.setVisibility(View.INVISIBLE);

            // Obtener ID real
            if (filtrado) {
                String nombreFiltrado = vieLista.getAdapter().getItem(position).toString();
                actualizarListaPacientes();
                pacienteSeleccionadoId = -1;
                for (int i = 0; i < pacientesNombres.length; i++) {
                    if (nombreFiltrado.equals(pacientesNombres[i])) {
                        pacienteSeleccionadoId = pacientesIds[i];
                        break;
                    }
                }
            } else {
                pacienteSeleccionadoId = (position < pacientesIds.length) ? pacientesIds[position] : -1;
            }

            if (pacienteSeleccionadoId != -1) {
                cargarDetallesPaciente(pacienteSeleccionadoId);
                detallesScrollView.setVisibility(View.VISIBLE);
                verLista.setVisibility(View.GONE);
                pacienteScrollView.setVisibility(View.GONE);
            }
        });

        actualizarListaPacientes();
        procesarExtras();
    }

    // ========================
    // CAMPOS DE FORMULARIO
    // ========================

    private void inicializarCampos() {
        editDNI = findViewById(R.id.editDNI);
        editNombre = findViewById(R.id.editNombre);
        editApellido1 = findViewById(R.id.editApellido1);
        editApellido2 = findViewById(R.id.editApellido2);
        editEdad = findViewById(R.id.editEdad);

        radioGroupGenero = findViewById(R.id.radioGroupGenero);
        radioMasculino = findViewById(R.id.radioMasculino);
        radioFemenino = findViewById(R.id.radioFemenino);

        editPatologia = findViewById(R.id.editPatologia);
        editMedicacion = findViewById(R.id.editMedicacion);
        editCIC = findViewById(R.id.editCIC);
        editIntensidad = findViewById(R.id.editIntensidad);
        editTiempo = findViewById(R.id.editTiempo);
        editIntensidad2 = findViewById(R.id.editIntensidad2);
        editTiempo2 = findViewById(R.id.editTiempo2);
        tilIntensidad2 = findViewById(R.id.tilIntensidad2);
        tilTiempo2 = findViewById(R.id.tilTiempo2);
        btnGuardar = findViewById(R.id.btnGuardar);

        btnGuardar.setOnClickListener(v -> guardarPaciente());

        // Filtro: solo letras en nombre y apellidos
        InputFilter filtroSoloLetras = (source, start, end, dest, dstart, dend) -> {
            for (int i = start; i < end; i++) {
                if (Character.isDigit(source.charAt(i))) return "";
            }
            return null;
        };
        editNombre.setFilters(new InputFilter[]{filtroSoloLetras});
        editApellido1.setFilters(new InputFilter[]{filtroSoloLetras});
        editApellido2.setFilters(new InputFilter[]{filtroSoloLetras});
    }

    private void guardarPaciente() {
        String dni = editDNI.getText().toString().trim();
        String nombre = editNombre.getText().toString().trim();
        String apellido1 = editApellido1.getText().toString().trim();
        String apellido2 = editApellido2.getText().toString().trim();
        String edad = editEdad.getText().toString().trim();
        String patologia = editPatologia.getText().toString().trim();
        String medicacion = editMedicacion.getText().toString().trim();
        String cic = editCIC.getText().toString().trim();
        String intensidadStr = editIntensidad.getText().toString().trim();
        String tiempoStr = editTiempo.getText().toString().trim();
        String intensidadStr2 = editIntensidad2.getText().toString().trim();
        String tiempoStr2 = editTiempo2.getText().toString().trim();

        String genero = "";
        int selectedId = radioGroupGenero.getCheckedRadioButtonId();

        if (selectedId == R.id.radioMasculino) {
            genero = "MASCULINO";
        } else if (selectedId == R.id.radioFemenino) {
            genero = "FEMENINO";
        }

        PacienteController.Resultado resultado;

        if (editando) {
            if (optionDis == 3) {
                resultado = pacienteController.actualizarPaciente2disp(idEditando, dni, nombre,
                        apellido1, apellido2, edad, genero, patologia, medicacion,
                        intensidadStr, tiempoStr, intensidadStr2, tiempoStr2, cic);
            } else {
                resultado = pacienteController.actualizarPaciente(idEditando, dni, nombre,
                        apellido1, apellido2, edad, genero, patologia, medicacion,
                        intensidadStr, tiempoStr, cic);
            }
        } else {
            if (optionDis == 3) {
                resultado = pacienteController.guardarPaciente2disp(idUsuarioReal,dni, nombre, apellido1, apellido2, edad, genero,
                        patologia, medicacion,
                        intensidadStr, tiempoStr, intensidadStr2, tiempoStr2, cic);
            } else {
                resultado = pacienteController.guardarPaciente(idUsuarioReal,dni, nombre, apellido1, apellido2, edad, genero,
                        patologia, medicacion,
                        intensidadStr, tiempoStr, cic);
            }
        }

        Toast.makeText(this, resultado.mensaje, Toast.LENGTH_SHORT).show();

        if (resultado.exito) {
            // Capturamos datos del formulario ANTES de limpiar
            final String _dni        = dni;
            final String _nombre     = nombre;
            final String _ap1        = apellido1;
            final String _ap2        = apellido2;
            final String _edad       = edad;
            final String _cic        = cic;
            final String _patologia  = patologia;
            final String _medicacion = medicacion;
            final String _intensidad = intensidadStr;
            final String _tiempo     = tiempoStr;
            final String _genero     = genero;

            pacienteSeleccionadoId = (int) resultado.id;

            limpiarCampos();
            editando = false;
            idEditando = -1;
            actualizarListaPacientes();

            // Rellenamos la ficha directamente con los datos del formulario,
            // sin hacer query a la BD (evita problema de timing con fuse/WAL)
            nomSelPaciente.setText(_nombre + " " + _ap1);
            tvDNI.setText("DNI: " + _dni);
            tvCIC.setText("CIC: " + (_cic.isEmpty() ? "-" : _cic));
            tvNombreCompleto.setText("Nombre: " + _nombre + " " + _ap1
                    + (_ap2.isEmpty() ? "" : " " + _ap2));
            tvEdad.setText("Edad: " + (_edad.isEmpty() ? "-" : _edad));
            tvGenero.setText("Genero: " + (_genero.isEmpty() ? "-" : _genero));
            tvPatologia.setText("Patología: " + (_patologia.isEmpty() ? "-" : _patologia));
            tvMedicacion.setText("Medicación: " + (_medicacion.isEmpty() ? "-" : _medicacion));
            tvIntensidad.setText("Intensidad: " + (_intensidad.isEmpty() ? "-" : _intensidad));
            tvTiempo.setText("Tiempo (min): " + (_tiempo.isEmpty() ? "-" : _tiempo));
            tvIntensidad2.setVisibility(View.GONE);
            tvTiempo2.setVisibility(View.GONE);
            tvCreadoPor.setVisibility(View.GONE);
            dividerCreador.setVisibility(View.GONE);

            pacienteScrollView.setVisibility(View.GONE);
            verLista.setVisibility(View.GONE);
            detallesScrollView.setVisibility(View.VISIBLE);
        }
    }

    private void limpiarCampos() {
        editDNI.setText("");
        editNombre.setText("");
        editApellido1.setText("");
        editApellido2.setText("");
        editEdad.setText("");
        radioGroupGenero.clearCheck();
        editPatologia.setText("");
        editMedicacion.setText("");
        editCIC.setText("");
        editIntensidad.setText("");
        editTiempo.setText("");
        editIntensidad2.setText("");
        editTiempo2.setText("");
    }

    // ========================
    // LISTA DE PACIENTES
    // ========================

    private void actualizarListaPacientes() {
        List<com.example.triviumgor.model.Paciente> lista = (idUsuarioActual == -1)
                ? pacienteController.obtenerTodosPacientes()
                : pacienteController.obtenerPacientesDeUsuario(idUsuarioActual);
        if (lista.isEmpty()) {
            pacientesNombres = new String[]{"No hay pacientes registrados"};
            pacientesIds = new int[0];
        } else {
            pacientesNombres = new String[lista.size()];
            pacientesIds = new int[lista.size()];
            for (int i = 0; i < lista.size(); i++) {
                com.example.triviumgor.model.Paciente p = lista.get(i);
                pacientesNombres[i] = "DNI: " + p.getDNI() + " - " + p.getNombre() + " " + p.getAp1();
                if (p.getAp2() != null && !p.getAp2().isEmpty())
                    pacientesNombres[i] += " " + p.getAp2();
                pacientesIds[i] = p.getID();
            }
        }
        vieLista.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, pacientesNombres));
        filtrado = false;
    }

    // ========================
    // DETALLES PACIENTE
    // ========================

    private void inicializarDetallesPaciente() {
        detallesPacienteLayout = findViewById(R.id.detallesPacienteLayout);
        tvDNI = findViewById(R.id.tvDNI);
        tvCIC = findViewById(R.id.tvCIC);
        tvNombreCompleto = findViewById(R.id.tvNombreCompleto);
        tvEdad = findViewById(R.id.tvEdad);
        tvGenero = findViewById(R.id.tvGenero);
        tvPatologia = findViewById(R.id.tvPatologia);
        tvMedicacion = findViewById(R.id.tvMedicacion);
        tvIntensidad = findViewById(R.id.tvIntensidad);
        tvTiempo = findViewById(R.id.tvTiempo);
        tvIntensidad2 = findViewById(R.id.tvIntensidad2);
        tvTiempo2 = findViewById(R.id.tvTiempo2);
        btnIniciarTratamiento = findViewById(R.id.btnIniciarTratamiento);
        btnEditar = findViewById(R.id.editarPaciente);
        btnVerHistorico = findViewById(R.id.btnVerHistorico);
        btnBorrar = findViewById(R.id.borrarPaciente);
        tvCreadoPor = findViewById(R.id.tvCreadoPor);
        dividerCreador = findViewById(R.id.dividerCreador);

        btnIniciarTratamiento.setOnClickListener(v -> {
            if (DNI_otroDisp != null && !DNI_otroDisp.isEmpty()) {
                Paciente pac = pacienteController.obtenerPacientePorId(pacienteSeleccionadoId, optionDis);
                if (pac != null && DNI_otroDisp.equals(pac.getDNI())) {
                    mostrarDialogoMismoPaciente();
                } else {
                    cerrarYEnviarInfo();
                }
            } else {
                cerrarYEnviarInfo();
            }
        });

        btnEditar.setOnClickListener(v -> {
            if (pacienteSeleccionadoId != -1) {
                Paciente pac = pacienteController.obtenerPacientePorId(pacienteSeleccionadoId, optionDis);
                if (pac == null) return;

                detallesScrollView.setVisibility(View.GONE);
                verLista.setVisibility(View.GONE);
                pacienteScrollView.setVisibility(View.VISIBLE);

                editando = true;
                idEditando = pac.getID();

                editDNI.setText(pac.getDNI());
                editNombre.setText(pac.getNombre());
                editApellido1.setText(pac.getAp1());
                if (pac.getAp2() != null) editApellido2.setText(pac.getAp2());
                // La edad es opcional; 0 se considera "sin especificar" y deja el campo vacío.
                if (pac.getEdad() > 0) editEdad.setText(String.valueOf(pac.getEdad()));
                if (pac.getPatologia() != null) editPatologia.setText(pac.getPatologia());
                if (pac.getGenero() != null) {
                    if (pac.getGenero() == Paciente.Genero.MASCULINO) {
                        radioMasculino.setChecked(true);
                    } else {
                        radioFemenino.setChecked(true);
                    }
                } else {
                    radioGroupGenero.clearCheck(); // ninguno marcado
                }
                if (pac.getMedicacion() != null) editMedicacion.setText(pac.getMedicacion());
                if (pac.getCIC() != null) editCIC.setText(pac.getCIC());
                if (pac.getIntensidad() >= 0) editIntensidad.setText(String.valueOf(pac.getIntensidad()));
                if (pac.getTiempoM() >= 0) editTiempo.setText(String.valueOf(pac.getTiempoM()));

                if (optionDis == 3) {
                    editIntensidad2.setText(String.valueOf(pac.getIntensidad2()));
                    editTiempo2.setText(String.valueOf(pac.getTiempoM2()));
                }
                configurarVisibilidadDisp2Editable();
            }
        });

        btnVerHistorico.setOnClickListener(v -> {
            if (pacienteSeleccionadoId != -1) {
                Paciente paciente = pacienteController.obtenerPacientePorId(pacienteSeleccionadoId, optionDis);
                if (paciente != null) {
                    Intent intent = new Intent(this, HistorialSesionesActivity.class);
                    intent.putExtra("PACIENTE_ID", pacienteSeleccionadoId);
                    intent.putExtra("NOMBRE_PACIENTE", paciente.getNombreCompleto());
                    startActivity(intent);
                }
            } else {
                Toast.makeText(this, "Seleccione un paciente primero", Toast.LENGTH_SHORT).show();
            }
        });

        btnBorrar.setOnClickListener(v -> {
            PacienteController.Resultado resultado = pacienteController.eliminarPaciente(pacienteSeleccionadoId);
            Toast.makeText(this, resultado.mensaje, Toast.LENGTH_SHORT).show();

            if (resultado.exito) {
                actualizarListaPacientes();
                nomSelPaciente.setText("");
                detallesScrollView.setVisibility(View.GONE);
                verLista.setVisibility(View.VISIBLE);
                pacienteScrollView.setVisibility(View.GONE);
            }
        });
    }

    private void cargarDetallesPaciente(int pacienteId) {
        Paciente paciente = pacienteController.obtenerPacientePorId(pacienteId, optionDis);
        if (paciente == null) return;

        tvDNI.setText("DNI: " + paciente.getDNI());
        tvCIC.setText("CIC: " + paciente.getCIC());
        tvNombreCompleto.setText("Nombre: " + paciente.getNombreCompleto());
        tvEdad.setText("Edad: " + (paciente.getEdad() > 0 ? paciente.getEdad() : "--"));
        tvGenero.setText("Genero: " + paciente.getGenero().getEtiqueta());
        tvPatologia.setText("Patología: " + paciente.getPatologia());
        tvMedicacion.setText("Medicación: " + paciente.getMedicacion());
        tvIntensidad.setText("Intensidad: " + paciente.getIntensidad());
        tvTiempo.setText("Tiempo (min): " + paciente.getTiempoM());

        if (optionDis == 3) {
            tvIntensidad2.setText("Intensidad2: " + paciente.getIntensidad2());
            tvTiempo2.setText("Tiempo2 (min): " + paciente.getTiempoM2());
            tvIntensidad2.setVisibility(View.VISIBLE);
            tvTiempo2.setVisibility(View.VISIBLE);
        } else {
            tvIntensidad2.setVisibility(View.GONE);
            tvTiempo2.setVisibility(View.GONE);
        }

        // Mostrar info del creador solo si el usuario es admin
        if (usuarioController.esAdmin()) {
            String infoCreador = pacienteController.obtenerInfoCreador(pacienteId);
            if (infoCreador != null) {
                tvCreadoPor.setText("📝 Creado por: " + infoCreador);
                tvCreadoPor.setVisibility(View.VISIBLE);
                dividerCreador.setVisibility(View.VISIBLE);
            } else {
                tvCreadoPor.setText("📝 Creador: Sin registro");
                tvCreadoPor.setVisibility(View.VISIBLE);
                dividerCreador.setVisibility(View.VISIBLE);
            }
        } else {
            tvCreadoPor.setVisibility(View.GONE);
            dividerCreador.setVisibility(View.GONE);
        }
    }

    // ========================
    // FILTRADO
    // ========================

    private void mostrarDialogoFiltrar() {
        String[] campos = {"Nombre", "Apellidos", "DNI", "CIC", "Patologia"};

        View dialogView = getLayoutInflater().inflate(R.layout.filtrar_dialog, null);
        Spinner spinner = dialogView.findViewById(R.id.spinnerCampoFiltro);
        EditText editText = dialogView.findViewById(R.id.editTextEntrada);

        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, campos));

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                editText.setInputType(campos[pos].equals("CIC")
                        ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(this)
                .setTitle("Filtrar lista")
                .setView(dialogView)
                .setPositiveButton("Filtrar", (dialog, which) -> {
                    String filtro = editText.getText().toString().toLowerCase().trim();
                    String campo = spinner.getSelectedItem().toString();

                    List<String> resultados = pacienteController.filtrarPacientes(filtro, campo);
                    vieLista.setAdapter(new ArrayAdapter<>(this,
                            android.R.layout.simple_list_item_1, resultados));
                    filtrado = true;

                    Toast.makeText(this,
                            filtro.isEmpty() ? "Mostrando todos los elementos"
                                    : "Se encontraron " + resultados.size() + " resultados",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .create().show();
    }

    // ========================
    // EXTRAS Y NAVEGACIÓN
    // ========================

    private void procesarExtras() {
        Bundle extras = getIntent().getExtras();
        boolean isVista = false;
        boolean isCrear = false;

        if (extras != null) {
            nomPacienteDado = extras.getString("NOMBRE_PACIENTE", "");
            DNIpacienteDado = extras.getString("DNI_PACIENTE", "");
            optionDis = extras.getInt("DISPOSITIVO_ELEC", 0);
            DNI_otroDisp = extras.getString("DNI_PAC_OTRODISP", "");
            isVista = extras.getBoolean("verSoloLista", false);
            isCrear = extras.getBoolean("CREAR_PACIENTE", false);
        }

        if (nomPacienteDado != null && !nomPacienteDado.isEmpty()
                && DNIpacienteDado != null && !DNIpacienteDado.isEmpty()) {
            int posicionDado = -1;
            for (int i = 0; i < pacientesNombres.length; i++) {
                if (pacientesNombres[i].contains(nomPacienteDado)
                        && pacientesNombres[i].contains(DNIpacienteDado)) {
                    posicionDado = i;
                }
            }
            if (posicionDado >= 0) {
                String nombrePacDado = pacientesNombres[posicionDado];
                nombrePacDado = nombrePacDado.substring(nombrePacDado.indexOf("-") + 1);
                nomSelPaciente.setText(nombrePacDado);
                pacienteSeleccionadoId = pacientesIds[posicionDado];
                cargarDetallesPaciente(pacienteSeleccionadoId);
                detallesScrollView.setVisibility(View.VISIBLE);
                verLista.setVisibility(View.GONE);
                pacienteScrollView.setVisibility(View.GONE);
            }
        } else {
            detallesScrollView.setVisibility(View.GONE);
            verLista.setVisibility(View.GONE);
            pacienteScrollView.setVisibility(View.GONE);
            nomSelPaciente.setText("No hay Paciente Seleccionado");
        }

        if (isVista) {
            btnIniciarTratamiento.setVisibility(View.GONE);
            detallesScrollView.setVisibility(View.GONE);
            verLista.setVisibility(View.VISIBLE);
            pacienteScrollView.setVisibility(View.GONE);
        } else if(isCrear){
            btnIniciarTratamiento.setVisibility(View.GONE);
            detallesPacienteLayout.setVisibility(View.GONE);
            verLista.setVisibility(View.GONE);
            pacienteScrollView.setVisibility(View.VISIBLE);
        }else {
            btnIniciarTratamiento.setVisibility(View.VISIBLE);
        }
    }

    private void mostrarDialogoMismoPaciente() {
        new AlertDialog.Builder(this)
                .setTitle("Ya Esta Inicializado")
                .setMessage("Hemos descubierto que este Paciente ya esta Inicializado en el otro Dispositivo.\n" +
                        "*Los cambios que hayas hecho en el otro dispositivo se reiniciaran al conectar ambos dispositivos")
                .setPositiveButton("Elegir otro Paciente", (dialog, which) -> {
                    detallesScrollView.setVisibility(View.GONE);
                    verLista.setVisibility(View.VISIBLE);
                    pacienteScrollView.setVisibility(View.GONE);
                    nomSelPaciente.setText("");
                    dialog.dismiss();
                })
                .setNegativeButton("Conectar ambos dispositivos", (dialog, which) -> {
                    optionDis = 3;
                    cerrarYEnviarInfo();
                })
                .create().show();
    }

    private void cerrarYEnviarInfo() {
        if (pacienteSeleccionadoId == -1) return;

        Paciente paciente = pacienteController.obtenerPacientePorId(pacienteSeleccionadoId, optionDis);
        if (paciente == null) return;

        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.putExtra("INTENSIDAD", paciente.getIntensidad());
        resultIntent.putExtra("TIEMPO", paciente.getTiempoM());
        resultIntent.putExtra("NOMBRE_PACIENTE", paciente.getNombre() + " " + paciente.getAp1());
        resultIntent.putExtra("DNI_PACIENTE", paciente.getDNI());
        resultIntent.putExtra("DISPOSITIVO_ELEC", optionDis);
        resultIntent.putExtra("INTENSIDAD2", paciente.getIntensidad2());
        resultIntent.putExtra("TIEMPO2", paciente.getTiempoM2());

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void configurarVisibilidadDisp2Editable() {
        if (optionDis == 3) {
            tilIntensidad2.setVisibility(View.VISIBLE);
            tilTiempo2.setVisibility(View.VISIBLE);
        } else {
            tilIntensidad2.setVisibility(View.GONE);
            tilTiempo2.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataManager != null) dataManager.close();
    }
}