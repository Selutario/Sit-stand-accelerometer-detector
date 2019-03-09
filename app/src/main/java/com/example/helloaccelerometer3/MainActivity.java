package com.example.helloaccelerometer3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private TextView tv_accelerometer;
    private EditText et_agility;
    private Button setButton;
    TextToSpeech t1;

    private SensorManager sensorManager;
    private Sensor sensorAcc;

    private int timeThreshold;
    private double yThreshold;
    private double zThreshold;

    private boolean movStarted = false;
    private boolean leanUp = false;
    private boolean leanDown = false;

    private float yHistory = 0;
    private float zHistory = 0;
    private long lastChangeTime = 0;
    private String direction = "DOWN_FINISHED"; // The initial position is expected to be seated.
    private String last_direction = "DOWN";
    private String last_printed_direction = "NONE";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_accelerometer = (TextView) findViewById(R.id.tv_accel_data);
        et_agility = (EditText) findViewById(R.id.et_set_agility);
        setButton = (Button) findViewById(R.id.agility_button);

        // Sensor declaration. We use 1Hz frequency to get smoother measurements.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAcc = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        sensorManager.registerListener(this, sensorAcc, 1000000);

        setAgility(2);

        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.UK);
                }
            }
        });

        setButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(et_agility.getText().toString().equals(null) || et_agility.getText().toString().equals(""))
                {
                    Toast.makeText(getBaseContext(),"Please enter something in text box",Toast.LENGTH_LONG).show();
                } else {
                    int et_value = Integer.parseInt(et_agility.getText().toString());

                    if (et_value == 1 || et_value == 2 || et_value == 3){
                        setAgility(et_value);
                        et_agility.setText("");
                        Toast msg = Toast.makeText(getBaseContext(), "Agility updated", Toast.LENGTH_SHORT);
                        msg.show();
                    } else {
                        Toast.makeText(getBaseContext(), "Error. Use only 1, 2 or 3.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

    }


    // Pause sensor listener to save battery and memory.
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    // Resume sensor listener
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorAcc, 1000000);
    }

    protected void readText(String text){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            t1.speak(text, TextToSpeech.QUEUE_FLUSH,null,null);
        } else {
            t1.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }


    protected void setAgility(int index){
        switch (index){
            case 1:
                timeThreshold = 1300;
                yThreshold = 0.10;
                zThreshold = 0.20;
                break;
            case 2:
                timeThreshold = 500;
                yThreshold = 0.6;
                zThreshold = 0.3;
                break;
            case 3:
                timeThreshold = 200;
                yThreshold = 0.8;
                zThreshold = 0.7;
                break;
        }

        et_agility.setHint(String.valueOf(index));
        tv_accelerometer.setText("");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // We will use time measurements to reduce the noise in the use of the accelerometer.
        long curTime = System.currentTimeMillis();
        long diffChanges = (curTime - lastChangeTime);

        // If first execution, history starts with current event value to avoid
        // getting a negative number as result of gravity force on yChange variable.
        if (yHistory == 0) {
            yHistory = event.values[1];
            zHistory = event.values[2];
        }

        // We obtain the difference of acceleration with respect to the previous measurement.
        float yChange = yHistory - event.values[1];
        float zChange = zHistory - event.values[2];
        yHistory = event.values[1];
        zHistory = event.values[2];


        // If the movement has already begun, it only accepts the
        // deceleration value that confirms the end of it.
        if (movStarted) {
            if (diffChanges > (timeThreshold/1.5)){
                if (direction == "UP_STARTED"){
                    if (yChange > yThreshold/1.5){
                        direction = "UP_FINISHED";
                        last_direction = "UP";
                        movStarted = false;
                        lastChangeTime = curTime;
                    }
                } else if (direction == "DOWN_STARTED") {
                    if (zChange > zThreshold/5 && yChange < -yThreshold) {
                        direction = "DOWN_FINISHED";
                        last_direction = "DOWN";
                        movStarted = false;
                        lastChangeTime = curTime;
                    }
                }
            }
            // If it hasn't begun, it only accepts values that indicate the
            // beginning of the movement.
        } else {
            if (zChange > zThreshold && yChange < -yThreshold && last_direction != "UP" && diffChanges > timeThreshold){
                direction = "UP_STARTED";
                movStarted = true;
                lastChangeTime = curTime;
            } else if (yChange > yThreshold && last_direction != "DOWN" && diffChanges > timeThreshold){
                direction = "DOWN_STARTED";
                movStarted = true;
                lastChangeTime = curTime;
            }
        }


        if (last_printed_direction != direction){
            //tv_accelerometer.setText("\nD: " + direction + " - Z: " + zChange + tv_accelerometer.getText());
            if (direction == "DOWN_FINISHED"){
                tv_accelerometer.setText("\nDOWN" + tv_accelerometer.getText());
                readText("DOWN");
            }

            else if (direction == "UP_FINISHED"){
                tv_accelerometer.setText("\nUP" + tv_accelerometer.getText());
                readText("UP");
            }

            int count = tv_accelerometer.getLineCount();
            if (count > 150)
                tv_accelerometer.setText(tv_accelerometer.getText().subSequence(0, 500));
            last_printed_direction = direction;
        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}