package com.autotune.arabic.engine;

/**
 * تحويل فورييه السريع (Cooley-Tukey) لأحجام مضاعفة لـ 2.
 * يُستخدم لتحليل الطيف الترددي للصوت.
 */
public class FFT {

    private final int n;
    private final double[] cosTable;
    private final double[] sinTable;

    public FFT(int n) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("حجم FFT يجب أن يكون قوة للعدد 2");
        this.n = n;
        cosTable = new double[n];
        sinTable = new double[n];
        for (int i = 0; i < n; i++) {
            double angle = -2.0 * Math.PI * i / n;
            cosTable[i] = Math.cos(angle);
            sinTable[i] = Math.sin(angle);
        }
    }

    /** تحويل فورييه للأمام - يعدّل المصفوفتين re وim في مكانهما */
    public void forward(double[] re, double[] im) {
        // إعادة ترتيب بعكس البت
        for (int i = 0, j = 0; i < n; i++) {
            if (i < j) {
                double tr = re[i]; re[i] = re[j]; re[j] = tr;
                double ti = im[i]; im[i] = im[j]; im[j] = ti;
            }
            for (int k = n >> 1; (j ^= k) < k; k >>= 1) ;
        }

        // حساب FFT بالفراشات
        for (int len = 2; len <= n; len <<= 1) {
            int halfLen = len >> 1;
            int step = n / len;
            for (int i = 0; i < n; i += len) {
                for (int j = 0; j < halfLen; j++) {
                    int tw = j * step;
                    double wr = cosTable[tw];
                    double wi = sinTable[tw];
                    double tr = wr * re[i + j + halfLen] - wi * im[i + j + halfLen];
                    double ti = wr * im[i + j + halfLen] + wi * re[i + j + halfLen];
                    re[i + j + halfLen] = re[i + j] - tr;
                    im[i + j + halfLen] = im[i + j] - ti;
                    re[i + j] += tr;
                    im[i + j] += ti;
                }
            }
        }
    }

    /** تحويل فورييه العكسي */
    public void inverse(double[] re, double[] im) {
        for (int i = 0; i < n; i++) im[i] = -im[i];
        forward(re, im);
        for (int i = 0; i < n; i++) {
            re[i] /= n;
            im[i] = -im[i] / n;
        }
    }

    public int size() { return n; }
}
