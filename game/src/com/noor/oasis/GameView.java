package com.noor.oasis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** GameView — game loop, rendering, touch controls, HUD. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    public interface Listener {
        void onPauseGame(int shards);
        void onLevelDone(int level, int shards);
        void onGameOver(int shards);
        void onWinGame(int shards);
    }

    private final Game game = new Game();
    private final AudioSys audio;
    private final Listener listener;
    private final int startLevel, startShards;
    private Thread thread;
    private volatile boolean running;
    private long lastT;

    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();

    // touch button state
    private int ptrLeft = -1, ptrRight = -1, ptrJump = -1, ptrAtk = -1, ptrDash = -1;
    private float bxL, byL, bxR, byR, bxJ, byJ, bxA, byA, bxD, byD, bxP, byP, bRad, bRadS;
    private boolean layoutDone;
    private boolean pauseSent, overSent, doneSent;
    private float evCooldown;

    public GameView(Context ctx, int level, int shards, AudioSys a, Listener l) {
        super(ctx);
        startLevel = level; startShards = shards;
        audio = a; listener = l;
        getHolder().addCallback(this);
        setFocusable(true); setFocusableInTouchMode(true);
        txt.setColor(Color.WHITE);
        txt.setTextAlign(Paint.Align.CENTER);
        btnT.setColor(Color.WHITE);
        btnT.setTextAlign(Paint.Align.CENTER);
        btnT.setAntiAlias(true);
    }

    public Game getGame() { return game; }

    @Override public void surfaceCreated(SurfaceHolder h) {
        game.loadLevel(startLevel, startShards);
        audio.startMusic(game.theme);
        running = true;
        lastT = System.nanoTime();
        thread = new Thread(loop, "noor-loop");
        thread.start();
    }

    @Override public void surfaceDestroyed(SurfaceHolder h) {
        running = false;
        try { if (thread != null) thread.join(800); } catch (Exception ignored) {}
        thread = null;
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) { layoutDone = false; }

    public void resumeGame() { pauseSent = false; lastT = System.nanoTime(); }
    public void retryFromCheck() {
        game.respawnAtCheck();
        overSent = false; doneSent = false;
        lastT = System.nanoTime();
    }

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            while (running) {
                long now = System.nanoTime();
                float dt = (now - lastT) / 1e9f;
                lastT = now;
                if (dt > 0.05f) dt = 0.05f;
                if (dt < 0.0005f) { try { Thread.sleep(2); } catch (Exception ignored) {} continue; }
                if (!pauseSent) {
                    game.inLeft = ptrLeft >= 0;
                    game.inRight = ptrRight >= 0;
                    game.update(dt);
                    drainEvents();
                }
                Canvas c = null;
                try {
                    c = getHolder().lockCanvas();
                    if (c != null) render(c);
                } catch (Exception ignored) {
                } finally {
                    if (c != null) try { getHolder().unlockCanvasAndPost(c); } catch (Exception ignored) {}
                }
                // ~60fps cap
                try { Thread.sleep(8); } catch (Exception ignored) {}
            }
        }
    };

    private void drainEvents() {
        if (evCooldown > 0) return;
        for (int i = 0; i < 12; i++) {
            int e = game.pollEvent();
            if (e == Game.EV_NONE) break;
            switch (e) {
                case Game.EV_COIN: audio.play(AudioSys.S_COIN); break;
                case Game.EV_HURT: audio.play(AudioSys.S_HIT); break;
                case Game.EV_JUMP: audio.play(AudioSys.S_JUMP); break;
                case Game.EV_SWORD: audio.play(AudioSys.S_SWORD); break;
                case Game.EV_DASH: audio.play(AudioSys.S_DASH); break;
                case Game.EV_LAND: audio.play(AudioSys.S_LAND); break;
                case Game.EV_CHECK: audio.play(AudioSys.S_CHECK); break;
                case Game.EV_BREAK: audio.play(AudioSys.S_BREAK); break;
                case Game.EV_STOMP: audio.play(AudioSys.S_HIT); break;
                case Game.EV_SHOOT: audio.play(AudioSys.S_SHOOT); break;
                case Game.EV_BOSSHIT: audio.play(AudioSys.S_BOSSHIT); break;
                case Game.EV_HEART: audio.play(AudioSys.S_HEART); break;
                case Game.EV_BOSSDIE: audio.play(AudioSys.S_DIE); break;
                case Game.EV_GAMEOVER:
                    if (!overSent) { overSent = true; audio.play(AudioSys.S_LOSE); postGameOver(); }
                    break;
                case Game.EV_LEVELDONE:
                    if (!doneSent) { doneSent = true; audio.play(AudioSys.S_WIN); postLevelDone(); }
                    break;
                case Game.EV_WINGAME:
                    if (!doneSent) { doneSent = true; audio.play(AudioSys.S_WIN); postWin(); }
                    break;
            }
        }
    }

    private void postGameOver() { post(new Runnable() { @Override public void run() { listener.onGameOver(game.shards); } }); }
    private void postLevelDone() { post(new Runnable() { @Override public void run() { listener.onLevelDone(game.level, game.shards); } }); }
    private void postWin() { post(new Runnable() { @Override public void run() { listener.onWinGame(game.shards); } }); }

    // ---------------- render ----------------
    private void render(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (!layoutDone) { computeLayout(w, h); layoutDone = true; }
        c.drawColor(0xFF000000);
        float s = Math.min(w / (float) Game.VW, h / (float) Game.VH);
        float ox = (w - Game.VW * s) / 2f, oy = (h - Game.VH * s) / 2f;
        c.save();
        c.translate(ox, oy);
        c.scale(s, s);
        c.clipRect(0, 0, Game.VW, Game.VH);

        float time = game.levelTime;
        Art.drawBackground(c, game.theme, game.camX, Game.VW, Game.VH, time);

        c.save();
        c.translate(-game.camX, 0);
        drawTiles(c);
        drawObjects(c, time);
        drawEntities(c, time);
        drawPlayer(c, time);
        drawBoss(c, time);
        drawParts(c);
        c.restore();

        drawHUD(c, w, h, s, ox, oy);
        c.restore();
        drawButtons(c, w, h);
    }

    private void drawTiles(Canvas c) {
        char[][] g = game.getGrid();
        int x0 = Math.max(0, (int) (game.camX / Game.TILE) - 1);
        int x1 = Math.min(game.getTW() - 1, x0 + Game.VW / Game.TILE + 3);
        for (int y = 0; y < game.getTH(); y++)
            for (int x = x0; x <= x1; x++) {
                char t = g[y][x];
                if (t == '#' || t == '=') Art.drawTile(c, t, x * Game.TILE, y * Game.TILE, Game.TILE, game.theme);
            }
        // spikes
        for (Game.Spike sp : game.getSpikes()) Art.drawSpikes(c, sp.x, sp.y, Game.TILE);
        // moving platforms
        for (Game.MPlat m : game.getMPlats()) {
            hud.setColor(game.theme == 1 ? 0xFF6a4bb3 : 0xFF8a5a2e);
            rf.set(m.x, m.y, m.x + m.w, m.y + 20);
            c.drawRoundRect(rf, 8, 8, hud);
            hud.setColor(0xFFffcf4d);
            c.drawRect(m.x + 4, m.y + 2, m.x + m.w - 4, m.y + 8, hud);
        }
        // goal
        hud.setAlpha(game.goalOpen ? 255 : 90);
        Art.drawGoal(c, game.getGoalX(), game.getGoalY(), Game.TILE, game.levelTime);
        hud.setAlpha(255);
    }

    private void drawObjects(Canvas c, float time) {
        for (Game.Coin cn : game.getCoins()) {
            if (cn.taken) continue;
            if (cn.x < game.camX - 60 || cn.x > game.camX + Game.VW + 60) continue;
            Art.drawShard(c, cn.x, cn.y, 13, time + cn.x * 0.01f);
        }
        for (Game.Heart hh : game.getHearts()) {
            if (hh.taken) continue;
            Art.drawHeart(c, hh.x, hh.y, 13, time);
        }
        for (Game.Vase v : game.getVases()) {
            if (!v.broken) Art.drawVase(c, v.x, v.y, Game.TILE);
        }
        for (Game.Check ch : game.getChecks())
            Art.drawCheckpoint(c, ch.x, ch.y, 22, ch.used, time);
    }

    private void drawEntities(Canvas c, float time) {
        for (Game.Ghoul g : game.getGhouls()) {
            if (!g.alive) continue;
            Art.drawGhoul(c, g.x, g.y, 1.15f, time + g.x, g.dir >= 0 ? 1 : -1, g.flash > 0);
        }
        for (Game.Bat b : game.getBats()) {
            if (!b.alive) continue;
            Art.drawBat(c, b.x, b.y, 1.2f, time, b.flash > 0);
        }
        for (Game.Proj p : game.getProjs()) {
            if (!p.alive) continue;
            Art.drawFireball(c, p.x, p.y, 11, time);
        }
    }

    private void drawPlayer(Canvas c, float time) {
        if (game.isInvuln() && ((int) (time * 12) % 2 == 0)) return; // blink
        Art.drawPlayer(c, game.px + Game.PW / 2, game.py + Game.PH, 1.0f,
                game.anim, game.animFrame, game.isFacingRight() ? 1 : -1, time, false);
    }

    private void drawBoss(Canvas c, float time) {
        if (!game.bossActive || game.bossDead) return;
        boolean fl = game.getBossFlash() > 0;
        Art.drawBoss(c, game.getBx(), game.getBy(), 1.5f, time,
                (game.px < game.getBx()) ? -1 : 1, fl,
                game.bossHp / (float) game.bossMax);
        if (game.getBossState() == 3) { // telegraph flash
            hud.setColor(0x66FF2222);
            c.drawCircle(game.getBx(), game.getBy() - 20, 70 + (float) Math.sin(time * 20) * 8, hud);
        }
    }

    private void drawParts(Canvas c) {
        for (Game.Part p : game.getParts()) {
            if (!p.alive) continue;
            float a = Math.max(0, Math.min(1, p.life / p.maxLife));
            hud.setColor(p.col);
            hud.setAlpha((int) (a * 255));
            c.drawCircle(p.x, p.y, p.size * (0.5f + a * 0.5f), hud);
        }
        hud.setAlpha(255);
    }

    private void drawHUD(Canvas c, int w, int h, float s, float ox, float oy) {
        // hearts top-right (RTL start)
        for (int i = 0; i < 3; i++) {
            float hx = Game.VW - 40 - i * 44, hy = 34;
            if (i < game.getHp()) Art.drawHeart(c, hx, hy, 14, game.levelTime);
            else { hud.setColor(0x55333333); c.drawCircle(hx, hy, 14, hud); }
        }
        // shards
        Art.drawShard(c, 40, 34, 13, game.levelTime);
        txt.setTextSize(26);
        txt.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.valueOf(game.shards), 62, 43, txt);
        txt.setTextAlign(Paint.Align.CENTER);
        // level name top-center
        txt.setTextSize(24);
        hud.setColor(0x55000000);
        c.drawText(Game.LEVEL_NAMES[game.level], Game.VW / 2 + 2, 36, hud);
        c.drawText(Game.LEVEL_NAMES[game.level], Game.VW / 2, 34, txt);
        // hint
        if (game.getHintT() > 0) {
            txt.setTextSize(26);
            float a = Math.min(1, game.getHintT());
            txt.setAlpha((int) (a * 255));
            hud.setColor(0xAA000000);
            c.drawText(game.hintText, Game.VW / 2 + 2, 502, hud);
            c.drawText(game.hintText, Game.VW / 2, 500, txt);
            txt.setAlpha(255);
        }
        // locked goal msg in boss level
        if (game.level == 2 && !game.goalOpen && game.bossActive) {
            txt.setTextSize(22);
            c.drawText("اهزم تنين الرمال لفتح البوابة!", Game.VW / 2, 66, txt);
        }
        // boss bar
        if (game.bossActive && !game.bossDead) {
            float bw = 420, bx0 = (Game.VW - bw) / 2, by0 = 46;
            hud.setColor(0xAA000000);
            rf.set(bx0 - 4, by0 - 4, bx0 + bw + 4, by0 + 22);
            c.drawRoundRect(rf, 10, 10, hud);
            hud.setColor(0xFF550000);
            rf.set(bx0, by0, bx0 + bw, by0 + 18);
            c.drawRoundRect(rf, 8, 8, hud);
            hud.setColor(0xFFFF4d4d);
            rf.set(bx0, by0, bx0 + bw * (game.bossHp / (float) game.bossMax), by0 + 18);
            c.drawRoundRect(rf, 8, 8, hud);
            txt.setTextSize(20);
            c.drawText("تنين الرمال", Game.VW / 2, by0 + 16, txt);
        }
    }

    private void computeLayout(int w, int h) {
        bRad = Math.max(44, Math.min(w, h) * 0.075f);
        bRadS = bRad * 0.82f;
        float m = bRad + 18;
        bxL = m + bRad * 0.2f; byL = h - m;
        bxR = bxL + bRad * 2.4f; byR = h - m;
        bxJ = w - m; byJ = h - m;
        bxA = bxJ - bRad * 2.4f; byA = h - m * 0.9f;
        bxD = bxJ - bRad * 1.25f; byD = h - m - bRad * 2.1f;
        bxP = w - 44; byP = 44;
    }

    private void drawButtons(Canvas c, int w, int h) {
        btn.setStyle(Paint.Style.FILL);
        btnT.setTextSize(bRad * 0.62f);
        drawBtn(c, bxL, byL, bRad, ptrLeft >= 0, "◀");
        drawBtn(c, bxR, byR, bRad, ptrRight >= 0, "▶");
        drawBtn(c, bxJ, byJ, bRad * 1.1f, ptrJump >= 0, "قفز");
        btnT.setTextSize(bRadS * 0.55f);
        drawBtn(c, bxA, byA, bRadS, ptrAtk >= 0, "هجوم");
        drawBtn(c, bxD, byD, bRadS, ptrDash >= 0, "دفع");
        btnT.setTextSize(30);
        drawBtn(c, bxP, byP, 30, false, "II");
    }

    private void drawBtn(Canvas c, float x, float y, float r, boolean on, String t) {
        btn.setColor(on ? 0xAAffcf4d : 0x553b2a5e);
        c.drawCircle(x, y, r, btn);
        btn.setStyle(Paint.Style.STROKE);
        btn.setStrokeWidth(3);
        btn.setColor(0xCCffffff);
        c.drawCircle(x, y, r, btn);
        btn.setStyle(Paint.Style.FILL);
        btnT.setColor(on ? 0xFF222222 : 0xFFFFFFFF);
        c.drawText(t, x, y + btnT.getTextSize() * 0.35f, btnT);
    }

    private boolean inside(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= (r + 26) * (r + 26);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        int idx = e.getActionIndex();
        float x = e.getX(idx), y = e.getY(idx);
        int id = e.getPointerId(idx);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (inside(x, y, bxL, byL, bRad)) ptrLeft = id;
            else if (inside(x, y, bxR, byR, bRad)) ptrRight = id;
            else if (inside(x, y, bxJ, byJ, bRad * 1.1f)) { ptrJump = id; game.queueJump(); }
            else if (inside(x, y, bxA, byA, bRadS)) { ptrAtk = id; game.queueAttack(); }
            else if (inside(x, y, bxD, byD, bRadS)) { ptrDash = id; game.queueDash(); }
            else if (inside(x, y, bxP, byP, 34)) {
                if (!pauseSent) { pauseSent = true; audio.play(AudioSys.S_CLICK);
                    post(new Runnable() { @Override public void run() { listener.onPauseGame(game.shards); } }); }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (id == ptrLeft) ptrLeft = -1;
            if (id == ptrRight) ptrRight = -1;
            if (id == ptrJump) ptrJump = -1;
            if (id == ptrAtk) ptrAtk = -1;
            if (id == ptrDash) ptrDash = -1;
            if (action == MotionEvent.ACTION_CANCEL) { ptrLeft = ptrRight = ptrJump = ptrAtk = ptrDash = -1; }
        }
        return true;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: ptrLeft = -2; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: ptrRight = -2; return true;
            case KeyEvent.KEYCODE_SPACE: case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_W:
                game.queueJump(); return true;
            case KeyEvent.KEYCODE_J: case KeyEvent.KEYCODE_ENTER: game.queueAttack(); return true;
            case KeyEvent.KEYCODE_K: case KeyEvent.KEYCODE_SHIFT_LEFT: game.queueDash(); return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_A: if (ptrLeft == -2) ptrLeft = -1; return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_D: if (ptrRight == -2) ptrRight = -1; return true;
        }
        return super.onKeyUp(code, e);
    }
}
