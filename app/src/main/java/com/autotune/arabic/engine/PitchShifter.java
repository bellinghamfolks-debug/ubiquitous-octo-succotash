package com.autotune.arabic.engine;

import java.util.Arrays;

/**
 * Phase Vocoder لتصحيح الطبقة الصوتية بدون تغيير السرعة.
 *
 * الإصلاحات الرئيسية مقارنةً بالنسخة الأولى:
 *   - ring buffer للمدخلات بدل System.arraycopy كل عينة (كان O(N) لكل عينة = 90M نسخة/ثانية)
 *   - scale = 2/3 الصحيح رياضياً لنافذة Hann × تداخل ×4 (كان 1.0 = تشويه بالتشبع)
 *   - synFreq بالمتوسط الموزون عند تداخل bins (كان يُكتب فوق القيمة السابقة)
 */
public class PitchShifter {

    private static final int FFT_SIZE = 2048;
    private static final int HOP_SIZE = 512;
    private static final int BINS     = FFT_SIZE / 2 + 1;
    private static final int OLA_SIZE = FFT_SIZE * 4;   // 8192 — كافٍ للتداخل ×4

    private final FFT    fft;
    private final int    sampleRate;
    private final double[] hann = new double[FFT_SIZE];

    // ─── مدخلات: ring buffer (بدل FIFO بنسخ) ────────────────────────
    private final double[] inRing    = new double[FFT_SIZE];
    private int            inWrPos   = 0;    // موضع الكتابة التالي
    private int            inHopCnt  = 0;    // عداد لبدء الإطار التالي

    // ─── مخرجات: Overlap-Add دائري ───────────────────────────────────
    private final double[] outOLA   = new double[OLA_SIZE];
    private int            outRdPos = 0;     // موضع القراءة
    private int            outFill  = 0;     // عينات جاهزة

    // ─── تتبع الطور ──────────────────────────────────────────────────
    private final double[] anaPhase = new double[BINS];
    private final double[] synPhase = new double[BINS];

    // ─── مصفوفات عمل مُخصَّصة مسبقاً ────────────────────────────────
    private final double[] re      = new double[FFT_SIZE];
    private final double[] im      = new double[FFT_SIZE];
    private final double[] mag     = new double[BINS];
    private final double[] truFreq = new double[BINS];
    private final double[] synMag  = new double[BINS];
    private final double[] synFreq = new double[BINS];

    public PitchShifter(int sampleRate) {
        this.sampleRate = sampleRate;
        this.fft = new FFT(FFT_SIZE);
        for (int i = 0; i < FFT_SIZE; i++)
            hann[i] = 0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / FFT_SIZE));
    }

    public void process(float[] input, float[] output, double targetRatio) {
        // التنعيم يتم في AudioEngine عبر correctionSpeed (شريط المستخدم).
        // تنعيم إضافي هنا = تأخير مضاعف (~1.5 ثانية) → الـ ratio يبقى ~1.0
        // → bypass دائم → لا autotune. نمرر الـ ratio كما هو.
        double ratio = targetRatio;
        int len = Math.min(input.length, output.length);

        for (int i = 0; i < len; i++) {

            // كتابة O(1) — بدون arraycopy
            inRing[inWrPos] = input[i];
            inWrPos = (inWrPos + 1) % FFT_SIZE;
            inHopCnt++;

            if (inHopCnt >= HOP_SIZE) {
                inHopCnt = 0;
                if (Math.abs(ratio - 1.0) > 0.002) {
                    processFrame(ratio);
                } else {
                    // bypass: نسخ مباشر بدون معالجة
                    for (int k = 0; k < HOP_SIZE; k++) {
                        int rIdx = (inWrPos - HOP_SIZE + k + FFT_SIZE) % FFT_SIZE;
                        int oIdx = (outRdPos + outFill + k) % OLA_SIZE;
                        outOLA[oIdx] += inRing[rIdx];
                    }
                    outFill += HOP_SIZE;
                }
            }

            if (outFill > 0) {
                output[i] = clamp((float) outOLA[outRdPos]);
                outOLA[outRdPos] = 0.0;
                outRdPos = (outRdPos + 1) % OLA_SIZE;
                outFill--;
            } else {
                output[i] = 0.0f;
            }
        }
    }

    private void processFrame(double ratio) {
        // قراءة ring buffer بالترتيب (من الأقدم إلى الأحدث)
        for (int k = 0; k < FFT_SIZE; k++) {
            re[k] = inRing[(inWrPos + k) % FFT_SIZE] * hann[k];
            im[k] = 0.0;
        }
        fft.forward(re, im);

        double freqPerBin   = (double) sampleRate / FFT_SIZE;
        double hopPhaseStep = 2.0 * Math.PI * HOP_SIZE / FFT_SIZE;

        // ─── تحليل الطور ─────────────────────────────────────────────
        for (int k = 0; k < BINS; k++) {
            mag[k] = Math.sqrt(re[k] * re[k] + im[k] * im[k]);
            double phase = Math.atan2(im[k], re[k]);
            double delta = phase - anaPhase[k] - k * hopPhaseStep;
            anaPhase[k] = phase;
            delta -= 2.0 * Math.PI * Math.round(delta / (2.0 * Math.PI));
            truFreq[k] = (k + delta / hopPhaseStep) * freqPerBin;
        }

        // ─── تحريك bins بنسبة ratio ───────────────────────────────────
        Arrays.fill(synMag,  0.0);
        Arrays.fill(synFreq, 0.0);
        for (int k = 0; k < BINS; k++) {
            if (mag[k] == 0.0) continue;
            int kNew = (int) Math.round(k * ratio);
            if (kNew >= 0 && kNew < BINS) {
                // تجميع المقدار والتردد (متوسط موزون بالمقدار)
                synFreq[kNew] = (synMag[kNew] * synFreq[kNew] + mag[k] * truFreq[k] * ratio)
                                / (synMag[kNew] + mag[k]);
                synMag[kNew] += mag[k];
            }
        }

        // ─── تركيب ────────────────────────────────────────────────────
        for (int k = 0; k < BINS; k++) {
            synPhase[k] += 2.0 * Math.PI * synFreq[k] / sampleRate * HOP_SIZE;
            re[k] = synMag[k] * Math.cos(synPhase[k]);
            im[k] = synMag[k] * Math.sin(synPhase[k]);
        }

        // طيف حقيقي متماثل
        for (int k = 1; k < FFT_SIZE / 2; k++) {
            re[FFT_SIZE - k] =  re[k];
            im[FFT_SIZE - k] = -im[k];
        }
        im[0]            = 0.0;
        im[FFT_SIZE / 2] = 0.0;

        fft.inverse(re, im);

        // ─── Overlap-Add ──────────────────────────────────────────────
        // scale = 2/3: تصحيح Hann(تحليل) × Hann(تركيب) × تداخل×4
        // مجموع hann²[n] على 4 إطارات متداخلة = 1.5 → scale = 1/1.5 = 2/3
        final double scale = 2.0 / 3.0;
        int wrStart = (outRdPos + outFill) % OLA_SIZE;
        for (int k = 0; k < FFT_SIZE; k++) {
            outOLA[(wrStart + k) % OLA_SIZE] += re[k] * hann[k] * scale;
        }
        outFill += HOP_SIZE;
    }

    public void reset() {
        Arrays.fill(inRing,   0.0);
        Arrays.fill(outOLA,   0.0);
        Arrays.fill(anaPhase, 0.0);
        Arrays.fill(synPhase, 0.0);
        inWrPos   = 0;
        inHopCnt  = 0;
        outRdPos  = 0;
        outFill   = 0;
    }

    private float clamp(float v) {
        return v > 1.0f ? 1.0f : (v < -1.0f ? -1.0f : v);
    }
}
