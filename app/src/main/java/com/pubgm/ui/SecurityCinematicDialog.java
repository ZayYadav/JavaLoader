package com.pubgm.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.pubgm.security.SecurityIncidentDispatcher;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** OneCore-style non-cancelable signature/integrity breach cinematic. */
@Obfuscate
public final class SecurityCinematicDialog {
    private static final String VIDEO_URL =
            "https://github.com/sk8787914-maker/Zoro-online-mod/releases/download/JANGAM/VID_20260822_235111_206.mp4";
    private static final String CACHE_FILE = "onecore_signature_breach_v4.mp4";
    private static final long MIN_VIDEO_BYTES = 64L * 1024L;
    private static final long MAX_VIDEO_BYTES = 160L * 1024L * 1024L;
    private static final long EXIT_DELAY_MS = 15_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOAD = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "OneCore-CinematicFetch");
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService EXIT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "OneCore-CinematicExit");
        thread.setDaemon(true);
        return thread;
    });

    private SecurityCinematicDialog() { }

    public static void show(Activity activity, SecurityIncidentDispatcher.Reason reason,
            String detail, Runnable onFinished) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MAIN.post(() -> show(activity, reason, detail, onFinished));
            return;
        }
        if (!usable(activity)) {
            if (onFinished != null) onFinished.run();
            return;
        }
        new Session(activity, reason, detail, onFinished).start();
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private interface ProgressCallback {
        void update(int progress, String message);
    }

    private static final class Session implements TextureView.SurfaceTextureListener {
        private final Activity activity;
        private final SecurityIncidentDispatcher.Reason reason;
        private final String detail;
        private final Runnable onFinished;
        private final AtomicBoolean finished = new AtomicBoolean(false);
        private final AtomicBoolean countdownArmed = new AtomicBoolean(false);

        private Dialog dialog;
        private FrameLayout stage;
        private ProgressBar progressBar;
        private TextView progressText;
        private TextView statusText;
        private TextView countdownText;
        private TextView countdownFooter;
        private TextureView textureView;
        private MediaPlayer mediaPlayer;
        private Surface mediaSurface;
        private File pendingVideo;
        private long exitDeadline;
        private AudioManager audioManager;
        private int previousVolume = -1;

        Session(Activity activity, SecurityIncidentDispatcher.Reason reason,
                String detail, Runnable onFinished) {
            this.activity = activity;
            this.reason = reason == null ? SecurityIncidentDispatcher.Reason.INTEGRITY : reason;
            this.detail = detail == null ? "" : detail;
            this.onFinished = onFinished;
        }

        void start() {
            dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCancelable(false);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener((ignored, keyCode, event) -> keyCode == KeyEvent.KEYCODE_BACK);
            dialog.setOnDismissListener(ignored -> restoreVolume());

            stage = new FrameLayout(activity);
            stage.setPadding(dp(8), dp(8), dp(8), dp(8));
            stage.setBackground(panelBackground());
            dialog.setContentView(stage);
            showProcessing();

            try {
                dialog.show();
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    WindowManager.LayoutParams attrs = window.getAttributes();
                    attrs.dimAmount = 0.94f;
                    window.setAttributes(attrs);
                    int width = activity.getResources().getDisplayMetrics().widthPixels;
                    int height = activity.getResources().getDisplayMetrics().heightPixels;
                    window.setLayout((int) (width * 0.96f), (int) (height * 0.88f));
                    window.setGravity(Gravity.CENTER);
                }
            } catch (Throwable ignored) {
                finishNow();
                return;
            }

            DOWNLOAD.execute(() -> {
                try {
                    File video = obtainVideo(activity,
                            (progress, message) -> MAIN.post(() -> updateProgress(progress, message)));
                    MAIN.post(() -> showVideo(video));
                } catch (Throwable ignored) {
                    MAIN.post(() -> showFallback("SECURE MEDIA UNAVAILABLE"));
                }
            });
        }

        private void showProcessing() {
            stage.removeAllViews();
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
            content.setPadding(dp(22), dp(26), dp(22), dp(24));
            stage.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            TextView eyebrow = text("ONECORE EDGE  •  SECURITY INTERCEPT", 10f, 0xFFFFC857, true);
            eyebrow.setGravity(Gravity.CENTER);
            eyebrow.setLetterSpacing(0.12f);
            content.addView(eyebrow, matchWrap(dp(12)));

            TextView title = text(reason == SecurityIncidentDispatcher.Reason.SIGNATURE
                    ? "SIGNATURE INTEGRITY BREACH" : "APPLICATION INTEGRITY BREACH",
                    24f, Color.WHITE, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title, matchWrap(dp(8)));

            TextView sub = text("VALIDATING SECURE RESPONSE  •  SESSION CONTAINED",
                    9.5f, 0xFF9BA7B7, true);
            sub.setGravity(Gravity.CENTER);
            sub.setLetterSpacing(0.07f);
            content.addView(sub, matchWrap(dp(26)));

            statusText = text("PREPARING SECURE VIDEO CHANNEL", 13f, Color.WHITE, true);
            statusText.setGravity(Gravity.CENTER);
            content.addView(statusText, matchWrap(dp(12)));

            progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFC857));
                progressBar.setProgressBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x443C4657));
            }
            LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            bar.bottomMargin = dp(10);
            content.addView(progressBar, bar);

            progressText = text("0%", 17f, 0xFF7DD3FC, true);
            progressText.setGravity(Gravity.CENTER);
            content.addView(progressText, matchWrap(dp(14)));

            TextView footer = text("SIGNER  •  APK V2  •  NATIVE  •  LOCKED",
                    9.5f, 0xFF67E8A5, true);
            footer.setGravity(Gravity.CENTER);
            footer.setLetterSpacing(0.08f);
            content.addView(footer, matchWrap(0));
        }

        private void updateProgress(int progress, String message) {
            if (finished.get()) return;
            int safe = Math.max(0, Math.min(100, progress));
            if (progressBar != null) progressBar.setProgress(safe);
            if (progressText != null) progressText.setText(String.format(Locale.US, "%d%%", safe));
            if (statusText != null && message != null && !message.isEmpty()) statusText.setText(message);
        }

        private void showVideo(File video) {
            if (finished.get()) return;
            if (!validMp4(video)) {
                showFallback("SECURE MEDIA VALIDATION FAILED");
                return;
            }
            pendingVideo = video;
            stage.removeAllViews();
            FrameLayout shell = new FrameLayout(activity);
            shell.setBackgroundColor(Color.BLACK);
            stage.addView(shell, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            textureView = new TextureView(activity);
            textureView.setSurfaceTextureListener(this);
            shell.addView(textureView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            TextView top = text("ONECORE EDGE // SECURITY INTERCEPT", 10.5f, 0xFFFFC857, true);
            top.setGravity(Gravity.CENTER);
            top.setLetterSpacing(0.1f);
            top.setPadding(dp(12), dp(8), dp(12), dp(8));
            top.setBackground(chipBackground(true));
            FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            topParams.topMargin = dp(14);
            shell.addView(top, topParams);

            LinearLayout bottom = new LinearLayout(activity);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            bottom.setPadding(dp(14), dp(10), dp(14), dp(10));
            bottom.setBackground(chipBackground(false));
            FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM);
            bottomParams.leftMargin = dp(14);
            bottomParams.rightMargin = dp(14);
            bottomParams.bottomMargin = dp(14);
            shell.addView(bottom, bottomParams);

            LinearLayout copy = new LinearLayout(activity);
            copy.setOrientation(LinearLayout.VERTICAL);
            bottom.addView(copy, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView breach = text("SIGNATURE INTEGRITY BREACH", 13f, 0xFFFF5E66, true);
            breach.setLetterSpacing(0.04f);
            copy.addView(breach, matchWrap(dp(3)));

            String safeDetail = detail.length() > 42 ? detail.substring(0, 42) : detail;
            TextView detailView = text(safeDetail.isEmpty() ? "SECURE RESPONSE ACTIVE" : safeDetail,
                    9f, 0xFFBAC5D5, true);
            copy.addView(detailView, matchWrap(dp(2)));

            countdownFooter = text("SECURE RESPONSE PLAYING  •  EXIT ARMED",
                    9f, 0xFF67E8A5, true);
            copy.addView(countdownFooter, matchWrap(0));

            countdownText = text("15s", 27f, 0xFFFF5E66, true);
            countdownText.setGravity(Gravity.CENTER);
            bottom.addView(countdownText, new LinearLayout.LayoutParams(dp(76),
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            if (textureView.isAvailable()) startMedia(textureView.getSurfaceTexture());
        }

        private void startMedia(SurfaceTexture surfaceTexture) {
            if (finished.get() || surfaceTexture == null || pendingVideo == null || mediaPlayer != null) return;
            try {
                mediaSurface = new Surface(surfaceTexture);
                MediaPlayer player = new MediaPlayer();
                mediaPlayer = player;
                player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                player.setSurface(mediaSurface);
                player.setDataSource(pendingVideo.getAbsolutePath());
                player.setLooping(true);
                player.setOnPreparedListener(prepared -> {
                    if (finished.get() || mediaPlayer != prepared) return;
                    raiseVolume();
                    try {
                        prepared.setVolume(1f, 1f);
                        prepared.start();
                        armCountdown();
                    } catch (Throwable ignored) {
                        showFallback("VIDEO PLAYBACK BLOCKED");
                    }
                });
                player.setOnErrorListener((ignored, what, extra) -> {
                    MAIN.post(() -> showFallback("VIDEO PLAYBACK BLOCKED"));
                    return true;
                });
                player.prepareAsync();
            } catch (Throwable ignored) {
                showFallback("VIDEO PLAYBACK BLOCKED");
            }
        }

        private void armCountdown() {
            if (!countdownArmed.compareAndSet(false, true)) return;
            exitDeadline = SystemClock.elapsedRealtime() + EXIT_DELAY_MS;
            tickCountdown();
            EXIT.schedule(() -> MAIN.post(this::finishNow), EXIT_DELAY_MS, TimeUnit.MILLISECONDS);
        }

        private void tickCountdown() {
            if (finished.get() || !countdownArmed.get()) return;
            long remaining = exitDeadline - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                finishNow();
                return;
            }
            int seconds = (int) Math.max(1L, (remaining + 999L) / 1000L);
            if (countdownText != null) countdownText.setText(String.format(Locale.US, "%02ds", seconds));
            if (countdownFooter != null) {
                countdownFooter.setText("SECURE RESPONSE PLAYING  •  EXIT IN " + seconds + "s");
            }
            MAIN.postDelayed(this::tickCountdown, 200L);
        }

        private void showFallback(String message) {
            if (finished.get()) return;
            releasePlayer();
            restoreVolume();
            stage.removeAllViews();
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            content.setPadding(dp(24), dp(24), dp(24), dp(24));
            stage.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            TextView title = text("SIGNATURE INTEGRITY BREACH", 24f, 0xFFFF5E66, true);
            title.setGravity(Gravity.CENTER);
            content.addView(title, matchWrap(dp(14)));
            TextView status = text(message, 12f, Color.WHITE, true);
            status.setGravity(Gravity.CENTER);
            content.addView(status, matchWrap(dp(10)));
            TextView detailView = text(detail.isEmpty() ? "APPLICATION INTEGRITY COULD NOT BE VERIFIED" : detail,
                    10f, 0xFFBAC5D5, false);
            detailView.setGravity(Gravity.CENTER);
            content.addView(detailView, matchWrap(0));
            if (!countdownArmed.get()) armCountdown();
        }

        private void finishNow() {
            if (!finished.compareAndSet(false, true)) return;
            releasePlayer();
            restoreVolume();
            try {
                if (dialog != null && dialog.isShowing()) dialog.dismiss();
            } catch (Throwable ignored) { }
            if (onFinished != null) onFinished.run();
        }

        private void releasePlayer() {
            MediaPlayer player = mediaPlayer;
            mediaPlayer = null;
            if (player != null) {
                try { player.stop(); } catch (Throwable ignored) { }
                try { player.release(); } catch (Throwable ignored) { }
            }
            Surface surface = mediaSurface;
            mediaSurface = null;
            if (surface != null) {
                try { surface.release(); } catch (Throwable ignored) { }
            }
        }

        private void raiseVolume() {
            try {
                audioManager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
                if (audioManager == null) return;
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int target = Math.max(previousVolume, Math.max(1, (int) (max * 0.70f)));
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            } catch (Throwable ignored) { }
        }

        private void restoreVolume() {
            if (audioManager != null && previousVolume >= 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0);
                } catch (Throwable ignored) { }
            }
            previousVolume = -1;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            startMedia(surface);
        }

        @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            releasePlayer();
            return true;
        }

        @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }

        private static File obtainVideo(Activity activity, ProgressCallback callback) throws Exception {
            File target = new File(activity.getCacheDir(), CACHE_FILE);
            if (validMp4(target)) {
                callback.update(100, "SECURE RESPONSE READY");
                return target;
            }
            if (target.exists()) target.delete();

            URL url = new URL(VIDEO_URL);
            if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalStateException("HTTPS required");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "OneCore-Integrity/1");
            connection.connect();
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
            long declared = connection.getContentLengthLong();
            if (declared > MAX_VIDEO_BYTES) throw new IllegalStateException("Response too large");

            File part = new File(activity.getCacheDir(), CACHE_FILE + ".part");
            if (part.exists()) part.delete();
            long total = 0L;
            byte[] buffer = new byte[32 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(part))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_VIDEO_BYTES) throw new IllegalStateException("Response too large");
                    output.write(buffer, 0, read);
                    int progress = declared > 0 ? (int) Math.min(99L, total * 100L / declared) : 35;
                    callback.update(progress, "DOWNLOADING SECURITY RESPONSE");
                }
            } finally {
                connection.disconnect();
            }
            if (!validMp4(part)) {
                part.delete();
                throw new IllegalStateException("Invalid MP4");
            }
            if (!part.renameTo(target)) {
                copyFile(part, target);
                part.delete();
            }
            if (!validMp4(target)) throw new IllegalStateException("Cached MP4 invalid");
            callback.update(100, "SECURE RESPONSE READY");
            return target;
        }

        private static void copyFile(File source, File target) throws Exception {
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
                 BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
        }

        private static boolean validMp4(File file) {
            if (file == null || !file.isFile() || file.length() < MIN_VIDEO_BYTES
                    || file.length() > MAX_VIDEO_BYTES) return false;
            byte[] header = new byte[12];
            try (FileInputStream input = new FileInputStream(file)) {
                if (input.read(header) != header.length) return false;
                return header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            } catch (Throwable ignored) {
                return false;
            }
        }

        private TextView text(String value, float size, int color, boolean bold) {
            TextView view = new TextView(activity);
            view.setText(value);
            view.setTextSize(size);
            view.setTextColor(color);
            if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
            return view;
        }

        private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = bottomMargin;
            return params;
        }

        private GradientDrawable panelBackground() {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xFF090D14);
            drawable.setCornerRadius(dp(24));
            drawable.setStroke(dp(1), 0x88FFC857);
            return drawable;
        }

        private GradientDrawable chipBackground(boolean gold) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xCC0A0E15);
            drawable.setCornerRadius(dp(14));
            drawable.setStroke(dp(1), gold ? 0xAAFFC857 : 0x88FF5E66);
            return drawable;
        }

        private int dp(int value) {
            return Math.round(value * activity.getResources().getDisplayMetrics().density);
        }
    }
}
