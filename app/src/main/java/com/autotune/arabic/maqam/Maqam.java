package com.autotune.arabic.maqam;

/**
 * يمثّل مقاماً موسيقياً عربياً واحداً بدرجاته ونسبه بالسنت من الجذر.
 * يدعم ربع التون (50 سنت) الأساسي في الموسيقى العربية.
 */
public class Maqam {

    public final String nameAr;
    public final String nameEn;
    public final String description;

    // درجات المقام بالسنت من النغمة الجذر (في نطاق أوكتاف واحد)
    private final double[] degreeCents;

    // ألوان مميزة لكل مقام (لعرضها في الواجهة)
    public final int accentColor;

    public Maqam(String nameAr, String nameEn, String description,
                 double[] degreeCents, int accentColor) {
        this.nameAr     = nameAr;
        this.nameEn     = nameEn;
        this.description = description;
        this.degreeCents = degreeCents.clone();
        this.accentColor = accentColor;
    }

    /**
     * يجد أقرب درجة في المقام لتردد الصوت المُكتشَف.
     *
     * @param detectedHz تردد الصوت المُكتشَف بالهرتز
     * @param rootHz     تردد النغمة الجذر للمقام (مثلاً D = 293.66 هرتز)
     * @return تردد أقرب درجة في المقام بالهرتز
     */
    public double nearestNote(double detectedHz, double rootHz) {
        if (detectedHz <= 0 || rootHz <= 0) return detectedHz;

        // تحويل التردد المكتشَف إلى سنت فوق الجذر
        double centsAboveRoot = 1200.0 * Math.log(detectedHz / rootHz) / Math.log(2.0);

        // إيجاد رقم الأوكتاف الحالي
        double octaveNum = Math.floor(centsAboveRoot / 1200.0);

        double minDist = Double.MAX_VALUE;
        double nearestCent = 0;

        // نبحث في الأوكتاف الحالي والمجاورين لضمان الدقة
        for (int oct = -1; oct <= 2; oct++) {
            for (double deg : degreeCents) {
                double candidate = deg + (octaveNum + oct) * 1200.0;
                double dist = Math.abs(centsAboveRoot - candidate);
                if (dist < minDist) {
                    minDist = dist;
                    nearestCent = candidate;
                }
            }
        }

        return rootHz * Math.pow(2.0, nearestCent / 1200.0);
    }

    /**
     * يحسب الانحراف بالسنت بين الصوت المكتشَف وأقرب درجة في المقام.
     * موجب = أعلى من الدرجة، سالب = أخفض.
     */
    public double deviationCents(double detectedHz, double rootHz) {
        double target = nearestNote(detectedHz, rootHz);
        if (target <= 0 || detectedHz <= 0) return 0;
        return 1200.0 * Math.log(detectedHz / target) / Math.log(2.0);
    }

    /**
     * يُعيد اسم درجة المقام الأقرب بالعربية (مثلاً "ري" أو "مي نصف بمول").
     */
    public String nearestNoteName(double detectedHz, double rootHz) {
        if (detectedHz <= 0 || rootHz <= 0) return "—";
        double centsAboveRoot = 1200.0 * Math.log(detectedHz / rootHz) / Math.log(2.0);
        double octaveNum = Math.floor(centsAboveRoot / 1200.0);

        double minDist = Double.MAX_VALUE;
        double nearestDeg = 0;
        int nearestIdx = 0;

        for (int oct = -1; oct <= 2; oct++) {
            for (int i = 0; i < degreeCents.length; i++) {
                double candidate = degreeCents[i] + (octaveNum + oct) * 1200.0;
                double dist = Math.abs(centsAboveRoot - candidate);
                if (dist < minDist) {
                    minDist = dist;
                    nearestDeg = degreeCents[i];
                    nearestIdx = i;
                }
            }
        }

        return degreeArabicName(nearestIdx, nearestDeg, rootHz);
    }

    private String degreeArabicName(int index, double cents, double rootHz) {
        // أسماء الدرجات الكروماتية من C بالسنت
        double[] chromatic = {0, 100, 150, 200, 300, 350, 400, 500, 600, 700, 800, 850, 900, 1000, 1050, 1100};
        String[] names = {"دو", "دو#", "ري♭½", "ري", "ري#", "مي♭½", "مي", "فا", "فا#", "صول", "صول#", "لا♭½", "لا", "لا#", "سي♭½", "سي"};

        // نحسب درجة C أقرب للجذر لنحدد أسماء الدرجات
        double centsFromC = getCentsFromC(rootHz);
        double degInC = (cents + centsFromC) % 1200.0;
        if (degInC < 0) degInC += 1200.0;

        double minD = Double.MAX_VALUE;
        String best = "—";
        for (int i = 0; i < chromatic.length; i++) {
            double d = Math.abs(degInC - chromatic[i]);
            if (d > 600) d = 1200 - d;
            if (d < minD) { minD = d; best = names[i]; }
        }
        return best;
    }

    private double getCentsFromC(double rootHz) {
        double c4 = 261.63;
        return 1200.0 * Math.log(rootHz / c4) / Math.log(2.0);
    }

    public double[] getDegreeCents() {
        return degreeCents.clone();
    }
}
