package com.autotune.arabic;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * يلتقط أي استثناء غير معالَج ويحفظه في ملف نصي
 * حتى يتمكن المستخدم من إرساله لمعرفة سبب الانهيار.
 */
public class CrashReporter implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultHandler;
    private final File crashDir;

    public static void install(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "crashes");
        dir.mkdirs();
        CrashReporter reporter = new CrashReporter(
                Thread.getDefaultUncaughtExceptionHandler(), dir);
        Thread.setDefaultUncaughtExceptionHandler(reporter);
    }

    /** يُعيد قائمة ملفات الأعطال المحفوظة مرتبةً بالأحدث */
    public static File[] listReports(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "crashes");
        if (!dir.exists()) return new File[0];
        File[] files = dir.listFiles(new java.io.FilenameFilter() {
            public boolean accept(File d, String n) { return n.endsWith(".txt"); }
        });
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, new java.util.Comparator<File>() {
            public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        return files;
    }

    private CrashReporter(Thread.UncaughtExceptionHandler def, File dir) {
        this.defaultHandler = def;
        this.crashDir = dir;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File out = new File(crashDir, "crash_" + ts + ".txt");
            PrintWriter pw = new PrintWriter(new FileWriter(out));

            pw.println("=== تقرير عطل أوتوتيون عربي ===");
            pw.println("التاريخ : " + ts);
            pw.println("الجهاز  : " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.println("MIUI    : " + getMiuiVersion());
            pw.println("Android : " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            pw.println("الخيط   : " + thread.getName());
            pw.println();
            pw.println("=== سبب العطل ===");
            ex.printStackTrace(pw);
            pw.println();
            pw.println("=== السلسلة الكاملة ===");
            Throwable cause = ex.getCause();
            while (cause != null) {
                pw.println("--- Caused by ---");
                cause.printStackTrace(pw);
                cause = cause.getCause();
            }
            pw.flush();
            pw.close();
        } catch (Exception ignored) {}

        // نمرر الاستثناء للمعالج الأصلي حتى يظهر للمستخدم
        if (defaultHandler != null) defaultHandler.uncaughtException(thread, ex);
    }

    private String getMiuiVersion() {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = cls.getMethod("get", String.class, String.class);
            return (String) get.invoke(null, "ro.miui.ui.version.name", "غير شاومي");
        } catch (Exception e) {
            return "—";
        }
    }
}
