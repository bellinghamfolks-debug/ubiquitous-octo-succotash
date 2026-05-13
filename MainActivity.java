package com.accessibletap.helper;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    static final String PREFS = "tap_helper_prefs";
    static final String KEY_X = "x_percent";
    static final String KEY_Y = "y_percent";
    static final String KEY_INTERVAL = "interval_ms";
    static final String KEY_SCROLL = "scroll_enabled";
    static final String KEY_MAX = "max_cycles";

    static final String ACTION_START = "com.accessibletap.helper.START";
    static final String ACTION_STOP = "com.accessibletap.helper.STOP";

    private SharedPreferences prefs;
    private int xPercent;
    private int yPercent;
    private int intervalMs;
    private int maxCycles;
    private boolean scrollEnabled;
    private TextView summary;
    private CheckBox scrollBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadPrefs();
        buildUi();
    }

    private void loadPrefs() {
        xPercent = prefs.getInt(KEY_X, 50);
        yPercent = prefs.getInt(KEY_Y, 82);
        intervalMs = prefs.getInt(KEY_INTERVAL, 2500);
        scrollEnabled = prefs.getBoolean(KEY_SCROLL, true);
        maxCycles = prefs.getInt(KEY_MAX, 20);
    }

    private void savePrefs() {
        prefs.edit()
                .putInt(KEY_X, xPercent)
                .putInt(KEY_Y, yPercent)
                .putInt(KEY_INTERVAL, intervalMs)
                .putBoolean(KEY_SCROLL, scrollEnabled)
                .putInt(KEY_MAX, maxCycles)
                .apply();
        updateSummary();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("مساعد النقر الميسر");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setContentDescription("عنوان التطبيق مساعد النقر الميسر");
        root.addView(title, matchWrap());

        TextView intro = new TextView(this);
        intro.setText("أداة بسيطة متوافقة مع قارئ الشاشة. تضبط موقع النقر كنسبة من الشاشة، ثم تبدأ أو توقف من التطبيق أو من أزرار الصوت أثناء وجودك في المتصفح. زر رفع الصوت يبدأ أو يوقف. زر خفض الصوت يوقف فورًا.");
        intro.setTextSize(16);
        intro.setPadding(0, dp(12), 0, dp(12));
        root.addView(intro, matchWrap());

        summary = new TextView(this);
        summary.setTextSize(17);
        summary.setPadding(0, dp(8), 0, dp(12));
        root.addView(summary, matchWrap());

        Button settings = bigButton("فتح إعدادات إمكانية الوصول");
        settings.setContentDescription("فتح إعدادات إمكانية الوصول لتفعيل خدمة مساعد النقر الميسر");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings, matchWrap());

        root.addView(section("موقع النقر الأفقي من اليسار إلى اليمين"));
        root.addView(row(
                controlButton("إنقاص إكس خمسة بالمئة", () -> { xPercent = clamp(xPercent - 5, 0, 100); savePrefs(); }),
                controlButton("زيادة إكس خمسة بالمئة", () -> { xPercent = clamp(xPercent + 5, 0, 100); savePrefs(); })
        ));

        root.addView(section("موقع النقر العمودي من أعلى الشاشة إلى أسفلها"));
        root.addView(row(
                controlButton("رفع النقرة خمسة بالمئة", () -> { yPercent = clamp(yPercent - 5, 0, 100); savePrefs(); }),
                controlButton("خفض النقرة خمسة بالمئة", () -> { yPercent = clamp(yPercent + 5, 0, 100); savePrefs(); })
        ));

        root.addView(section("الفاصل الزمني بين دورة وأخرى"));
        root.addView(row(
                controlButton("أبطأ نصف ثانية", () -> { intervalMs = clamp(intervalMs + 500, 1200, 10000); savePrefs(); }),
                controlButton("أسرع نصف ثانية", () -> { intervalMs = clamp(intervalMs - 500, 1200, 10000); savePrefs(); })
        ));

        root.addView(section("عدد الدورات قبل الإيقاف التلقائي"));
        root.addView(row(
                controlButton("تقليل العدد خمس دورات", () -> { maxCycles = clamp(maxCycles - 5, 1, 200); savePrefs(); }),
                controlButton("زيادة العدد خمس دورات", () -> { maxCycles = clamp(maxCycles + 5, 1, 200); savePrefs(); })
        ));

        scrollBox = new CheckBox(this);
        scrollBox.setText("تشغيل السحب للأعلى بعد كل نقرة");
        scrollBox.setTextSize(18);
        scrollBox.setChecked(scrollEnabled);
        scrollBox.setContentDescription("تشغيل أو إيقاف السحب للأعلى بعد كل نقرة");
        scrollBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            scrollEnabled = isChecked;
            savePrefs();
        });
        root.addView(scrollBox, matchWrap());

        root.addView(section("أوامر التشغيل"));
        Button start = bigButton("ابدأ التشغيل الآن");
        start.setContentDescription("ابدأ التشغيل الآن. بعد فتح المتصفح يمكن أيضًا استخدام زر رفع الصوت للتشغيل أو الإيقاف");
        start.setOnClickListener(v -> {
            sendCommand(ACTION_START);
            toast("تم إرسال أمر البدء. إذا لم يعمل، تأكد من تفعيل خدمة إمكانية الوصول.");
        });
        root.addView(start, matchWrap());

        Button stop = bigButton("إيقاف فورًا");
        stop.setContentDescription("إيقاف النقر فورًا");
        stop.setOnClickListener(v -> {
            sendCommand(ACTION_STOP);
            toast("تم إرسال أمر الإيقاف.");
        });
        root.addView(stop, matchWrap());

        TextView safety = new TextView(this);
        safety.setText("مهم: استخدم الأداة للأغراض الشخصية والمسموح بها فقط. لا تستخدمها لتجاوز حماية المواقع أو تنفيذ نشاط مخالف لشروط أي خدمة.");
        safety.setTextSize(15);
        safety.setPadding(0, dp(16), 0, 0);
        root.addView(safety, matchWrap());

        updateSummary();
        setContentView(scroll);
    }

    private void updateSummary() {
        if (summary == null) return;
        String text = "الإعدادات الحالية:\n" +
                "إكس: " + xPercent + " بالمئة.\n" +
                "واي: " + yPercent + " بالمئة.\n" +
                "الفاصل: " + (intervalMs / 1000.0) + " ثانية.\n" +
                "السحب بعد النقر: " + (scrollEnabled ? "مفعل" : "متوقف") + ".\n" +
                "الإيقاف التلقائي بعد: " + maxCycles + " دورة.";
        summary.setText(text);
        summary.setContentDescription(text);
    }

    private void sendCommand(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(19);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(18), 0, dp(6));
        tv.setContentDescription(text);
        return tv;
    }

    private Button bigButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(18);
        b.setMinHeight(dp(56));
        b.setAllCaps(false);
        return b;
    }

    private Button controlButton(String text, Runnable action) {
        Button b = bigButton(text);
        b.setOnClickListener(v -> action.run());
        b.setContentDescription(text);
        return b;
    }

    private LinearLayout row(View left, View right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        int margin = dp(4);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(margin, margin, margin, margin);
        row.addView(left, lp);
        row.addView(right, lp);
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(5), 0, dp(5));
        return lp;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}
