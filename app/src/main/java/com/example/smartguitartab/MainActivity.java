package com.example.smartguitartab;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.AsyncTask;
import android.os.Bundle;

import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.RelativeLayout;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Vector;

import static java.lang.Math.log;

public class MainActivity extends AppCompatActivity {
    static TextView noteTxt = null;
    static PolyphonicPitchDetection poly;
    static TextView tabs[];
    Button startButton = null;
    Button stopButton = null;

    RelativeLayout layout = null;

    private static Vector<String> currentNotes ;
    private  static Map<Integer, Integer> currentTabValues;

    private final int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    private final int sampleRate = 44100;

    private int bufferSize = 0;
    private final int channelConfiguration = AudioFormat.CHANNEL_IN_MONO;

    private AudioRecord audioRecord;

    private AudioTask audioTask;

    private short[] sampleBuffer = null;

    // we display current freq by sending msg to handler and updating ui here
    // -> not possible directly in doInBackground of async task!
    private static Handler uiHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg)
        {
            System.out.println("Notes trouvées : " + currentNotes);
            noteTxt.setText("");
            if (noteTxt == null) System.out.println("notetxt nul " + currentNotes);
            else System.out.println("notetxt non nul " + currentNotes);
            for(String note : currentNotes){
                noteTxt.append(note+ " ");
            }

            if(currentTabValues != null) writeTab(currentTabValues);

        }
    };



    private AudioRecord findAudioRecord()
    {
        try
        {
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    channelConfiguration, audioEncoding);

            if (bufferSize != AudioRecord.ERROR_BAD_VALUE)
            {
                // check if we can instantiate and have a success

                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        sampleRate, channelConfiguration, audioEncoding, bufferSize);

                if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                    return recorder;
            }
        } catch (Exception e)
        {
            Log.e("Main", "Couldn't setup mic!");
        }
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        noteTxt = (TextView)findViewById(R.id.freqTextView);
        startButton = (Button) findViewById(R.id.startButton);
        stopButton = (Button) findViewById(R.id.stopButton);

        tabs = new TextView[6];
        tabs[0] = (TextView)findViewById(R.id.tab1View);
        tabs[1] = (TextView)findViewById(R.id.tab2View);
        tabs[2] = (TextView)findViewById(R.id.tab3View);
        tabs[3] = (TextView)findViewById(R.id.tab4View);
        tabs[4] = (TextView)findViewById(R.id.tab5View);
        tabs[5] = (TextView)findViewById(R.id.tab6View);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioTask = new AudioTask();
                audioTask.execute();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                audioTask.stopExecute();
            }
        });


        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfiguration,
                audioEncoding);

        // using short array -> half size
        bufferSize /= 2;
        bufferSize = (int) Math.pow(2, Math.ceil(log(bufferSize)/log(2)));
        String sizeMsg = "Buffer size is " + Integer.toString(bufferSize);
        Log.e("Configuration", sizeMsg);

        sampleBuffer = new short[bufferSize];

        audioRecord = this.findAudioRecord();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            Log.e("Main", "Not allowed to access mic :(");
        }
        else Log.e("Main", "Mic is accessible");

        if (audioRecord == null)
        {
            Log.e("Main", "Couldn't setup mic!");
            return;
        }
        else Log.e("Main", "Mic found");
        poly = new PolyphonicPitchDetection(bufferSize, sampleRate, 16);


    }

    public static void writeTab(Map<Integer, Integer> tabValues)
    {
        // Clear All
        for(int i =0 ; i<6 ; i++)  tabs[i].setText("");

        for(int i =0 ; i<6 ; i++)
        {
            if(tabValues.get(i+1) != null)
                tabs[i].setText(Integer.toString(tabValues.get(i+1)));
        }
    }


    private class AudioTask extends AsyncTask<Void, Void, Void> {
        private boolean execute = false;

        public synchronized void stopExecute() {
            this.execute = false;
        }

        @SuppressLint("NewApi")
        @Override
        protected Void doInBackground(Void... params) {
            audioRecord.startRecording();
            while (this.execute) {
                int read = audioRecord.read(sampleBuffer, 0, bufferSize);

                if (read < 0) {
                    Log.e("audiotask", "continue");
                    continue;
                }

                // resize if less or more was read
                if (read != bufferSize) {
                    sampleBuffer = Arrays.copyOf(sampleBuffer, read);
                    bufferSize = read;
                }
                int max = getMax(sampleBuffer);
                Vector<Double> frequencies;
                if(max >(short)200) // Seuil minimum
                {
                    frequencies = poly.run(sampleBuffer);
                    if (frequencies.isEmpty()) continue;

                    /* Removing impossible frequencies  */
                    for(int i=0 ; i<frequencies.size() ; i++)
                    {
                        if(frequencies.get(i)<70) frequencies.remove(i); // Frequencies < 70 get out
                        if(frequencies.get(i)>1400) frequencies.remove(i); // Frequencies > 1400 get out
                    }


                    if (frequencies.isEmpty())
                        continue;

                    Collections.sort(frequencies); // sort from lower to higher

                    Vector<String> notes = new Vector<String>();
                    Log.e("audiotask", "frequencies found : ");
                    for(double freq : frequencies)
                    {
                        Log.e("audiotask", freq+ " ");
                        notes.add(Solfege.getPitchFromFrequence(freq));

                    }
                    if (notes == null)
                        continue;

                    Map<Integer, Integer> tabValues;
                    tabValues = Solfege.findTabValue(notes);

                    Log.e("audiotask", "tab values : ");
                    System.out.println(Arrays.asList(tabValues));

                    currentNotes = notes;
                    currentTabValues = tabValues;
                    uiHandler.sendEmptyMessage(0); // tell ui it should update
                }

                else continue;
                //something went wrong
            }
            audioRecord.stop();
            return null;
        }

        @Override
        protected void onPreExecute() {
            this.execute = true;
        }

        // Method for getting the maximum value
        public int getMax(short[] inputArray){
            int maxValue = inputArray[0];
            for(int i=1;i < inputArray.length;i++){
                if(inputArray[i] > maxValue){
                    maxValue = inputArray[i];
                }
            }
            return maxValue;
        }
    }


}
