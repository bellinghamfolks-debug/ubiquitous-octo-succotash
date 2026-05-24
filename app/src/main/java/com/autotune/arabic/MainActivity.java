package com.autotune.arabic;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.autotune.arabic.engine.AudioEngine;
import com.autotune.arabic.maqam.Maqam;
import com.autotune.arabic.maqam.MaqamLibrary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements AudioEngine.Listener {

    private static final int COLOR_BG      = 0xFF0D0D14;
    private static final int COLOR_CARD    = 0xFF1A1A2E;
    private static final int COLOR_GOLD    = 0xFFD4A017;
    private static final int COLOR_GREEN   = 0xFF00E676;
    private static final int COLOR_RED     = 0xFFFF1744;
    private static final int COLOR_TEXT    = 0xFFE8E8F0;
    private static final int COLOR_SUBTEXT = 0xFF8888AA;
    private static final int COLOR_DIVIDER = 0xFF2A2A45;
    private static final int COLOR_REC     = 0xFFFF1744;

    private static final int PERM_CODE = 101;

    private AudioEngine engine;
    private List<Maqam> maqams;
    private int selectedMaqamIdx = 0;
    private int selectedRootIdx  = 2;

    private PitchMeterView pitchMeter;
    private TextView       tvDetectedNote, tvDetectedHz, tvTargetNote, tvDeviationCents;
    private TextView       tvStatus;
    private TextView[]     maqamButtons, rootButtons;
    private TextView       btnStart, btnRecord;
    private CheckBox       chkBypass;
    private LinearLayout   recordingsContainer;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private long     recStartMs   = 0;
    private Runnable recTimerTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CrashReporter.install(this);
        try {
            getWindow().getDecorView().setBackgroundColor(COLOR_BG);
            maqams = MaqamLibrary.buildAll();
            engine = new AudioEngine();
            engine.setListener(this);
            engine.setMaqam(maqams.get(selectedMaqamIdx));
            engine.setRootHz(MaqamLibrary.ROOT_FREQUENCIES[selectedRootIdx]);
            buildUi();
        } catch (Throwable t) {
            showStartupError(t);
        }
    }

    private void showStartupError(final Throwable t) {
        try {
            File dir = new File(getFilesDir(), "crashes");
            dir.mkdirs();
            PrintWriter pw = new PrintWriter(new File(dir, "startup_error.txt"));
            pw.println("Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            t.printStackTrace(pw);
            pw.close();
        } catch (Exception ignored) {}

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(COLOR_BG);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(30, 60, 30, 30);
        sv.addView(ll);

        TextView title = new TextView(this);
        title.setText("⚠ خطأ عند البدء — خذ لقطة شاشة");
        title.setTextColor(COLOR_RED);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        ll.addView(title);

        TextView info = new TextView(this);
        info.setText("Android " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT
                + " | " + Build.MANUFACTURER + " " + Build.MODEL);
        info.setTextColor(COLOR_SUBTEXT);
        info.setTextSize(13);
        info.setPadding(0, 12, 0, 12);
        ll.addView(info);

        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        final String trace = "Android " + Build.VERSION.RELEASE + " API "
                + Build.VERSION.SDK_INT + " | " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n\n" + sw.toString();

        TextView tv = new TextView(this);
        tv.setText(trace);
        tv.setTextColor(0xFFFFCCCC);
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        ll.addView(tv);

        TextView btnCopy = new TextView(this);
        btnCopy.setText("نسخ النص كاملاً");
        btnCopy.setTextColor(Color.BLACK);
        btnCopy.setTextSize(16);
        btnCopy.setGravity(Gravity.CENTER);
        btnCopy.setPadding(20, 20, 20, 20);
        btnCopy.setBackgroundColor(COLOR_GOLD);
        btnCopy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("err", trace));
                Toast.makeText(MainActivity.this, "تم النسخ — الصقه هنا في المحادثة",
                        Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 24, 0, 0);
        ll.addView(btnCopy, btnLp);

        setContentView(sv);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) engine.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == PERM_CODE && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startEngine();
        } else {
            Toast.makeText(this, "يجب منح إذن الميكروفون", Toast.LENGTH_LONG).show();
        }
    }

    // ─── بناء الواجهة ────────────────────────────────────────────────

    private void buildUi() {
        ScrollView root = new ScrollView(this);
        root.setBackgroundColor(COLOR_BG);
        root.setFillViewport(true);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(24), dp(16), dp(32));
        root.addView(page);

        page.addView(buildHeader());
        page.addView(buildPitchDisplay(), mpWrap(0, dp(20)));
        page.addView(sectionTitle("اختر المقام"));
        page.addView(buildMaqamGrid(), mpWrap(0, dp(8)));
        page.addView(sectionTitle("نغمة الجذر"));
        page.addView(buildRootRow(), mpWrap(0, dp(8)));
        page.addView(sectionTitle("إعدادات التصحيح"));
        page.addView(buildControls(), mpWrap(0, dp(8)));

        chkBypass = new CheckBox(this);
        chkBypass.setText("وضع التمرير المباشر (Bypass)");
        chkBypass.setTextColor(COLOR_SUBTEXT);
        chkBypass.setTextSize(15);
        chkBypass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) { engine.setBypass(c); }
        });
        page.addView(chkBypass, mpWrap(0, dp(12)));

        page.addView(buildStartButton(), mpWrap(dp(8), dp(4)));
        page.addView(buildRecordButton(), mpWrap(dp(8), dp(8)));

        page.addView(sectionTitle("التسجيلات المحفوظة"));
        recordingsContainer = new LinearLayout(this);
        recordingsContainer.setOrientation(LinearLayout.VERTICAL);
        page.addView(recordingsContainer, mpWrap(0, dp(4)));
        refreshRecordingsList();

        TextView note = label("محفوظ في مجلد Music في الجهاز", 12, COLOR_SUBTEXT);
        note.setGravity(Gravity.CENTER);
        page.addView(note, mpWrap(0, dp(12)));

        File[] crashes = CrashReporter.listReports(this);
        if (crashes.length > 0) {
            page.addView(sectionTitle("⚠ تقارير أعطال محفوظة"));
            for (File crash : crashes) {
                page.addView(buildCrashRow(crash), mpWrap(0, dp(4)));
            }
        }

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);

        TextView title = label("أوتوتيون عربي", 28, COLOR_GOLD);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        h.addView(title, mpWrap());

        TextView sub = label("كاشف ومُصحِّح الطبقات الصوتية للمقامات العربية", 14, COLOR_SUBTEXT);
        sub.setGravity(Gravity.CENTER);
        h.addView(sub, mpWrap(0, dp(4)));

        tvStatus = label("● متوقف", 13, COLOR_SUBTEXT);
        tvStatus.setGravity(Gravity.CENTER);
        h.addView(tvStatus, mpWrap(0, dp(4)));
        return h;
    }

    private View buildPitchDisplay() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(20), dp(16), dp(20));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        LinearLayout col1 = col();
        col1.addView(label("صوتك", 12, COLOR_SUBTEXT));
        tvDetectedNote = label("—", 42, COLOR_TEXT);
        tvDetectedNote.setTypeface(Typeface.DEFAULT_BOLD);
        tvDetectedNote.setGravity(Gravity.CENTER);
        col1.addView(tvDetectedNote);
        tvDetectedHz = label("— هرتز", 13, COLOR_SUBTEXT);
        tvDetectedHz.setGravity(Gravity.CENTER);
        col1.addView(tvDetectedHz);

        TextView arrow = label("→", 28, COLOR_GOLD);
        arrow.setPadding(dp(16), 0, dp(16), 0);

        LinearLayout col2 = col();
        col2.addView(label("الهدف", 12, COLOR_SUBTEXT));
        tvTargetNote = label("—", 42, COLOR_GREEN);
        tvTargetNote.setTypeface(Typeface.DEFAULT_BOLD);
        tvTargetNote.setGravity(Gravity.CENTER);
        col2.addView(tvTargetNote);
        tvDeviationCents = label("± 0 سنت", 13, COLOR_SUBTEXT);
        tvDeviationCents.setGravity(Gravity.CENTER);
        col2.addView(tvDeviationCents);

        row.addView(col1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(arrow);
        row.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row, mpWrap(0, dp(16)));

        pitchMeter = new PitchMeterView(this);
        pitchMeter.setContentDescription("مقياس الانحراف الصوتي");
        card.addView(pitchMeter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        TextView ml = label("← أخفض   ضبط تام   أعلى →", 12, COLOR_SUBTEXT);
        ml.setGravity(Gravity.CENTER);
        card.addView(ml, mpWrap(0, dp(6)));
        return card;
    }

    private View buildMaqamGrid() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        maqamButtons = new TextView[maqams.size()];
        int cols = 3;
        for (int row = 0; row * cols < maqams.size(); row++) {
            LinearLayout rowL = new LinearLayout(this);
            rowL.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < cols && row * cols + col < maqams.size(); col++) {
                final int idx = row * cols + col;
                Maqam m = maqams.get(idx);
                TextView btn = maqamChip(m.nameAr, idx == selectedMaqamIdx, m.accentColor);
                btn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { selectMaqam(idx); }
                });
                maqamButtons[idx] = btn;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                rowL.addView(btn, lp);
            }
            outer.addView(rowL, mpWrap());
        }
        return outer;
    }

    private View buildRootRow() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(4), 0, dp(4), 0);
        scroll.addView(row);
        rootButtons = new TextView[MaqamLibrary.ROOT_NAMES_AR.length];
        for (int i = 0; i < MaqamLibrary.ROOT_NAMES_AR.length; i++) {
            final int fi = i;
            TextView btn = rootChip(MaqamLibrary.ROOT_NAMES_AR[i], i == selectedRootIdx);
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selectRoot(fi); }
            });
            rootButtons[i] = btn;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(btn, lp);
        }
        return scroll;
    }

    private View buildControls() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        card.addView(label("قوة التصحيح", 14, COLOR_TEXT));
        SeekBar sbSens = new SeekBar(this);
        sbSens.setMax(100); sbSens.setProgress(100);
        sbSens.setContentDescription("قوة التصحيح — شريط تمرير");
        sbSens.setOnSeekBarChangeListener(simpleSeek(new SeekAction() {
            public void onValue(int p) { engine.setSensitivity(p / 100.0); }
        }));
        card.addView(sbSens, mpWrap(0, dp(8)));

        card.addView(label("سرعة التصحيح  (بطيء ◄────► سريع)", 14, COLOR_TEXT));
        SeekBar sbSpeed = new SeekBar(this);
        sbSpeed.setMax(100); sbSpeed.setProgress(60);
        sbSpeed.setContentDescription("سرعة التصحيح — شريط تمرير");
        sbSpeed.setOnSeekBarChangeListener(simpleSeek(new SeekAction() {
            public void onValue(int p) { engine.setCorrectionSpeed(0.01 + p / 100.0 * 0.49); }
        }));
        card.addView(sbSpeed, mpWrap(0, dp(4)));
        return card;
    }

    private View buildStartButton() {
        btnStart = new TextView(this);
        btnStart.setText("▶  ابدأ المعالجة");
        btnStart.setContentDescription("ابدأ المعالجة الصوتية");
        btnStart.setTextSize(20);
        btnStart.setTypeface(Typeface.DEFAULT_BOLD);
        btnStart.setTextColor(Color.BLACK);
        btnStart.setGravity(Gravity.CENTER);
        btnStart.setPadding(dp(24), dp(18), dp(24), dp(18));
        btnStart.setBackgroundColor(COLOR_GOLD);
        btnStart.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleEngine(); }
        });
        return btnStart;
    }

    private View buildRecordButton() {
        btnRecord = new TextView(this);
        btnRecord.setText("⏺  ابدأ التسجيل");
        btnRecord.setContentDescription("ابدأ تسجيل الصوت المُعالَج");
        btnRecord.setTextSize(18);
        btnRecord.setTypeface(Typeface.DEFAULT_BOLD);
        btnRecord.setTextColor(Color.WHITE);
        btnRecord.setGravity(Gravity.CENTER);
        btnRecord.setPadding(dp(24), dp(16), dp(24), dp(16));
        btnRecord.setBackgroundColor(COLOR_DIVIDER);
        btnRecord.setEnabled(false);
        btnRecord.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { toggleRecording(); }
        });
        return btnRecord;
    }

    // ─── منطق التشغيل ────────────────────────────────────────────────

    private void toggleEngine() {
        if (engine.isRunning()) {
            if (recTimerTask != null) { uiHandler.removeCallbacks(recTimerTask); recTimerTask = null; }
            engine.stop();
            btnStart.setText("▶  ابدأ المعالجة");
            btnStart.setBackgroundColor(COLOR_GOLD);
            btnStart.setTextColor(Color.BLACK);
            tvStatus.setText("● متوقف");
            tvStatus.setTextColor(COLOR_SUBTEXT);
            btnRecord.setEnabled(false);
            btnRecord.setBackgroundColor(COLOR_DIVIDER);
            btnRecord.setText("⏺  ابدأ التسجيل");
            resetDisplay();
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_CODE);
            } else {
                startEngine();
            }
        }
    }

    private void startEngine() {
        boolean started;
        try {
            started = engine.start();
        } catch (Throwable t) {
            showEngineError(t);
            return;
        }
        if (!started) {
            Toast.makeText(this, "تعذّر فتح الميكروفون — تأكد من منح إذن الميكروفون",
                    Toast.LENGTH_LONG).show();
            return;
        }
        btnStart.setText("■  إيقاف المعالجة");
        btnStart.setBackgroundColor(COLOR_RED);
        btnStart.setTextColor(Color.WHITE);
        tvStatus.setText("● يعمل");
        tvStatus.setTextColor(COLOR_GREEN);
        btnRecord.setEnabled(true);
        btnRecord.setBackgroundColor(COLOR_REC);
    }

    private void showEngineError(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        final String trace = t.getClass().getSimpleName() + ": " + t.getMessage()
                + "\n\n" + sw.toString();
        // حفظ في ملف
        try {
            File dir = new File(getFilesDir(), "crashes");
            dir.mkdirs();
            PrintWriter pw = new PrintWriter(new File(dir, "engine_error.txt"));
            pw.println("Android: " + Build.VERSION.RELEASE + " API " + Build.VERSION.SDK_INT);
            pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            pw.print(trace);
            pw.close();
        } catch (Exception ignored) {}
        // عرض AlertDialog بالخطأ مع زر نسخ
        new android.app.AlertDialog.Builder(this)
                .setTitle("خطأ في المحرك الصوتي")
                .setMessage(trace)
                .setPositiveButton("نسخ", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        ClipboardManager cm = (ClipboardManager)
                                getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("err", trace));
                        Toast.makeText(MainActivity.this, "تم النسخ",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("إغلاق", null)
                .show();
    }

    private void toggleRecording() {
        if (engine.isRecording()) {
            if (recTimerTask != null) { uiHandler.removeCallbacks(recTimerTask); recTimerTask = null; }
            engine.stopRecording();
            btnRecord.setText("⏺  ابدأ التسجيل");
            btnRecord.setBackgroundColor(COLOR_REC);
            tvStatus.setText("● يعمل");
            tvStatus.setTextColor(COLOR_GREEN);
        } else {
            File dir = getSaveDir();
            if (engine.startRecording(dir)) {
                btnRecord.setText("⏹  إيقاف التسجيل");
                btnRecord.setBackgroundColor(0xFFB71C1C);
                recStartMs = System.currentTimeMillis();
                recTimerTask = new Runnable() {
                    public void run() {
                        if (engine.isRecording()) {
                            long s = (System.currentTimeMillis() - recStartMs) / 1000;
                            tvStatus.setText("⏺ يُسجِّل  " + (s / 60) + ":"
                                    + String.format("%02d", s % 60));
                            tvStatus.setTextColor(COLOR_REC);
                            uiHandler.postDelayed(this, 1000);
                        }
                    }
                };
                uiHandler.post(recTimerTask);
            } else {
                Toast.makeText(this, "تعذّر بدء التسجيل", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private File getSaveDir() {
        File ext = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (ext != null && (ext.exists() || ext.mkdirs())) return ext;
        File internal = new File(getFilesDir(), "recordings");
        internal.mkdirs();
        return internal;
    }

    private void selectMaqam(int idx) {
        maqamButtons[selectedMaqamIdx].setBackgroundColor(COLOR_CARD);
        maqamButtons[selectedMaqamIdx].setTextColor(COLOR_TEXT);
        selectedMaqamIdx = idx;
        Maqam m = maqams.get(idx);
        maqamButtons[idx].setBackgroundColor(m.accentColor);
        maqamButtons[idx].setTextColor(Color.BLACK);
        engine.setMaqam(m);
    }

    private void selectRoot(int idx) {
        rootButtons[selectedRootIdx].setBackgroundColor(COLOR_DIVIDER);
        rootButtons[selectedRootIdx].setTextColor(COLOR_TEXT);
        selectedRootIdx = idx;
        rootButtons[idx].setBackgroundColor(COLOR_GOLD);
        rootButtons[idx].setTextColor(Color.BLACK);
        engine.setRootHz(MaqamLibrary.ROOT_FREQUENCIES[idx]);
    }

    // ─── التسجيلات المحفوظة ──────────────────────────────────────────

    private void refreshRecordingsList() {
        if (recordingsContainer == null) return;
        recordingsContainer.removeAllViews();

        List<File> files = new ArrayList<File>();
        File[] dirs = { getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                        new File(getFilesDir(), "recordings") };
        for (File d : dirs) {
            if (d == null || !d.exists()) continue;
            File[] arr = d.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) { return name.endsWith(".wav"); }
            });
            if (arr != null) files.addAll(Arrays.asList(arr));
        }
        Collections.sort(files, new Comparator<File>() {
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        if (files.isEmpty()) {
            recordingsContainer.addView(label("لا توجد تسجيلات بعد", 14, COLOR_SUBTEXT));
            return;
        }
        for (File f : files) {
            recordingsContainer.addView(buildRecordingRow(f));
        }
    }

    private View buildRecordingRow(final File file) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(COLOR_CARD);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setLayoutParams(mpWrap(0, dp(4)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        String name = file.getName().replace("AutoTune_", "").replace(".wav", "");
        String display = name;
        try {
            Date d = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(name);
            display = new SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.US).format(d);
        } catch (Exception ignored) {}
        info.addView(label(display, 14, COLOR_TEXT));
        info.addView(label(formatSize(file.length()) + "  •  WAV", 12, COLOR_SUBTEXT));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        final TextView btnPlay = new TextView(this);
        btnPlay.setText("▶");
        btnPlay.setContentDescription("تشغيل التسجيل");
        btnPlay.setTextSize(20);
        btnPlay.setTextColor(COLOR_GREEN);
        btnPlay.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnPlay.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { playFile(file, btnPlay); }
        });
        row.addView(btnPlay);

        TextView btnShare = new TextView(this);
        btnShare.setText("↑");
        btnShare.setContentDescription("مشاركة التسجيل");
        btnShare.setTextSize(20);
        btnShare.setTextColor(COLOR_GOLD);
        btnShare.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnShare.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { shareFile(file); }
        });
        row.addView(btnShare);

        TextView btnDel = new TextView(this);
        btnDel.setText("✕");
        btnDel.setContentDescription("حذف التسجيل");
        btnDel.setTextSize(18);
        btnDel.setTextColor(COLOR_RED);
        btnDel.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnDel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { file.delete(); refreshRecordingsList(); }
        });
        row.addView(btnDel);

        return row;
    }

    private View buildCrashRow(final File crash) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(0xFF2A1020);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        String name = crash.getName().replace("crash_", "").replace(".txt", "")
                                     .replace("startup_error", "خطأ بدء")
                                     .replace("engine_error", "خطأ محرك");
        String display = name;
        try {
            Date d = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(name);
            display = new SimpleDateFormat("dd/MM/yyyy  HH:mm:ss", Locale.US).format(d);
        } catch (Exception ignored) {}

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(label("⚠ عطل: " + display, 13, COLOR_RED));
        info.addView(label(formatSize(crash.length()), 11, COLOR_SUBTEXT));
        row.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView btnCopy = new TextView(this);
        btnCopy.setText("نسخ");
        btnCopy.setContentDescription("نسخ تقرير العطل");
        btnCopy.setTextSize(14);
        btnCopy.setTextColor(COLOR_GOLD);
        btnCopy.setPadding(dp(10), dp(8), dp(10), dp(8));
        btnCopy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(crash));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) { sb.append(line).append('\n'); }
                    br.close();
                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("crash", sb.toString()));
                    Toast.makeText(MainActivity.this, "✓ تم النسخ — الصقه هنا في المحادثة",
                            Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذّر قراءة الملف",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        row.addView(btnCopy);

        TextView btnDel = new TextView(this);
        btnDel.setText("✕");
        btnDel.setContentDescription("حذف تقرير العطل");
        btnDel.setTextSize(18);
        btnDel.setTextColor(COLOR_RED);
        btnDel.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnDel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                crash.delete();
                if (row.getParent() instanceof ViewGroup)
                    ((ViewGroup) row.getParent()).removeView(row);
            }
        });
        row.addView(btnDel);

        return row;
    }

    // ─── تشغيل ومشاركة ───────────────────────────────────────────────

    private MediaPlayer currentPlayer;
    private TextView    currentPlayBtn;

    private void playFile(final File file, final TextView btn) {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.release();
            currentPlayer = null;
            if (currentPlayBtn != null) {
                currentPlayBtn.setText("▶");
                currentPlayBtn.setContentDescription("تشغيل التسجيل");
            }
            boolean wasSameBtn = (currentPlayBtn == btn);
            currentPlayBtn = null;
            if (wasSameBtn) return;
        }
        try {
            currentPlayer = new MediaPlayer();
            currentPlayer.setDataSource(file.getAbsolutePath());
            currentPlayer.prepare();
            currentPlayer.start();
            btn.setText("■");
            btn.setContentDescription("إيقاف التسجيل");
            currentPlayBtn = btn;
            currentPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    mp.release();
                    currentPlayer = null;
                    currentPlayBtn = null;
                    uiHandler.post(new Runnable() {
                        public void run() {
                            btn.setText("▶");
                            btn.setContentDescription("تشغيل التسجيل");
                        }
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "تعذّر تشغيل الملف", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file) {
        android.net.Uri uri = AudioFileProvider.uriForFile(file);
        android.content.Intent intent = new android.content.Intent(
                android.content.Intent.ACTION_SEND);
        intent.setType("audio/wav");
        intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(android.content.Intent.createChooser(intent, "مشاركة التسجيل"));
    }

    // ─── استجابة المحرك ──────────────────────────────────────────────

    @Override
    public void onPitchDetected(double detectedHz, double targetHz,
                                double deviationCents, String noteName) {
        tvDetectedNote.setText(noteName.isEmpty() ? "—" : noteName);
        tvDetectedHz.setText(String.format("%.1f هرتز", detectedHz));
        tvTargetNote.setText(noteName.isEmpty() ? "—" : noteName + " ✓");
        double abs = Math.abs(deviationCents);
        int col = abs < 15 ? COLOR_GREEN : (abs < 35 ? 0xFFFFD600 : COLOR_RED);
        tvDeviationCents.setText(String.format("%+.1f سنت", deviationCents));
        tvDeviationCents.setTextColor(col);
        tvTargetNote.setTextColor(col);
        pitchMeter.setDeviation((float) deviationCents);
    }

    @Override public void onSilence() { resetDisplay(); }

    @Override
    public void onEngineError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRecordingSaved(File file, long durationMs) {
        long secs = durationMs / 1000;
        Toast.makeText(this, "✓ تم الحفظ: " + file.getName()
                + "\nالمدة: " + (secs / 60) + ":" + String.format("%02d", secs % 60),
                Toast.LENGTH_LONG).show();
        refreshRecordingsList();
    }

    private void resetDisplay() {
        tvDetectedNote.setText("—");
        tvDetectedHz.setText("— هرتز");
        tvTargetNote.setText("—");
        tvTargetNote.setTextColor(COLOR_GREEN);
        tvDeviationCents.setText("± 0 سنت");
        tvDeviationCents.setTextColor(COLOR_SUBTEXT);
        pitchMeter.setDeviation(0);
    }

    // ─── مقياس الانحراف ──────────────────────────────────────────────

    private class PitchMeterView extends View {
        private final Paint paintBg     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintBar    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float deviation = 0;

        PitchMeterView(android.content.Context ctx) {
            super(ctx);
            paintBg.setColor(COLOR_DIVIDER);
            paintCenter.setColor(COLOR_GOLD);
            paintCenter.setStrokeWidth(dp(2));
        }

        void setDeviation(float dev) {
            float c = Math.max(-100f, Math.min(100f, dev));
            if (Math.abs(c - deviation) > 0.5f) { deviation = c; invalidate(); }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), mid = w / 2f;
            canvas.drawRoundRect(new RectF(0, 0, w, h), dp(6), dp(6), paintBg);
            float end = mid + (deviation / 100f) * (w / 2f - dp(4));
            int col = Math.abs(deviation) < 15 ? COLOR_GREEN
                    : (Math.abs(deviation) < 35 ? 0xFFFFD600 : COLOR_RED);
            paintBar.setShader(new LinearGradient(mid, 0, end, 0,
                    new int[]{col & 0x80FFFFFF, col}, null, Shader.TileMode.CLAMP));
            RectF bar = end > mid ? new RectF(mid, dp(2), end, h - dp(2))
                                  : new RectF(end, dp(2), mid, h - dp(2));
            canvas.drawRoundRect(bar, dp(3), dp(3), paintBar);
            canvas.drawLine(mid, dp(2), mid, h - dp(2), paintCenter);
        }
    }

    // ─── مساعدات ─────────────────────────────────────────────────────

    interface SeekAction { void onValue(int value); }

    private SeekBar.OnSeekBarChangeListener simpleSeek(final SeekAction onChange) {
        return new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { onChange.onValue(p); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setBackgroundColor(COLOR_CARD);
        return v;
    }

    private LinearLayout col() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private TextView label(String text, float sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        return tv;
    }

    private TextView sectionTitle(String text) {
        TextView tv = label(text, 16, COLOR_GOLD);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(20), 0, dp(8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView maqamChip(String name, boolean sel, int accent) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(12), dp(8), dp(12));
        tv.setBackgroundColor(sel ? accent : COLOR_CARD);
        tv.setTextColor(sel ? Color.BLACK : COLOR_TEXT);
        return tv;
    }

    private TextView rootChip(String name, boolean sel) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setBackgroundColor(sel ? COLOR_GOLD : COLOR_DIVIDER);
        tv.setTextColor(sel ? Color.BLACK : COLOR_TEXT);
        return tv;
    }

    private LinearLayout.LayoutParams mpWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams mpWrap(int h, int top) {
        LinearLayout.LayoutParams lp = mpWrap();
        lp.setMargins(h, top, h, 0);
        return lp;
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024f);
        return String.format("%.1f MB", bytes / (1024f * 1024f));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
