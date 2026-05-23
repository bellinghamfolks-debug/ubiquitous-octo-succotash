package com.autotune.arabic.engine;

/**
 * كاشف الطبقة الصوتية باستخدام خوارزمية YIN.
 *
 * YIN هي خوارزمية دقيقة وفعّالة لكشف تردد الأصوات الأحادية (صوت بشري، آلات موسيقية).
 * تعمل بالتحليل التلقائي للارتباط الذاتي مع تصحيح منهجي للأخطاء.
 *
 * نطاق الكشف: 80 هرتز (أعمق الأصوات البشرية) إلى 1200 هرتز (فوق التسجيل العادي).
 */
public class PitchDetector {

    public static final double NO_PITCH = -1.0;

    private static final double YIN_THRESHOLD = 0.12;  // عتبة الثقة (أقل = أدق لكن أكثر خطأ)
    private static final double MIN_ENERGY    = 5e-5;  // حد أدنى لطاقة الإشارة لتجاهل الصمت

    private final int sampleRate;
    private final int bufferSize;
    private final double[] yin;  // مصفوفة عمل YIN

    public PitchDetector(int sampleRate, int bufferSize) {
        this.sampleRate = sampleRate;
        this.bufferSize = bufferSize;
        this.yin = new double[bufferSize / 2];
    }

    /**
     * يكتشف التردد الأساسي للصوت في الإطار المعطى.
     *
     * @param buffer  مصفوفة عينات الصوت بين -1.0 و+1.0
     * @return التردد بالهرتز، أو NO_PITCH إذا لم يُكتشَف صوت واضح
     */
    public double detect(float[] buffer) {
        int half = bufferSize / 2;

        // التحقق من وجود طاقة كافية (إذا كان صمتاً فلا فائدة من الحساب)
        double energy = 0;
        for (float s : buffer) energy += (double) s * s;
        if (energy / buffer.length < MIN_ENERGY) return NO_PITCH;

        // الخطوة 1: دالة الفرق (Difference Function)
        for (int tau = 0; tau < half; tau++) {
            yin[tau] = 0;
            for (int j = 0; j < half; j++) {
                double d = buffer[j] - buffer[j + tau];
                yin[tau] += d * d;
            }
        }

        // الخطوة 2: المتوسط التراكمي المُعيَّر (Cumulative Mean Normalized)
        yin[0] = 1.0;
        double runSum = 0;
        for (int tau = 1; tau < half; tau++) {
            runSum += yin[tau];
            yin[tau] = runSum > 0 ? yin[tau] * tau / runSum : 1.0;
        }

        // نطاق البحث: من 80 هرتز إلى 1200 هرتز
        int tauMin = sampleRate / 1200;
        int tauMax = Math.min(half - 2, sampleRate / 80);

        // الخطوة 3: إيجاد أول وادٍ أقل من العتبة
        int tauEst = -1;
        for (int tau = tauMin; tau <= tauMax; tau++) {
            if (yin[tau] < YIN_THRESHOLD) {
                // نتقدم إلى أدنى نقطة في هذا الوادي
                while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) tau++;
                tauEst = tau;
                break;
            }
        }

        if (tauEst == -1) return NO_PITCH;

        // الخطوة 4: الاستيفاء القطعي لتحسين الدقة
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
