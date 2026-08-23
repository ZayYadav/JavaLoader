package com.pubgm.license;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class EliteResetPage {

    public interface Callback {
        void onResetDone(String key);
        void onClose();
        void onError(String message);
    }

    private Context context;
    private Dialog dialog;
    private Callback callback;
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final String RESET_API =
            "https://blackbox360.business/ResetLicence.php?action=reset&userkey=";

    public EliteResetPage(Context ctx, Callback cb) {
        context = ctx;
        callback = cb;
    }

    public void open(String key) {

        dialog = new Dialog(context);
        dialog.setCancelable(true);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(50,50,50,50);
        root.setBackgroundColor(Color.parseColor("#2A2A3A"));

        TextView title = new TextView(context);
        title.setText("Reset License");
        title.setTextSize(22);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        EditText input = new EditText(context);
        input.setHint("Enter your key");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setBackgroundColor(Color.parseColor("#1AFFFFFF"));
        input.setPadding(25,18,25,18);
        input.setText(key);
        root.addView(input);

        ProgressBar progress = new ProgressBar(context);
        progress.setVisibility(View.GONE);
        root.addView(progress);

        Button reset = new Button(context);
        reset.setText("RESET");
        reset.setBackgroundColor(Color.parseColor("#f44336"));
        reset.setTextColor(Color.WHITE);
        root.addView(reset);

        Button close = new Button(context);
        close.setText("CLOSE");
        root.addView(close);

        dialog.setContentView(root);
        dialog.show();

        reset.setOnClickListener(v -> {

            String k = input.getText().toString().trim();
            if (TextUtils.isEmpty(k)){
                showPremiumToast("Enter key", false);
                return;
            }

            progress.setVisibility(View.VISIBLE);
            resetLicense(k,progress);
        });

        close.setOnClickListener(v -> {
            dialog.dismiss();
            if(callback!=null) callback.onClose();
        });
    }

    private void resetLicense(String key, ProgressBar progress){

        new Thread(() -> {
            try {

                String url = RESET_API + URLEncoder.encode(key,"UTF-8");
                HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);

                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line=r.readLine())!=null) sb.append(line);

                JSONObject j = new JSONObject(sb.toString());
                boolean ok = j.optBoolean("success");

                handler.post(() -> {
                    progress.setVisibility(View.GONE);

                    if(ok){
                        showPremiumToast("Reset success", true);
                        if(callback!=null) callback.onResetDone(key);
                        dialog.dismiss();
                    }else{
                        showPremiumToast("Reset failed", false);
                        if(callback!=null) callback.onError("Reset failed");
                    }
                });

            } catch (Exception e){
                handler.post(() -> {
                    progress.setVisibility(View.GONE);
                    showPremiumToast("Network error", false);
                    if(callback!=null) callback.onError("Network error");
                });
            }
        }).start();
    }

    private void showPremiumToast(String msg, boolean success) {

        LinearLayout layout = new LinearLayout(context);
        layout.setPadding(40,25,40,25);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(40);
        bg.setColor(success ? Color.parseColor("#2E7D32") : Color.parseColor("#C62828"));

        layout.setBackground(bg);

        TextView tv = new TextView(context);
        tv.setText(msg);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        layout.addView(tv);

        Toast t = new Toast(context);
        t.setView(layout);
        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,0,150);
        t.setDuration(Toast.LENGTH_SHORT);
        t.show();
    }
}