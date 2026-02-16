package com.example.triviumgor.view;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.triviumgor.R;
import com.example.triviumgor.controller.UsuarioController;
import com.example.triviumgor.model.Usuario;

import java.util.List;

public class UsuarioAdapter extends RecyclerView.Adapter<UsuarioAdapter.UsuarioViewHolder> {

    public interface OnUsuarioClickListener {
        void onUsuarioClick(int position);
    }

    private List<Usuario> usuarios;
    private OnUsuarioClickListener listener;

    public UsuarioAdapter(List<Usuario> usuarios, OnUsuarioClickListener listener) {
        this.usuarios = usuarios;
        this.listener = listener;
    }

    public void actualizarLista(List<Usuario> nuevaLista) {
        this.usuarios = nuevaLista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public UsuarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_usuario, parent, false);
        return new UsuarioViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UsuarioViewHolder holder, int position) {
        Usuario usuario = usuarios.get(position);

        holder.tvUsername.setText(usuario.getUsername());
        holder.tvNombreCompleto.setText(usuario.getNombreCompleto());

        // Rol con emoji
        String emoji = UsuarioController.Rol.fromCodigo(usuario.getRol()).getEmoji();
        holder.tvRol.setText(emoji + " " + usuario.getRol());

        // Indicador de estado: verde = activo, gris = inactivo
        int colorEstado;
        if (usuario.getActivo() == 1) {
            colorEstado = ContextCompat.getColor(holder.itemView.getContext(), R.color.color_success);
        } else {
            colorEstado = ContextCompat.getColor(holder.itemView.getContext(), R.color.md_theme_outline);
        }
        // Compatible con API 19
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(colorEstado);
        holder.viewEstado.setBackground(circle);

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onUsuarioClick(holder.getAdapterPosition());
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return usuarios != null ? usuarios.size() : 0;
    }

    static class UsuarioViewHolder extends RecyclerView.ViewHolder {
        View viewEstado;
        TextView tvUsername;
        TextView tvNombreCompleto;
        TextView tvRol;

        UsuarioViewHolder(@NonNull View itemView) {
            super(itemView);
            viewEstado = itemView.findViewById(R.id.viewEstado);
            tvUsername = itemView.findViewById(R.id.tvUsername);
            tvNombreCompleto = itemView.findViewById(R.id.tvNombreCompleto);
            tvRol = itemView.findViewById(R.id.tvRol);
        }
    }
}