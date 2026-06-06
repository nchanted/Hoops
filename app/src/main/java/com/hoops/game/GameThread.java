package com.hoops.game;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

/** Dedicated render/update thread tied to the SurfaceHolder lifecycle. */
public class GameThread extends Thread {
    private final SurfaceHolder holder;
    private final GameView view;
    private volatile boolean running = false;

    public GameThread(SurfaceHolder holder, GameView view) {
        this.holder = holder;
        this.view = view;
    }

    public void setRunning(boolean r) {
        running = r;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    synchronized (holder) {
                        view.update(dt);
                        view.render(c);
                    }
                }
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
