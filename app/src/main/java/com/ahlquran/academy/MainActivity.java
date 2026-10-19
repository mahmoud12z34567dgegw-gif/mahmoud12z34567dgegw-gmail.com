package com.ahlquran.academy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** Offline, single-device academy demonstration. No website or custom domain required. */
public class MainActivity extends Activity {
    private WebView web;
    private static final String HOME = "file:///android_asset/index.html";
    private static final String PREF = "academy_offline_data";

    public final class OfflineData {
        @JavascriptInterface public String load() {
            return getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("state", "");
        }
        @JavascriptInterface public boolean save(String value) {
            if (value == null || value.length() > 750000) return false;
            return getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("state", value).commit();
        }
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(0,72,54));
        getWindow().setNavigationBarColor(Color.rgb(248,248,242));
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true); // read only our packaged android_asset files
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new OfflineData(), "AcademyOffline");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("file:///android_asset/")) return false;
                Uri uri = request.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (Exception ex) { Toast.makeText(MainActivity.this,"تعذر فتح الرابط الخارجي",Toast.LENGTH_SHORT).show(); }
                }
                return true;
            }
        });
        setContentView(web);
        web.loadUrl(HOME);
    }
    @Override public void onBackPressed() {
        new AlertDialog.Builder(this).setTitle("أكاديمية أهل القرآن")
            .setMessage("هل تريد إغلاق التطبيق؟ يتم حفظ التعديلات المحلية على هذا الجهاز فقط.")
            .setNegativeButton("إلغاء", null).setPositiveButton("إغلاق", (d, which) -> finish()).show();
    }
    @Override protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
