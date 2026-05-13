package com.accessibletap.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

public class TapAccessibilityService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private int completedCycles = 0;
    private BroadcastReceiver receiver;

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
            int maxCycles = prefs.getInt(MainActivity.KEY_MAX, 20);
            if (completedCycles >= maxCycles) {
                stopRunning("تم الإيقاف تلقائيًا بعد الوصول إلى العدد المحدد");
                return;
            }
            performTapThenMaybeScroll();
            completedCycles++;
            int interval = prefs.getInt(MainActivity.KEY_INTERVAL, 2500);
            handler.postDelayed(this, interval);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        registerCommandReceiver();
        toast("خدمة مساعد النقر جاهزة. زر رفع الصوت يبدأ أو يوقف. زر خفض الصوت يوقف فورًا.");
    }

    private void registerCommandReceiver() {
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) return;
                if (MainActivity.ACTION_START.equals(intent.getAction())) {
                    startRunning();
                } else if (MainActivity.ACTION_STOP.equals(intent.getAction())) {
                    stopRunning("تم الإيقاف");
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(MainActivity.ACTION_START);
        filter.addAction(MainActivity.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // لا نحتاج إلى قراءة محتوى الشاشة. الأداة تنفذ إيماءات فقط حسب إعدادات المستخدم.
    }

    @Override
    public void onInterrupt() {
        stopRunning("تمت مقاطعة الخدمة");
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP) {
            if (running) {
                stopRunning("تم الإيقاف بزر رفع الصوت");
            } else {
                startRunning();
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            stopRunning("تم الإيقاف بزر خفض الصوت");
            return true;
        }
        return false;
    }

    private void startRunning() {
        completedCycles = 0;
        running = true;
        handler.removeCallbacks(loop);
        toast("بدأ التشغيل");
        handler.post(loop);
    }

    private void stopRunning(String message) {
        running = false;
        handler.removeCallbacks(loop);
        toast(message);
    }

    private void performTapThenMaybeScroll() {
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int xPercent = prefs.getInt(MainActivity.KEY_X, 50);
        int yPercent = prefs.getInt(MainActivity.KEY_Y, 82);
        boolean scrollEnabled = prefs.getBoolean(MainActivity.KEY_SCROLL, true);

        int[] size = getScreenSize();
        int x = Math.round(size[0] * (xPercent / 100f));
        int y = Math.round(size[1] * (yPercent / 100f));

        Path tapPath = new Path();
        tapPath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(tapPath, 0, 80));
        dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                if (scrollEnabled && running) {
                    handler.postDelayed(() -> performScroll(size), 300);
                }
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                // لا نوقف الخدمة مباشرة؛ قد يكون الإلغاء بسبب انتقال الصفحة أو تأخر النظام.
            }
        }, handler);
    }

    private void performScroll(int[] size) {
        int w = size[0];
        int h = size[1];
        int x = w / 2;
        int startY = Math.round(h * 0.78f);
        int endY = Math.round(h * 0.35f);

        Path swipePath = new Path();
        swipePath.moveTo(x, startY);
        swipePath.lineTo(x, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 450));
        dispatchGesture(builder.build(), null, handler);
    }

    private int[] getScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                metrics = getResources().getDisplayMetrics();
            } else {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            }
        } else {
            metrics = getResources().getDisplayMetrics();
        }
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiver = null;
        }
        super.onDestroy();
    }
}
