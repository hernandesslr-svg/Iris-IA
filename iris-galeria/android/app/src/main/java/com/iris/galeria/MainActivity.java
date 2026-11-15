package com.iris.galeria;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int PICK_IMAGES = 1001;
    private static final int AUTH = 1002;
    private static final int CAMERA = 1003;
    private Uri cameraUri;

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
        webView.setBackgroundColor(0xFF141414);
        darkBars();
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new IrisBridge(this, webView), "IrisAndroid");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                pickingFile = true;
                try {
                    if (params.isCaptureEnabled()) {
                        startCamera();
                        return true;
                    }
                    ArrayList<String> mimes = new ArrayList<>();
                    String[] acc = params.getAcceptTypes();
                    if (acc != null) {
                        for (String a : acc) {
                            if (a == null) continue;
                            for (String part : a.split(",")) {
                                String t = part.trim().toLowerCase();
                                if (t.isEmpty()) continue;
                                if (t.equals(".zip")) {
                                    if (!mimes.contains("application/zip")) mimes.add("application/zip");
                                    if (!mimes.contains("application/x-zip-compressed")) mimes.add("application/x-zip-compressed");
                                } else if (t.contains("/") && !mimes.contains(t)) mimes.add(t);
                            }
                        }
                    }
                    boolean multi = params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
                    String only = mimes.size() == 1 ? mimes.get(0) : "";
                    boolean media = only.equals("image/*") || only.equals("video/*");
                    if (media) {
                        // Fotos e vídeos: abre a galeria do celular.
                        Intent g;
                        if (Build.VERSION.SDK_INT >= 33) {
                            g = new Intent(MediaStore.ACTION_PICK_IMAGES);
                            if (multi) g.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
                        } else {
                            g = new Intent(Intent.ACTION_GET_CONTENT);
                            g.addCategory(Intent.CATEGORY_OPENABLE);
                            g.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
                        }
                        g.setType(only);
                        try {
                            startActivityForResult(g, PICK_IMAGES);
                            return true;
                        } catch (Exception noGallery) {
                            // sem galeria disponível: cai no seletor de arquivos
                        }
                    }
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    if (mimes.size() == 1) intent.setType(mimes.get(0));
                    else {
                        intent.setType("*/*");
                        if (mimes.size() > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                    }
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi);
                    startActivityForResult(intent, PICK_IMAGES);
                } catch (Exception e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    pickingFile = false;
                }
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    /** Barras do celular (de cima e de baixo) no grafite da galeria, com ícones claros. */
    @SuppressWarnings("deprecation")
    private void darkBars() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(0xFF232323);
            w.setNavigationBarColor(0xFF141414);
            if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
            if (Build.VERSION.SDK_INT >= 30) {
                WindowInsetsController c = w.getInsetsController();
                if (c != null) c.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            } else {
                View d = w.getDecorView();
                int f = d.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                d.setSystemUiVisibility(f);
            }
        } catch (Exception ignored) {
        }
    }

    /** Abre a câmera do celular e devolve a foto tirada para a página. */
    private void startCamera() throws Exception {
        File f = new File(IrisShareProvider.dir(this), "foto-" + System.currentTimeMillis() + ".jpg");
        f.createNewFile();
        cameraUri = new Uri.Builder().scheme("content").authority(IrisShareProvider.AUTHORITY).appendPath(f.getName()).build();
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        i.setClipData(ClipData.newRawUri("", cameraUri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(i, CAMERA);
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

    /** Evita travar o app quando o usuário sai só para compartilhar uma foto. */
    public void markExternal() { pickingFile = true; }

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
        pickingFile = false;
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
        if (requestCode == CAMERA) {
            if (fileCallback != null) {
                Uri u = cameraUri;
                boolean ok = resultCode == RESULT_OK && u != null;
                if (ok) {
                    File f = new File(IrisShareProvider.dir(this), String.valueOf(u.getLastPathSegment()));
                    ok = f.exists() && f.length() > 0;
                }
                fileCallback.onReceiveValue(ok ? new Uri[]{u} : null);
                fileCallback = null;
            }
            cameraUri = null;
            return;
        }
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
