package com.webapp.webappxkeli;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.widget.EditText;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final boolean FULLSCREEN = true;
    private static final int FILE_CHOOSER = 1001;
    private static final String[] PH = new String[]{};
    private static final String[] HINT = new String[]{};
    private static final String[] WANT = new String[]{"android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.READ_MEDIA_AUDIO", "android.permission.POST_NOTIFICATIONS"};

    private boolean started = false;
    private PermissionRequest pendingReq;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @SuppressLint({"SetJavaScriptEnabled"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FULLSCREEN) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        try {
            getWindow().setStatusBarColor(Color.parseColor("#0f172a"));
        } catch (Exception ignored) { }

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setGeolocationEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith("http") || u.startsWith("file") || u.startsWith("about")
                        || u.startsWith("data") || u.startsWith("blob") || u.startsWith("javascript")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
                } catch (Exception ignored) { }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest req) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        java.util.ArrayList<String> need = new java.util.ArrayList<String>();
                        for (String r : req.getResources()) {
                            String p = null;
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) p = "android.permission.CAMERA";
                            else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) p = "android.permission.RECORD_AUDIO";
                            if (p != null && Build.VERSION.SDK_INT >= 23
                                    && MainActivity.this.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                                need.add(p);
                            }
                        }
                        if (need.isEmpty()) {
                            req.grant(req.getResources());
                        } else {
                            pendingReq = req;
                            MainActivity.this.requestPermissions(need.toArray(new String[0]), 2002);
                        }
                    }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, true, false);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    if (url.startsWith("http")) {
                        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                        r.setMimeType(mime);
                        r.addRequestHeader("User-Agent", ua);
                        String ck = android.webkit.CookieManager.getInstance().getCookie(url);
                        if (ck != null) r.addRequestHeader("Cookie", ck);
                        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, cd, mime));
                        ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    }
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) { }
                }
            }
        });

        web.addJavascriptInterface(new Bridge(this, web), "Android");
        web.addJavascriptInterface(new StoreBridge(this, web), "Store");
        startFlow();
    }

    private void startFlow() {
        if (Build.VERSION.SDK_INT >= 23 && WANT.length > 0) {
            java.util.ArrayList<String> need = new java.util.ArrayList<String>();
            for (String p : WANT) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
            }
            if (!need.isEmpty()) {
                requestPermissions(need.toArray(new String[0]), 2001);
                return;
            }
        }
        loadApp();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 2001) {
            loadApp();
        } else if (code == 2002 && pendingReq != null) {
            boolean ok = res.length > 0;
            for (int r : res) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) pendingReq.grant(pendingReq.getResources()); else pendingReq.deny();
            pendingReq = null;
        }
    }

    private void loadApp() {
        if (started) return;
        started = true;
        Bridge.startSvc(this);
        new android.os.Handler().postDelayed(new Runnable() { public void run() { Bridge.setupDialog(MainActivity.this); } }, 2500);
        if (PH.length == 0) {
            web.loadUrl("file:///android_asset/index.html");
        } else {
            askKey(0);
        }
    }

    private void askKey(final int i) {
        final SharedPreferences sp = getSharedPreferences("app_keys", MODE_PRIVATE);
        if (i >= PH.length) { render(); return; }
        if (sp.contains(PH[i])) { askKey(i + 1); return; }
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint(HINT[i]);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("API Key দিন (" + (i + 1) + "/" + PH.length + ")");
        b.setMessage("এই অ্যাপ চালাতে কী লাগবে। কী শুধু আপনার ফোনে সেভ থাকবে।");
        b.setView(et);
        b.setCancelable(false);
        b.setPositiveButton("সেভ", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                String v = et.getText().toString().trim();
                if (v.length() > 0) sp.edit().putString(PH[i], v).apply();
                askKey(i + 1);
            }
        });
        b.setNegativeButton("বাদ দিন", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                askKey(i + 1);
            }
        });
        b.show();
    }

    private void render() {
        SharedPreferences sp = getSharedPreferences("app_keys", MODE_PRIVATE);
        String html = readAsset();
        for (int i = 0; i < PH.length; i++) {
            String v = sp.getString(PH[i], "");
            StringBuilder sb = new StringBuilder();
            for (char c : v.toCharArray()) {
                if (c == 92 || c == 34 || c == 39 || c == 96 || c == 60 || c == 62 || c < 32) continue;
                sb.append(c);
            }
            html = html.replace(PH[i], sb.toString());
        }
        web.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private String readAsset() {
        try {
            InputStream in = getAssets().open("index.html");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (FULLSCREEN && hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
