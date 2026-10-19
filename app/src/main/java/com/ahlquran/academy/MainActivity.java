package com.ahlquran.academy;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
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

/** Offline core with optional in-app audio rooms via LiveKit and authenticated Cloudflare Worker; no custom domain. */
public class MainActivity extends Activity {
    private WebView web;
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final int MIC_REQUEST=5043;
    private PermissionRequest pendingMic;
    private WebViewAssetLoader assetLoader;
    private static final String PREF = "academy_offline_data";
    private static final int EXPORT_BACKUP = 5041;
    private static final int IMPORT_BACKUP = 5042;

    public final class OfflineData {
        @JavascriptInterface public String load() {
            return getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("state", "");
        }
        @JavascriptInterface public String newPassword() {
            String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#";
            java.security.SecureRandom rng = new java.security.SecureRandom();
            StringBuilder result = new StringBuilder(18);
            for (int i=0; i<18; i++) result.append(alphabet.charAt(rng.nextInt(alphabet.length())));
            return result.toString();
        }
        @JavascriptInterface public void shareText(String value) {
            if (value == null || value.length() > 3000) return;
            runOnUiThread(() -> {
                try {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, value);
                    startActivity(Intent.createChooser(send, "مشاركة دعوة الحصة"));
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "تعذرت مشاركة الدعوة", Toast.LENGTH_SHORT).show();
                }
            });
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
        assetLoader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); // secure HTTPS virtual origin via asset loader
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false); // remote audio plays after joining; microphone still requires user & Android permission
        web.addJavascriptInterface(new OfflineData(), "AcademyOffline");
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){
                runOnUiThread(()->{
                    if(!"https://appassets.androidplatform.net".equals(request.getOrigin().toString().replaceAll("/$", ""))){request.deny();return;}
                    boolean audio=false;
                    for(String r:request.getResources())if(PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r))audio=true;
                    if(!audio){request.deny();return;}
                    if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});return;}
                    if(pendingMic!=null)pendingMic.deny();pendingMic=request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);
                });
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request){if(pendingMic==request)pendingMic=null;}
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request){
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("https://appassets.androidplatform.net/assets/")) return false;
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
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==MIC_REQUEST&&pendingMic!=null){
            PermissionRequest r=pendingMic;pendingMic=null;
            if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else {r.deny();Toast.makeText(this,"يمكنك سماع الحصة؛ اسمح بالميكروفون للتسميع",Toast.LENGTH_LONG).show();}
        }
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
        if (web != null) { web.evaluateJavascript("window.voiceLeave && window.voiceLeave()",null); web.destroy(); web = null; }
        super.onDestroy();
    }
}
