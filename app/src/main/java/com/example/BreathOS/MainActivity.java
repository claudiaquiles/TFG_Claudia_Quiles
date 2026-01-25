package com.example.BreathOS;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button btnRegular, btnFast, btnSlow, btnApnea, btnCough, btnCheyne, btnBiot, btnPause;
    TextView txtClassification, txtEvent;
    LineChart lineChart;
    BluetoothSocket btSocket;
    OutputStream btOutput;
    InputStream btInput;
    Handler handler;
    ArrayList<Entry> chartEntries = new ArrayList<>();
    private float timeIndex = 0f; // counter



    final String DEVICE_ADDRESS = "00:23:09:01:F5:19"; //"00:23:09:01:F5:19" "00:24:09:00:DA:E9" HC-05 MAC address
    final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Handler eventHandler = new Handler();


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRegular = findViewById(R.id.btnNormal);
        btnFast = findViewById(R.id.btnAcelerada);
        btnSlow = findViewById(R.id.btnLenta);
        btnApnea = findViewById(R.id.btnApnea);
        btnCough = findViewById(R.id.btnTos);
        btnCheyne = findViewById(R.id.btnCheyne);
        btnBiot = findViewById(R.id.btnBiot);
        btnPause = findViewById(R.id.btnPause);

        txtClassification = findViewById(R.id.txtClasificacion);
        txtEvent = findViewById(R.id.txtEvent);
        lineChart = findViewById(R.id.lineChart);

        setupChart();
        connectBluetooth();


        btnRegular.setOnClickListener(v -> sendCommand('R'));
        btnFast.setOnClickListener(v -> sendCommand('F'));
        btnSlow.setOnClickListener(v -> sendCommand('S'));
        btnApnea.setOnClickListener(v -> sendCommand('A'));
        btnCough.setOnClickListener(v -> sendCommand('T'));
        btnCheyne.setOnClickListener(v -> sendCommand('C'));
        btnBiot.setOnClickListener(v -> sendCommand('B'));
        btnPause.setOnClickListener(v -> sendCommand('K'));

        handler = new Handler();
        receiveData();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            connectBluetooth(); // TRY AGAIN IF NOT GIVEN
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
        }
    }


    void setupChart() {
        LineDataSet dataSet = new LineDataSet(chartEntries, "Breathing");
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSet.setColor(Color.BLUE);
        dataSet.setLineWidth(2f);
        dataSet.setMode(LineDataSet.Mode.LINEAR);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        lineChart.getDescription().setEnabled(false);

        // right axis disabled
        lineChart.getAxisRight().setEnabled(false);

        // axis X (time in seconds)
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setDrawLabels(true); // show labels
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ((int) value) + "s";
            }
        });

        // axis y left (degrees)
        YAxis yAxis = lineChart.getAxisLeft();
        yAxis.setAxisMinimum(0f);
        yAxis.setAxisMaximum(180f);
        yAxis.setDrawGridLines(true);
        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return ((int) value) + "°";
            }
        });


        lineChart.setTouchEnabled(false);
        lineChart.setDragEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(false);
        lineChart.setBackgroundColor(Color.WHITE);

        // label axis
        Description desc = new Description();
        desc.setText("Time (s)    |    Degrees (°)");
        desc.setTextSize(12f);
        desc.setTextColor(Color.DKGRAY);
        lineChart.setDescription(desc);
    }



    void connectBluetooth() {
       BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

       //VERIFY PERMISSION BEFORE USING SENSITIVE FUNCTIONS
       if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
           requestPermissions(new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
           return;
       }

       try {
           BluetoothDevice device = btAdapter.getRemoteDevice(DEVICE_ADDRESS);
           btSocket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
           btSocket.connect();
           btOutput = btSocket.getOutputStream();
           btInput = btSocket.getInputStream();
           Toast.makeText(this, "Bluetooth connected", Toast.LENGTH_SHORT).show();
       } catch (Exception e) {
           Toast.makeText(this, "Bluetooth connection error", Toast.LENGTH_SHORT).show();
       }
   }


    void sendCommand(char command) {
        try {
            btOutput.write(command);
        } catch (Exception e) {
            Toast.makeText(this, "Command send error", Toast.LENGTH_SHORT).show();
        }
    }
    void receiveData() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            StringBuilder messageBuffer = new StringBuilder();

            while (true) {
                try {
                    if ((bytes = btInput.read(buffer)) > 0) {
                        String chunk = new String(buffer, 0, bytes);
                        messageBuffer.append(chunk);

                        int endIndex;
                        while ((endIndex = messageBuffer.indexOf("\n")) != -1) {
                            String completeMessage = messageBuffer.substring(0, endIndex).trim();
                            messageBuffer.delete(0, endIndex + 1);


                            runOnUiThread(() -> processData(completeMessage));
                        }
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }).start();
    }


    void processData(String data)  {
        if (data.startsWith("MODE:")) {
            String modeName = data.substring(5); // remove "MODE:"
            txtClassification.setText("MODE:"+ modeName);
        } else if (data.startsWith("ANGLE:")) {
            try {
                float angle = Float.parseFloat(data.substring(6)); // remove "ANGLE:"
                Log.d("Bluetooth", "Received angle: " + angle);
                addEntry(angle);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                Log.e("Bluetooth", "Invalid float: " + data);
            }
        }else if (data.startsWith("EVENT:")) {
            String eventText = data.substring(6).trim();
            runOnUiThread(() -> showEvent(eventText));
        }
    }

    void showEvent(String eventText) {
        txtEvent.setText(eventText);

        // Delete the event after 3 sec
        eventHandler.removeCallbacksAndMessages(null); // cancel any before
        eventHandler.postDelayed(() -> txtEvent.setText("No events"), 3000);
    }


    void addEntry(float angle) {
        if (lineChart.getData() != null && lineChart.getData().getDataSetCount() > 0) {
            LineDataSet dataSet = (LineDataSet) lineChart.getData().getDataSetByIndex(0);

            // use time index to let axis x increasing
            dataSet.addEntry(new Entry(timeIndex, angle));
            timeIndex += 1f; //1 sec

            // limit to 200 entries
            if (dataSet.getEntryCount() > 200) {
                dataSet.removeEntry(0);
            }

            // automatized adjustment
            float minY = Float.MAX_VALUE;
            float maxY = Float.MIN_VALUE;
            for (int i = 0; i < dataSet.getEntryCount(); i++) {
                float y = dataSet.getEntryForIndex(i).getY();
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
            lineChart.getAxisLeft().setAxisMinimum(minY - 5f);
            lineChart.getAxisLeft().setAxisMaximum(maxY + 5f);

            // notify changes
            lineChart.getData().notifyDataChanged();
            lineChart.notifyDataSetChanged();
            lineChart.invalidate();
        }
    }






}