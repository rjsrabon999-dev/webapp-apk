package com.webapp.myapppxarx;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Bridge {

    private static final boolean CONFIRM = true;

    private final Activity act;
    private final WebView web;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SpeechRecognizer sr;

    public Bridge(Activity a, WebView w) {
        act = a;
        web = w;
        tts = new TextToSpeech(a, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = (status == TextToSpeech.SUCCESS);
            }
        });
    }

    private boolean has(String p) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED;
    }

    private void ui(Runnable r) {
        act.runOnUiThread(r);
    }

    private void js(final String code) {
        ui(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript(code, null);
            }
        });
    }

    private void start(final Intent i) {
        ui(new Runnable() {
            @Override
            public void run() {
                try {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    act.startActivity(i);
                } catch (Exception ignored) { }
            }
        });
    }

    private void tn(String m) {
        Toast.makeText(act, m, Toast.LENGTH_SHORT).show();
    }

    private void ask(final String msg, final Runnable yes) {
        if (!CONFIRM) {
            ui(yes);
            return;
        }
        ui(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(act)
                        .setTitle("AI অনুমতি চাইছে")
                        .setMessage(msg)
                        .setPositiveButton("হ্যাঁ", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                yes.run();
                            }
                        })
                        .setNegativeButton("না", null)
                        .show();
            }
        });
    }

    @JavascriptInterface
    public String status() {
        try {
            JSONObject o = new JSONObject();
            o.put("call", has("android.permission.CALL_PHONE"));
            o.put("sms", has("android.permission.SEND_SMS"));
            o.put("contacts", has("android.permission.READ_CONTACTS"));
            o.put("callLog", has("android.permission.READ_CALL_LOG"));
            o.put("mic", has("android.permission.RECORD_AUDIO"));
            o.put("overlay", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(act));
            o.put("accessibility", ControlService.inst != null);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public void toast(final String m) {
        ui(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(act, m, Toast.LENGTH_LONG).show();
            }
        });
    }

    @JavascriptInterface
    public String call(final String number) {
        final boolean direct = has("android.permission.CALL_PHONE");
        ask("কল করবে: " + number, new Runnable() {
            @Override
            public void run() {
                try {
                    Uri u = Uri.parse("tel:" + Uri.encode(number));
                    act.startActivity(new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, u));
                } catch (Exception ignored) { }
            }
        });
        return direct ? "calling" : "dialer_opened";
    }

    @JavascriptInterface
    public String dial(String number) {
        start(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))));
        return "ok";
    }

    @JavascriptInterface
    public String sms(final String number, final String text) {
        if (!has("android.permission.SEND_SMS")) return "no_permission";
        ask("এসএমএস পাঠাবে " + number + " : " + text, new Runnable() {
            @Override
            public void run() {
                try {
                    SmsManager sm = SmsManager.getDefault();
                    ArrayList<String> parts = sm.divideMessage(text);
                    sm.sendMultipartTextMessage(number, null, parts, null, null);
                    tn("এসএমএস পাঠানো হয়েছে");
                } catch (Exception e) {
                    tn("এসএমএস পাঠানো যায়নি");
                }
            }
        });
        return "ok";
    }

    @JavascriptInterface
    public String findContact(String name) {
        if (!has("android.permission.READ_CONTACTS")) return "no_permission";
        JSONArray arr = new JSONArray();
        Cursor c = null;
        try {
            c = act.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            while (c != null && c.moveToNext() && arr.length() < 5) {
                JSONObject o = new JSONObject();
                o.put("name", c.getString(0));
                o.put("number", c.getString(1));
                arr.put(o);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String callContact(String name) {
        String r = findContact(name);
        try {
            JSONArray a = new JSONArray(r);
            if (a.length() == 0) return "not_found";
            return call(a.getJSONObject(0).getString("number"));
        } catch (Exception e) {
            return r;
        }
    }

    @JavascriptInterface
    public String openApp(String q) {
        try {
            PackageManager pm = act.getPackageManager();
            Intent li = pm.getLaunchIntentForPackage(q);
            if (li == null) {
                Intent main = new Intent(Intent.ACTION_MAIN);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
                String ql = q.toLowerCase();
                for (ResolveInfo ri : list) {
                    String label = String.valueOf(ri.loadLabel(pm)).toLowerCase();
                    if (label.length() > 0 && (label.contains(ql) || ql.contains(label))) {
                        li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName);
                        break;
                    }
                }
            }
            if (li == null) return "not_found";
            start(li);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String listApps() {
        JSONArray arr = new JSONArray();
        try {
            PackageManager pm = act.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                if (arr.length() >= 300) break;
                JSONObject o = new JSONObject();
                o.put("name", String.valueOf(ri.loadLabel(pm)));
                o.put("pkg", ri.activityInfo.packageName);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        return arr.toString();
    }

    @JavascriptInterface
    public String openUrl(String url) {
        start(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        return "ok";
    }

    @JavascriptInterface
    public String flashlight(boolean on) {
        try {
            if (Build.VERSION.SDK_INT < 23) return "unsupported";
            CameraManager cm = (CameraManager) act.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = cm.getCameraIdList();
            if (ids.length == 0) return "no_camera";
            cm.setTorchMode(ids[0], on);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String setVolume(int percent) {
        try {
            AudioManager am = (AudioManager) act.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int p = Math.max(0, Math.min(100, percent));
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max * p / 100, AudioManager.FLAG_SHOW_UI);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(Math.max(1, Math.min(ms, 5000)));
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String speak(String text) {
        if (!ttsReady) return "tts_not_ready";
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ai");
        return "ok";
    }

    @JavascriptInterface
    public String setSpeechLang(String tag) {
        try {
            tts.setLanguage(Locale.forLanguageTag(tag));
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public void listen(final String lang) {
        ui(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!SpeechRecognizer.isRecognitionAvailable(act)) {
                        js("window.onNativeSpeechError && window.onNativeSpeechError('unavailable')");
                        return;
                    }
                    if (!has("android.permission.RECORD_AUDIO")) {
                        js("window.onNativeSpeechError && window.onNativeSpeechError('no_permission')");
                        return;
                    }
                    if (sr != null) sr.destroy();
                    sr = SpeechRecognizer.createSpeechRecognizer(act);
                    sr.setRecognitionListener(new RecognitionListener() {
                        @Override public void onReadyForSpeech(Bundle params) { }
                        @Override public void onBeginningOfSpeech() { }
                        @Override public void onRmsChanged(float rmsdB) { }
                        @Override public void onBufferReceived(byte[] buffer) { }
                        @Override public void onEndOfSpeech() { }
                        @Override public void onPartialResults(Bundle partialResults) { }
                        @Override public void onEvent(int eventType, Bundle params) { }
                        @Override
                        public void onError(int error) {
                            js("window.onNativeSpeechError && window.onNativeSpeechError('error_" + error + "')");
                        }
                        @Override
                        public void onResults(Bundle results) {
                            ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (r != null && r.size() > 0) {
                                js("window.onNativeSpeech && window.onNativeSpeech(" + JSONObject.quote(r.get(0)) + ")");
                            } else {
                                js("window.onNativeSpeechError && window.onNativeSpeechError('empty')");
                            }
                        }
                    });
                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, (lang == null || lang.length() == 0) ? "bn-BD" : lang);
                    sr.startListening(i);
                } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface
    public void stopListening() {
        ui(new Runnable() {
            @Override
            public void run() {
                try {
                    if (sr != null) sr.stopListening();
                } catch (Exception ignored) { }
            }
        });
    }

    @JavascriptInterface
    public String readSms(int n) {
        if (!has("android.permission.READ_SMS")) return "no_permission";
        int lim = Math.max(1, Math.min(n, 30));
        JSONArray arr = new JSONArray();
        Cursor c = null;
        try {
            c = act.getContentResolver().query(Uri.parse("content://sms/inbox"),
                    new String[]{"address", "body", "date"}, null, null, "date DESC");
            while (c != null && c.moveToNext() && arr.length() < lim) {
                JSONObject o = new JSONObject();
                o.put("from", c.getString(0));
                o.put("text", c.getString(1));
                o.put("date", c.getLong(2));
                arr.put(o);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String callLog(int n) {
        if (!has("android.permission.READ_CALL_LOG")) return "no_permission";
        int lim = Math.max(1, Math.min(n, 30));
        JSONArray arr = new JSONArray();
        Cursor c = null;
        try {
            c = act.getContentResolver().query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE},
                    null, null, CallLog.Calls.DATE + " DESC");
            while (c != null && c.moveToNext() && arr.length() < lim) {
                JSONObject o = new JSONObject();
                o.put("number", c.getString(0));
                o.put("name", c.getString(1));
                o.put("type", c.getInt(2));
                o.put("date", c.getLong(3));
                arr.put(o);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return arr.toString();
    }

    @JavascriptInterface
    public String goHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        start(i);
        return "ok";
    }

    @JavascriptInterface
    public String screen(String what) {
        return ControlService.run(what);
    }

    @JavascriptInterface
    public String tapText(String t) {
        return ControlService.tapText(t);
    }

    @JavascriptInterface
    public String typeText(String t) {
        return ControlService.typeText(t);
    }

    @JavascriptInterface
    public String scroll(String dir) {
        return ControlService.scroll("down".equals(dir));
    }

    @JavascriptInterface
    public String readScreen() {
        return ControlService.readScreen();
    }

    @JavascriptInterface
    public String tap(double x, double y) {
        return ControlService.tap((float) x, (float) y);
    }

    @JavascriptInterface
    public String swipe(double x1, double y1, double x2, double y2, int ms) {
        return ControlService.swipe((float) x1, (float) y1, (float) x2, (float) y2, ms);
    }

    @JavascriptInterface
    public String startBackground() {
        startSvc(act);
        return "ok";
    }

    @JavascriptInterface
    public String stopBackground() {
        act.stopService(new Intent(act, KeepAliveService.class));
        return "ok";
    }

    @JavascriptInterface
    public void openOverlaySettings() {
        overlaySettings(act);
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        a11ySettings(act);
    }

    @JavascriptInterface
    public void openBatterySettings() {
        batterySettings(act);
    }

    public static void startSvc(Context c) {
        try {
            Intent s = new Intent(c, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) { }
    }

    static void overlaySettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception ignored) { }
    }

    static void a11ySettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception ignored) { }
    }

    static void batterySettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            try {
                a.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) { }
        }
    }

    public static void setupDialog(final Activity a) {
        final SharedPreferences sp = a.getSharedPreferences("ai_setup", Context.MODE_PRIVATE);
        if (sp.getBoolean("shown", false)) return;
        sp.edit().putBoolean("shown", true).apply();
        final String[] items = new String[]{
                "ফ্লোটিং বাবল (অন্য অ্যাপের ওপর দেখানো)",
                "স্ক্রিন কন্ট্রোল (Accessibility চালু করুন)",
                "ব্যাটারি সীমা তুলুন (ব্যাকগ্রাউন্ডে চালু রাখতে)",
                "পরে করব"};
        new AlertDialog.Builder(a)
                .setTitle("AI কন্ট্রোলের বিশেষ অনুমতি")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) overlaySettings(a);
                        else if (w == 1) a11ySettings(a);
                        else if (w == 2) batterySettings(a);
                    }
                })
                .show();
    }
}
