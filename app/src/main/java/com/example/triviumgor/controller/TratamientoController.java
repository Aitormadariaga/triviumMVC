package com.example.triviumgor.controller;

import android.os.Handler;
import android.util.Log;

import com.example.triviumgor.model.DispositivoState;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;

/**
 * Controlador de Tratamiento.
 * Gestiona: construcción de tramas, envío al hardware, timers de sesión y lectura de batería.
 * NO contiene referencias a Views. Comunica eventos a la Activity via TratamientoListener.
 */
public class TratamientoController {

    private static final String TAG = "TratamientoController";
    private static final int TIMER_INTERVAL_MS = 20000;
    private static final int BATTERY_CHECK_INTERVAL_MIN = 5;
    private static final int ANCHO_PULSO_DEFAULT = 4;
    private static final int PERIODO_MS_DEFAULT = 100;

    // Niveles de batería
    public static final int NIVEL_ALTA = 3;
    public static final int NIVEL_MEDIA = 2;
    public static final int NIVEL_BAJA = 1;

    /**
     * Listener para comunicar eventos al Activity (View).
     */
    public interface TratamientoListener {
        void onTiempoRestanteActualizado(int dispositivoNum, int minutosRestantes);
        void onSesionFinalizada(int dispositivoNum);
        void onBateriaActualizada(int dispositivoNum, int valorCarga, int nivel, String textoNivel);
        void onError(int dispositivoNum, String mensaje);
        void onSesionIniciada(int dispositivoNum);
        void onIntensidadActualizada(int dispositivoNum, int intensidad);
    }

    private final TratamientoListener listener;
    private final Handler timeHandler1 = new Handler();
    private final Handler timeHandler2 = new Handler();

    private int minutoAnt1 = -1;
    private int minutoAnt2 = -1;

    // Threads de lectura de batería
    private BatteryReaderThread batteryThread1;
    private BatteryReaderThread batteryThread2;

    // Buffer de escritura compartido
    private final int[] byteArrayWrite = new int[28];

    public TratamientoController(TratamientoListener listener) {
        this.listener = listener;
    }

    // ========================
    // INICIAR / ACTUALIZAR SESIÓN
    // ========================

    /**
     * Inicia una sesión nueva o actualiza la intensidad si ya hay una activa.
     * @return true si es sesión nueva, false si es actualización
     */
    public boolean iniciarOActualizarSesion(DispositivoState dispositivo, int intensidad, int duracionMin) {
        if (!dispositivo.isConnected()) {
            listener.onError(dispositivo.getNumero(), "Dispositivo no conectado");
            return false;
        }

        boolean esSesionNueva = dispositivo.isClockStopped();

        dispositivo.setIntensidad(intensidad);
        dispositivo.setDuracionMin(duracionMin);

        // Construir y enviar trama
        try {
            byte[] trama = construirTrama(intensidad, duracionMin);
            enviarTrama(dispositivo, trama);
        } catch (IOException e) {
            listener.onError(dispositivo.getNumero(), "Error al enviar datos: " + e.getMessage());
            return false;
        }

        if (esSesionNueva) {
            // Resetear temporizador
            dispositivo.setMinutoTranscurrido(0);
            dispositivo.setClockStopped(false);
            dispositivo.setBattMon(true);

            // Iniciar timer
            iniciarTimer(dispositivo);

            // Iniciar lectura de batería
            iniciarLecturaBateria(dispositivo);

            listener.onSesionIniciada(dispositivo.getNumero());
            Log.d(TAG, "Sesión nueva iniciada en dispositivo " + dispositivo.getNumero());
            return true;
        } else {
            listener.onIntensidadActualizada(dispositivo.getNumero(), intensidad);
            Log.d(TAG, "Intensidad actualizada a " + intensidad + " en dispositivo " + dispositivo.getNumero());
            return false;
        }
    }

    // ========================
    // FINALIZAR SESIÓN
    // ========================

    /**
     * Envía comando de parada (0x43 = 'C') y detiene el timer.
     */
    public void finalizarSesion(DispositivoState dispositivo) {
        if (!dispositivo.isConnected()) return;

        try {
            OutputStream os = dispositivo.getOutputStream();
            if (os != null) {
                os.write(0x43);
                os.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error al enviar comando de parada: " + e.getMessage());
        }

        dispositivo.setClockStopped(true);
        detenerTimer(dispositivo);

        // Cancelar thread de lectura de batería
        if (dispositivo.getNumero() == 1 && batteryThread1 != null) {
            batteryThread1.cancel();
            batteryThread1 = null;
        } else if (dispositivo.getNumero() == 2 && batteryThread2 != null) {
            batteryThread2.cancel();
            batteryThread2 = null;
        }

        Log.d(TAG, "Sesión finalizada en dispositivo " + dispositivo.getNumero());
    }

    // ========================
    // CONSTRUCCIÓN DE TRAMA
    // ========================

    /**
     * Construye la trama de bytes para enviar al hardware.
     * Formato: 'A' + anchoPulso(x2) + periodo(x2) + intensidad(x2) + duración + 'B'
     */
    public byte[] construirTrama(int intensidad, int duracionMin) {
        int index = 0;
        int[] trama = new int[28];

        // Comando inicio
        trama[index++] = 0x41; // 'A'

        // Ancho de pulso (4ms) - repetido 2 veces
        index = escribirValor4Digitos(trama, index, ANCHO_PULSO_DEFAULT);
        index = escribirValor4Digitos(trama, index, ANCHO_PULSO_DEFAULT);

        // Periodo (100ms = 10Hz) - repetido 2 veces
        index = escribirValor4Digitos(trama, index, PERIODO_MS_DEFAULT);
        index = escribirValor4Digitos(trama, index, PERIODO_MS_DEFAULT);

        // Intensidad - repetida 2 veces
        index = escribirValor2Digitos(trama, index, intensidad);
        index = escribirValor2Digitos(trama, index, intensidad);

        // Duración en minutos
        index = escribirValor2Digitos(trama, index, duracionMin);

        // Comando fin
        trama[index++] = 0x42; // 'B'

        // Convertir a byte[]
        byte[] resultado = new byte[index];
        for (int i = 0; i < index; i++) {
            resultado[i] = (byte) trama[i];
        }
        return resultado;
    }

    /**
     * Escribe un valor de 4 dígitos en formato ASCII en la trama.
     */
    private int escribirValor4Digitos(int[] trama, int index, int valor) {
        int aux1, aux2;
        aux1 = valor / 1000;
        aux2 = valor % 1000;
        trama[index++] = aux1 + 0x30;

        aux1 = aux2 / 100;
        aux2 = aux2 % 100;
        trama[index++] = aux1 + 0x30;

        aux1 = aux2 / 10;
        aux2 = aux2 % 10;
        trama[index++] = aux1 + 0x30;

        trama[index++] = aux2 + 0x30;

        return index;
    }

    /**
     * Escribe un valor de 2 dígitos en formato ASCII en la trama.
     */
    private int escribirValor2Digitos(int[] trama, int index, int valor) {
        int aux1 = valor / 10;
        int aux2 = valor % 10;
        trama[index++] = aux1 + 0x30;
        trama[index++] = aux2 + 0x30;
        return index;
    }

    // ========================
    // ENVÍO
    // ========================

    /**
     * Envía la trama byte a byte al dispositivo con delay entre bytes.
     */
    private void enviarTrama(DispositivoState dispositivo, byte[] trama) throws IOException {
        OutputStream os = dispositivo.getOutputStream();
        if (os == null) {
            throw new IOException("OutputStream es null");
        }

        for (byte b : trama) {
            os.write(b);
            os.flush();
            // Delay entre bytes (equivalente al for vacío del original)
            for (int s = 60000; s > 0; s--) ;
        }
    }

    // ========================
    // TIMER DE SESIÓN
    // ========================

    private void iniciarTimer(DispositivoState dispositivo) {
        if (dispositivo.getNumero() == 1) {
            minutoAnt1 = Calendar.getInstance().get(Calendar.MINUTE);
            timeHandler1.postDelayed(crearCheckTimer(dispositivo, timeHandler1), TIMER_INTERVAL_MS);
        } else {
            minutoAnt2 = Calendar.getInstance().get(Calendar.MINUTE);
            timeHandler2.postDelayed(crearCheckTimer(dispositivo, timeHandler2), TIMER_INTERVAL_MS);
        }
    }

    private void detenerTimer(DispositivoState dispositivo) {
        if (dispositivo.getNumero() == 1) {
            timeHandler1.removeCallbacksAndMessages(null);
        } else {
            timeHandler2.removeCallbacksAndMessages(null);
        }
    }

    private Runnable crearCheckTimer(DispositivoState dispositivo, Handler handler) {
        return new Runnable() {
            @Override
            public void run() {
                Calendar calendar = Calendar.getInstance();
                int minutoActual = calendar.get(Calendar.MINUTE);
                int minutoAnterior = (dispositivo.getNumero() == 1) ? minutoAnt1 : minutoAnt2;

                if (minutoActual != minutoAnterior) {
                    if (!dispositivo.isClockStopped()) {
                        dispositivo.incrementarMinuto();
                        int restante = dispositivo.getDuracionMin() - dispositivo.getMinutoTranscurrido();
                        listener.onTiempoRestanteActualizado(dispositivo.getNumero(), restante);

                        // Cada 5 minutos: solicitar batería
                        if (dispositivo.getMinutoTranscurrido() % BATTERY_CHECK_INTERVAL_MIN == 0) {
                            solicitarBateria(dispositivo);
                        }

                        // Tiempo agotado
                        if (dispositivo.isTiempoAgotado()) {
                            dispositivo.setClockStopped(true);
                            listener.onSesionFinalizada(dispositivo.getNumero());
                        }
                    }
                }

                if (dispositivo.getNumero() == 1) {
                    minutoAnt1 = minutoActual;
                } else {
                    minutoAnt2 = minutoActual;
                }

                // Reprogramar
                handler.postDelayed(this, TIMER_INTERVAL_MS);
            }
        };
    }

    // ========================
    // BATERÍA
    // ========================

    /**
     * Envía comando de solicitud de batería (0x46 = 'F').
     * Público para poder solicitar la batería inmediatamente tras conectar.
     */
    public void solicitarBateria(DispositivoState dispositivo) {
        try {
            OutputStream os = dispositivo.getOutputStream();
            if (os != null && dispositivo.isConnected()) {
                os.write(0x46);
                os.flush();
                dispositivo.setBattMon(true);

                iniciarLecturaBateria(dispositivo);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error al solicitar batería: " + e.getMessage());
        }
    }

    /**
     * Inicia un thread de lectura de batería para un dispositivo.
     */
    private void iniciarLecturaBateria(DispositivoState dispositivo) {
        if (dispositivo.getNumero() == 1) {
            if (batteryThread1 != null) batteryThread1.cancel();
            batteryThread1 = new BatteryReaderThread(dispositivo);
            batteryThread1.start();
        } else {
            if (batteryThread2 != null) batteryThread2.cancel();
            batteryThread2 = new BatteryReaderThread(dispositivo);
            batteryThread2.start();
        }
    }

    /**
     * Thread que lee datos de batería del dispositivo vía InputStream.
     * Equivalente a ConnectedThread/ConnectedThread2 del original.
     */
    private class BatteryReaderThread extends Thread {
        private final DispositivoState dispositivo;
        private final java.io.InputStream myInputStream; // referencia propia al stream de ESTA conexión
        private volatile boolean running = true;

        BatteryReaderThread(DispositivoState dispositivo) {
            this.dispositivo = dispositivo;
            this.myInputStream = dispositivo.getInputStream(); // capturar en construcción
        }

        public void cancel() {
            running = false;
            dispositivo.setBattMon(false);
            try {
                if (myInputStream != null) {
                    myInputStream.close(); // cierra SOLO el stream de esta conexión
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[28];
            byte[] recBuffer = new byte[28];
            int nBytes = 0;
            int indiceRec = 0;
            int valorCargaAnt = 0;

            while (running && dispositivo.isBattMon()) {
                try {
                    int bytes = myInputStream.read(buffer);
                    for (int i = 0; i < bytes; i++) {
                        recBuffer[indiceRec] = buffer[i];
                        indiceRec++;
                    }

                    nBytes += bytes;

                    while (nBytes >= 4) {
                        int j = 0;
                        while (recBuffer[j] < 2 || recBuffer[j] > 3) j++;

                        indiceRec = 0;
                        Log.d(TAG, "Recibidos Total Bytes: " + nBytes);
                        nBytes = 0;

                        int auxint;
                        if (recBuffer[j + 1] < 0) {
                            auxint = recBuffer[j + 1] + 1;
                            auxint = 255 + auxint;
                        } else {
                            auxint = recBuffer[j + 1];
                        }
                        auxint = auxint + recBuffer[j] * 256;

                        int valorCarga = auxint;
                        if (valorCarga != valorCargaAnt && valorCarga > 780) {
                            valorCargaAnt = valorCarga;
                            dispositivo.setBattMon(false);
                        }

                        recBuffer[0] = 0;
                        recBuffer[1] = 0;
                        recBuffer[2] = 0;
                        recBuffer[3] = 0;

                        // Calcular nivel
                        final int cargaFinal = valorCargaAnt;
                        int nivel;
                        String textoNivel;
                        if (cargaFinal >= 890) {
                            nivel = NIVEL_ALTA;
                            textoNivel = "ALTA";
                        } else if (cargaFinal >= 840) {
                            nivel = NIVEL_MEDIA;
                            textoNivel = "MEDIA";
                        } else {
                            nivel = NIVEL_BAJA;
                            textoNivel = "BAJA";
                        }

                        listener.onBateriaActualizada(dispositivo.getNumero(),
                                cargaFinal, nivel, textoNivel);
                    }
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    // ========================
    // LIMPIEZA
    // ========================

    /**
     * Detiene todos los timers y threads. Llamar desde onDestroy().
     */
    public void destroy() {
        timeHandler1.removeCallbacksAndMessages(null);
        timeHandler2.removeCallbacksAndMessages(null);

        if (batteryThread1 != null) {
            batteryThread1.cancel();
            batteryThread1 = null;
        }
        if (batteryThread2 != null) {
            batteryThread2.cancel();
            batteryThread2 = null;
        }
    }
}