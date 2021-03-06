package com.example.helloaccelerometer3;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private TextView tv_accelerometer;
    TextToSpeech tts;
    private MediaPlayer beep;
    private boolean beepReady = false;

    private SensorManager sensorManager;
    private Sensor sensorAcc;

    private List<String> instructions;
    private float axisChanges[] = new float[8];
    private int instructionIndex = 0;
    private int lastInstruction = -1;

    private boolean movStarted = false;
    private boolean leanDown = false;
    private boolean leanUp = false;
    private boolean calibrating = true;
    private boolean ttsReady = false;

    private int timeThreshold = 500;
    private double corrCoeff = 3.5;

    private float maxYchange = 0;
    private float maxZchange = 0;
    private float minYchange = 0;
    private float minZchange = 0;

    private float yHistory = 0;
    private float zHistory = 0;
    private long iniTime = 0;
    private long lastChangeTime = 0;
    private String direction = "DOWN_FINISHED"; // The initial position is expected to be seated.
    private String last_direction = "DOWN";
    private String last_printed_direction = "NONE";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_accelerometer = (TextView) findViewById(R.id.tv_accel_data);

        // Keeps screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Sensor declaration. We use 1Hz frequency to get smoother measurements.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorAcc = sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
        sensorManager.registerListener(this, sensorAcc, 1000000);

        instructions = new ArrayList<String>();
        instructions.add(getString(R.string.step0));
        instructions.add(getString(R.string.step1));
        instructions.add(getString(R.string.step2));
        instructions.add(getString(R.string.step3));

        for(int i=0; i<axisChanges.length; i++)
            axisChanges[i] = 0;

        beep = MediaPlayer.create(this, R.raw.beep);
        tts=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    //Locale locSpanish = new Locale("spa", "ES");
                    //t1.setLanguage(locSpanish);
                    tts.setLanguage(Locale.UK);
                    ttsReady = true;
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tts.stop();
        tts.shutdown();
        beep.stop();
        beep.release();

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Pause sensor listener to save battery and memory.
    protected void onPause() {
        super.onPause();
        tts.stop();
        sensorManager.unregisterListener(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // Resume sensor listener
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorAcc, 1000000);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SuppressWarnings("deprecation")
    protected void readText(String text){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_ADD,null,null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null);
        }
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

        if (!calibrating){
            // If the movement has already begun, it only accepts the
            // deceleration value that confirms the end of it.
            if (movStarted) {
                if (diffChanges > (timeThreshold/1.5)){
                    if (direction == "UP_STARTED"){
                        if (yChange > axisChanges[3]/corrCoeff){
                            direction = "UP_FINISHED";
                            last_direction = "UP";
                            movStarted = false;
                            lastChangeTime = curTime;
                        }
                    } else if (direction == "DOWN_STARTED") {
                        if(!leanDown && yChange < axisChanges[6]/corrCoeff )
                            leanDown = true;
                        if (leanDown && zChange < axisChanges[4]/corrCoeff) {
                            direction = "DOWN_FINISHED";
                            last_direction = "DOWN";
                            movStarted = false;
                            leanDown = false;
                            lastChangeTime = curTime;
                        }
                    }
                }
                // If it hasn't begun, it only accepts values that indicate the
                // beginning of the movement.
            } else {
                if(!leanUp && zChange > axisChanges[1]/corrCoeff)
                    leanUp = true;
                // From sitting to standing
                if (yChange < axisChanges[2]/corrCoeff && leanUp && last_direction != "UP" && diffChanges > timeThreshold){
                    direction = "UP_STARTED";
                    movStarted = true;
                    lastChangeTime = curTime;
                    leanUp = false;
                } else if (yChange > axisChanges[7]/corrCoeff && last_direction != "DOWN" && diffChanges > timeThreshold){
                    direction = "DOWN_STARTED";
                    movStarted = true;
                    lastChangeTime = curTime;
                }
            }
            // Calibrate if TextToSpeech engine is ready
        } else if(ttsReady) {
            // Read each instruction only once
            if(instructionIndex != lastInstruction){
                lastInstruction = instructionIndex;
                readText(instructions.get(instructionIndex));
            }

            // Wait until reading is finished
            if (tts.isSpeaking()) {
                lastChangeTime = curTime;
                beepReady = true;
            } else {
                if (beepReady){
                    beep.start();
                    beepReady = false;
                    iniTime = curTime;
                }
                // Read the max and min values of Z, Y axis
                if (zChange < minZchange){
                    minZchange = zChange;
                    lastChangeTime = curTime;
                }
                if (zChange > maxZchange){
                    maxZchange = zChange;
                    lastChangeTime = curTime;
                }
                if (yChange < minYchange){
                    minYchange = yChange;
                    lastChangeTime = curTime;
                }
                if (yChange > maxYchange){
                    maxYchange = yChange;
                    lastChangeTime = curTime;
                }

                // When there is no major change in the last second
                if(diffChanges > 1000){
                    // Save the values depending on whether the user is standing up or sitting.
                    // and calculate the average value of the two measurements.
                    // (if sitting instruction, isSitting is 1 so the measurements have to be
                    // saved on the last 4 positions of the array)
                    int isSitting = instructionIndex%2;
                    axisChanges[0 + 4*isSitting] = (axisChanges[0 + 4*isSitting]+minZchange)/(instructionIndex/2 + 1);
                    axisChanges[1 + 4*isSitting] = (axisChanges[1 + 4*isSitting]+maxZchange)/(instructionIndex/2 + 1);
                    axisChanges[2 + 4*isSitting] = (axisChanges[2 + 4*isSitting]+minYchange)/(instructionIndex/2 + 1);
                    axisChanges[3 + 4*isSitting] = (axisChanges[3 + 4*isSitting]+maxYchange)/(instructionIndex/2 + 1);

                    // Restore the default value
                    minYchange = 0;
                    maxYchange = 0;
                    minZchange = 0;
                    maxZchange = 0;

                    if(instructionIndex < instructions.size()-1) {
                        instructionIndex++;
                    } else {
                        float overall = 0;

                        for(int i = 0; i < axisChanges.length; i++){
                            if (i%2 == 0) {
                                overall += Math.abs(axisChanges[i]);
                            } else {
                                overall += axisChanges[i];
                            }
                        }
                        if (overall > 25.0f){
                            corrCoeff = corrCoeff + overall/10;
                        }

                        if(overall > 9.0f){
                            readText(getString(R.string.endCalibrating));
                            calibrating = false;
                        } else {
                            readText(getString(R.string.errorCalibrating));
                            calibrating = true;
                        }

                        instructionIndex = 0;
                        lastInstruction = -1;
                        lastChangeTime = curTime;
                    }
                }
            }
        }

        if (last_printed_direction != direction){
            //tv_accelerometer.setText("\nD: " + direction + " - Y: " + yChange + " - Z: " + zChange + tv_accelerometer.getText());
            if (direction == "DOWN_FINISHED"){
                tv_accelerometer.setText("\nCurrent pos: DOWN" + tv_accelerometer.getText());
                readText(getString(R.string.posDown));
            }

            else if (direction == "UP_FINISHED"){
                tv_accelerometer.setText("\nCurrent pos: UP" + tv_accelerometer.getText());
                readText(getString(R.string.posUp));
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