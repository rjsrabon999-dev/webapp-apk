package com.webapp.webappxkeli;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class ControlService extends AccessibilityService {

    public static ControlService inst;

    @Override
    protected void onServiceConnected() {
        inst = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        inst = null;
        return super.onUnbind(intent);
    }

    public static String run(String what) {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        int a;
        if ("back".equals(what)) a = GLOBAL_ACTION_BACK;
        else if ("home".equals(what)) a = GLOBAL_ACTION_HOME;
        else if ("recents".equals(what)) a = GLOBAL_ACTION_RECENTS;
        else if ("notifications".equals(what)) a = GLOBAL_ACTION_NOTIFICATIONS;
        else if ("quick".equals(what)) a = GLOBAL_ACTION_QUICK_SETTINGS;
        else if ("lock".equals(what)) a = GLOBAL_ACTION_LOCK_SCREEN;
        else if ("screenshot".equals(what)) a = GLOBAL_ACTION_TAKE_SCREENSHOT;
        else return "unknown_action";
        return s.performGlobalAction(a) ? "ok" : "failed";
    }

    public static String tapText(String t) {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "no_window";
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(t);
        if (list == null) return "not_found";
        for (AccessibilityNodeInfo n : list) {
            AccessibilityNodeInfo x = n;
            while (x != null && !x.isClickable()) x = x.getParent();
            if (x != null && x.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "ok";
        }
        return "not_found";
    }

    public static String typeText(String t) {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "no_window";
        AccessibilityNodeInfo f = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (f == null) return "no_input_focused";
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, t);
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b) ? "ok" : "failed";
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 30) return null;
        if (n.isScrollable()) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = findScrollable(n.getChild(i), depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    public static String scroll(boolean down) {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "no_window";
        AccessibilityNodeInfo sc = findScrollable(root, 0);
        if (sc == null) return "no_scrollable";
        int act = down ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD;
        return sc.performAction(act) ? "ok" : "failed";
    }

    private static void collect(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || sb.length() > 4000 || depth > 30) return;
        CharSequence t = n.getText();
        if (t != null && t.length() > 0) sb.append(t).append(" | ");
        CharSequence d = n.getContentDescription();
        if (d != null && d.length() > 0) sb.append(d).append(" | ");
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb, depth + 1);
    }

    public static String readScreen() {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "no_window";
        StringBuilder sb = new StringBuilder();
        collect(root, sb, 0);
        return sb.toString();
    }

    public static String tap(float x, float y) {
        return gesture(x, y, x, y, 60);
    }

    public static String swipe(float x1, float y1, float x2, float y2, long ms) {
        return gesture(x1, y1, x2, y2, ms);
    }

    private static String gesture(float x1, float y1, float x2, float y2, long ms) {
        ControlService s = inst;
        if (s == null) return "accessibility_off";
        if (Build.VERSION.SDK_INT < 24) return "unsupported";
        Path p = new Path();
        p.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) p.lineTo(x2, y2);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(new GestureDescription.StrokeDescription(p, 0, Math.max(ms, 1)));
        return s.dispatchGesture(b.build(), null, null) ? "ok" : "failed";
    }
}
