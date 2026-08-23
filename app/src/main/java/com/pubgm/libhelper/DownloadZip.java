package com.pubgm.libhelper;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import net.lingala.zip4j.ZipFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class DownloadZip {

    private final Context context;
    private final ExecutorService executor;
    private final Handler handler;
    private final String ZIP_FILE_NAME = "Saved.zip";
    private Dialog downloadDialog;
    private WebView webView;
    private boolean downloadCancelled = false;

    public DownloadZip(Context context) {
        this.context = context;
        executor = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
    }

    public void startDownload(String downloadUrl) {
        handler.post(() -> {
            showDownloadDialog();

            executor.execute(() -> {
                boolean success = downloadFile(downloadUrl);

                handler.post(() -> {
                    if (success && !downloadCancelled) {
                        boolean processSuccess = processDownloadedFile();
                        if (processSuccess) {
                            updateUIComplete();
                        } else {
                            dismissDialog();
                            Toast.makeText(context, "Extraction failed", Toast.LENGTH_LONG).show();
                        }
                    } else if (!downloadCancelled) {
                        dismissDialog();
                        Toast.makeText(context, "Download failed", Toast.LENGTH_LONG).show();
                    }
                });
            });
        });
    }

    private boolean downloadFile(String downloadUrl) {
        File outputFile = new File(context.getFilesDir(), ZIP_FILE_NAME);

        try {
            URL url = new URL(downloadUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }

            int fileLength = connection.getContentLength();
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(outputFile)) {

                byte[] data = new byte[4096];
                long total = 0;
                int count;

                while ((count = input.read(data)) != -1) {
                    if (downloadCancelled) {
                        return false;
                    }
                    total += count;
                    if (fileLength > 0) {
                        final int progress = (int) (total * 100 / fileLength);
                        updateProgress(progress);
                    }
                    output.write(data, 0, count);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateProgress(final int progress) {
        handler.post(() -> {
            if (downloadDialog != null && downloadDialog.isShowing() && webView != null) {
                try {
                    String javascript = String.format("updateProgress(%d, '%s');",progress,getStatusMessage(progress));
                    webView.evaluateJavascript(javascript, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private String getStatusMessage(int progress) {
        if (progress < 10) {
            return "Starting download...";
        } else if (progress < 30) {
            return "Connecting to server...";
        } else if (progress < 60) {
            return "Downloading files...";
        } else if (progress < 85) {
            return "Almost there...";
        } else if (progress < 100) {
            return "Finishing download...";
        }
        return "Processing...";
    }

    private void updateUIComplete() {
        handler.post(() -> {
            if (downloadDialog != null && downloadDialog.isShowing() && webView != null) {
                try {
                    webView.evaluateJavascript("showCompletion();", null);
                    handler.postDelayed(() -> {
                        dismissDialog();
                        Toast.makeText(context, "Download successful!", Toast.LENGTH_LONG).show();
                    }, 3000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void showDownloadDialog() {
        try {
            downloadDialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar);
            downloadDialog.setCancelable(false);
            downloadDialog.setCanceledOnTouchOutside(false);

            if (downloadDialog.getWindow() != null) {
                downloadDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            webView = new WebView(context);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.getSettings().setJavaScriptEnabled(true);
            webView.getSettings().setDomStorageEnabled(true);
            webView.setWebViewClient(new WebViewClient());

            webView.addJavascriptInterface(new Object() {
                @JavascriptInterface
                public String getDeviceInfo() {
                    return "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" + "Android: " + Build.VERSION.RELEASE + "\n" + "SDK: " + Build.VERSION.SDK_INT;
                }
            }, "Android");

            webView.loadDataWithBaseURL(
                null,
                getDownloadHtml(),
                "text/html",
                "UTF-8",
                null
            );

            downloadDialog.setContentView(webView);
            downloadDialog.show();

            Window window = downloadDialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams layoutParams = window.getAttributes();
                layoutParams.width = dp(250);
                layoutParams.height = dp(400);
                layoutParams.gravity = Gravity.CENTER;
                layoutParams.dimAmount = 0.6f;
                window.setAttributes(layoutParams);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Error showing dialog", Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    private String getDownloadHtml() {
        return "<!DOCTYPE html>" +
               "<html>" +
               "<head>" +
               "<meta charset='UTF-8'>" +
               "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
               "<style>" +
               "* { margin: 0; padding: 0; box-sizing: border-box; font-family: 'Segoe UI', system-ui, sans-serif; }" +
               "body { background: transparent; display: flex; justify-content: center; align-items: center; height: 100vh; }" +
               ".card { width: 240px; background: #000000; border-radius: 12px; padding: 14px; border: 2px solid #4CAF50; box-shadow: 0 8px 25px rgba(0,0,0,0.8), 0 0 0 1px rgba(76, 175, 80, 0.3); position: relative; overflow: hidden; }" +
               ".card::before { content: ''; position: absolute; top: -2px; left: -2px; right: -2px; bottom: -2px; background: #4CAF50; border-radius: 14px; z-index: -1; opacity: 0.3; filter: blur(5px); }" +
               ".header { display: flex; align-items: center; justify-content: center; gap: 8px; margin-bottom: 12px; padding-bottom: 10px; border-bottom: 1px solid rgba(76, 175, 80, 0.4); }" +
               ".title { font-size: 14px; font-weight: 700; color: #4CAF50; letter-spacing: 0.5px; }" +
               ".progress-container { background: rgba(76, 175, 80, 0.1); border-radius: 8px; padding: 10px; margin: 10px 0; }" +
               ".progress-bar { height: 8px; background: linear-gradient(90deg, #4CAF50 0%, #8BC34A 100%); border-radius: 4px; transition: width 0.3s ease; width: 0%; }" +
               ".progress-text { font-size: 18px; font-weight: bold; margin: 8px 0; color: #4CAF50; text-align: center; }" +
               ".status { font-size: 12px; color: #ffffff; text-align: center; opacity: 0.8; margin: 5px 0; }" +
               ".device-info { background: rgba(76, 175, 80, 0.08); border-radius: 8px; padding: 7px; margin: 10px 0; border: 1px solid rgba(76, 175, 80, 0.15); }" +
               ".info-row { display: flex; justify-content: space-between; margin: 3px 0; }" +
               ".info-label { font-size: 9px; color: #a5d8ff; font-weight: 500; }" +
               ".info-value { font-size: 9px; color: #ffffff; font-weight: 600; }" +
               ".footer { margin-top: 10px; text-align: center; padding-top: 8px; border-top: 1px solid rgba(76, 175, 80, 0.2); }" +
               ".success-message { font-size: 14px; color: #4CAF50; font-weight: bold; text-align: center; margin: 10px 0; padding: 10px; background: rgba(76, 175, 80, 0.1); border-radius: 8px; border: 1px solid rgba(76, 175, 80, 0.2); }" +
               ".countdown { font-size: 11px; color: #33ff66; font-weight: 600; text-align: center; margin-top: 10px; background: rgba(51,255,102,0.1); padding: 6px; border-radius: 5px; border: 1px solid rgba(51,255,102,0.2); }" +
               "</style>" +
               "</head>" +
               "<body>" +
               "<div class='card'>" +
               "    <div class='header'>" +
               "        <div class='title'>FILE DOWNLOADER</div>" +
               "    </div>" +
               "    <div id='deviceInfo' class='device-info'></div>" +
               "    <div class='progress-container'>" +
               "        <div id='progressBar' class='progress-bar'></div>" +
               "        <div id='progressText' class='progress-text'>0%</div>" +
               "        <div id='statusMessage' class='status'>Initializing...</div>" +
               "    </div>" +
               "    <div id='successContainer' style='display:none;'>" +
               "        <div class='success-message'>✓ DOWNLOAD SUCCESSFUL</div>" +
               "        <div class='countdown'>Auto closing in <span id='timer'>3</span> seconds</div>" +
               "    </div>" +
               "</div>" +
               "<script>" +
               "let countdownInterval;" +
               "" +
               "function updateProgress(progress, status) {" +
               "    document.getElementById('progressBar').style.width = progress + '%';" +
               "    document.getElementById('progressText').textContent = progress + '%';" +
               "    document.getElementById('statusMessage').textContent = status;" +
               "}" +
               "" +
               "function showCompletion() {" +
               "    updateProgress(100, 'Download Complete!');" +
               "    document.getElementById('progressContainer').style.display = 'none';" +
               "    document.getElementById('successContainer').style.display = 'block';" +
               "    startCountdown();" +
               "}" +
               "" +
               "function startCountdown() {" +
               "    let seconds = 3;" +
               "    const timerElement = document.getElementById('timer');" +
               "    countdownInterval = setInterval(() => {" +
               "        seconds--;" +
               "        timerElement.textContent = seconds;" +
               "        if (seconds <= 0) {" +
               "            clearInterval(countdownInterval);" +
               "        }" +
               "    }, 1000);" +
               "}" +
               "" +
               "document.addEventListener('DOMContentLoaded', function() {" +
               "    if (window.Android && Android.getDeviceInfo) {" +
               "        try {" +
               "            const info = Android.getDeviceInfo();" +
               "            const lines = info.split('\\n');" +
               "            const deviceInfoDiv = document.getElementById('deviceInfo');" +
               "            deviceInfoDiv.innerHTML = '';" +
               "            lines.forEach(line => {" +
               "                if (line.trim()) {" +
               "                    const [label, value] = line.split(':').map(s => s.trim());" +
               "                    if (label && value) {" +
               "                        const row = document.createElement('div');" +
               "                        row.className = 'info-row';" +
               "                        row.innerHTML = '<span class=\"info-label\">' + label + ':</span><span class=\"info-value\">' + value + '</span>';" +
               "                        deviceInfoDiv.appendChild(row);" +
               "                    }" +
               "                }" +
               "            });" +
               "        } catch (e) {" +
               "            document.getElementById('deviceInfo').innerHTML = " +
               "                '<div class=\"info-row\"><span class=\"info-label\">Device:</span><span class=\"info-value\">Unknown</span></div>';" +
               "        }" +
               "    }" +
               "    document.querySelector('.progress-container').id = 'progressContainer';" +
               "});" +
               "</script>" +
               "</body>" +
               "</html>";
    }

    private void dismissDialog() {
        try {
            if (downloadDialog != null && downloadDialog.isShowing()) {
                downloadDialog.dismiss();
                downloadDialog = null;
                webView = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private boolean processDownloadedFile() {
        File zipFile = new File(context.getFilesDir(), ZIP_FILE_NAME);
        String zipPath = zipFile.getAbsolutePath();
        String outputDir = context.getFilesDir().getAbsolutePath();
        String password = "";

        if (unzipEncrypted(zipPath, outputDir, password)) {
            moveSoFiles(new File(outputDir, "loader"));
            zipFile.delete();
            Toast.makeText(context, "Update successful!", Toast.LENGTH_LONG).show();
            return true;
        } else {
            Toast.makeText(context, "Extraction failed", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private boolean unzipEncrypted(String zipPath, String outputDir, String password) {
        try {
            new ZipFile(zipPath, password.toCharArray()).extractAll(outputDir);
            setPermissions(new File(outputDir));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void moveSoFiles(File loaderFolder) {
        File outputDir = context.getFilesDir();
        if (!loaderFolder.exists()) loaderFolder.mkdirs();
        File[] files = outputDir.listFiles((dir, name) -> name.endsWith(".so"));
        if (files != null) {
            for (File soFile : files) {
                try {
                    Files.move(soFile.toPath(), new File(loaderFolder, soFile.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setPermissions(File fileOrDir) {
        try {
            fileOrDir.setExecutable(true, false);
            fileOrDir.setReadable(true, false);
            fileOrDir.setWritable(true, false);
            if (fileOrDir.isDirectory()) {
                File[] children = fileOrDir.listFiles();
                if (children != null) {
                    for (File child : children) {
                        setPermissions(child);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}