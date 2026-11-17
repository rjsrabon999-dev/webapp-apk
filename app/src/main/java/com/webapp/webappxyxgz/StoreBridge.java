package com.webapp.webappxyxgz;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class StoreBridge {
    private static final boolean CAN_DL = true;
    private static final boolean CAN_INSTALL = true;
    private static final boolean CAN_UNINSTALL = true;
    private static final boolean CAN_QUERY = true;

    private final Activity act;
    private final WebView web;
    private final DownloadManager dm;
    private final HashSet<Long> auto = new HashSet<Long>();

    public StoreBridge(Activity a, WebView w) {
        act = a;
        web = w;
        dm = (DownloadManager) a.getSystemService(Context.DOWNLOAD_SERVICE);
        if (CAN_DL) {
            BroadcastReceiver r = new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id < 0) return;
                    boolean ok = false;
                    Cursor cur = null;
                    try {
                        cur = dm.query(new DownloadManager.Query().setFilterById(id));
                        if (cur != null && cur.moveToFirst()) {
                            ok = cur.getInt(cur.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL;
                        }
                    } catch (Exception e) {
                    } finally {
                        if (cur != null) cur.close();
                    }
                    if (ok) {
                        js("window.onStoreDownloaded && window.onStoreDownloaded('" + id + "')");
                        boolean go;
                        synchronized (auto) { go = auto.remove(id); }
                        if (go) install(String.valueOf(id));
                    } else {
                        js("window.onStoreDownloadFailed && window.onStoreDownloadFailed('" + id + "')");
                    }
                }
            };
            IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
            else a.registerReceiver(r, f);
        }
    }

    private void js(final String code) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                try { web.evaluateJavascript(code, null); } catch (Exception ignored) { }
            }
        });
    }

    private String safeName(String n) {
        String x = n == null ? "" : n.replaceAll("[^A-Za-z0-9._-]", "_");
        if (x.length() == 0) x = "app.apk";
        if (!x.toLowerCase().endsWith(".apk")) x = x + ".apk";
        return x;
    }

    @JavascriptInterface
    public String download(String url, String fileName, boolean autoInstall) {
        if (!CAN_DL) return "no_permission";
        try {
            if (url == null || !url.startsWith("http")) return "error";
            String fn = safeName(fileName);
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType("application/vnd.android.package-archive");
            r.setTitle(fn);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalFilesDir(act, Environment.DIRECTORY_DOWNLOADS, fn);
            long id = dm.enqueue(r);
            if (autoInstall && CAN_INSTALL) { synchronized (auto) { auto.add(id); } }
            return String.valueOf(id);
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String canInstall() {
        if (Build.VERSION.SDK_INT >= 26) return String.valueOf(act.getPackageManager().canRequestPackageInstalls());
        return "true";
    }

    @JavascriptInterface
    public String openInstallSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String install(String idStr) {
        if (!CAN_INSTALL) return "no_permission";
        try {
            if (Build.VERSION.SDK_INT >= 26 && !act.getPackageManager().canRequestPackageInstalls()) {
                openInstallSettings();
                return "need_unknown_sources";
            }
            Uri u = dm.getUriForDownloadedFile(Long.parseLong(idStr));
            if (u == null) return "not_found";
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String isInstalled(String p) {
        try {
            JSONObject o = new JSONObject();
            try {
                PackageInfo pi = act.getPackageManager().getPackageInfo(p, 0);
                o.put("installed", true);
                o.put("versionName", pi.versionName == null ? "" : pi.versionName);
                o.put("versionCode", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : (long) pi.versionCode);
            } catch (PackageManager.NameNotFoundException e) {
                o.put("installed", false);
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    @JavascriptInterface
    public String open(String p) {
        try {
            Intent i = act.getPackageManager().getLaunchIntentForPackage(p);
            if (i == null) return "not_found";
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String uninstall(String p) {
        if (!CAN_UNINSTALL) return "no_permission";
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + p));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
            return "ok";
        } catch (Exception e) {
            return "error";
        }
    }

    @JavascriptInterface
    public String listInstalled() {
        if (!CAN_QUERY) return "no_permission";
        try {
            PackageManager pm = act.getPackageManager();
            List<ApplicationInfo> all = pm.getInstalledApplications(0);
            JSONArray arr = new JSONArray();
            for (ApplicationInfo ai : all) {
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                JSONObject o = new JSONObject();
                o.put("package", ai.packageName);
                o.put("name", String.valueOf(pm.getApplicationLabel(ai)));
                arr.put(o);
            }
            return arr.toString();
        } catch (Exception e) {
            return "[]";
        }
    }
}
