package com.autotune.arabic.engine;

import java.util.Arrays;

/**
 * مُصحِّح الطبقة الصوتية بخوارزمية Phase Vocoder.
 *
 * Phase Vocoder يُحلّل الصوت في الطيف الترددي (FFT) ويعيد تركيبه
 * بعد تحريك الترددات نسبياً - مما يُعطي تأثير AutoTune الاحترافي
 * دون تغيير سرعة الكلام أو الغناء.
 *
 * المعاملات المُختارة:
 *   FFT_SIZE = 2048 → دقة ترددية جيدة
 *   HOP_SIZE = 512  → تحديث كل 11.6ms (عند 44100 هرتز) → استجابة سريعة
 *   تداخل × 4      → جودة عالية في إعادة التركيب
 */
public class PitchShifter {

    private static final int FFT_SIZE = 2048;
    private static final int HOP_SIZE = 512;
    private static final int BINS     = FFT_SIZE / 2 + 1;

    private final FFT fft;
    private final int sampleRate;

    // نافذة Hann للتحليل والتركيب
    private final double[] hann = new double[FFT_SIZE];

    // مصفوفات التحليل
    private final double[] inputFifo  = new double[FFT_SIZE];
    private final double[] anaPhase   = new double[BINS];

    // مصفوفات التركيب
    private final double[] synPhase   = new double[BINS];
    private final double[] outputAcc  = new double[FFT_SIZE + HOP_SIZE * 2];

    // مصفوفات عمل (مُخصَّصة مسبقاً لتجنب GC في خيط الصوت)
    private final double[] re      = new double[FFT_SIZE];
    private final double[] im      = new double[FFT_SIZE];
    private final double[] mag     = new double[BINS];
    private final double[] truFreq = new double[BINS];
    private final double[] synMag  = new double[BINS];
    private final double[] synFreq = new double[BINS];

    private int inputFill     = 0;  // عدد العينات الجديدة في الـ FIFO
    private int outputReadPos = 0;  // موضع القراءة من مصفوفة الخرج
    private int outputAvail   = 0;  // عدد العينات الجاهزة للقراءة

    // معامل التنعيم لتجنب القفزات المفاجئة في نسبة التحريك
    private double smoothedRatio = 1.0;
    private static final double SMOOTH_FACTOR = 0.08;

    public PitchShifter(int sampleRate) {
        this.sampleRate = sampleRate;
        this.fft = new FFT(FFT_SIZE);

        // بناء نافذة Hann مُعيَّرة للتداخل الرباعي
        for (int i = 0; i < FFT_SIZE; i++) {
            hann[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / FFT_SIZE));
        }
    }

    /**
     * يعالج مصفوفة عينات صوتية ويُعيد نسخة مُصحَّحة الطبقة.
     *
     * @param input      عينات الدخل (بين -1 و+1)
     * @param output     مصفوفة الخرج (بنفس حجم input)
     * @param targetRatio نسبة تحريك الطبقة: > 1 = أعلى، < 1 = أخفض، 1 = بدون تغيير
     */
    public void process(float[] input, float[] output, double targetRatio) {
        // تنعيم النسبة لمنع الانقطاعات الصوتية
        smoothedRatio += (targetRatio - smoothedRatio) * SMOOTH_FACTOR;
        double ratio = smoothedRatio;

        int len = Math.min(input.length, output.length);

        for (int i = 0; i < len; i++) {
            // إضافة العينة الجديدة إلى نهاية الـ FIFO
            System.arraycopy(inputFifo, 1, inputFifo, 0, FFT_SIZE - 1);
            inputFifo[FFT_SIZE - 1] = input[i];
            inputFill++;

            // معالجة إطار جديد كل HOP_SIZE عينة
            if (inputFill >= HOP_SIZE) {
                inputFill = 0;
                if (Math.abs(ratio - 1.0) > 0.004) {
                    processFrame(ratio);
                } else {
                    // بدون تصحيح - نمرر العينات مباشرة
                    for (int k = 0; k < HOP_SIZE; k++) {
                        int pos = (outputReadPos + outputAvail + k) % outputAcc.length;
                        outputAcc[pos] = inputFifo[FFT_SIZE - HOP_SIZE + k];
                    }
                    outputAvail += HOP_SIZE;
                }
            }

            // قراءة عينة من مصفوفة الخرج
            if (outputAvail > 0) {
                double val = outputAcc[outputReadPos];
                outputAcc[outputReadPos] = 0;
                outputReadPos = (outputReadPos + 1) % outputAcc.length;
                outputAvail--;
                output[i] = clamp((float) val);
            } else {
                output[i] = 0;
            }
        }
    }

    private void processFrame(double ratio) {
        // تطبيق نافذة Hann على الـ FIFO
        for (int k = 0; k < FFT_SIZE; k++) {
            re[k] = inputFifo[k] * hann[k];
            im[k] = 0;
        }
        fft.forward(re, im);

        double freqPerBin = (double) sampleRate / FFT_SIZE;
        double expPhaseStep = 2.0 * Math.PI * HOP_SIZE / FFT_SIZE;

        // ─── تحليل: حساب المقدار والتردد الحقيقي لكل bin ───
        for (int k = 0; k < BINS; k++) {
            mag[k] = Math.sqrt(re[k] * re[k] + im[k] * im[k]);
            double phase = Math.atan2(im[k], re[k]);

            // فرق الطور من الإطار السابق ناقص القيمة المتوقعة
            double delta = phase - anaPhase[k] - k * expPhaseStep;
            anaPhase[k] = phase;

            // طي delta إلى النطاق [-π، π]
            delta -= 2.0 * Math.PI * Math.round(delta / (2.0 * Math.PI));

            // التردد الحقيقي الآني
            truFreq[k] = k * freqPerBin + delta * sampleRate / (2.0 * Math.PI * HOP_SIZE);
        }

        // ─── تحريك: إعادة توزيع الـ bins بحسب نسبة التحريك ───
        Arrays.fill(synMag, 0);
        Arrays.fill(synFreq, 0);

        for (int k = 0; k < BINS; k++) {
            int kNew = (int) Math.round(k * ratio);
            if (kNew >= 0 && kNew < BINS) {
                synMag[kNew] += mag[k];
                // إذا ارتفعت الطبقة فالتردد يرتفع بنفس النسبة
                if (synMag[kNew] > 0) synFreq[kNew] = truFreq[k] * ratio;
            }
        }

        // ─── تركيب: تحديث أطوار الخرج وإعادة بناء الطيف ───
        for (int k = 0; k < BINS; k++) {
            synPhase[k] += 2.0 * Math.PI * synFreq[k] * HOP_SIZE / sampleRate;
            re[k] = synMag[k] * Math.cos(synPhase[k]);
            im[k] = synMag[k] * Math.sin(synPhase[k]);
        }

        // طيف حقيقي: bin النصف مُكرَّر بشكل مرايا
        for (int k = 1; k < FFT_SIZE / 2; k++) {
            re[FFT_SIZE - k] =  re[k];
            im[FFT_SIZE - k] = -im[k];
        }
        im[0] = 0;
        im[FFT_SIZE / 2] = 0;

        fft.inverse(re, im);

        // ─── Overlap-Add إلى مصفوفة الخرج ───
        // مع نافذة Hann وتداخل ×4: مجموع النوافذ = 2، لذا scale = 1 يعطي كسباً وحدوياً
        final double scale = 1.0;
        for (int k = 0; k < FFT_SIZE; k++) {
            int pos = (outputReadPos + outputAvail + k) % outputAcc.length;
            outputAcc[pos] += re[k] * hann[k] * scale;
        }
        outputAvail += HOP_SIZE;
    }

    public void reset() {
        Arrays.fill(inputFifo, 0);
        Arrays.fill(anaPhase, 0);
        Arrays.fill(synPhase, 0);
        Arrays.fill(outputAcc, 0);
        inputFill = 0;
        outputReadPos = 0;
        outputAvail = 0;
        smoothedRatio = 1.0;
    }

    private float clamp(float v) {
        return v > 1f ? 1f : (v < -1f ? -1f : v);
    }
}
