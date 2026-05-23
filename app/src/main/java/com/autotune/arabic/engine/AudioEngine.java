package com.autotune.arabic.engine;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.autotune.arabic.maqam.Maqam;

import java.io.File;
import java.io.IOException;

/**
 * محرك الصوت الرئيسي - يربط التسجيل والكشف والتصحيح والتشغيل.
 *
 * دورة المعالجة:
 *   AudioRecord → كشف الطبقة (YIN) → حساب الهدف (مقام) → تصحيح (Phase Vocoder) → AudioTrack
 *   وفي نفس الوقت إذا كان التسجيل مفعلاً: الخرج المُعالَج → ملف WAV
 */
public class AudioEngine {

    public static final int SAMPLE_RATE = 44100;

    private static final int DETECT_BUFFER  = 4096;
    private static final int PROCESS_BUFFER = 1024;

    private AudioRecord recorder;
    private AudioTrack  player;
    private Thread      processingThread;
    private volatile boolean running = false;

    private final PitchDetector  detector;
    private final PitchShifter   shifter;
    private final RecordingWriter writer = new RecordingWriter();

    // إعدادات قابلة للتعديل من الخيط الرئيسي
    private volatile Maqam   activeMaqam;
    private volatile double  rootHz          = 293.66;
    private volatile boolean bypassMode      = false;
    private volatile double  sensitivity     = 1.0;
    private volatile double  correctionSpeed = 0.10;

    // واجهة الاسترجاع لتحديث الواجهة
    public interface Listener {
        void onPitchDetected(double detectedHz, double targetHz, double deviationCents, String noteName);
        void onSilence();
        void onEngineError(String message);
        void onRecordingSaved(File file, long durationMs);
    }

    private volatile Listener listener;

    // handler مُنشأ مرة واحدة (بدل إنشاء جديد كل إطار في processingLoop)
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    // مصفوفات العمل
    private final float[] inputF    = new float[PROCESS_BUFFER];
    private final float[] outputF   = new float[PROCESS_BUFFER];
    private final short[] inputS    = new short[PROCESS_BUFFER];
    private final short[] outputS   = new short[PROCESS_BUFFER];
    private final float[] detectBuf = new float[DETECT_BUFFER];
    private int detectPos = 0;

    private double currentRatio = 1.0;
    private long   recordingStartMs = 0;
    private int    uiFrameCounter = 0;

    public AudioEngine() {
        detector = new PitchDetector(SAMPLE_RATE, DETECT_BUFFER);
        shifter  = new PitchShifter(SAMPLE_RATE);
    }

    public void setListener(Listener l)          { this.listener        = l; }
    public void setMaqam(Maqam m)                { this.activeMaqam     = m; }
    public void setRootHz(double hz)             { this.rootHz          = hz; }
    public void setBypass(boolean bypass)        { this.bypassMode      = bypass; }
    public void setSensitivity(double s)         { this.sensitivity     = Math.max(0, Math.min(1, s)); }
    public void setCorrectionSpeed(double speed) { this.correctionSpeed = Math.max(0.01, Math.min(0.5, speed)); }

    // ─── التسجيل ────────────────────────────────────────────────────

    /**
     * يبدأ تسجيل الصوت المُعالَج إلى ملف WAV في المجلد المحدد.
     * يجب أن يكون المحرك قيد التشغيل أولاً.
     */
    public boolean startRecording(File saveDir) {
        if (!running) return false;
        try {
            writer.start(saveDir);
            recordingStartMs = System.currentTimeMillis();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** يوقف التسجيل ويحفظ الملف */
    public void stopRecording() {
        File saved = writer.stop();
        if (saved != null && listener != null) {
            final long dur = System.currentTimeMillis() - recordingStartMs;
            final Listener snap = listener;
            final File fSaved = saved;
            mainHandler.post(new Runnable() {
                public void run() { if (snap != null) snap.onRecordingSaved(fSaved, dur); }
            });
        }
    }

    public boolean isRecording() { return writer.isActive(); }

    // ─── تشغيل المحرك ───────────────────────────────────────────────

    public boolean start() {
        if (running) return true;

        int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuf == AudioRecord.ERROR_BAD_VALUE) return false;

        recorder = new AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(Math.max(minBuf, PROCESS_BUFFER * 4))
                .build();
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release(); return false;
        }

        int minTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        player = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(Math.max(minTrack, PROCESS_BUFFER * 8))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
        if (player.getState() != AudioTrack.STATE_INITIALIZED) {
            recorder.release(); player.release(); return false;
        }

        shifter.reset();
        currentRatio = 1.0;
        detectPos = 0;
        java.util.Arrays.fill(detectBuf, 0f);
        recorder.startRecording();
        player.play();
        running = true;

        processingThread = new Thread(new Runnable() {
            public void run() { processingLoop(); }
        }, "AudioEngine");
        processingThread.setPriority(Thread.MAX_PRIORITY);
        processingThread.start();
        return true;
    }

    public void stop() {
        if (writer.isActive()) stopRecording();
        running = false;
        if (processingThread != null) {
            try { processingThread.join(800); } catch (InterruptedException ignored) {}
        }
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        if (player   != null) { try { player.stop();   } catch (Exception ignored) {} player.release();   player   = null; }
    }

    public boolean isRunning() { return running; }

    // ─── حلقة المعالجة ──────────────────────────────────────────────

    private void processingLoop() {
        while (running) {
            int read = recorder.read(inputS, 0, PROCESS_BUFFER);
            if (read <= 0) continue;

            for (int i = 0; i < read; i++) inputF[i] = inputS[i] / 32768f;

            for (int i = 0; i < read; i++) {
                detectBuf[detectPos] = inputF[i];
                detectPos = (detectPos + 1) % DETECT_BUFFER;
            }

            double detectedHz = detector.detect(detectBuf, detectPos);
            double ratio = 1.0;
            uiFrameCounter++;
            boolean postUi = (uiFrameCounter % 4 == 0);

            if (detectedHz > 0 && activeMaqam != null && !bypassMode) {
                double targetHz  = activeMaqam.nearestNote(detectedHz, rootHz);
                double rawRatio  = targetHz > 0 ? targetHz / detectedHz : 1.0;
                ratio = 1.0 + (rawRatio - 1.0) * sensitivity;
                currentRatio += (ratio - currentRatio) * correctionSpeed;
                ratio = currentRatio;

                if (postUi && listener != null) {
                    double dev  = activeMaqam.deviationCents(detectedHz, rootHz);
                    String name = activeMaqam.nearestNoteName(detectedHz, rootHz);
                    final double fd = detectedHz, ft = activeMaqam.nearestNote(detectedHz, rootHz), fdev = dev;
                    final String fn = name;
                    final Listener snap = listener;
                    mainHandler.post(new Runnable() {
                        public void run() {
                            if (snap != null) snap.onPitchDetected(fd, ft, fdev, fn);
                        }
                    });
                }
            } else if (detectedHz <= 0) {
                currentRatio = 1.0;
                shifter.reset();
                if (postUi && listener != null) {
                    final Listener snap = listener;
                    mainHandler.post(new Runnable() {
                        public void run() { if (snap != null) snap.onSilence(); }
                    });
                }
            }

            shifter.process(inputF, outputF, ratio);

            for (int i = 0; i < read; i++) {
                float v = outputF[i];
                outputS[i] = (short)(v > 1f ? 32767 : (v < -1f ? -32768 : (short)(v * 32767)));
            }
            player.write(outputS, 0, read);

            // كتابة الخرج المُعالَج في ملف WAV إذا كان التسجيل نشطاً
            if (writer.isActive()) {
                writer.write(outputS, read);
            }
        }
    }
}
