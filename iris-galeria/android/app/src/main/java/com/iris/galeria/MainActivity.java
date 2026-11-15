package com.iris.galeria;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private static final int AUTH = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private boolean pickingFile = false;

    // Bloqueio com a senha, padrão ou digital do próprio celular.
    private volatile boolean locked = false;
    private boolean authenticating = false;
    private boolean skipNextPrompt = false;
    private String authPurpose = "unlock";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        locked = isLockEnabled() && isDeviceSecure();
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new IrisBridge(this, webView), "IrisAndroid");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pickingFile = true;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, PICK_IMAGES);
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    /* ---------------- bloqueio ---------------- */

    private SharedPreferences prefs() {
        return getSharedPreferences("iris_security", Context.MODE_PRIVATE);
    }

    public boolean isLockEnabled() { return prefs().getBoolean("lock", false); }

    public boolean isDeviceSecure() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    public boolean isLockedNow() { return locked; }

    /** purpose: "unlock", "enable" ou "disable". Abre a tela de senha/padrão/digital do celular. */
    public void requestAuth(String purpose) {
        runOnUiThread(() -> {
            if (authenticating) return;
            if (!isDeviceSecure()) {
                sendLockEvent("nosecure");
                return;
            }
            KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            Intent intent = km.createConfirmDeviceCredentialIntent("Iris Galeria", "Confirme que é você");
            if (intent == null) {
                sendLockEvent("nosecure");
                return;
            }
            authPurpose = purpose;
            authenticating = true;
            startActivityForResult(intent, AUTH);
        });
    }

    private void sendLockEvent(String event) {
        if (webView == null) return;
        webView.evaluateJavascript(
                "window.irisLockEvent && window.irisLockEvent({event:'" + event + "'})", null);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!locked) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (skipNextPrompt) { skipNextPrompt = false; return; }
        if (locked && !authenticating) requestAuth("unlock");
    }

    @Override protected void onPause() {
        // Esconde a prévia do app na lista de apps recentes quando o bloqueio está ligado.
        if (isLockEnabled()) getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        super.onPause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (!pickingFile && !authenticating && isLockEnabled() && isDeviceSecure()) {
            locked = true;
            if (webView != null) webView.evaluateJavascript("window.irisLock && window.irisLock()", null);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AUTH) {
            authenticating = false;
            boolean ok = resultCode == RESULT_OK;
            if (!ok) skipNextPrompt = true;
            switch (authPurpose) {
                case "enable":
                    if (ok) { prefs().edit().putBoolean("lock", true).apply(); sendLockEvent("enabled"); }
                    else sendLockEvent("failed");
                    break;
                case "disable":
                    if (ok) { prefs().edit().putBoolean("lock", false).apply(); sendLockEvent("disabled"); }
                    else sendLockEvent("failed");
                    break;
                default:
                    if (ok) { locked = false; sendLockEvent("unlocked"); }
                    else sendLockEvent("locked");
            }
            return;
        }
        pickingFile = false;
        if (requestCode != PICK_IMAGES || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                result = new Uri[count];
                for (int i = 0; i < count; i++) result[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) result = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override public void onBackPressed() {
        webView.evaluateJavascript("(window.irisBack ? window.irisBack() : false)", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    @Override protected void onDestroy() { webView.destroy(); super.onDestroy(); }
}
