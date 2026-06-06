package com.hoops.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists PlayerData to SharedPreferences as JSON. */
public class SaveManager {
    private static final String PREFS = "hoops_save";
    private static final String KEY = "data";
    private final SharedPreferences prefs;

    public SaveManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public PlayerData load() {
        return PlayerData.fromJson(prefs.getString(KEY, ""));
    }

    public void save(PlayerData pd) {
        prefs.edit().putString(KEY, pd.toJson().toString()).apply();
    }

    public void wipe() {
        prefs.edit().clear().apply();
    }
}
