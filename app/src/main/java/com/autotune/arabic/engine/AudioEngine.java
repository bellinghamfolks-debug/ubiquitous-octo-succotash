package com.autotune.arabic.engine;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import com.autotune.arabic.maqam.Maqam;

/**
 * محرك الصوت الرئيسي - يربط التسجيل والكشف والتصحيح والتشغيل.
 *
 * يعمل في خيط مستقل لضمان معالجة فورية منخفضة التأخير.
 * دورة المعالجة:
 *   AudioRecord → كشف الطبقة (YIN) → حساب الهدف (مقام) → تصحيح الطبقة (Phase Vocoder) → AudioTrack
 */
public class AudioEngine {

    public static final int SAMPLE_RATE = 44100;

    private static final int DETECT_BUFFER  = 4096;  // عينات لخوارزمية YIN (≈93ms)
    private static final int PROCESS_BUFFER = 1024;  // حجم كتلة المعالجة لكل دورة

    private AudioRecord recorder;
    private AudioTrack  player;
    private Thread      processingThread;
    private volatile boolean running = false;

    private final PitchDetector detector;
    private final PitchShifter  shifter;

    // إعدادات قابلة للتعديل من الخيط الرئيسي
    private volatile Maqam   activeMaqam;
    private volatile double  rootHz       = 293.66; // ري افتراضياً
    private volatile boolean bypassMode   = false;
    private volatile double  sensitivity  = 1.0;    // 0.5 = تصحيح جزئي، 1.0 = تصحيح كامل
    private volatile double  correctionSpeed = 0.10; // سرعة الاستجابة

    // واجهة الاسترجاع لتحديث الواجهة
    public interface Listener {
        void onPitchDetected(double detectedHz, double targetHz, double deviationCents, String noteName);
        void onSilence();
        void onEngineError(String message);
    }

    private volatile Listener listener;

    // مصفوفات العمل (مُخصَّصة مرة واحدة لتجنب GC في خيط الصوت)
    private final float[] inputF  = new float[PROCESS_BUFFER];
    private final float[] outputF = new float[PROCESS_BUFFER];
    private final short[] inputS  = new short[PROCESS_BUFFER];
    private final short[] outputS = new short[PROCESS_BUFFER];
    private final float[] detectBuf = new float[DETECT_BUFFER];
    private int detectPos = 0;

    // تنعيم النسبة بين دورات المعالجة
    private double currentRatio = 1.0;

    public AudioEngine() {
        detector = new PitchDetector(SAMPLE_RATE, DETECT_BUFFER);
        shifter  = new PitchShifter(SAMPLE_RATE);
    }

    public void setListener(Listener l) { this.listener = l; }
    public void setMaqam(Maqam m) { this.activeMaqam = m; }
    public void setRootHz(double hz) { this.rootHz = hz; }
    public void setBypass(boolean bypass) { this.bypassMode = bypass; }
    public void setSensitivity(double s) { this.sensitivity = Math.max(0, Math.min(1, s)); }
    public void setCorrectionSpeed(double speed) { this.correctionSpeed = Math.max(0.01, Math.min(0.5, speed)); }

    /** يبدأ محرك الصوت ويُعيد true إذا نجح */
    public boolean start() {
        if (running) return true;

        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        if (minBuf == AudioRecord.ERROR_BAD_VALUE) return false;

        int recBuf = Math.max(minBuf, PROCESS_BUFFER * 4);

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                recBuf);

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            return false;
        }

        int minTrackBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        int playBuf = Math.max(minTrackBuf, PROCESS_BUFFER * 8);

        player = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                playBuf,
                AudioTrack.MODE_STREAM);

        if (player.getState() != AudioTrack.STATE_INITIALIZED) {
            recorder.release();
            player.release();
            return false;
        }

        shifter.reset();
        currentRatio = 1.0;

        recorder.startRecording();
        player.play();
        running = true;

        processingThread = new Thread(this::processingLoop, "AudioEngine");
        processingThread.setPriority(Thread.MAX_PRIORITY);
        processingThread.start();

        return true;
    }

    /** يوقف المحرك بأمان */
    public void stop() {
        running = false;
        if (processingThread != null) {
            try { processingThread.join(800); } catch (InterruptedException ignored) {}
        }
        if (recorder != null) { try { recorder.stop(); } catch (Exception ignored) {} recorder.release(); recorder = null; }
        if (player   != null) { try { player.stop();   } catch (Exception ignored) {} player.release();   player   = null; }
    }

    public boolean isRunning() { return running; }

    // ─── حلقة المعالجة الرئيسية ────────────────────────────────────
    private void processingLoop() {
        while (running) {
            // قراءة عينات من الميكروفون
            int read = recorder.read(inputS, 0, PROCESS_BUFFER);
            if (read <= 0) continue;

            // تحويل من short إلى float [-1, +1]
            for (int i = 0; i < read; i++) {
                inputF[i] = inputS[i] / 32768f;
            }

            // تراكم العينات في مصفوفة الكشف
            for (int i = 0; i < read; i++) {
                detectBuf[detectPos] = inputF[i];
                detectPos = (detectPos + 1) % DETECT_BUFFER;
            }

            // كشف الطبقة
            double detectedHz = detector.detect(detectBuf);
            double targetHz   = detectedHz;
            double ratio      = 1.0;

            if (detectedHz > 0 && activeMaqam != null && !bypassMode) {
                targetHz = activeMaqam.nearestNote(detectedHz, rootHz);
                if (targetHz > 0 && detectedHz > 0) {
                    double rawRatio = targetHz / detectedHz;
                    // تطبيق الحساسية: ratio = 1 + (rawRatio - 1) * sensitivity
                    ratio = 1.0 + (rawRatio - 1.0) * sensitivity;
                    // تنعيم الانتقال
                    currentRatio += (ratio - currentRatio) * correctionSpeed;
                    ratio = currentRatio;
                }

                // إخطار الواجهة
                if (listener != null) {
                    double dev = activeMaqam.deviationCents(detectedHz, rootHz);
                    String name = activeMaqam.nearestNoteName(detectedHz, rootHz);
                    final double fd = detectedHz, ft = targetHz, fdev = dev;
                    final String fn = name;
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    h.post(() -> { if (listener != null) listener.onPitchDetected(fd, ft, fdev, fn); });
                }
            } else if (detectedHz <= 0) {
                currentRatio = 1.0;
                shifter.reset();
                if (listener != null) {
                    android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                    h.post(() -> { if (listener != null) listener.onSilence(); });
                }
            }

            // تصحيح الطبقة
            shifter.process(inputF, outputF, ratio);

            // تحويل من float إلى short وإرسال للمكبر
            for (int i = 0; i < read; i++) {
                float v = outputF[i];
                outputS[i] = (short) (v > 1f ? 32767 : (v < -1f ? -32768 : (short)(v * 32767)));
            }
            player.write(outputS, 0, read);
        }
    }
}
