package com.autotune.arabic;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.autotune.arabic.engine.AudioEngine;
import com.autotune.arabic.maqam.Maqam;
import com.autotune.arabic.maqam.MaqamLibrary;

import java.util.List;

/**
 * الشاشة الرئيسية لتطبيق أوتوتيون عربي.
 *
 * تتضمن:
 *  - عرض الطبقة المكتشَفة والهدف والانحراف بالسنت
 *  - اختيار المقام (13 مقاماً عربياً)
 *  - اختيار نغمة الجذر (12 نغمة كروماتية)
 *  - التحكم في الحساسية وسرعة التصحيح
 *  - وضع المرور المباشر (Bypass)
 */
public class MainActivity extends Activity implements AudioEngine.Listener {

    // ─── ثوابت الألوان ──────────────────────────────────────────────
    private static final int COLOR_BG       = 0xFF0D0D14;  // خلفية داكنة
    private static final int COLOR_CARD     = 0xFF1A1A2E;  // بطاقات
    private static final int COLOR_GOLD     = 0xFFD4A017;  // ذهبي عربي
    private static final int COLOR_GREEN    = 0xFF00E676;  // مضبوط
    private static final int COLOR_RED      = 0xFFFF1744;  // بعيد
    private static final int COLOR_TEXT     = 0xFFE8E8F0;  // نص رئيسي
    private static final int COLOR_SUBTEXT  = 0xFF8888AA;  // نص فرعي
    private static final int COLOR_DIVIDER  = 0xFF2A2A45;  // فاصل

    private static final int PERM_CODE = 101;

    // ─── المحرك والبيانات ────────────────────────────────────────────
    private AudioEngine engine;
    private List<Maqam> maqams;
    private int selectedMaqamIdx = 0;
    private int selectedRootIdx  = 2;  // ري افتراضياً

    // ─── عناصر الواجهة ──────────────────────────────────────────────
    private PitchMeterView  pitchMeter;
    private TextView        tvDetectedNote;
    private TextView        tvDetectedHz;
    private TextView        tvTargetNote;
    private TextView        tvDeviationCents;
    private TextView        tvStatus;
    private TextView[]      maqamButtons;
    private TextView[]      rootButtons;
    private TextView        btnStart;
    private CheckBox        chkBypass;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    // ─── دورة الحياة ────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setBackgroundColor(COLOR_BG);

        maqams = MaqamLibrary.buildAll();
        engine = new AudioEngine();
        engine.setListener(this);
        engine.setMaqam(maqams.get(selectedMaqamIdx));
        engine.setRootHz(MaqamLibrary.ROOT_FREQUENCIES[selectedRootIdx]);

        buildUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        engine.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == PERM_CODE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startEngine();
        } else {
            Toast.makeText(this, "يجب منح إذن الميكروفون لاستخدام التطبيق", Toast.LENGTH_LONG).show();
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

        // عنوان التطبيق
        page.addView(buildHeader());

        // شاشة عرض الطبقة الصوتية
        page.addView(buildPitchDisplay(), mpWrap(0, dp(20)));

        // اختيار المقام
        page.addView(sectionTitle("اختر المقام"));
        page.addView(buildMaqamGrid(), mpWrap(0, dp(8)));

        // اختيار الجذر
        page.addView(sectionTitle("نغمة الجذر"));
        page.addView(buildRootRow(), mpWrap(0, dp(8)));

        // التحكمات
        page.addView(sectionTitle("إعدادات التصحيح"));
        page.addView(buildControls(), mpWrap(0, dp(8)));

        // وضع التجاوز
        chkBypass = new CheckBox(this);
        chkBypass.setText("وضع التمرير المباشر (Bypass) — سماع الصوت بدون تصحيح");
        chkBypass.setTextColor(COLOR_SUBTEXT);
        chkBypass.setTextSize(15);
        chkBypass.setOnCheckedChangeListener((b, checked) -> engine.setBypass(checked));
        page.addView(chkBypass, mpWrap(0, dp(12)));

        // زر التشغيل/الإيقاف
        page.addView(buildStartButton(), mpWrap(dp(8), dp(4)));

        // ملاحظة سفلية
        TextView note = label("التطبيق للاستخدام الشخصي والفني فقط.", 13, COLOR_SUBTEXT);
        note.setGravity(Gravity.CENTER);
        page.addView(note, mpWrap(0, dp(16)));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.VERTICAL);
        h.setGravity(Gravity.CENTER);
        h.setPadding(0, 0, 0, dp(8));

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

        // عرض الطبقة المكتشَفة
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);

        LinearLayout col1 = col();
        TextView lbl1 = label("صوتك", 12, COLOR_SUBTEXT);
        lbl1.setGravity(Gravity.CENTER);
        tvDetectedNote = label("—", 42, COLOR_TEXT);
        tvDetectedNote.setGravity(Gravity.CENTER);
        tvDetectedNote.setTypeface(Typeface.DEFAULT_BOLD);
        tvDetectedHz = label("— هرتز", 13, COLOR_SUBTEXT);
        tvDetectedHz.setGravity(Gravity.CENTER);
        col1.addView(lbl1, mpWrap());
        col1.addView(tvDetectedNote, mpWrap());
        col1.addView(tvDetectedHz, mpWrap());

        TextView arrow = label("→", 28, COLOR_GOLD);
        arrow.setPadding(dp(16), 0, dp(16), 0);

        LinearLayout col2 = col();
        TextView lbl2 = label("الهدف", 12, COLOR_SUBTEXT);
        lbl2.setGravity(Gravity.CENTER);
        tvTargetNote = label("—", 42, COLOR_GREEN);
        tvTargetNote.setGravity(Gravity.CENTER);
        tvTargetNote.setTypeface(Typeface.DEFAULT_BOLD);
        tvDeviationCents = label("± 0 سنت", 13, COLOR_SUBTEXT);
        tvDeviationCents.setGravity(Gravity.CENTER);
        col2.addView(lbl2, mpWrap());
        col2.addView(tvTargetNote, mpWrap());
        col2.addView(tvDeviationCents, mpWrap());

        row1.addView(col1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row1.addView(arrow);
        row1.addView(col2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(row1, mpWrap(0, dp(16)));

        // مقياس الانحراف المرئي
        pitchMeter = new PitchMeterView(this);
        card.addView(pitchMeter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        TextView meterLabel = label("← أخفض   ضبط تام   أعلى →", 12, COLOR_SUBTEXT);
        meterLabel.setGravity(Gravity.CENTER);
        card.addView(meterLabel, mpWrap(0, dp(6)));

        return card;
    }

    private View buildMaqamGrid() {
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        maqamButtons = new TextView[maqams.size()];

        int cols = 3;
        for (int row = 0; row * cols < maqams.size(); row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < cols && row * cols + col < maqams.size(); col++) {
                int idx = row * cols + col;
                Maqam m = maqams.get(idx);
                TextView btn = maqamChip(m.nameAr, idx == selectedMaqamIdx, m.accentColor);
                final int fi = idx;
                btn.setOnClickListener(v -> selectMaqam(fi));
                maqamButtons[idx] = btn;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                rowLayout.addView(btn, lp);
            }
            outer.addView(rowLayout, mpWrap());
        }

        return outer;
    }

    private View buildRootRow() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        scroll.addView(row);

        rootButtons = new TextView[MaqamLibrary.ROOT_NAMES_AR.length];
        for (int i = 0; i < MaqamLibrary.ROOT_NAMES_AR.length; i++) {
            TextView btn = rootChip(MaqamLibrary.ROOT_NAMES_AR[i], i == selectedRootIdx);
            final int fi = i;
            btn.setOnClickListener(v -> selectRoot(fi));
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

        // حساسية التصحيح
        card.addView(label("قوة التصحيح", 14, COLOR_TEXT));
        SeekBar sbSens = new SeekBar(this);
        sbSens.setMax(100);
        sbSens.setProgress(100);
        sbSens.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) { engine.setSensitivity(p / 100.0); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(sbSens, mpWrap(0, dp(8)));

        // سرعة التصحيح
        card.addView(label("سرعة التصحيح  (بطيء ◄────► سريع)", 14, COLOR_TEXT));
        SeekBar sbSpeed = new SeekBar(this);
        sbSpeed.setMax(100);
        sbSpeed.setProgress(30);
        sbSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                engine.setCorrectionSpeed(0.01 + (p / 100.0) * 0.49);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        card.addView(sbSpeed, mpWrap(0, dp(4)));

        return card;
    }

    private View buildStartButton() {
        btnStart = new TextView(this);
        btnStart.setText("▶  ابدأ التسجيل");
        btnStart.setTextSize(20);
        btnStart.setTypeface(Typeface.DEFAULT_BOLD);
        btnStart.setTextColor(Color.BLACK);
        btnStart.setGravity(Gravity.CENTER);
        btnStart.setPadding(dp(24), dp(18), dp(24), dp(18));
        btnStart.setBackgroundColor(COLOR_GOLD);
        btnStart.setOnClickListener(v -> toggleEngine());
        return btnStart;
    }

    // ─── منطق التشغيل ────────────────────────────────────────────────

    private void toggleEngine() {
        if (engine.isRunning()) {
            engine.stop();
            btnStart.setText("▶  ابدأ التسجيل");
            btnStart.setBackgroundColor(COLOR_GOLD);
            tvStatus.setText("● متوقف");
            tvStatus.setTextColor(COLOR_SUBTEXT);
            resetDisplay();
        } else {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERM_CODE);
            } else {
                startEngine();
            }
        }
    }

    private void startEngine() {
        if (!engine.start()) {
            Toast.makeText(this, "تعذّر فتح الميكروفون - تأكد من الإذن", Toast.LENGTH_LONG).show();
            return;
        }
        btnStart.setText("■  إيقاف");
        btnStart.setBackgroundColor(COLOR_RED);
        tvStatus.setText("● يعمل");
        tvStatus.setTextColor(COLOR_GREEN);
    }

    private void selectMaqam(int idx) {
        if (maqamButtons == null) return;
        Maqam prev = maqams.get(selectedMaqamIdx);
        maqamButtons[selectedMaqamIdx].setBackgroundColor(COLOR_CARD);
        maqamButtons[selectedMaqamIdx].setTextColor(COLOR_TEXT);

        selectedMaqamIdx = idx;
        Maqam m = maqams.get(idx);
        maqamButtons[idx].setBackgroundColor(m.accentColor);
        maqamButtons[idx].setTextColor(Color.BLACK);

        engine.setMaqam(m);
        Toast.makeText(this, "مقام " + m.nameAr + " — " + m.description, Toast.LENGTH_SHORT).show();
    }

    private void selectRoot(int idx) {
        if (rootButtons == null) return;
        rootButtons[selectedRootIdx].setBackgroundColor(COLOR_DIVIDER);
        rootButtons[selectedRootIdx].setTextColor(COLOR_TEXT);

        selectedRootIdx = idx;
        rootButtons[idx].setBackgroundColor(COLOR_GOLD);
        rootButtons[idx].setTextColor(Color.BLACK);

        engine.setRootHz(MaqamLibrary.ROOT_FREQUENCIES[idx]);
    }

    // ─── استجابة المحرك ──────────────────────────────────────────────

    @Override
    public void onPitchDetected(double detectedHz, double targetHz, double deviationCents, String noteName) {
        // يُستدعى من الخيط الرئيسي (Handler في AudioEngine)
        tvDetectedNote.setText(noteName.isEmpty() ? "—" : noteName);
        tvDetectedHz.setText(String.format("%.1f هرتز", detectedHz));
        tvTargetNote.setText(noteName.isEmpty() ? "—" : noteName + " ✓");

        double absDev = Math.abs(deviationCents);
        String devStr = String.format("%+.1f سنت", deviationCents);
        tvDeviationCents.setText(devStr);

        // لون الانحراف: أخضر إذا كان < 15 سنت، أصفر < 30، أحمر بعد ذلك
        int devColor = absDev < 15 ? COLOR_GREEN : (absDev < 35 ? 0xFFFFD600 : COLOR_RED);
        tvDeviationCents.setTextColor(devColor);
        tvTargetNote.setTextColor(devColor);

        // تحديث مقياس الانحراف
        pitchMeter.setDeviation((float) deviationCents);
    }

    @Override
    public void onSilence() {
        resetDisplay();
    }

    @Override
    public void onEngineError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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

    // ─── مقياس الانحراف المرئي ────────────────────────────────────────

    private class PitchMeterView extends View {
        private final Paint paintBg  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintBar = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint paintCenter = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float deviation = 0;  // بالسنت (-100 إلى +100)

        PitchMeterView(android.content.Context ctx) {
            super(ctx);
            paintBg.setColor(COLOR_DIVIDER);
            paintCenter.setColor(COLOR_GOLD);
            paintCenter.setStrokeWidth(dp(2));
        }

        void setDeviation(float dev) {
            float clamped = Math.max(-100f, Math.min(100f, dev));
            if (Math.abs(clamped - deviation) > 0.5f) {
                deviation = clamped;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float midX = w / 2f;
            float radius = dp(6);

            // خلفية مستديرة الأطراف
            canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paintBg);

            // شريط الانحراف (يمتد من المنتصف)
            float barEnd = midX + (deviation / 100f) * (w / 2f - dp(4));
            int barColor = Math.abs(deviation) < 15 ? COLOR_GREEN : (Math.abs(deviation) < 35 ? 0xFFFFD600 : COLOR_RED);
            paintBar.setShader(new LinearGradient(midX, 0, barEnd, 0,
                    new int[]{barColor & 0x80FFFFFF, barColor}, null, Shader.TileMode.CLAMP));
            if (barEnd > midX) {
                canvas.drawRoundRect(new RectF(midX, dp(2), barEnd, h - dp(2)), dp(3), dp(3), paintBar);
            } else {
                canvas.drawRoundRect(new RectF(barEnd, dp(2), midX, h - dp(2)), dp(3), dp(3), paintBar);
            }

            // خط المنتصف (الضبط التام)
            canvas.drawLine(midX, dp(2), midX, h - dp(2), paintCenter);
        }
    }

    // ─── مساعدات بناء الواجهة ────────────────────────────────────────

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

    private TextView maqamChip(String name, boolean selected, int accent) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(15);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), dp(12), dp(8), dp(12));
        tv.setBackgroundColor(selected ? accent : COLOR_CARD);
        tv.setTextColor(selected ? Color.BLACK : COLOR_TEXT);
        return tv;
    }

    private TextView rootChip(String name, boolean selected) {
        TextView tv = new TextView(this);
        tv.setText(name);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setBackgroundColor(selected ? COLOR_GOLD : COLOR_DIVIDER);
        tv.setTextColor(selected ? Color.BLACK : COLOR_TEXT);
        return tv;
    }

    private LinearLayout.LayoutParams mpWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams mpWrap(int hMargin, int topMargin) {
        LinearLayout.LayoutParams lp = mpWrap();
        lp.setMargins(hMargin, topMargin, hMargin, 0);
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
