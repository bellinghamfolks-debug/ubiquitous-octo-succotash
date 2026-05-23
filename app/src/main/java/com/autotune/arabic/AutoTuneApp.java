package com.autotune.arabic;

import android.app.Application;

/**
 * يُشغَّل قبل أي Activity — يثبّت كاشف الأعطال مبكراً
 * حتى تُلتقط حتى أخطاء تحميل الـ class.
 */
public class AutoTuneApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReporter.install(this);
    }
}
