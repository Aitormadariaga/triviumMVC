package com.example.triviumgor.view;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private RecyclerView recyclerViewSesiones;
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
        recyclerViewSesiones = findViewById(R.id.recyclerViewSesiones);
        btnVolver = findViewById(R.id.btnVolver);

        recyclerViewSesiones.setLayoutManager(new LinearLayoutManager(this));

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
                recyclerViewSesiones.setAdapter(new MensajeAdapter(mensajes));
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
        recyclerViewSesiones.setAdapter(new FechaAdapter(fechas));
        mostrandoDetalleDia = false;
    }

    private void mostrarSesionesDelDia(int posicionGrupo) {
        if (grupos != null && posicionGrupo < grupos.size()) {
            SesionController.GrupoDia grupo = grupos.get(posicionGrupo);
            List<Sesion> sesionesDelDia = new ArrayList<>(
                    sesiones.subList(grupo.indiceInicio, grupo.indiceFin));
            recyclerViewSesiones.setAdapter(new SesionRecyclerAdapter(sesionesDelDia));
            mostrandoDetalleDia = true;
        }
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

    // =========================================================
    //  Adapters internos
    // =========================================================

    /** Adapter para mensajes simples (ej: "No hay sesiones") */
    private class MensajeAdapter extends RecyclerView.Adapter<MensajeAdapter.VH> {
        private final List<String> mensajes;
        MensajeAdapter(List<String> mensajes) { this.mensajes = mensajes; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sesion_fecha, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tv.setText(mensajes.get(pos));
        }
        @Override public int getItemCount() { return mensajes.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(View v) { super(v); tv = v.findViewById(R.id.tvFechaGrupo); }
        }
    }

    /** Adapter para fechas agrupadas */
    private class FechaAdapter extends RecyclerView.Adapter<FechaAdapter.VH> {
        private final List<String> fechas;
        FechaAdapter(List<String> fechas) { this.fechas = fechas; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sesion_fecha, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tv.setText(fechas.get(pos));
            h.itemView.setOnClickListener(v -> mostrarSesionesDelDia(h.getAdapterPosition()));
        }
        @Override public int getItemCount() { return fechas.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(View v) { super(v); tv = v.findViewById(R.id.tvFechaGrupo); }
        }
    }

    /** Adapter para sesiones individuales de un día */
    private class SesionRecyclerAdapter extends RecyclerView.Adapter<SesionRecyclerAdapter.VH> {
        private final List<Sesion> lista;
        SesionRecyclerAdapter(List<Sesion> lista) { this.lista = lista; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_sesion, parent, false);
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Sesion s = lista.get(pos);
            h.tvFecha.setText(s.getFecha());
            h.tvInfo.setText(s.getDispositivo() + " · Intensidad: " + s.getIntensidad());
            h.itemView.setOnClickListener(v -> mostrarDetallesSesion(s));
            h.itemView.setOnLongClickListener(v -> {
                dialogoBorrarSesion(s);
                return true;
            });
        }
        @Override public int getItemCount() { return lista.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvFecha, tvInfo;
            VH(View v) {
                super(v);
                tvFecha = v.findViewById(R.id.tvFechaSesion);
                tvInfo = v.findViewById(R.id.tvInfoSesion);
            }
        }
    }
}