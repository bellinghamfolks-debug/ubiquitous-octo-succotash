package com.autotune.arabic.engine;

/**
 * كاشف الطبقة الصوتية باستخدام خوارزمية YIN.
 */
public class PitchDetector {

    public static final double NO_PITCH = -1.0;

    private static final double YIN_THRESHOLD = 0.15;
    private static final double MIN_ENERGY    = 1e-6;

    private final int     sampleRate;
    private final int     bufferSize;
    private final double[] yin;
    private final float[]  linear;  // نسخة مرتبة زمنياً من ring buffer

    public PitchDetector(int sampleRate, int bufferSize) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.yin    = new double[bufferSize / 2];
        this.linear = new float[bufferSize];
    }

    /**
     * @param ring     ring buffer مُعبَّأ دائرياً
     * @param writePos موضع الكتابة التالي (= أقدم عينة في الـ ring)
     */
    public double detect(float[] ring, int writePos) {
        // نسخ الـ ring buffer بالترتيب الزمني الصحيح (أقدم → أحدث)
        int tail = bufferSize - writePos;
        System.arraycopy(ring, writePos, linear, 0,    tail);
        System.arraycopy(ring, 0,        linear, tail, writePos);

        int half = bufferSize / 2;

        double energy = 0;
        for (int i = 0; i < bufferSize; i++) energy += (double) linear[i] * linear[i];
        if (energy / bufferSize < MIN_ENERGY) return NO_PITCH;

        // دالة الفرق
        for (int tau = 0; tau < half; tau++) {
            yin[tau] = 0;
            for (int j = 0; j < half; j++) {
                double d = linear[j] - linear[j + tau];
                yin[tau] += d * d;
            }
        }

        // المتوسط التراكمي المُعيَّر
        yin[0] = 1.0;
        double runSum = 0;
        for (int tau = 1; tau < half; tau++) {
            runSum += yin[tau];
            yin[tau] = runSum > 0 ? yin[tau] * tau / runSum : 1.0;
        }

        int tauMin = sampleRate / 1200;
        int tauMax = Math.min(half - 2, sampleRate / 80);

        int tauEst = -1;
        for (int tau = tauMin; tau <= tauMax; tau++) {
            if (yin[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) tau++;
                tauEst = tau;
                break;
            }
        }

        if (tauEst == -1) return NO_PITCH;

        double betterTau;
        if (tauEst > 1 && tauEst < half - 1) {
            double s0 = yin[tauEst - 1];
            double s1 = yin[tauEst];
            double s2 = yin[tauEst + 1];
            double den = 2.0 * (2.0 * s1 - s0 - s2);
            betterTau = Math.abs(den) > 1e-12 ? tauEst + (s2 - s0) / den : tauEst;
        } else {
            betterTau = tauEst;
        }

        return sampleRate / betterTau;
    }
}
