package com.pubgm.floating;

import android.content.Context;
import com.pubgm.utils.FPrefs;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ItmeManager {

    private static volatile ItmeManager instance;

    private final FPrefs prefs;
    private final Context context;
    private final Map<String, String[]> categories = new HashMap<>();

    private ItmeManager(Context ctx) {
        context = ctx.getApplicationContext();
        prefs = FPrefs.with(context, "esp_items");
        loadItemsFromJson();
    }

    public static ItmeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ItmeManager.class) {
                if (instance == null) {
                    instance = new ItmeManager(context);
                }
            }
        }
        return instance;
    }
    
    private void loadItemsFromJson() {
        try {
            InputStream is = context.getAssets().open("items.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;

            while ((n = is.read(buffer)) != -1) {
                bos.write(buffer, 0, n);
            }
            is.close();

            String jsonString = bos.toString(StandardCharsets.UTF_8.name());
            JSONObject json = new JSONObject(jsonString);
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONArray arr = json.getJSONArray(key);
                categories.put(key, jsonArrayToStringArray(arr));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] jsonArrayToStringArray(JSONArray arr) {
        try {
            String[] result = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                result[i] = arr.getString(i);
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[0];
    }
    
    public void setCategory(String category, boolean enable) {
        String[] items = categories.get(category);
        if (items == null) return;

        for (String item : items) {
            prefs.writeBoolean(item, enable);
        }
    }
    
    public void vehicles(boolean enable) { 
		setCategory("vehicles", enable); 
	}
    public void weapons(boolean enable) { 
		setCategory("weapons", enable); 
	}
    public void scopes(boolean enable) { 
		setCategory("scopes", enable); 
	}
    public void ammo(boolean enable) {
		setCategory("ammo", enable);
	}
    public void attachments(boolean enable) { 
		setCategory("attachments", enable); 
	}
    public void equipment(boolean enable) { 
		setCategory("equipment", enable); 
	}
    public void consumables(boolean enable) { 
		setCategory("consumables", enable);
	}
    public void throwables(boolean enable) {
		setCategory("throwables", enable);
	}
    public void special(boolean enable) { 
		setCategory("special", enable); 
	}
    
    public void all(boolean enable) {
        for (String category : categories.keySet()) {
            setCategory(category, enable);
        }
    }

    public void clear() {
        prefs.clear();
    }

    public void reset() {
        all(false);
    }

    public boolean isEnabled(String itemName) {
        return prefs.readBoolean(itemName, false);
    }
    
    public Map<String, String[]> getCategories() {
        return categories;
    }
}