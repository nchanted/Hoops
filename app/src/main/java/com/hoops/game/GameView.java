package com.hoops.game;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    // ---- Screen states ----
    private static final int MENU = 0, PLAY = 1, SHOP = 2, UPGRADES = 3;
    private int screen = MENU;

    private boolean paused = false;
    private boolean levelComplete = false;
    private boolean resetConfirm = false;
    private boolean gameComplete = false; // reached final stage at least once

    private static final int FINAL_STAGE = 60;

    // ---- Core objects ----
    private final SurfaceHolder holder;
    private GameThread thread;
    private final SaveManager saveManager;
    private PlayerData pd;

    // ---- Dimensions ----
    private int W, H;
    private boolean layoutReady = false;

    // ---- Ball / physics ----
    private float ballX, ballY, ballVX, ballVY, ballR;
    private float homeX, homeY, groundY;
    private float gravity, POWER, maxSpeed;
    private boolean inFlight = false, scoredThisShot = false;
    private float flightTime = 0f;
    private float slowTimer = 0f;

    // ---- Hoop ----
    private float hoopBaseX, hoopBaseY, ow, rimR, boardH;
    private float hoopCenterX, hoopY;
    private float amp, hoopSpeed, bobAmp, bobSpeed, windAccel;
    private float tAccum = 0f;

    // ---- Stage / scoring ----
    private int makesThisStage = 0;
    private int requiredThisStage = 4;
    private int combo = 0;

    // ---- Input ----
    private boolean dragging = false;
    private float dragStartX, dragStartY, dragCurX, dragCurY;
    private float touchDownX, touchDownY, scrollStart;
    private boolean touchMovedFar = false;

    // ---- Lists / scrolling ----
    private int shopTab = 0; // 0 = outfits, 1 = balls
    private float scrollY = 0f;

    // ---- Floating message ----
    private String msg = "";
    private float msgTimer = 0f;
    private int msgColor = Color.WHITE;

    // ---- Paints ----
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint preview = new Paint(Paint.ANTI_ALIAS_FLAG);

    // ---- Button rects ----
    private final RectF bPlay = new RectF(), bShop = new RectF(), bUpg = new RectF(), bReset = new RectF();
    private final RectF bPause = new RectF();
    private final RectF bResume = new RectF(), bToMenuP = new RectF();
    private final RectF bContinue = new RectF(), bToMenuC = new RectF();
    private final RectF bBackShop = new RectF(), bTabOut = new RectF(), bTabBall = new RectF();
    private final RectF bBackUpg = new RectF();
    private final RectF bResetYes = new RectF(), bResetNo = new RectF();

    private float listTop, rowH;

    public GameView(Context ctx) {
        super(ctx);
        holder = getHolder();
        holder.addCallback(this);
        saveManager = new SaveManager(ctx);
        pd = saveManager.load();
        setFocusable(true);

        stroke.setStyle(Paint.Style.STROKE);
        text.setColor(Color.WHITE);
        text.setTextAlign(Paint.Align.CENTER);
        preview.setStyle(Paint.Style.FILL);
        preview.setColor(0x88FFFFFF);
    }

    // =================== Lifecycle ===================

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        thread = new GameThread(holder, this);
        thread.setRunning(true);
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
        W = width;
        H = height;
        computeDimensions();
        layoutButtons();
        applyDifficulty(pd.stage);
        resetBall();
        layoutReady = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        save();
        boolean retry = true;
        if (thread != null) {
            thread.setRunning(false);
            while (retry) {
                try {
                    thread.join();
                    retry = false;
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    public void onActivityPause() {
        save();
    }

    private void computeDimensions() {
        groundY = H * 0.88f;
        ballR = W * 0.045f;
        homeX = W * 0.5f;
        homeY = groundY - ballR;
        gravity = H * 1.5f;
        POWER = 7.0f;
        maxSpeed = H * 2.4f;
        rimR = W * 0.013f;
        boardH = H * 0.10f;
    }

    // =================== Difficulty ===================

    private void applyDifficulty(int stage) {
        requiredThisStage = 4 + (int) (stage * 0.6f);
        ow = Math.max(W * 0.12f, W * 0.22f - stage * (W * 0.0016f));
        amp = Math.min(W * 0.30f, (stage <= 4 ? 0 : (stage - 4)) * (W * 0.012f));
        hoopSpeed = Math.min(2.2f, 0.5f + stage * 0.03f);
        bobAmp = stage <= 15 ? 0 : Math.min(H * 0.06f, (stage - 15) * (H * 0.004f));
        bobSpeed = 1.2f;
        windAccel = stage <= 25 ? 0 : ((stage % 2 == 0 ? 1 : -1) * Math.min(W * 0.6f, (stage - 25) * (W * 0.03f)));
        hoopBaseX = W * 0.5f;
        hoopBaseY = H * (0.32f - Math.min(0.14f, stage * 0.0022f));
        hoopCenterX = hoopBaseX;
        hoopY = hoopBaseY;
    }

    // =================== Derived skill effects ===================

    private float coinMult() { return 1f + 0.20f * pd.skills[PlayerData.VALUE]; }
    private float magnetStrength() { return pd.skills[PlayerData.MAGNET] * 1.5f; }
    private int comboMax() { return 3 + pd.skills[PlayerData.COMBO] * 2; }
    private int previewDots() { return 14 + pd.skills[PlayerData.AIM] * 12; }
    private float slowDur() { return pd.skills[PlayerData.SLOWMO] * 0.18f; }
    private float powerAssist() { return pd.skills[PlayerData.POWER] * 0.012f; }

    // =================== Update ===================

    public void update(float dt) {
        if (msgTimer > 0) msgTimer -= dt;
        if (!layoutReady) return;
        if (screen != PLAY || paused || levelComplete || resetConfirm) return;

        tAccum += dt;
        hoopCenterX = hoopBaseX + amp * (float) Math.sin(tAccum * hoopSpeed);
        hoopY = hoopBaseY + bobAmp * (float) Math.sin(tAccum * bobSpeed);

        if (inFlight) {
            float eff = dt;
            if (slowTimer > 0) {
                slowTimer -= dt;
                eff = dt * 0.45f;
            }
            int steps = 5;
            float h = eff / steps;
            for (int i = 0; i < steps && inFlight; i++) {
                stepPhysics(h);
            }
        }
    }

    private void stepPhysics(float h) {
        float prevY = ballY;

        ballVY += gravity * h;
        if (windAccel != 0) ballVX += windAccel * h;

        // Rim magnet (helps near-misses)
        float ms = magnetStrength();
        if (ms > 0 && ballVY > 0
                && Math.abs(ballY - hoopY) < ballR * 6f
                && Math.abs(ballX - hoopCenterX) < ow * 1.2f) {
            ballVX += (hoopCenterX - ballX) * ms * h;
        }

        ballX += ballVX * h;
        ballY += ballVY * h;

        collideRimEnd(hoopCenterX - ow / 2f, hoopY);
        collideRimEnd(hoopCenterX + ow / 2f, hoopY);
        collideBackboard();

        // Score: crossing the rim plane downward inside the opening
        if (!scoredThisShot && prevY < hoopY && ballY >= hoopY && ballVY > 0) {
            float halfInner = ow / 2f - ballR * 0.15f;
            if (Math.abs(ballX - hoopCenterX) < halfInner) {
                onScore();
            }
        }

        flightTime += h;
        if (ballY > groundY || ballX < -2 * ballR || ballX > W + 2 * ballR
                || ballY > H + 2 * ballR || flightTime > 6f) {
            onTerminal();
        }
    }

    private void collideRimEnd(float ex, float ey) {
        float dx = ballX - ex, dy = ballY - ey;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float min = ballR + rimR;
        if (dist > 0 && dist < min) {
            float nx = dx / dist, ny = dy / dist;
            ballX = ex + nx * min;
            ballY = ey + ny * min;
            float vn = ballVX * nx + ballVY * ny;
            float e = 0.55f;
            ballVX -= (1 + e) * vn * nx;
            ballVY -= (1 + e) * vn * ny;
        }
    }

    private void collideBackboard() {
        float boardX = hoopCenterX + ow / 2f + rimR * 2f;
        float top = hoopY - boardH;
        if (ballY > top && ballY < hoopY + rimR
                && ballX + ballR > boardX && ballX - ballR < boardX + rimR * 2f
                && ballVX > 0) {
            ballX = boardX - ballR;
            ballVX = -ballVX * 0.6f;
        }
    }

    private void onScore() {
        scoredThisShot = true;
        boolean swish = Math.abs(ballX - hoopCenterX) < ow * 0.18f;
        combo++;
        if (combo > comboMax()) combo = comboMax();
        float comboMult = 1f + 0.25f * (combo - 1);

        int coinsGain = Math.round(5 * coinMult() * comboMult * (swish ? 2f : 1f));
        long xpGain = Math.round(10 * comboMult * (swish ? 1.5f : 1f));

        pd.coins += coinsGain;
        pd.totalMade++;
        if (combo > pd.bestStreak) pd.bestStreak = combo;
        addXp(xpGain);

        makesThisStage++;
        showMsg((swish ? "SWISH!  +" : "SCORE  +") + coinsGain, swish ? 0xFFFFD54F : 0xFF81C784);

        if (makesThisStage >= requiredThisStage) {
            onStageComplete();
        }
        save();
    }

    private void onTerminal() {
        if (!scoredThisShot) combo = 0;
        resetBall();
    }

    private void resetBall() {
        ballX = homeX;
        ballY = homeY;
        ballVX = 0;
        ballVY = 0;
        inFlight = false;
        scoredThisShot = false;
        flightTime = 0;
        slowTimer = 0;
    }

    private void addXp(long g) {
        pd.xp += g;
        long need = needXp(pd.playerLevel);
        while (pd.xp >= need) {
            pd.xp -= need;
            pd.playerLevel++;
            pd.skillPoints++;
            need = needXp(pd.playerLevel);
            showMsg("LEVEL UP!  +1 skill point", 0xFF64B5F6);
        }
    }

    private long needXp(int level) {
        return 100 + (long) level * 40;
    }

    private void onStageComplete() {
        int bonus = 25 + pd.stage * 5;
        pd.coins += bonus;
        pd.stage++;
        if (pd.stage >= FINAL_STAGE && !gameComplete) {
            gameComplete = true;
        }
        applyDifficulty(pd.stage);
        makesThisStage = 0;
        combo = 0;
        levelComplete = true;
        resetBall();
        save();
    }

    private void startStage() {
        applyDifficulty(pd.stage);
        makesThisStage = 0;
        combo = 0;
        paused = false;
        levelComplete = false;
        resetBall();
    }

    private void launch(float vx, float vy) {
        float sp = (float) Math.hypot(vx, vy);
        if (sp > maxSpeed) {
            vx *= maxSpeed / sp;
            vy *= maxSpeed / sp;
        }
        ballX = homeX;
        ballY = homeY;
        ballVX = vx + (hoopCenterX - homeX) * powerAssist();
        ballVY = vy;
        inFlight = true;
        scoredThisShot = false;
        flightTime = 0;
        slowTimer = slowDur();
    }

    private void showMsg(String m, int color) {
        msg = m;
        msgColor = color;
        msgTimer = 1.4f;
    }

    private void save() {
        if (saveManager != null && pd != null) saveManager.save(pd);
    }

    // =================== Input ===================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!layoutReady) return true;
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();

        synchronized (holder) {
            switch (screen) {
                case MENU:
                    if (action == MotionEvent.ACTION_DOWN) handleMenuTap(x, y);
                    break;
                case PLAY:
                    handlePlayTouch(action, x, y);
                    break;
                case SHOP:
                case UPGRADES:
                    handleListTouch(action, x, y);
                    break;
            }
        }
        return true;
    }

    private void handleMenuTap(float x, float y) {
        if (resetConfirm) {
            if (bResetYes.contains(x, y)) {
                saveManager.wipe();
                pd = new PlayerData();
                gameComplete = false;
                applyDifficulty(pd.stage);
                resetConfirm = false;
                showMsg("Progress reset", 0xFFEF5350);
            } else if (bResetNo.contains(x, y)) {
                resetConfirm = false;
            }
            return;
        }
        if (bPlay.contains(x, y)) {
            screen = PLAY;
            startStage();
        } else if (bShop.contains(x, y)) {
            screen = SHOP;
            shopTab = 0;
            scrollY = 0;
        } else if (bUpg.contains(x, y)) {
            screen = UPGRADES;
            scrollY = 0;
        } else if (bReset.contains(x, y)) {
            resetConfirm = true;
        }
    }

    private void handlePlayTouch(int action, float x, float y) {
        if (levelComplete) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (bContinue.contains(x, y)) {
                    startStage();
                } else if (bToMenuC.contains(x, y)) {
                    screen = MENU;
                    save();
                }
            }
            return;
        }
        if (paused) {
            if (action == MotionEvent.ACTION_DOWN) {
                if (bResume.contains(x, y)) paused = false;
                else if (bToMenuP.contains(x, y)) {
                    screen = MENU;
                    save();
                }
            }
            return;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (bPause.contains(x, y)) {
                    paused = true;
                    return;
                }
                if (!inFlight) {
                    dragging = true;
                    dragStartX = x;
                    dragStartY = y;
                    dragCurX = x;
                    dragCurY = y;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    dragCurX = x;
                    dragCurY = y;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    dragCurX = x;
                    dragCurY = y;
                    float vx = (dragCurX - dragStartX) * POWER;
                    float vy = (dragCurY - dragStartY) * POWER;
                    float len = (float) Math.hypot(dragCurX - dragStartX, dragCurY - dragStartY);
                    if (vy < 0 && len > W * 0.05f) {
                        launch(vx, vy);
                    }
                }
                break;
        }
    }

    private void handleListTouch(int action, float x, float y) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = x;
                touchDownY = y;
                scrollStart = scrollY;
                touchMovedFar = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(y - touchDownY) > W * 0.02f) touchMovedFar = true;
                scrollY = scrollStart - (y - touchDownY);
                clampScroll();
                break;
            case MotionEvent.ACTION_UP:
                if (!touchMovedFar) {
                    if (screen == SHOP) handleShopTap(x, y);
                    else handleUpgradeTap(x, y);
                }
                break;
        }
    }

    private void clampScroll() {
        int count = screen == SHOP ? (shopTab == 0 ? Skins.OUTFITS.size() : Skins.BALLS.size())
                : PlayerData.SKILL_COUNT;
        float content = count * rowH;
        float visible = H - listTop - H * 0.04f;
        float max = Math.max(0, content - visible);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > max) scrollY = max;
    }

    private void handleShopTap(float x, float y) {
        if (bBackShop.contains(x, y)) {
            screen = MENU;
            scrollY = 0;
            return;
        }
        if (bTabOut.contains(x, y)) {
            shopTab = 0;
            scrollY = 0;
            return;
        }
        if (bTabBall.contains(x, y)) {
            shopTab = 1;
            scrollY = 0;
            return;
        }
        int count = shopTab == 0 ? Skins.OUTFITS.size() : Skins.BALLS.size();
        for (int i = 0; i < count; i++) {
            float top = listTop - scrollY + i * rowH;
            RectF btn = new RectF(W * 0.68f, top + rowH * 0.22f, W * 0.95f, top + rowH * 0.78f);
            if (btn.contains(x, y)) {
                if (shopTab == 0) buyOrEquipOutfit(i);
                else buyOrEquipBall(i);
                return;
            }
        }
    }

    private void buyOrEquipOutfit(int i) {
        Skins.Outfit o = Skins.OUTFITS.get(i);
        if (pd.ownedOutfits.contains(o.id)) {
            pd.equippedOutfit = o.id;
            showMsg("Equipped " + o.name, 0xFF81C784);
        } else if (pd.coins >= o.cost) {
            pd.coins -= o.cost;
            pd.ownedOutfits.add(o.id);
            pd.equippedOutfit = o.id;
            showMsg("Bought " + o.name + "!", 0xFFFFD54F);
        } else {
            showMsg("Not enough coins", 0xFFEF5350);
        }
        save();
    }

    private void buyOrEquipBall(int i) {
        Skins.BallSkin b = Skins.BALLS.get(i);
        if (pd.ownedBalls.contains(b.id)) {
            pd.equippedBall = b.id;
            showMsg("Equipped " + b.name, 0xFF81C784);
        } else if (pd.coins >= b.cost) {
            pd.coins -= b.cost;
            pd.ownedBalls.add(b.id);
            pd.equippedBall = b.id;
            showMsg("Bought " + b.name + "!", 0xFFFFD54F);
        } else {
            showMsg("Not enough coins", 0xFFEF5350);
        }
        save();
    }

    private void handleUpgradeTap(float x, float y) {
        if (bBackUpg.contains(x, y)) {
            screen = MENU;
            scrollY = 0;
            return;
        }
        for (int i = 0; i < PlayerData.SKILL_COUNT; i++) {
            float top = listTop - scrollY + i * rowH;
            RectF btn = new RectF(W * 0.70f, top + rowH * 0.22f, W * 0.95f, top + rowH * 0.78f);
            if (btn.contains(x, y)) {
                upgradeSkill(i);
                return;
            }
        }
    }

    private void upgradeSkill(int i) {
        if (pd.skills[i] >= PlayerData.SKILL_MAX) {
            showMsg("Maxed out", 0xFFBBBBBB);
            return;
        }
        int cost = pd.skills[i] + 1;
        if (pd.skillPoints >= cost) {
            pd.skillPoints -= cost;
            pd.skills[i]++;
            showMsg("Upgraded!", 0xFF81C784);
            save();
        } else {
            showMsg("Need " + cost + " skill points", 0xFFEF5350);
        }
    }

    public boolean onBack() {
        if (screen == MENU) {
            if (resetConfirm) {
                resetConfirm = false;
                return true;
            }
            return false;
        }
        if (screen == PLAY && !paused && !levelComplete) {
            paused = true;
        } else {
            screen = MENU;
            save();
        }
        return true;
    }

    // =================== Rendering ===================

    public void render(Canvas c) {
        if (c == null) return;
        if (!layoutReady) {
            c.drawColor(0xFF15301B);
            return;
        }
        switch (screen) {
            case MENU: drawMenu(c); break;
            case PLAY: drawPlay(c); break;
            case SHOP: drawShop(c); break;
            case UPGRADES: drawUpgrades(c); break;
        }
        drawMessage(c);
    }

    private void drawBackground(Canvas c) {
        fill.setColor(0xFF1A4D2E);
        c.drawRect(0, 0, W, H, fill);
        fill.setColor(0xFF14361F);
        c.drawRect(0, groundY, W, H, fill);
        stroke.setColor(0x33FFFFFF);
        stroke.setStrokeWidth(W * 0.006f);
        c.drawLine(0, groundY, W, groundY, stroke);
    }

    private void drawMenu(Canvas c) {
        drawBackground(c);

        // animated decorative ball
        float by = groundY - ballR - Math.abs((float) Math.sin(tAccumMenu())) * H * 0.12f;
        drawBall(c, W * 0.5f, by, ballR * 1.2f);

        text.setColor(0xFFFFFFFF);
        text.setTextSize(W * 0.18f);
        text.setFakeBoldText(true);
        c.drawText("HOOPS", W * 0.5f, H * 0.20f, text);
        text.setFakeBoldText(false);

        text.setTextSize(W * 0.045f);
        text.setColor(0xFFFFD54F);
        c.drawText("Coins: " + pd.coins + "    Lvl " + pd.playerLevel
                + "    Stage " + pd.stage, W * 0.5f, H * 0.27f, text);

        drawButton(c, bPlay, "PLAY", 0xFF2E7D32, true);
        drawButton(c, bShop, "SHOP", 0xFF1565C0, true);
        drawButton(c, bUpg, "UPGRADES (" + pd.skillPoints + ")", 0xFF6A1B9A, true);
        drawButton(c, bReset, "Reset progress", 0xFF5D4037, true);

        if (gameComplete) {
            text.setTextSize(W * 0.04f);
            text.setColor(0xFFFFD700);
            c.drawText("\u2605 Champion \u2605", W * 0.5f, H * 0.32f, text);
        }

        if (resetConfirm) {
            drawDim(c);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(W * 0.06f);
            c.drawText("Erase all progress?", W * 0.5f, H * 0.42f, text);
            drawButton(c, bResetYes, "Yes, erase", 0xFFC62828, true);
            drawButton(c, bResetNo, "Cancel", 0xFF455A64, true);
        }
    }

    private float menuTime = 0f;
    private float tAccumMenu() {
        menuTime += 0.03f;
        return menuTime;
    }

    private void drawPlay(Canvas c) {
        drawBackground(c);
        drawHoop(c);
        drawCharacter(c);

        if (!inFlight) {
            drawBall(c, homeX, homeY, ballR);
            if (dragging) drawTrajectory(c);
        } else {
            drawBall(c, ballX, ballY, ballR);
        }

        drawHud(c);

        if (paused) {
            drawDim(c);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(W * 0.09f);
            c.drawText("PAUSED", W * 0.5f, H * 0.34f, text);
            drawButton(c, bResume, "Resume", 0xFF2E7D32, true);
            drawButton(c, bToMenuP, "Main menu", 0xFF455A64, true);
        }

        if (levelComplete) {
            drawDim(c);
            text.setColor(0xFFFFD54F);
            text.setTextSize(W * 0.085f);
            c.drawText("STAGE CLEAR!", W * 0.5f, H * 0.30f, text);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(W * 0.05f);
            c.drawText("Next: Stage " + pd.stage, W * 0.5f, H * 0.37f, text);
            if (gameComplete && pd.stage > FINAL_STAGE) {
                text.setColor(0xFFFFD700);
                c.drawText("All " + FINAL_STAGE + " stages beaten — endless mode!", W * 0.5f, H * 0.42f, text);
            }
            drawButton(c, bContinue, "Continue", 0xFF2E7D32, true);
            drawButton(c, bToMenuC, "Main menu", 0xFF455A64, true);
        }
    }

    private void drawHud(Canvas c) {
        // top bar background
        fill.setColor(0x66000000);
        c.drawRect(0, 0, W, H * 0.11f, fill);

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFD54F);
        text.setTextSize(W * 0.04f);
        c.drawText("\u25CF " + pd.coins, W * 0.03f, H * 0.045f, text);

        text.setColor(0xFFFFFFFF);
        c.drawText("Stage " + pd.stage, W * 0.03f, H * 0.092f, text);

        text.setTextAlign(Paint.Align.CENTER);
        c.drawText(makesThisStage + " / " + requiredThisStage, W * 0.5f, H * 0.045f, text);
        if (combo > 1) {
            text.setColor(0xFFFF7043);
            c.drawText("Combo x" + combo, W * 0.5f, H * 0.092f, text);
        }

        // XP bar
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFF90CAF9);
        c.drawText("Lvl " + pd.playerLevel, W * 0.97f, H * 0.045f, text);
        float barW = W * 0.30f, barH = H * 0.012f;
        float bx = W * 0.97f - barW, by = H * 0.066f;
        fill.setColor(0xFF263238);
        c.drawRect(bx, by, bx + barW, by + barH, fill);
        float frac = Math.min(1f, (float) pd.xp / (float) needXp(pd.playerLevel));
        fill.setColor(0xFF42A5F5);
        c.drawRect(bx, by, bx + barW * frac, by + barH, fill);

        text.setTextAlign(Paint.Align.CENTER);

        // pause button
        drawButton(c, bPause, "II", 0xAA000000, true);
    }

    private void drawTrajectory(Canvas c) {
        float vx = (dragCurX - dragStartX) * POWER;
        float vy = (dragCurY - dragStartY) * POWER;
        if (vy >= 0) return;
        float sp = (float) Math.hypot(vx, vy);
        if (sp > maxSpeed) {
            vx *= maxSpeed / sp;
            vy *= maxSpeed / sp;
        }
        float sx = homeX, sy = homeY, svx = vx, svy = vy;
        float dt2 = 0.03f;
        int dots = previewDots();
        for (int i = 0; i < dots; i++) {
            svy += gravity * dt2;
            sx += svx * dt2;
            sy += svy * dt2;
            if (sy > H || sx < 0 || sx > W) break;
            c.drawCircle(sx, sy, ballR * 0.16f, preview);
        }
    }

    private void drawHoop(Canvas c) {
        float left = hoopCenterX - ow / 2f;
        float right = hoopCenterX + ow / 2f;

        // backboard
        float boardX = right + rimR * 2f;
        fill.setColor(0xFFECEFF1);
        c.drawRect(boardX, hoopY - boardH, boardX + rimR * 2f, hoopY + rimR, fill);
        stroke.setColor(0xFFEF5350);
        stroke.setStrokeWidth(W * 0.006f);
        float sq = boardH * 0.4f;
        c.drawRect(boardX - sq, hoopY - boardH * 0.7f, boardX, hoopY - boardH * 0.7f + sq, stroke);

        // net
        fill.setColor(0x55FFFFFF);
        Path net = new Path();
        net.moveTo(left, hoopY);
        net.lineTo(left + ow * 0.22f, hoopY + ballR * 2.2f);
        net.lineTo(right - ow * 0.22f, hoopY + ballR * 2.2f);
        net.lineTo(right, hoopY);
        net.close();
        c.drawPath(net, fill);

        // rim
        stroke.setColor(0xFFFF5722);
        stroke.setStrokeWidth(rimR * 2f);
        c.drawLine(left, hoopY, right, hoopY, stroke);
        fill.setColor(0xFFFF7043);
        c.drawCircle(left, hoopY, rimR, fill);
        c.drawCircle(right, hoopY, rimR, fill);
    }

    private void drawBall(Canvas c, float x, float y, float r) {
        Skins.BallSkin b = Skins.ball(pd.equippedBall);
        fill.setColor(b.main);
        c.drawCircle(x, y, r, fill);
        stroke.setColor(b.line);
        stroke.setStrokeWidth(r * 0.10f);
        c.drawCircle(x, y, r, stroke);
        c.drawLine(x - r, y, x + r, y, stroke);
        c.drawLine(x, y - r, x, y + r, stroke);
        RectF arc1 = new RectF(x - r * 1.6f, y - r, x - r * 0.2f, y + r);
        c.drawArc(arc1, -60, 120, false, stroke);
        RectF arc2 = new RectF(x + r * 0.2f, y - r, x + r * 1.6f, y + r);
        c.drawArc(arc2, 120, 120, false, stroke);
    }

    private void drawCharacter(Canvas c) {
        Skins.Outfit o = Skins.outfit(pd.equippedOutfit);
        float cx = W * 0.5f;
        float feet = groundY;
        float h = H * 0.16f;
        float headR = h * 0.18f;
        float headY = feet - h + headR;

        // legs
        stroke.setStrokeWidth(W * 0.03f);
        stroke.setColor(o.shorts);
        c.drawLine(cx - W * 0.03f, feet - h * 0.42f, cx - W * 0.045f, feet, stroke);
        c.drawLine(cx + W * 0.03f, feet - h * 0.42f, cx + W * 0.045f, feet, stroke);

        // shorts
        fill.setColor(o.shorts);
        c.drawRect(cx - W * 0.06f, feet - h * 0.55f, cx + W * 0.06f, feet - h * 0.40f, fill);

        // torso (jersey)
        fill.setColor(o.jersey);
        c.drawRect(cx - W * 0.065f, feet - h * 0.80f, cx + W * 0.065f, feet - h * 0.42f, fill);
        fill.setColor(o.accent);
        c.drawRect(cx - W * 0.065f, feet - h * 0.80f, cx - W * 0.045f, feet - h * 0.42f, fill);

        // arms
        stroke.setStrokeWidth(W * 0.022f);
        stroke.setColor(o.skin);
        c.drawLine(cx - W * 0.065f, feet - h * 0.76f, cx - W * 0.12f, feet - h * 0.55f, stroke);
        c.drawLine(cx + W * 0.065f, feet - h * 0.76f, cx + W * 0.12f, feet - h * 0.55f, stroke);

        // head
        fill.setColor(o.skin);
        c.drawCircle(cx, headY, headR, fill);
        fill.setColor(o.accent);
        c.drawRect(cx - headR, headY - headR * 0.9f, cx + headR, headY - headR * 0.45f, fill);
    }

    private void drawShop(Canvas c) {
        c.drawColor(0xFF101820);

        text.setColor(0xFFFFFFFF);
        text.setTextSize(W * 0.06f);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("Shop", W * 0.04f, H * 0.06f, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFFFD54F);
        c.drawText("\u25CF " + pd.coins, W * 0.96f, H * 0.06f, text);
        text.setTextAlign(Paint.Align.CENTER);

        drawButton(c, bTabOut, "Outfits", shopTab == 0 ? 0xFF1565C0 : 0xFF37474F, true);
        drawButton(c, bTabBall, "Balls", shopTab == 1 ? 0xFF1565C0 : 0xFF37474F, true);

        c.save();
        c.clipRect(0, listTop - rowH * 0.1f, W, H - H * 0.10f);
        if (shopTab == 0) {
            for (int i = 0; i < Skins.OUTFITS.size(); i++) {
                Skins.Outfit o = Skins.OUTFITS.get(i);
                boolean owned = pd.ownedOutfits.contains(o.id);
                boolean equipped = pd.equippedOutfit.equals(o.id);
                drawShopRow(c, i, o.name, o.cost, owned, equipped, o.jersey, o.shorts);
            }
        } else {
            for (int i = 0; i < Skins.BALLS.size(); i++) {
                Skins.BallSkin b = Skins.BALLS.get(i);
                boolean owned = pd.ownedBalls.contains(b.id);
                boolean equipped = pd.equippedBall.equals(b.id);
                drawShopRow(c, i, b.name, b.cost, owned, equipped, b.main, b.line);
            }
        }
        c.restore();

        drawBottomBack(c, bBackShop);
    }

    private void drawShopRow(Canvas c, int i, String name, int cost,
                             boolean owned, boolean equipped, int col1, int col2) {
        float top = listTop - scrollY + i * rowH;
        if (top > H || top + rowH < listTop - rowH) return;

        fill.setColor(0xFF1C2833);
        c.drawRect(W * 0.04f, top + rowH * 0.08f, W * 0.96f, top + rowH * 0.92f, fill);

        // swatches
        fill.setColor(col1);
        c.drawCircle(W * 0.12f, top + rowH * 0.5f, rowH * 0.20f, fill);
        fill.setColor(col2);
        c.drawCircle(W * 0.20f, top + rowH * 0.5f, rowH * 0.14f, fill);

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFFFFFFF);
        text.setTextSize(W * 0.045f);
        c.drawText(name, W * 0.27f, top + rowH * 0.45f, text);
        text.setTextSize(W * 0.035f);
        if (owned) {
            text.setColor(0xFF81C784);
            c.drawText(equipped ? "Equipped" : "Owned", W * 0.27f, top + rowH * 0.70f, text);
        } else {
            text.setColor(0xFFFFD54F);
            c.drawText("\u25CF " + cost, W * 0.27f, top + rowH * 0.70f, text);
        }
        text.setTextAlign(Paint.Align.CENTER);

        RectF btn = new RectF(W * 0.68f, top + rowH * 0.22f, W * 0.95f, top + rowH * 0.78f);
        String label = equipped ? "Equipped" : owned ? "Equip" : "Buy";
        int col = equipped ? 0xFF2E7D32 : owned ? 0xFF1565C0
                : (pd.coins >= cost ? 0xFFEF6C00 : 0xFF424242);
        drawButton(c, btn, label, col, true);
    }

    private void drawUpgrades(Canvas c) {
        c.drawColor(0xFF101820);

        text.setColor(0xFFFFFFFF);
        text.setTextSize(W * 0.06f);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText("Upgrades", W * 0.04f, H * 0.06f, text);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFF90CAF9);
        c.drawText("Skill pts: " + pd.skillPoints, W * 0.96f, H * 0.06f, text);
        text.setTextAlign(Paint.Align.CENTER);

        String[] names = {"Aim Assist", "Power Control", "Coin Value",
                "Slow-Mo", "Rim Magnet", "Combo Master"};
        String[] desc = {"Longer aim guide", "Auto-aim nudge + range", "+20% coins / basket",
                "Slow time on launch", "Pulls near-misses in", "Higher combo cap & rewards"};

        c.save();
        c.clipRect(0, listTop - rowH * 0.1f, W, H - H * 0.10f);
        for (int i = 0; i < PlayerData.SKILL_COUNT; i++) {
            float top = listTop - scrollY + i * rowH;
            if (top > H || top + rowH < listTop - rowH) continue;

            fill.setColor(0xFF1C2833);
            c.drawRect(W * 0.04f, top + rowH * 0.08f, W * 0.96f, top + rowH * 0.92f, fill);

            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(0xFFFFFFFF);
            text.setTextSize(W * 0.045f);
            c.drawText(names[i], W * 0.07f, top + rowH * 0.40f, text);
            text.setColor(0xFF90A4AE);
            text.setTextSize(W * 0.032f);
            c.drawText(desc[i], W * 0.07f, top + rowH * 0.64f, text);

            // level dots
            float dotY = top + rowH * 0.82f;
            for (int d = 0; d < PlayerData.SKILL_MAX; d++) {
                fill.setColor(d < pd.skills[i] ? 0xFF42A5F5 : 0xFF37474F);
                c.drawCircle(W * 0.07f + d * W * 0.05f, dotY, W * 0.014f, fill);
            }
            text.setTextAlign(Paint.Align.CENTER);

            RectF btn = new RectF(W * 0.70f, top + rowH * 0.22f, W * 0.95f, top + rowH * 0.78f);
            if (pd.skills[i] >= PlayerData.SKILL_MAX) {
                drawButton(c, btn, "MAX", 0xFF2E7D32, true);
            } else {
                int cost = pd.skills[i] + 1;
                int col = pd.skillPoints >= cost ? 0xFF6A1B9A : 0xFF424242;
                drawButton(c, btn, cost + " pt" + (cost > 1 ? "s" : ""), col, true);
            }
        }
        c.restore();

        drawBottomBack(c, bBackUpg);
    }

    private void drawBottomBack(Canvas c, RectF r) {
        fill.setColor(0xFF101820);
        c.drawRect(0, H - H * 0.10f, W, H, fill);
        drawButton(c, r, "Back", 0xFF455A64, true);
    }

    // =================== UI helpers ===================

    private void drawButton(Canvas c, RectF r, String label, int color, boolean enabled) {
        fill.setColor(enabled ? color : 0xFF333333);
        float rad = r.height() * 0.25f;
        c.drawRoundRect(r, rad, rad, fill);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Math.min(r.height() * 0.45f, W * 0.05f));
        float ty = r.centerY() - (text.descent() + text.ascent()) / 2f;
        c.drawText(label, r.centerX(), ty, text);
    }

    private void drawDim(Canvas c) {
        fill.setColor(0xAA000000);
        c.drawRect(0, 0, W, H, fill);
    }

    private void drawMessage(Canvas c) {
        if (msgTimer <= 0 || msg.isEmpty()) return;
        int alpha = (int) (Math.min(1f, msgTimer / 1.4f) * 255);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor((alpha << 24) | (msgColor & 0x00FFFFFF));
        text.setTextSize(W * 0.06f);
        text.setFakeBoldText(true);
        c.drawText(msg, W * 0.5f, H * 0.55f, text);
        text.setFakeBoldText(false);
    }

    // =================== Button layout ===================

    private void layoutButtons() {
        float bw = W * 0.6f, bh = H * 0.075f, cx = W * 0.5f;
        bPlay.set(cx - bw / 2, H * 0.40f, cx + bw / 2, H * 0.40f + bh);
        bShop.set(cx - bw / 2, H * 0.52f, cx + bw / 2, H * 0.52f + bh);
        bUpg.set(cx - bw / 2, H * 0.64f, cx + bw / 2, H * 0.64f + bh);
        bReset.set(cx - W * 0.25f, H * 0.78f, cx + W * 0.25f, H * 0.78f + bh * 0.8f);

        bResetYes.set(cx - W * 0.42f, H * 0.52f, cx - W * 0.02f, H * 0.52f + bh);
        bResetNo.set(cx + W * 0.02f, H * 0.52f, cx + W * 0.42f, H * 0.52f + bh);

        bPause.set(W * 0.86f, H * 0.115f, W * 0.97f, H * 0.115f + W * 0.11f);

        bResume.set(cx - bw / 2, H * 0.45f, cx + bw / 2, H * 0.45f + bh);
        bToMenuP.set(cx - bw / 2, H * 0.56f, cx + bw / 2, H * 0.56f + bh);

        bContinue.set(cx - bw / 2, H * 0.50f, cx + bw / 2, H * 0.50f + bh);
        bToMenuC.set(cx - bw / 2, H * 0.61f, cx + bw / 2, H * 0.61f + bh);

        bTabOut.set(W * 0.04f, H * 0.09f, W * 0.48f, H * 0.09f + bh * 0.8f);
        bTabBall.set(W * 0.52f, H * 0.09f, W * 0.96f, H * 0.09f + bh * 0.8f);

        bBackShop.set(cx - W * 0.25f, H - H * 0.085f, cx + W * 0.25f, H - H * 0.02f);
        bBackUpg.set(cx - W * 0.25f, H - H * 0.085f, cx + W * 0.25f, H - H * 0.02f);

        listTop = H * 0.17f;
        rowH = H * 0.135f;
    }
}
