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
import android.content.ActivityNotFoundException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Offline, single-device academy demonstration. No website or custom domain required. */
public class MainActivity extends Activity {
    private WebView web;
    private static final String HOME = "file:///android_asset/index.html";
    private static final String PREF = "academy_offline_data";
    private static final int EXPORT_BACKUP = 5041;
    private static final int IMPORT_BACKUP = 5042;

    public final class OfflineData {
        @JavascriptInterface public String load() {
            return getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("state", "");
        }
        @JavascriptInterface public void exportBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "ahl-quran-backup.json");
                try { startActivityForResult(i, EXPORT_BACKUP); }
                catch (ActivityNotFoundException ex) { Toast.makeText(MainActivity.this,"تعذر فتح مدير الملفات",Toast.LENGTH_SHORT).show(); }
            });
        }
        @JavascriptInterface public void importBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                try { startActivityForResult(i, IMPORT_BACKUP); }
                catch (ActivityNotFoundException ex) { Toast.makeText(MainActivity.this,"تعذر فتح مدير الملفات",Toast.LENGTH_SHORT).show(); }
            });
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
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try {
            Uri u=data.getData();
            if(requestCode==EXPORT_BACKUP){
                String state=getSharedPreferences(PREF,Context.MODE_PRIVATE).getString("state", "");
                if(state.isEmpty())state="{}";
                try(OutputStream os=getContentResolver().openOutputStream(u)){
                    if(os==null)throw new IllegalStateException("No output stream");
                    os.write(state.getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this,"تم حفظ النسخة الاحتياطية",Toast.LENGTH_LONG).show();
            } else if(requestCode==IMPORT_BACKUP){
                byte[] content;
                try(InputStream is=getContentResolver().openInputStream(u); ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                    if(is==null)throw new IllegalStateException("No input stream");
                    byte[] chunk=new byte[8192]; int size, total=0;
                    while((size=is.read(chunk))!=-1){
                        total+=size;
                        if(total>750000)throw new IllegalArgumentException("Backup too large");
                        buffer.write(chunk,0,size);
                    }
                    content=buffer.toByteArray();
                }
                if(content.length>750000)throw new IllegalArgumentException("Backup too large");
                String value=new String(content,StandardCharsets.UTF_8);
                web.evaluateJavascript("window.restoreAcademyBackup("+JSONObject.quote(value)+")",null);
            }
        }catch(Exception ex){Toast.makeText(this,"تعذر التعامل مع النسخة الاحتياطية",Toast.LENGTH_LONG).show();}
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
