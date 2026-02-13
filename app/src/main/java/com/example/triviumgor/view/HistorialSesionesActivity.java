package com.example.triviumgor.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.triviumgor.R;
import com.example.triviumgor.controller.SesionController;
import com.example.triviumgor.database.PacienteDataManager;
import com.example.triviumgor.model.Sesion;

import java.util.ArrayList;
import java.util.List;

public class HistorialSesionesActivity extends AppCompatActivity {

    private TextView tvTitulo;
    private TextView tvNombrePaciente;
    private TextView tvInfoHistorial;
    private ListView listViewSesiones;
    private Button btnVolver;

    private PacienteDataManager dataManager;
    private SesionController sesionController;
    private List<Sesion> sesiones;
    private List<SesionController.GrupoDia> grupos;
    private int pacienteId;
    private String nombrePaciente;
    private boolean mostrandoDetalleDia = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.historial_sesiones);

        tvTitulo = findViewById(R.id.tvTitulo);
        tvNombrePaciente = findViewById(R.id.tvNombrePaciente);
        tvInfoHistorial = findViewById(R.id.tvInfoHistorial);
        listViewSesiones = findViewById(R.id.listViewSesiones);
        btnVolver = findViewById(R.id.btnVolver);

        Intent intent = getIntent();
        pacienteId = intent.getIntExtra("PACIENTE_ID", -1);
        nombrePaciente = intent.getStringExtra("NOMBRE_PACIENTE");

        if (pacienteId == -1 || nombrePaciente == null) {
            Toast.makeText(this, "Error: datos del paciente no encontrados", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvTitulo.setText("Histórico de Sesiones");
        tvNombrePaciente.setText("Paciente: " + nombrePaciente);

        dataManager = new PacienteDataManager(this);
        if (!dataManager.open()) {
            Toast.makeText(this, "Error al abrir la base de datos", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sesionController = new SesionController(dataManager);
        cargarSesiones();

        listViewSesiones.setOnItemClickListener((parent, view, position, id) -> {
            if (!mostrandoDetalleDia) {
                // Click en fecha → mostrar sesiones de ese día
                if (grupos != null && position < grupos.size()) {
                    SesionController.GrupoDia grupo = grupos.get(position);
                    List<Sesion> sesionesDelDia = sesiones.subList(grupo.indiceInicio, grupo.indiceFin);
                    SesionAdapter adapter = new SesionAdapter(sesionesDelDia);
                    listViewSesiones.setAdapter(adapter);
                    mostrandoDetalleDia = true;
                }
            } else {
                // Click en sesión → mostrar detalles
                Sesion sesion = (Sesion) listViewSesiones.getAdapter().getItem(position);
                mostrarDetallesSesion(sesion);
            }
        });

        listViewSesiones.setOnItemLongClickListener((adapterView, view, i, l) -> {
            if (mostrandoDetalleDia) {
                Sesion sesion = (Sesion) listViewSesiones.getAdapter().getItem(i);
                dialogoBorrarSesion(sesion);
                return true;
            }
            return false;
        });

        btnVolver.setOnClickListener(v -> {
            if (mostrandoDetalleDia) {
                mostrarVistaAgrupada();
            } else {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dataManager != null) dataManager.close();
    }

    private void cargarSesiones() {
        try {
            sesiones = sesionController.obtenerSesionesPaciente(pacienteId);
            tvInfoHistorial.setText("Sesiones registradas: " + sesiones.size());

            if (sesiones.isEmpty()) {
                ArrayList<String> mensajes = new ArrayList<>();
                mensajes.add("No hay sesiones registradas para este paciente");
                listViewSesiones.setAdapter(new ArrayAdapter<>(this,
                        android.R.layout.simple_list_item_1, mensajes));
                return;
            }

            mostrarVistaAgrupada();
        } catch (Exception e) {
            Log.e("HistorialSesiones", "Error al cargar sesiones: " + e.getMessage());
            Toast.makeText(this, "Error al cargar sesiones", Toast.LENGTH_SHORT).show();
        }
    }

    private void mostrarVistaAgrupada() {
        grupos = sesionController.agruparPorDia(sesiones);
        List<String> fechas = sesionController.obtenerFechasAgrupadas(grupos);
        listViewSesiones.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, fechas));
        mostrandoDetalleDia = false;
    }

    private void mostrarDetallesSesion(Sesion sesion) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.detalle_sesion, null);
        builder.setView(dialogView);

        TextView tvFechaCompleta = dialogView.findViewById(R.id.tvFechaCompleta);
        TextView tvHora = dialogView.findViewById(R.id.tvHora);
        TextView tvDispositivo = dialogView.findViewById(R.id.tvDispositivo);
        TextView tvIntensidad = dialogView.findViewById(R.id.tvIntensidad);
        TextView tvTiempo = dialogView.findViewById(R.id.tvTiempo);
        Button btnCerrar = dialogView.findViewById(R.id.btnCerrar);
        Button btnBorrarS = dialogView.findViewById(R.id.tvBTNBorrar);

        tvFechaCompleta.setText(sesionController.formatearFecha(sesion.getFecha()));
        tvHora.setText(sesionController.formatearHora(sesion.getFecha()));
        tvDispositivo.setText(sesion.getDispositivo());
        tvIntensidad.setText(String.valueOf(sesion.getIntensidad()));
        tvTiempo.setText(sesion.getTiempo() + " minutos");

        final AlertDialog dialog = builder.create();

        btnCerrar.setOnClickListener(v -> dialog.dismiss());
        btnBorrarS.setOnClickListener(v -> dialogoBorrarSesion(sesion));

        dialog.show();
    }

    private void dialogoBorrarSesion(Sesion sesion) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Desea Borrar este Historico?")
                .setMessage(sesion.getFecha())
                .setPositiveButton("Borrar", (dialog, which) -> {
                    boolean eliminado = sesionController.eliminarSesion(sesion.getId());
                    cargarSesiones();
                    Toast.makeText(this,
                            eliminado ? "Borrado correctamente"
                                    : "No se ha borrado el Historico " + sesion.getFecha(),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
                .create().show();
    }

    // Adaptador interno para mostrar sesiones individuales
    private class SesionAdapter extends ArrayAdapter<Sesion> {
        public SesionAdapter(List<Sesion> sesiones) {
            super(HistorialSesionesActivity.this, R.layout.sesion, sesiones);
        }

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.sesion, parent, false);
            }
            Sesion sesion = getItem(position);
            TextView tvFechaSesion = convertView.findViewById(R.id.tvFechaSesion);
            tvFechaSesion.setText(sesion.getFecha());
            return convertView;
        }
    }
}
