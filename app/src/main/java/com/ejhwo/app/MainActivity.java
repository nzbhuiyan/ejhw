package com.ejhwo.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.ActivityResultLauncher;
import android.webkit.ValueCallback;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/**
 * EJHWO WebView shell.
 * Offline custom page, back confirm, APK download via DownloadManager.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progress;
    private boolean exitDialogShowing = false;
    private boolean showingOffline = false;

    /** WebView input type=file (profile photo etc.) */
    private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri[] results = null;
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    if (data.getClipData() != null) {
                        int n = data.getClipData().getItemCount();
                        results = new Uri[n];
                        for (int i = 0; i < n; i++) {
                            results[i] = data.getClipData().getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{ data.getData() };
                    }
                }
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
            });

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        progress = findViewById(R.id.progress);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String url = uri.toString();
                String host = uri.getHost() == null ? "" : uri.getHost();
                String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();

                // Offline retry scheme
                if ("ejhwo".equalsIgnoreCase(uri.getScheme()) && "retry".equalsIgnoreCase(uri.getHost())) {
                    reloadApp();
                    return true;
                }

                if (url.endsWith(".apk") || path.endsWith(".apk")
                        || (url.contains("drive.google.com") && url.contains("export=download"))) {
                    try {
                        startDownload(url, URLUtil.guessFileName(url, null, null));
                    } catch (Exception ignored) {
                    }
                    return true;
                }

                if (host.contains("ejhwo") || host.contains("firebaseapp.com")
                        || host.contains("web.app") || host.contains("googleapis.com")
                        || host.contains("gstatic.com") || host.contains("google.com")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (!showingOffline) {
                    progress.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    showOfflinePage();
                }
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                showOfflinePage();
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                try {
                    String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    startDownload(url, name);
                    Toast.makeText(MainActivity.this, "ডাউনলোড শুরু হয়েছে…", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ignored) {
                        Toast.makeText(MainActivity.this, "ডাউনলোড ব্যর্থ", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (showingOffline) {
                    progress.setVisibility(View.GONE);
                    return;
                }
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    Intent intent = fileChooserParams.createIntent();
                    // Prefer images for profile photo
                    if (intent.getType() == null || "*/*".equals(intent.getType())) {
                        intent.setType("image/*");
                    }
                    fileChooserLauncher.launch(Intent.createChooser(intent, "ছবি নির্বাচন করুন"));
                    return true;
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "ফাইল পিকার খোলা যায়নি", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        if (savedInstanceState == null) {
            if (isOnline()) {
                webView.loadUrl(getString(R.string.app_url));
            } else {
                showOfflinePage();
            }
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (showingOffline) {
                    showExitConfirm();
                    return;
                }
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    showExitConfirm();
                }
            }
        });
    }

    private boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && (
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                );
            } else {
                android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
                return ni != null && ni.isConnected();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void reloadApp() {
        showingOffline = false;
        if (isOnline()) {
            progress.setVisibility(View.VISIBLE);
            webView.loadUrl(getString(R.string.app_url));
        } else {
            showOfflinePage();
            Toast.makeText(this, "এখনো ইন্টারনেট নেই", Toast.LENGTH_SHORT).show();
        }
    }

    private void showOfflinePage() {
        showingOffline = true;
        progress.setVisibility(View.GONE);
        String html = ""
                + "<!DOCTYPE html><html lang='bn'><head><meta charset='utf-8'/>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1'/>"
                + "<style>"
                + "*{box-sizing:border-box;margin:0;padding:0}"
                + "body{font-family:system-ui,-apple-system,'Noto Sans Bengali',sans-serif;"
                + "min-height:100vh;min-height:100dvh;display:flex;align-items:center;justify-content:center;"
                + "padding:28px 22px;background:linear-gradient(160deg,#f0fdfa 0%,#f8fafc 45%,#ecfdf5 100%);color:#0f172a}"
                + ".card{width:100%;max-width:360px;text-align:center;"
                + "background:rgba(255,255,255,.92);border:1px solid rgba(13,148,136,.14);"
                + "border-radius:24px;padding:28px 22px 24px;"
                + "box-shadow:0 20px 50px -18px rgba(15,23,42,.18)}"
                + ".icon{width:72px;height:72px;margin:0 auto 16px;border-radius:20px;"
                + "display:grid;place-items:center;"
                + "background:linear-gradient(145deg,#ccfbf1,#99f6e4);"
                + "color:#0f766e;box-shadow:0 10px 24px -8px rgba(13,148,136,.35)}"
                + ".icon svg{width:36px;height:36px}"
                + "h1{font-size:20px;font-weight:700;margin-bottom:8px;color:#0f766e}"
                + "p{font-size:14px;line-height:1.55;color:#64748b;margin-bottom:22px}"
                + ".btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;"
                + "width:100%;padding:14px 18px;border:none;border-radius:14px;"
                + "background:linear-gradient(135deg,#14b8a6,#0d9488);color:#fff;"
                + "font-size:15px;font-weight:700;text-decoration:none;"
                + "box-shadow:0 10px 24px -8px rgba(13,148,136,.5)}"
                + ".btn:active{opacity:.92;transform:scale(.98)}"
                + ".hint{margin-top:14px;font-size:12px;color:#94a3b8}"
                + "</style></head><body>"
                + "<div class='card'>"
                + "<div class='icon'><svg fill='none' stroke='currentColor' stroke-width='1.8' "
                + "stroke-linecap='round' stroke-linejoin='round' viewBox='0 0 24 24'>"
                + "<path d='M1 1l22 22M16.72 11.06A10.94 10.94 0 0112 13c-2.5 0-4.77-.9-6.53-2.4'/>"
                + "<path d='M5 5a15.3 15.3 0 00-2.86 2.74M9.67 5.3A10.9 10.9 0 0112 5c1.9 0 3.7.47 5.28 1.3'/>"
                + "<path d='M8.53 16.11A6 6 0 0112 17c1.1 0 2.12-.3 3-.82'/>"
                + "<path d='M12 20h.01'/></svg></div>"
                + "<h1>ইন্টারনেট সংযোগ নেই</h1>"
                + "<p>EJHWO অ্যাপ চালাতে মোবাইল ডাটা বা Wi‑Fi চালু করুন। সংযোগ ফিরলে আবার চেষ্টা করুন।</p>"
                + "<a class='btn' href='ejhwo://retry'>আবার চেষ্টা করুন</a>"
                + "<div class='hint'>Wi‑Fi / মোবাইল ডাটা চালু আছে কিনা দেখুন</div>"
                + "</div></body></html>";
        webView.loadDataWithBaseURL("https://ejhwo.local/", html, "text/html", "UTF-8", null);
    }

    private void startDownload(String url, String fileName) {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                fileName != null ? fileName : "ejhwo.apk");
        req.setTitle(fileName != null ? fileName : "EJHWO");
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) req.addRequestHeader("Cookie", cookies);
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null) dm.enqueue(req);
    }

    private void showExitConfirm() {
        if (exitDialogShowing || isFinishing()) return;
        exitDialogShowing = true;
        new AlertDialog.Builder(this)
                .setTitle("অ্যাপ বন্ধ করবেন?")
                .setMessage("আপনি কি EJHWO অ্যাপ থেকে বের হতে চান?")
                .setCancelable(true)
                .setPositiveButton("হ্যাঁ, বের হব", (dialog, which) -> {
                    exitDialogShowing = false;
                    finishAffinity();
                })
                .setNegativeButton("না", (dialog, which) -> {
                    exitDialogShowing = false;
                    dialog.dismiss();
                })
                .setOnCancelListener(dialog -> exitDialogShowing = false)
                .setOnDismissListener(dialog -> exitDialogShowing = false)
                .show();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null && !showingOffline) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null && !showingOffline) {
            webView.restoreState(savedInstanceState);
        }
    }
}
