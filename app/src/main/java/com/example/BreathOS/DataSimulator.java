package com.example.BreathOS;
import android.os.Handler;
import java.util.Random;

public class DataSimulator {
    private Handler handler = new Handler();
    private double t = 0;
    private Random random = new Random();
    private OnDataReceivedListener listener;
    private String currentMode = "Regular";

    public interface OnDataReceivedListener {
        void onDataReceived(String data);
    }

    public DataSimulator(OnDataReceivedListener listener) {
        this.listener = listener;
    }

    public void start() {
        handler.postDelayed(dataRunnable, 100);
    }

    public void stop() {
        handler.removeCallbacks(dataRunnable);
    }

    public void setMode(String mode) {
        this.currentMode = mode;
        listener.onDataReceived("MODE:" + mode);
    }

    private Runnable dataRunnable = new Runnable() {
        @Override
        public void run() {
            // Simular señal sinusoidal
            double angle = Math.sin(t) * 30; // amplitud de 30 grados
            t += 0.1;

            // Enviar valor de ángulo
            listener.onDataReceived("ANGLE:" + String.format("%.2f", angle));

            // Simular evento aleatorio cada ~5 segundos
            if (random.nextInt(50) == 0) {
                String[] events = {"Cough", "Apnea", "Resume"};
                listener.onDataReceived("EVENT:" + events[random.nextInt(events.length)]);
            }

            handler.postDelayed(this, 100); // repetir cada 100 ms
        }
    };
}
