package com.example.bilawoga.utils;

import android.content.Context;
import android.util.Log;
import org.tensorflow.lite.Interpreter;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.mfcc.MFCC;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class SilentEmergencyAI {
    private static final String TAG = "SilentEmergencyAI";
    private final Context context;
    private Interpreter tflite;
    private AudioDispatcher dispatcher;
    private boolean isMonitoring = false;
    private EmergencyListener listener;
    private static final float THRESHOLD = 0.5f; // Emergency probability threshold

    public interface EmergencyListener {
        void onEmergencyDetected(String type, float confidence);
        void onEmergencyConfirmed(String type);
        void onFalseAlarmPrevented(String reason);
    }
    
    public SilentEmergencyAI(Context context, EmergencyListener listener) {
        this.context = context;
        this.listener = listener;
        try {
            tflite = new Interpreter(loadModelFile(context, "sos_audio_model.tflite"));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load TFLite model: " + e.getMessage());
        }
    }

    private MappedByteBuffer loadModelFile(Context context, String modelName) throws IOException {
        FileDescriptor fd = context.getAssets().openFd(modelName).getFileDescriptor();
        FileInputStream inputStream = new FileInputStream(fd);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = context.getAssets().openFd(modelName).getStartOffset();
        long declaredLength = context.getAssets().openFd(modelName).getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void startSilentMonitoring() {
        if (isMonitoring || tflite == null) return;
        isMonitoring = true;
        // Remove incorrect Object declaration and use the static method directly
        dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(16000, 1024, 512);
        MFCC mfcc = new MFCC(1024, 16000, 40, 50, 300, 8000);
        List<float[]> mfccFrames = new ArrayList<>();
        dispatcher.addAudioProcessor(mfcc);
        dispatcher.addAudioProcessor(new be.tarsos.dsp.AudioProcessor() {
            @Override
            public boolean process(be.tarsos.dsp.AudioEvent audioEvent) {
                float[] mfccs = mfcc.getMFCC();
                mfccFrames.add(mfccs.clone());
                if (mfccFrames.size() >= 431) {
                    float[][][][] input = new float[1][40][431][1];
                    for (int t = 0; t < 431; t++) {
                        for (int f = 0; f < 40; f++) {
                            input[0][f][t][0] = mfccFrames.get(t)[f];
                        }
                    }
                    float[][] output = new float[1][1];
                    tflite.run(input, output);
                    float emergencyProb = output[0][0];
                    if (emergencyProb > THRESHOLD) {
                        if (listener != null) {
                            listener.onEmergencyDetected("AI Detected Emergency", emergencyProb);
                            listener.onEmergencyConfirmed("AI Detected Emergency");
                        }
                    }
                    mfccFrames.clear();
                }
                return true;
            }
            @Override
            public void processingFinished() {
                // No action needed
            }
        });
        new Thread(dispatcher, "Audio Dispatcher").start();
    }

    public void stopSilentMonitoring() {
        isMonitoring = false;
        if (dispatcher != null) dispatcher.stop();
    }
    
    public void cleanup() {
        stopSilentMonitoring();
        if (tflite != null) tflite.close();
    }
}
