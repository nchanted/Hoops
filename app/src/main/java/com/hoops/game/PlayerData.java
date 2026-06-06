package com.hoops.game;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/** All persistent player progress. */
public class PlayerData {

    // Skill indices
    public static final int AIM = 0, POWER = 1, VALUE = 2, SLOWMO = 3, MAGNET = 4, COMBO = 5;
    public static final int SKILL_COUNT = 6;
    public static final int SKILL_MAX = 5;

    public int coins = 0;
    public long xp = 0;
    public int playerLevel = 1;
    public int skillPoints = 0;
    public int[] skills = new int[SKILL_COUNT];

    public int stage = 1;
    public int bestStreak = 0;
    public long totalMade = 0;

    public String equippedOutfit = "rookie";
    public String equippedBall = "classic";
    public Set<String> ownedOutfits = new HashSet<>();
    public Set<String> ownedBalls = new HashSet<>();

    public PlayerData() {
        ownedOutfits.add("rookie");
        ownedBalls.add("classic");
    }

    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("coins", coins);
            o.put("xp", xp);
            o.put("playerLevel", playerLevel);
            o.put("skillPoints", skillPoints);
            JSONArray sk = new JSONArray();
            for (int v : skills) sk.put(v);
            o.put("skills", sk);
            o.put("stage", stage);
            o.put("bestStreak", bestStreak);
            o.put("totalMade", totalMade);
            o.put("equippedOutfit", equippedOutfit);
            o.put("equippedBall", equippedBall);
            o.put("ownedOutfits", new JSONArray(ownedOutfits));
            o.put("ownedBalls", new JSONArray(ownedBalls));
            return o;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static PlayerData fromJson(String s) {
        PlayerData pd = new PlayerData();
        if (s == null || s.isEmpty()) return pd;
        try {
            JSONObject o = new JSONObject(s);
            pd.coins = o.optInt("coins", 0);
            pd.xp = o.optLong("xp", 0);
            pd.playerLevel = o.optInt("playerLevel", 1);
            pd.skillPoints = o.optInt("skillPoints", 0);
            JSONArray sk = o.optJSONArray("skills");
            if (sk != null) {
                for (int i = 0; i < SKILL_COUNT && i < sk.length(); i++) pd.skills[i] = sk.optInt(i, 0);
            }
            pd.stage = Math.max(1, o.optInt("stage", 1));
            pd.bestStreak = o.optInt("bestStreak", 0);
            pd.totalMade = o.optLong("totalMade", 0);
            pd.equippedOutfit = o.optString("equippedOutfit", "rookie");
            pd.equippedBall = o.optString("equippedBall", "classic");
            pd.ownedOutfits = toSet(o.optJSONArray("ownedOutfits"), "rookie");
            pd.ownedBalls = toSet(o.optJSONArray("ownedBalls"), "classic");
        } catch (Exception ignored) {
        }
        return pd;
    }

    private static Set<String> toSet(JSONArray a, String mustHave) {
        Set<String> set = new HashSet<>();
        set.add(mustHave);
        if (a != null) {
            for (int i = 0; i < a.length(); i++) set.add(a.optString(i));
        }
        return set;
    }
}
