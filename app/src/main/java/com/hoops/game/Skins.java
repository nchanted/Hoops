package com.hoops.game;

import java.util.ArrayList;
import java.util.List;

/** Cosmetic catalog: character outfits and ball skins. Colors are ARGB ints. */
public class Skins {

    public static class Outfit {
        public final String id, name;
        public final int cost;
        public final int jersey, shorts, skin, accent;
        public Outfit(String id, String name, int cost, int jersey, int shorts, int skin, int accent) {
            this.id = id; this.name = name; this.cost = cost;
            this.jersey = jersey; this.shorts = shorts; this.skin = skin; this.accent = accent;
        }
    }

    public static class BallSkin {
        public final String id, name;
        public final int cost;
        public final int main, line;
        public BallSkin(String id, String name, int cost, int main, int line) {
            this.id = id; this.name = name; this.cost = cost; this.main = main; this.line = line;
        }
    }

    public static final List<Outfit> OUTFITS = new ArrayList<>();
    public static final List<BallSkin> BALLS = new ArrayList<>();

    static {
        OUTFITS.add(new Outfit("rookie",  "Rookie",      0,    0xFFE53935, 0xFF1565C0, 0xFFE0AC69, 0xFFFFFFFF));
        OUTFITS.add(new Outfit("street",  "Streetball",  150,  0xFF212121, 0xFF424242, 0xFF8D5524, 0xFFFFC107));
        OUTFITS.add(new Outfit("retro",   "Retro 80s",   300,  0xFF8E24AA, 0xFF00ACC1, 0xFFE0AC69, 0xFFFFEB3B));
        OUTFITS.add(new Outfit("forest",  "Forest",      500,  0xFF2E7D32, 0xFF1B5E20, 0xFFC68642, 0xFFA5D6A7));
        OUTFITS.add(new Outfit("ocean",   "Ocean",       800,  0xFF0277BD, 0xFF01579B, 0xFFE0AC69, 0xFF80DEEA));
        OUTFITS.add(new Outfit("flame",   "Flame",       1200, 0xFFD84315, 0xFFBF360C, 0xFF8D5524, 0xFFFFD54F));
        OUTFITS.add(new Outfit("ice",     "Ice",         1700, 0xFFB3E5FC, 0xFF4FC3F7, 0xFFC68642, 0xFFFFFFFF));
        OUTFITS.add(new Outfit("royal",   "Royal",       2300, 0xFF311B92, 0xFF512DA8, 0xFFE0AC69, 0xFFFFD700));
        OUTFITS.add(new Outfit("neon",    "Neon",        3000, 0xFF00E676, 0xFFD500F9, 0xFF8D5524, 0xFF18FFFF));
        OUTFITS.add(new Outfit("shadow",  "Shadow",      4000, 0xFF0A0A0A, 0xFF1A1A1A, 0xFF5D4037, 0xFF7C4DFF));
        OUTFITS.add(new Outfit("gold",    "Gold MVP",    5500, 0xFFFFD700, 0xFFFFA000, 0xFFE0AC69, 0xFFFFFFFF));
        OUTFITS.add(new Outfit("legend",  "Legend",      8000, 0xFFFFFFFF, 0xFFFFD700, 0xFFC68642, 0xFFFF1744));

        BALLS.add(new BallSkin("classic", "Classic",     0,    0xFFE8741E, 0xFF3E2723));
        BALLS.add(new BallSkin("blue",    "Blue Stripe", 250,  0xFF1E88E5, 0xFF0D47A1));
        BALLS.add(new BallSkin("neon",    "Neon",        600,  0xFF00E676, 0xFF004D40));
        BALLS.add(new BallSkin("marble",  "Marble",      1200, 0xFFECEFF1, 0xFF607D8B));
        BALLS.add(new BallSkin("gold",    "Golden",      2500, 0xFFFFD700, 0xFF8D6E00));
        BALLS.add(new BallSkin("galaxy",  "Galaxy",      6000, 0xFF5E35B1, 0xFF18FFFF));
    }

    public static Outfit outfit(String id) {
        for (Outfit o : OUTFITS) if (o.id.equals(id)) return o;
        return OUTFITS.get(0);
    }

    public static BallSkin ball(String id) {
        for (BallSkin b : BALLS) if (b.id.equals(id)) return b;
        return BALLS.get(0);
    }
}
