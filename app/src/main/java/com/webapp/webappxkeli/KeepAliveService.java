package com.webapp.webappxkeli;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class KeepAliveService extends Service {

    private WindowManager wm;
    private View bubble;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startFg();
        showBubble();
        return START_STICKY;
    }

    private void startFg() {
        String ch = "ai_bg";
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(ch, "AI Assistant", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int fl = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, fl);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, ch)
                : new Notification.Builder(this);
        b.setContentTitle("AI অ্যাসিস্ট্যান্ট চালু আছে");
        b.setContentText("অ্যাপ খুলতে ট্যাপ করুন");
        b.setSmallIcon(android.R.drawable.ic_dialog_info);
        b.setContentIntent(pi);
        b.setOngoing(true);
        startForeground(1, b.build());
    }

    private void showBubble() {
        if (bubble != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        TextView tv = new TextView(this);
        tv.setText("AI");
        tv.setTextColor(Color.WHITE);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(16);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(Color.parseColor("#7c5cff"));
        tv.setBackground(g);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                150, 150, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20;
        lp.y = 300;
        tv.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy;
            float tx, ty;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    sx = lp.x;
                    sy = lp.y;
                    tx = e.getRawX();
                    ty = e.getRawY();
                    moved = false;
                    return true;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = e.getRawX() - tx;
                    float dy = e.getRawY() - ty;
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                    lp.x = sx + (int) dx;
                    lp.y = sy + (int) dy;
                    wm.updateViewLayout(v, lp);
                    return true;
                } else if (action == MotionEvent.ACTION_UP) {
                    if (!moved) {
                        Intent i = new Intent(KeepAliveService.this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    }
                    return true;
                }
                return false;
            }
        });
        try {
            wm.addView(tv, lp);
            bubble = tv;
        } catch (Exception ignored) { }
    }

    @Override
    public void onDestroy() {
        try {
            if (bubble != null && wm != null) wm.removeView(bubble);
        } catch (Exception ignored) { }
        bubble = null;
        super.onDestroy();
    }
}
