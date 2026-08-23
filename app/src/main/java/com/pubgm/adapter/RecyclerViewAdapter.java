package com.pubgm.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;

// REMOVED: com.elite.core.env.BEnvironment
// ADDED: BlackBoxCore BEnvironment
import top.niunaijun.blackbox.core.env.BEnvironment;

import com.pubgm.activity.MainActivity;
import com.pubgm.floating.FloatLogo;
import com.pubgm.libhelper.DownloadZipAdapter;
import com.pubgm.libhelper.FileHelper;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FLog;
import com.pubgm.utils.UiKit;
import com.google.android.material.snackbar.Snackbar;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import static com.pubgm.Config.GAME_LIST_ICON;
import static com.pubgm.Config.GAME_LIST_PKG;
import android.content.Intent;
import com.pubgm.R;
import com.pubgm.libhelper.ApkEnv;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder> {
    public MainActivity activity;
    public ArrayList<Integer> imageValues;
    public ArrayList<String> titleValues;
    public ArrayList<String> versionValues;
    public ArrayList<String> statusValues;
    public ArrayList<String> packageValues;
    public ArrayList<String> genreValues;
    public ArrayList<String> regionValues;
    
    public RecyclerViewAdapter(MainActivity activity, ArrayList<Integer> imageValues, ArrayList<String> titleValues, ArrayList<String> versionValues, ArrayList<String> statusValues, ArrayList<String> packageValues) {
        this.activity = activity;
        this.imageValues = imageValues;
        this.titleValues = titleValues;
        this.versionValues = versionValues;
        this.statusValues = statusValues;
        this.packageValues = packageValues;
        
        this.genreValues = new ArrayList<>();
        this.regionValues = new ArrayList<>();
        for (int i = 0; i < titleValues.size(); i++) {
            genreValues.add("Action");
            regionValues.add("Global");
        }
    }
    
    public RecyclerViewAdapter(MainActivity activity, ArrayList<Integer> imageValues, ArrayList<String> titleValues, ArrayList<String> versionValues, ArrayList<String> statusValues, ArrayList<String> packageValues, ArrayList<String> genreValues) {
        this.activity = activity;
        this.imageValues = imageValues;
        this.titleValues = titleValues;
        this.versionValues = versionValues;
        this.statusValues = statusValues;
        this.packageValues = packageValues;
        this.genreValues = genreValues;
        
        this.regionValues = new ArrayList<>();
        for (int i = 0; i < titleValues.size(); i++) {
            regionValues.add("Global");
        }
    }
    
    public RecyclerViewAdapter(MainActivity activity, ArrayList<Integer> imageValues, ArrayList<String> titleValues, ArrayList<String> versionValues, ArrayList<String> statusValues, ArrayList<String> packageValues, ArrayList<String> genreValues, ArrayList<String> regionValues) {
        this.activity = activity;
        this.imageValues = imageValues;
        this.titleValues = titleValues;
        this.versionValues = versionValues;
        this.statusValues = statusValues;
        this.packageValues = packageValues;
        this.genreValues = genreValues;
        this.regionValues = regionValues;
    }

    @Override
    public MyViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View listItem = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_games, parent, false);
        return new MyViewHolder(listItem);
    }

    @Override
    public void onBindViewHolder(MyViewHolder holder, int position) {
        holder.gameIcon.setImageResource(imageValues.get(position));
        holder.gameTitle.setText(titleValues.get(position));
        holder.gameVersion.setText(versionValues.get(position));
        holder.gameStatus.setText(statusValues.get(position));
        holder.gamePackage.setText(packageValues.get(position));
        
        if (holder.gameGenre != null && genreValues != null && position < genreValues.size()) {
            holder.gameGenre.setText(genreValues.get(position));
        }
        
        if (holder.gameRegion != null && regionValues != null && position < regionValues.size()) {
            holder.gameRegion.setText(regionValues.get(position));
        }

        doInitGames(holder.okBtn, holder.noBtn, packageValues.get(position));
        
        String packageName = packageValues.get(position);
        // FIXED: Using BlackBoxCore BEnvironment
        File dataDir = BEnvironment.getExternalDataDir(packageName);

        if (dataDir.exists()) {
            holder.gameSize.setText("Calculating...");
            new Thread(() -> {
                long size = getFolderSize(dataDir);
                String formatted = formatSize(size);
                activity.runOnUiThread(() -> {
                    holder.gameSize.setText(formatted);
                    setMemoryColor(holder.gameSize);
                });
            }).start();
        } else {
            holder.gameSize.setText("0 MB");
            setMemoryColor(holder.gameSize);
        }
        
        String region = regionValues.get(position);
        holder.gameRegion.setText(region);
        setRegionColor(holder.gameRegion, region);

        String status = statusValues.get(position);
        if (status.equals("Risk")) {
            holder.gameStatus.setTextColor(Color.RED);
        } else if (status.equals("Maintenance")) {
            holder.gameStatus.setTextColor(Color.YELLOW);
        } else if (status.equals("Coming Soon")) {
            holder.gameStatus.setTextColor(Color.GRAY);
        } else {
            holder.gameStatus.setTextColor(Color.GREEN);
        }

        holder.okBtn.setOnClickListener(v -> {
            if (status.equals("Maintenance") || status.equals("Coming Soon")) {
                activity.toast("App is currently under: " + status);
            } else {
                activity.doShowProgress(true);
                doInstallAndRun(holder, position);
            }
        });

        holder.noBtn.setOnClickListener(v -> {
            activity.doShowProgress(true);
            unInstallWithDellay(packageValues.get(position));
        });
        
    }

    @Override
    public int getItemCount() {
        return imageValues.size();
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        private ImageView gameIcon;
        private TextView gameTitle;
        private TextView gameVersion;
        private TextView gameStatus;
        private TextView gamePackage;
        private TextView gameSize;
        private TextView okBtn;
        private TextView noBtn;
        private TextView gameGenre;
        private TextView gameRegion;

        public MyViewHolder(View itemView) {
            super(itemView);
            gameIcon = itemView.findViewById(R.id.gameIcon);
            gameTitle = itemView.findViewById(R.id.gameTitle);
            gameVersion = itemView.findViewById(R.id.gameVersion);
            gameStatus = itemView.findViewById(R.id.gameStatus);
            gamePackage = itemView.findViewById(R.id.gamePackage);
            gameSize = itemView.findViewById(R.id.gameSize);
            okBtn = itemView.findViewById(R.id.okBtn);
            noBtn = itemView.findViewById(R.id.noBtn);
            
            View genreView = itemView.findViewById(R.id.gameGenre);
            if (genreView instanceof TextView) {
                gameGenre = (TextView) genreView;
            }
            
            View regionView = itemView.findViewById(R.id.gameRegion);
            if (regionView instanceof TextView) {
                gameRegion = (TextView) regionView;
            }
        }
    }

    public void doInitGames(TextView okBtn, TextView noBtn, String packageName) {
        activity.runOnUiThread(() -> {
            if (ApkEnv.getInstance().isInstalled(packageName)) {
                // Show delete button with animation
                if (noBtn.getVisibility() != View.VISIBLE) {
                    noBtn.setVisibility(View.VISIBLE);
                    noBtn.setAlpha(0f);
                    noBtn.setScaleX(0.8f);
                    noBtn.setScaleY(0.8f);
                    noBtn.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();
                }
                if (ApkEnv.getInstance().isRunning(packageName)) {
                    okBtn.setBackground(activity.getDrawable(R.drawable.button_uinstall));
                    okBtn.setText(R.string.stop_game);
                    okBtn.setTextColor(Color.WHITE);
                } else {
                    okBtn.setBackground(activity.getDrawable(R.drawable.button_play));
                    okBtn.setText(R.string.play_game);
                    okBtn.setTextColor(Color.BLACK);
                }
                noBtn.setBackground(activity.getDrawable(R.drawable.button_uinstall));
                noBtn.setEnabled(true);
                noBtn.setTextColor(Color.WHITE);
            } else {
                okBtn.setBackground(activity.getDrawable(R.drawable.button_install));
                okBtn.setText(R.string.install_game);
                okBtn.setTextColor(Color.WHITE);
                okBtn.setEnabled(true);
                // Hide delete button with animation
                if (noBtn.getVisibility() == View.VISIBLE) {
                    noBtn.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200).withEndAction(() -> noBtn.setVisibility(View.GONE)).start();
                }
                noBtn.setBackground(activity.getDrawable(R.drawable.button_coming));
                noBtn.setEnabled(false);
                noBtn.setTextColor(Color.GRAY);
            }
        });
    }

    private void doInstallAndRun2(MyViewHolder holder, int position) {
        if (activity == null) {
            return;
        }
        activity.CURRENT_PACKAGE = packageValues.get(position);
        
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (ApkEnv.getInstance().isInstalled(packageValues.get(position))) {
                    activity.doHideProgress();
                    if (ApkEnv.getInstance().isRunning(packageValues.get(position))) {
                        ApkEnv.getInstance().stopRunningApp(packageValues.get(position));
                        holder.okBtn.setBackground(activity.getDrawable(R.drawable.button_play));
                        holder.okBtn.setText(R.string.play_game);
                    } else {
                        activity.startPatcher();
                        activity.launchSplash(packageValues.get(position));
                        doInitGames(holder.okBtn, holder.noBtn, packageValues.get(position));
                    }
                } else {
                    FileHelper.tryInstallWithCopyObb(activity, activity.getProgresBar(), packageValues.get(position));
                }
            }
        });
    }
    
    private void doInstallAndRun(MyViewHolder holder, int position) {
        if (activity == null) {
            return;
        }
        activity.CURRENT_PACKAGE = packageValues.get(position);
        
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (ApkEnv.getInstance().isInstalled(packageValues.get(position))) {
                    activity.doHideProgress();
                    if (ApkEnv.getInstance().isRunning(packageValues.get(position))) {
                        ApkEnv.getInstance().stopRunningApp(packageValues.get(position));
                        holder.okBtn.setBackground(activity.getDrawable(R.drawable.button_play));
                        holder.okBtn.setText(R.string.play_game);
                    } else {
                        // ZIP download aur extract karne ke baad launch karein
                        downloadAndExtractBeforeLaunch(holder, position);
                    }
                } else {
                    FileHelper.tryInstallWithCopyObb(activity, activity.getProgresBar(), packageValues.get(position));
                }
            }
        });
    }

    private void downloadAndExtractBeforeLaunch(MyViewHolder holder, int position) {
        String packageName = packageValues.get(position);
        String zipUrl = getZipUrlForPackage(packageName);
        String zipFileName = getZipFileNameForPackage(packageName);
        activity.toast("Downloading files for " + titleValues.get(position) + "...");
        // DownloadZip class ka instance banayein
        DownloadZipAdapter downloadZip = new DownloadZipAdapter(activity);
        downloadZip.setZipFileName(zipFileName);
        // Download start karein with callback
        downloadZip.startDownload(zipUrl, new DownloadZipAdapter.DownloadCallback() {
            @Override
            public void onDownloadComplete(boolean success, String message) {
                activity.runOnUiThread(() -> {
                    activity.doHideProgress();
                    if (success) {
                        activity.startPatcher();
                        activity.launchSplash(packageValues.get(position));
                        doInitGames(holder.okBtn, holder.noBtn, packageValues.get(position));
                    } else {
                        activity.toast("Download failed: " + message);
                    }
                });
            }
            
            @Override
            public void onProgress(int progress) {
                // Optionally update progress in activity
                if (activity.getProgresBar() != null) {
                    activity.runOnUiThread(() -> {
                        activity.getProgresBar().setProgress(progress);
                    });
                }
            }
        });
    }
    
    public String getZipUrlForPackage(String packageName) {
        // Check which package it is and return appropriate URL
        if (packageName.equals(GAME_LIST_PKG[0])) {
            return "https://github.com/Flakervip143/Sock/releases/download/Cool/assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[1])) {
            return "https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[2])) {
            return "https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[3])) {
            return "https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[4])) {
            return "https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/assets.zip";
        } else {
            return "https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/assets.zip";
        }
    }

    public String getZipFileNameForPackage(String packageName) {
        if (packageName.equals(GAME_LIST_PKG[0])) {
            return "assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[1])) {
            return "assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[2])) {
            return "assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[3])) {
            return "assets.zip";
        } else if (packageName.equals(GAME_LIST_PKG[4])) {
            return "assets.zip";
        } else {
            return "assets.zip";
        }
    }

    private void unInstallWithDellay(String packageName) {
        UiKit.defer().when(() -> {
            long time = System.currentTimeMillis();
            ApkEnv.getInstance().unInstallApp(packageName);
            time = System.currentTimeMillis() - time;
            long delta = 500L - time;
            if (delta > 0) {
                UiKit.sleep(delta);
            }
        }).done((res) -> {
            activity.doInitRecycler();
            activity.doHideProgress();
            activity.toast(packageName + " was successfully uninstalled.");
        });
    }
    
    public void updateGameStatus(int position, String newStatus) {
        if (position >= 0 && position < statusValues.size()) {
            statusValues.set(position, newStatus);
            notifyItemChanged(position);
        }
    }
    
    public void updateGameInstallation(int position, boolean installed, boolean running) {
        if (position >= 0 && position < packageValues.size()) {
            notifyItemChanged(position);
        }
    }
    
    private long getFolderSize(File dir) {
        long size = 0;
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getFolderSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }
    
    private String formatSize(long size) {
        double kb = size / 1024.0;
        double mb = kb / 1024.0;
        double gb = mb / 1024.0;
        if (gb >= 1) {
            return String.format("%.2f GB", gb);
        } else if (mb >= 1) {
            return String.format("%.2f MB", mb);
        } else {
            return String.format("%.2f KB", kb);
        }
    }
    
    private void setRegionColor(TextView regionView, String region) {
        int isStroke = 2;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(7);
        if (region.equalsIgnoreCase("INDIA")) {
            drawable.setStroke(isStroke, Color.parseColor("#4CAF50"));
            drawable.setColor(Color.parseColor("#224CAF50"));
        } else if (region.equalsIgnoreCase("GLOBAL")) {
            drawable.setStroke(isStroke, Color.parseColor("#03A9F4"));
            drawable.setColor(Color.parseColor("#2203A9F4"));
        } else if (region.equalsIgnoreCase("KOREA")) {
            drawable.setStroke(isStroke, Color.parseColor("#E91E63"));
            drawable.setColor(Color.parseColor("#22E91E63"));
        } else if (region.equalsIgnoreCase("TAIWAN")) {
            drawable.setStroke(isStroke, Color.parseColor("#FFC107"));
            drawable.setColor(Color.parseColor("#22FFC107"));
        } else if (region.equalsIgnoreCase("VIETNAM")) {
            drawable.setStroke(isStroke, Color.parseColor("#FF5722"));
            drawable.setColor(Color.parseColor("#22FF5722"));
        } else {
            drawable.setStroke(isStroke, Color.GRAY);
            drawable.setColor(Color.parseColor("#22000000"));
        }
        regionView.setBackground(drawable);
        regionView.setTextColor(Color.WHITE);
    }
    
    private void setMemoryColor(TextView sizeView) {
        String sizeText = sizeView.getText().toString();
        if (sizeText.contains("GB")) {
            try {
                double gb = Double.parseDouble(sizeText.replace("GB","").trim());
                if (gb <= 1) {
                    sizeView.setTextColor(Color.parseColor("#66BB6A")); // Light Green
                } else if (gb <= 3) {
                    sizeView.setTextColor(Color.parseColor("#00E676")); // Green
                } else if (gb <= 5) {
                    sizeView.setTextColor(Color.parseColor("#FFC107")); // Yellow
                } else if (gb <= 6) {
                    sizeView.setTextColor(Color.parseColor("#FF9800")); // Orange
                } else if (gb <= 8) {
                    sizeView.setTextColor(Color.parseColor("#FF7043")); // Light Red
                } else if (gb <= 10) {
                    sizeView.setTextColor(Color.parseColor("#FF5252")); // Red
                } else {
                    sizeView.setTextColor(Color.parseColor("#D50000")); // Dark Red
                }
            } catch (Exception e) {
                sizeView.setTextColor(Color.RED);
            }
        } else if (sizeText.contains("MB")) {
            sizeView.setTextColor(Color.parseColor("#66BB6A"));
        } else {
            sizeView.setTextColor(Color.RED);
        }
    }
}