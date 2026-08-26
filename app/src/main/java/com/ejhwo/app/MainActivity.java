package com.ejhwo.app;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
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
 * Back: history → exit confirm. APK download via DownloadManager.
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progress;
    private boolean exitDialogShowing = false;

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

                // APK → external download / install flow
                if (path.endsWith(".apk") || url.toLowerCase().contains(".apk")) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            startDownload(url, URLUtil.guessFileName(url, null, null));
                        } catch (Exception ignored) {
                        }
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
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
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
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        String url = getString(R.string.app_url);
        if (savedInstanceState == null) {
            webView.loadUrl(url);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleAppBack();
            }
        });
    }

    private void startDownload(String url, String fileName) {
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setMimeType("application/vnd.android.package-archive");
        String cookies = CookieManager.getInstance().getCookie(url);
        if (cookies != null) {
            req.addRequestHeader("cookie", cookies);
        }
        req.addRequestHeader("User-Agent", webView.getSettings().getUserAgentString());
        req.setDescription("EJHWO অ্যাপ আপডেট");
        req.setTitle(fileName != null ? fileName : "ejhwo-update.apk");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                fileName != null ? fileName : "ejhwo-update.apk");
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm != null) {
            dm.enqueue(req);
        }
    }

    private void handleAppBack() {
        if (webView == null) {
            showExitConfirm();
            return;
        }
        webView.evaluateJavascript(
                "(function(){"
                        + "try{"
                        + "var d=document.querySelector('.side-drawer.open, .side-drawer.show, #sideDrawer.open');"
                        + "if(d){if(typeof closeSideDrawer==='function'){closeSideDrawer();return 'drawer';}"
                        + "d.classList.remove('open');d.classList.remove('show');return 'drawer';}"
                        + "var m=document.querySelector('.modal-overlay.open, .modal-overlay.show, .confirm-overlay.open, #ejhwoUpdateModal.show');"
                        + "if(m){m.classList.remove('open');m.classList.remove('show');m.style.display='none';return 'modal';}"
                        + "if(typeof history!=='undefined' && history.state && history.state.ejhwo && history.length>1){"
                        + "history.back();return 'hist';}"
                        + "return 'none';"
                        + "}catch(e){return 'none';}"
                        + "})();",
                value -> {
                    String v = value == null ? "none" : value.replace("\"", "");
                    if ("drawer".equals(v) || "modal".equals(v) || "hist".equals(v)) {
                        return;
                    }
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        showExitConfirm();
                    }
                }
        );
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
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (webView != null) {
            webView.restoreState(savedInstanceState);
        }
    }
}
