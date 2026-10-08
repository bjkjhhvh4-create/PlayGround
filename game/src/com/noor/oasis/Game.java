package com.noor.oasis;

import java.util.ArrayList;

/**
 * Game — world simulation: physics, levels, enemies, boss, particles.
 * World units = pixels at logical resolution 960x540. Y grows downward.
 */
public class Game {

    public static final int VW = 960, VH = 540, TILE = 48;
    private static final float GRAV = 2400f, MOVE = 330f, JUMP_V = 920f, MAX_FALL = 1450f;

    // events consumed by GameView for SFX/HUD
    public static final int EV_NONE = 0, EV_COIN = 1, EV_HURT = 2, EV_GAMEOVER = 3,
            EV_CHECK = 4, EV_BREAK = 5, EV_STOMP = 6, EV_SHOOT = 7, EV_BOSSHIT = 8,
            EV_LEVELDONE = 9, EV_WINGAME = 10, EV_HEART = 11, EV_JUMP = 12,
            EV_SWORD = 13, EV_DASH = 14, EV_BOSSDIE = 15, EV_LAND = 16;

    public static final String[] LEVEL_NAMES = {"واحة النخيل", "كهف البلور", "عرش الرمال"};
    public static final String[] LEVEL_HINTS = {
            "اجمع شظايا الشمس.. واحذر غيلان الرمال!",
            "الخفافيش تتربص في الظلام.. تقدّم بحذر!",
            "تنين الرمال ينتظرك.. اقضِ عليه لتفوز!"
    };

    // input (set by GameView each frame)
    public boolean inLeft, inRight;
    private boolean qJump, qAttack, qDash;

    public void queueJump() { qJump = true; }
    public void queueAttack() { qAttack = true; }
    public void queueDash() { qDash = true; }

    // player state
    public float px, py;           // top-left
    public static final float PW = 34, PH = 56;
    private float vx, vy;
    private boolean onGround, facing = true; // true=right
    private int hp = 3;
    public int shards = 0;
    private float coyote, jumpBuf, invuln, atkT, atkCd, dashT, dashCd, hurtT, deadT, winT;
    private boolean attacking, dead, won;
    public int anim = Art.IDLE, animFrame;
    private float animTime, runDustT;

    // level
    public int level = 0, theme = 0;
    private int tw, th;           // tiles
    private char[][] grid;
    public float camX;
    public float levelTime;
    public String hintText = "";
    private float hintT;
    public boolean goalOpen = true;

    // entities
    static class Ghoul { float x, y, vx; int hp = 2, dir = -1; float t; boolean alive = true; float flash; }
    static class Bat { float ax, ay, x, y, t; int hp = 1; boolean alive = true, diving; float flash; float dvx, dvy; }
    static class Coin { float x, y, vx, vy; boolean taken, physical; float t; }
    static class Vase { float x, y; boolean broken; }
    static class Spike { float x, y; }
    static class Check { float x, y; boolean used; }
    static class Heart { float x, y; boolean taken; float t; }
    static class Proj { float x, y, vx, vy; boolean alive; float t; }
    static class Part { float x, y, vx, vy, life, maxLife; int col; float size, grav; boolean alive; }
    static class Plat { float x, y, w; } // moving platform (level 2+)

    private final ArrayList<Ghoul> ghouls = new ArrayList<>();
    private final ArrayList<Bat> bats = new ArrayList<>();
    private final ArrayList<Coin> coins = new ArrayList<>();
    private final ArrayList<Vase> vases = new ArrayList<>();
    private final ArrayList<Spike> spikes = new ArrayList<>();
    private final ArrayList<Check> checks = new ArrayList<>();
    private final ArrayList<Heart> hearts = new ArrayList<>();
    private final ArrayList<Proj> projs = new ArrayList<>();
    private final ArrayList<Plat> plats = new ArrayList<>();
    private float goalX, goalY;
    private float spawnX, spawnY, checkX, checkY;
    private boolean hasCheck;

    // boss
    public boolean bossActive, bossDead;
    public int bossHp, bossMax = 14;
    private float bx, by, bvx, bvy, bossT, bossStateT;
    private int bossState; // 0 enter,1 hover,2 volley,3 telegraph,4 swoop,5 summon
    private float bossFlash;
    private int summonCount;
    private static final int B_ENTER = 0, B_HOVER = 1, B_VOLLEY = 2, B_TELE = 3, B_SWOOP = 4, B_SUMMON = 5;

    private final Part[] parts = new Part[140];
    private int partIdx;
    private final int[] evBuf = new int[32];
    private int evHead, evTail;

    public Game() {
        for (int i = 0; i < parts.length; i++) parts[i] = new Part();
    }

    private void emit(int e) {
        evBuf[evTail] = e; evTail = (evTail + 1) % evBuf.length;
        if (evTail == evHead) evHead = (evHead + 1) % evBuf.length;
    }
    public int pollEvent() {
        if (evHead == evTail) return EV_NONE;
        int e = evBuf[evHead]; evHead = (evHead + 1) % evBuf.length;
        return e;
    }

    // ---------------- level building ----------------
    public void loadLevel(int lv, int startShards) {
        level = lv; shards = startShards;
        theme = lv == 0 ? Art.THEME_OASIS : (lv == 1 ? Art.THEME_CAVE : Art.THEME_THRONE);
        ghouls.clear(); bats.clear(); coins.clear(); vases.clear(); spikes.clear();
        checks.clear(); hearts.clear(); projs.clear(); plats.clear();
        for (Part p : parts) p.alive = false;
        bossActive = false; bossDead = false; bossHp = bossMax; summonCount = 0;
        dead = false; won = false; deadT = 0; winT = 0;
        hp = 3; vx = 0; vy = 0; invuln = 0; atkT = 0; dashT = 0; dashCd = 0;
        attacking = false; hasCheck = false;
        hintText = LEVEL_HINTS[lv]; hintT = 4f;
        goalOpen = lv != 2;
        if (lv == 0) buildL1(); else if (lv == 1) buildL2(); else buildL3();
        px = spawnX; py = spawnY; checkX = spawnX; checkY = spawnY;
        camX = 0; levelTime = 0;
    }

    private void newGrid(int w, int h) {
        tw = w; th = h;
        grid = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) grid[y][x] = ' ';
    }
    private void ground(int x0, int x1, int topRow) {
        for (int x = x0; x <= x1; x++) for (int y = topRow; y < th; y++)
            if (inB(x, y)) grid[y][x] = '#';
    }
    private boolean inB(int x, int y) { return x >= 0 && x < tw && y >= 0 && y < th; }
    private void plat(int x, int y, int w) {
        for (int i = 0; i < w; i++) if (inB(x + i, y)) grid[y][x + i] = '=';
    }
    private void coinArc(float cx, float cy, int n, float dx, float dyy) {
        for (int i = 0; i < n; i++) addCoin(cx + i * dx, cy + Math.abs(i - (n - 1) / 2f) * -dyy);
    }
    private void addCoin(float x, float y) { Coin c = new Coin(); c.x = x; c.y = y; coins.add(c); }
    private void addGhoul(int tx, int ty) { Ghoul g = new Ghoul(); g.x = tx * TILE; g.y = ty * TILE; ghouls.add(g); }
    private void addBat(int tx, int ty) { Bat b = new Bat(); b.ax = b.x = tx * TILE; b.ay = b.y = ty * TILE; bats.add(b); }
    private void addVase(int tx, int ty) { Vase v = new Vase(); v.x = tx * TILE; v.y = (ty + 1) * TILE; vases.add(v); }
    private void addSpike(int tx, int ty) { Spike s = new Spike(); s.x = tx * TILE; s.y = ty * TILE; spikes.add(s); }
    private void addCheck(int tx, int ty) { Check c = new Check(); c.x = tx * TILE + TILE / 2f; c.y = (ty + 1) * TILE; checks.add(c); }
    private void addHeart(int tx, int ty) { Heart h = new Heart(); h.x = tx * TILE + 24; h.y = ty * TILE + 24; hearts.add(h); }
    private void addMPlat(int tx, int ty, int wTiles, float range, float speed) {
        // static list reused as moving platforms via plats with extra fields encoded in Plat subclass? keep simple: static platforms already in grid; moving ones:
        MPlat m = new MPlat(); m.x = tx * TILE; m.y = ty * TILE; m.w = wTiles * TILE;
        m.x0 = m.x; m.range = range; m.speed = speed; mplats.add(m);
    }
    private final ArrayList<MPlat> mplats = new ArrayList<>();
    static class MPlat extends Plat { float x0, range, speed, t, dx; }

    private void buildL1() {
        newGrid(92, 11); mplats.clear();
        int G = 8;
        ground(0, 16, G); ground(20, 38, G); ground(42, 60, G); ground(64, 91, G); // pits at 17-19, 39-41, 61-63
        plat(6, 5, 3); plat(24, 5, 3); plat(30, 4, 2); plat(46, 5, 3); plat(55, 4, 2); plat(70, 5, 3); plat(78, 4, 2);
        coinArc(6 * TILE, 3.4f * TILE, 4, 40, 14);
        coinArc(24 * TILE, 3.4f * TILE, 4, 40, 14);
        coinArc(17 * TILE, 5.5f * TILE, 5, 38, 10);   // over pit
        coinArc(46 * TILE, 3.4f * TILE, 4, 40, 14);
        for (int i = 0; i < 5; i++) addCoin((66 + i) * TILE, 6.6f * TILE);
        for (int i = 0; i < 5; i++) addCoin((84 + i) * TILE, 5.2f * TILE); // secret high
        addVase(8, 4); addVase(56, 3); addVase(79, 3);
        addSpike(28, 7); addSpike(29, 7); addSpike(52, 7); addSpike(74, 7); addSpike(75, 7);
        addCheck(21, 7); addCheck(44, 7); addCheck(66, 7);
        addHeart(31, 3); addHeart(72, 4);
        addGhoul(12, 6); addGhoul(26, 6); addGhoul(48, 6); addGhoul(68, 6); addGhoul(82, 6);
        addBat(35, 3); addBat(58, 3);
        spawnX = 2 * TILE; spawnY = 5 * TILE; goalX = 88 * TILE; goalY = G * TILE;
    }

    private void buildL2() {
        newGrid(100, 11); mplats.clear();
        int G = 8;
        ground(0, 14, G); ground(18, 40, G); ground(44, 66, G); ground(70, 99, G);
        plat(5, 5, 2); plat(10, 4, 2); plat(22, 5, 3); plat(33, 4, 2); plat(47, 5, 3);
        plat(54, 4, 2); plat(60, 5, 2); plat(74, 5, 3); plat(82, 4, 2); plat(90, 5, 2);
        addMPlat(41, 5, 2, 120, 1.2f); // moving platform over big pit
        coinArc(5 * TILE, 3.4f * TILE, 3, 40, 12);
        coinArc(33 * TILE, 2.4f * TILE, 4, 40, 12);
        for (int i = 0; i < 4; i++) addCoin((41.5f + i) * TILE, 3.6f * TILE);
        coinArc(54 * TILE, 2.4f * TILE, 3, 40, 12);
        coinArc(82 * TILE, 2.4f * TILE, 4, 40, 12);
        for (int i = 0; i < 6; i++) addCoin((93 + i) * TILE, 6.4f * TILE);
        addVase(11, 3); addVase(55, 3); addVase(91, 4);
        addSpike(24, 7); addSpike(25, 7); addSpike(50, 7); addSpike(62, 7); addSpike(63, 7); addSpike(80, 7);
        addCheck(19, 7); addCheck(45, 7); addCheck(71, 7);
        addHeart(34, 3); addHeart(83, 3);
        addGhoul(10, 6); addGhoul(26, 6); addGhoul(36, 6); addGhoul(52, 6); addGhoul(64, 6); addGhoul(78, 6);
        addBat(30, 2); addBat(48, 3); addBat(72, 2); addBat(88, 3);
        spawnX = 2 * TILE; spawnY = 5 * TILE; goalX = 97 * TILE; goalY = G * TILE;
    }

    private void buildL3() {
        newGrid(72, 11); mplats.clear();
        int G = 8;
        ground(0, 71, G); // flat arena, no pits
        plat(8, 5, 3); plat(20, 4, 2); plat(34, 5, 2); plat(50, 5, 3);
        coinArc(8 * TILE, 3.4f * TILE, 4, 40, 12);
        coinArc(50 * TILE, 3.4f * TILE, 4, 40, 12);
        for (int i = 0; i < 5; i++) addCoin((30 + i) * TILE, 6.4f * TILE);
        addVase(9, 4); addVase(51, 4);
        addSpike(26, 7); addSpike(27, 7); addSpike(44, 7);
        addCheck(6, 7); addCheck(40, 7);
        addHeart(21, 3); addHeart(35, 4);
        addGhoul(16, 6); addGhoul(30, 6); addGhoul(48, 6);
        addBat(24, 3);
        spawnX = 2 * TILE; spawnY = 5 * TILE; goalX = 68 * TILE; goalY = G * TILE;
    }

    // ---------------- helpers ----------------
    private boolean solidAt(int tx, int ty) {
        if (tx < 0 || tx >= tw) return true;
        if (ty < 0 || ty >= th) return false;
        char c = grid[ty][tx];
        return c == '#' || c == '=';
    }
    private boolean solidPx(float x, float y) {
        return solidAt((int) (x / TILE), (int) (y / TILE));
    }

    private void burst(float x, float y, int n, int col, float spd, float grav, float life, float size) {
        for (int i = 0; i < n; i++) {
            Part p = parts[partIdx]; partIdx = (partIdx + 1) % parts.length;
            double a = Math.random() * Math.PI * 2;
            double s = spd * (0.4 + Math.random() * 0.8);
            p.x = x; p.y = y;
            p.vx = (float) (Math.cos(a) * s); p.vy = (float) (Math.sin(a) * s - spd * 0.5);
            p.maxLife = p.life = (float) (life * (0.6 + Math.random() * 0.6));
            p.col = col; p.size = size; p.grav = grav; p.alive = true;
        }
    }

    // ---------------- update ----------------
    public void update(float dt) {
        levelTime += dt;
        if (hintT > 0) hintT -= dt;
        updatePlayer(dt);
        updateGhouls(dt);
        updateBats(dt);
        updateBossWrapped(dt);
        updateProjs(dt);
        updateCoins(dt);
        updatePlats(dt);
        // particles
        for (Part p : parts) {
            if (!p.alive) continue;
            p.life -= dt;
            if (p.life <= 0) { p.alive = false; continue; }
            p.vy += p.grav * dt;
            p.x += p.vx * dt; p.y += p.vy * dt;
        }
        // camera
        float target = px + PW / 2 - VW / 2;
        camX += (target - camX) * Math.min(1, dt * 6);
        if (camX < 0) camX = 0;
        float maxCam = tw * TILE - VW;
        if (camX > maxCam) camX = maxCam < 0 ? 0 : maxCam;
        qJump = qAttack = qDash = false;
    }

    private void moveCollide(float dt, boolean isPlayer) {
        // X axis
        float nx = px + vx * dt;
        if (vx > 0) {
            if (solidPx(nx + PW, py + 4) || solidPx(nx + PW, py + PH / 2) || solidPx(nx + PW, py + PH - 2)) {
                nx = ((int) ((nx + PW) / TILE)) * TILE - PW - 0.01f; vx = 0;
            }
        } else if (vx < 0) {
            if (solidPx(nx, py + 4) || solidPx(nx, py + PH / 2) || solidPx(nx, py + PH - 2)) {
                nx = (((int) (nx / TILE)) + 1) * TILE + 0.01f; vx = 0;
            }
        }
        px = nx;
        // moving platform carry (player only)
        if (isPlayer) {
            for (MPlat m : mplats) {
                if (py + PH >= m.y - 6 && py + PH <= m.y + 14 && px + PW > m.x && px < m.x + m.w) {
                    if (onGround) px += m.dx * dt;
                }
            }
        }
        // Y axis
        float ny = py + vy * dt;
        onGround = false;
        if (vy > 0) {
            if (solidPx(px + 3, ny + PH) || solidPx(px + PW - 3, ny + PH)) {
                ny = ((int) ((ny + PH) / TILE)) * TILE - PH - 0.01f;
                if (!isPlayer) {} else if (vy > 900) { emit(EV_LAND); burst(px + PW / 2, py + PH, 6, 0xFFd9c9a8, 120, 500, 0.4f, 3); }
                vy = 0; onGround = true;
            } else if (isPlayer) {
                for (MPlat m : mplats) {
                    if (px + PW > m.x + 4 && px < m.x + m.w - 4 && py + PH <= m.y + 8 && ny + PH >= m.y) {
                        ny = m.y - PH; vy = 0; onGround = true; px += m.dx * dt;
                    }
                }
            }
        } else if (vy < 0) {
            if (solidPx(px + 3, ny) || solidPx(px + PW - 3, ny)) {
                ny = (((int) (ny / TILE)) + 1) * TILE + 0.01f; vy = 0;
            }
        }
        py = ny;
    }

    private void updatePlayer(float dt) {
        if (dead) {
            deadT += dt;
            anim = Art.DEATH;
            vy += GRAV * dt; moveCollide(dt, true);
            if (deadT > 1.6f) emit(EV_GAMEOVER);
            return;
        }
        if (won) { winT += dt; anim = Art.WIN; if (winT > 2.2f) emit(level == 2 ? EV_WINGAME : EV_LEVELDONE); return; }

        animTime += dt;
        if (invuln > 0) invuln -= dt;
        if (coyote > 0) coyote -= dt;
        if (jumpBuf > 0) jumpBuf -= dt;
        if (atkCd > 0) atkCd -= dt;
        if (dashCd > 0) dashCd -= dt;
        if (hurtT > 0) hurtT -= dt;

        boolean dashing = dashT > 0;
        if (dashing) { dashT -= dt; vy = 0; }
        else {
            float ax = 0;
            if (inLeft) { ax -= 1; facing = false; }
            if (inRight) { ax += 1; facing = true; }
            float target = ax * MOVE;
            float rate = onGround ? 14 : 9;
            vx += (target - vx) * Math.min(1, dt * rate);
            if (Math.abs(vx) < 8 && ax == 0) vx = 0;
            vy += GRAV * dt;
            if (vy > MAX_FALL) vy = MAX_FALL;
        }

        if (qJump) jumpBuf = 0.12f;
        if (jumpBuf > 0 && (onGround || coyote > 0) && !dashing) {
            vy = -JUMP_V; onGround = false; coyote = 0; jumpBuf = 0;
            emit(EV_JUMP);
            burst(px + PW / 2, py + PH, 7, 0xFFe8dcc0, 130, 600, 0.35f, 3);
        }
        if (qDash && dashCd <= 0 && !dashing) {
            dashT = 0.18f; dashCd = 1.1f; invuln = Math.max(invuln, 0.25f);
            vx = (facing ? 1 : -1) * 720f;
            emit(EV_DASH);
            burst(px + PW / 2, py + PH / 2, 10, 0xFF7fe8ff, 200, 0, 0.3f, 3);
        }
        if (qAttack && atkCd <= 0 && !attacking) {
            attacking = true; atkT = 0.32f; atkCd = 0.45f;
            emit(EV_SWORD);
        }
        if (attacking) {
            atkT -= dt;
            if (atkT <= 0) attacking = false;
            else meleeHit();
        }

        moveCollide(dt, true);
        if (onGround) coyote = 0.1f;

        // run dust
        if (onGround && Math.abs(vx) > 200) {
            runDustT += dt;
            if (runDustT > 0.16f) { runDustT = 0; burst(px + PW / 2, py + PH, 2, 0xFFd9c9a8, 70, 400, 0.3f, 2.5f); }
        }

        // fall death
        if (py > th * TILE + 80) { hurt(1, true); if (!dead) { px = checkX; py = checkY - 10; vx = 0; vy = 0; } return; }

        // spikes
        for (Spike s : spikes) {
            if (overlap(px + 5, py + 10, PW - 10, PH - 10, s.x + 8, s.y + 14, TILE - 16, TILE - 14)) {
                hurt(1, false); break;
            }
        }
        // checkpoints
        for (Check c : checks) {
            if (!c.used && Math.abs(px + PW / 2 - c.x) < 40 && Math.abs(py + PH - c.y) < 90) {
                c.used = true; checkX = c.x - PW / 2; checkY = c.y - PH; hasCheck = true;
                emit(EV_CHECK);
                burst(c.x, c.y - 60, 14, 0xFF2dd4a8, 160, 200, 0.6f, 3);
            }
        }
        // hearts
        for (Heart h : hearts) {
            if (!h.taken && overlap(px, py, PW, PH, h.x - 14, h.y - 14, 28, 28)) {
                h.taken = true;
                if (hp < 3) hp++;
                else shards += 5;
                emit(EV_HEART);
                burst(h.x, h.y, 10, 0xFFff4d6b, 150, 200, 0.5f, 3);
            }
        }
        // goal
        if (overlap(px, py, PW, PH, goalX - 10, goalY - 130, 70, 130)) {
            if (goalOpen) { won = true; winT = 0; burst(px + PW / 2, py, 30, 0xFFffcf4d, 260, 300, 0.9f, 4); }
        }

        // animation select
        animFrame = (int) (animTime * 10);
        if (hurtT > 0) anim = Art.HURT;
        else if (attacking) anim = Art.ATTACK;
        else if (!onGround) anim = vy < 0 ? Art.JUMP : Art.FALL;
        else if (Math.abs(vx) > 30) anim = Art.WALK;
        else anim = Art.IDLE;
    }

    private void meleeHit() {
        float hx = facing ? px + PW - 6 : px - 64;
        float hy = py + 2, hw = 70, hh = PH;
        // vases
        for (Vase v : vases) {
            if (!v.broken && overlap(hx, hy, hw, hh, v.x, v.y - 60, 48, 60)) {
                v.broken = true; emit(EV_BREAK);
                burst(v.x + 24, v.y - 30, 12, 0xFFb5651d, 200, 700, 0.5f, 4);
                for (int i = 0; i < 3; i++) {
                    Coin c = new Coin(); c.x = v.x + 24; c.y = v.y - 40;
                    c.vx = (float) (Math.random() * 260 - 130); c.vy = -420;
                    c.physical = true; coins.add(c);
                }
            }
        }
        // ghouls
        for (Ghoul g : ghouls) {
            if (g.alive && overlap(hx, hy, hw, hh, g.x - 20, g.y - 40, 40, 44)) {
                g.hp--; g.flash = 0.25f;
                g.vx = (facing ? 1 : -1) * 260;
                burst(g.x, g.y - 24, 8, 0xFFFFe94d, 220, 300, 0.4f, 3);
                if (g.hp <= 0) { g.alive = false; emit(EV_STOMP); burst(g.x, g.y - 20, 16, 0xFF7a4fd0, 260, 600, 0.6f, 4); }
                else emit(EV_BOSSHIT);
            }
        }
        // bats
        for (Bat b : bats) {
            if (b.alive && overlap(hx, hy, hw, hh, b.x - 22, b.y - 16, 44, 32)) {
                b.alive = false; emit(EV_STOMP);
                burst(b.x, b.y, 12, 0xFF3fae9d, 220, 400, 0.5f, 3);
            }
        }
        // boss
        if (bossActive && !bossDead && overlap(hx, hy, hw, hh, bx - 60, by - 80, 130, 120)) {
            bossHp--; bossFlash = 0.3f; emit(EV_BOSSHIT);
            burst(bx, by - 20, 10, 0xFFFF6a3d, 260, 300, 0.5f, 4);
            if (bossHp <= 0) {
                bossDead = true; goalOpen = true; emit(EV_BOSSDIE);
                burst(bx, by - 20, 60, 0xFFFFcf4d, 420, 400, 1.2f, 5);
                burst(bx, by - 20, 30, 0xFFFF6a3d, 300, 300, 1.0f, 4);
            }
        }
    }

    private void hurt(int dmg, boolean noKnock) {
        if (invuln > 0 || dead || won || dashT > 0) return;
        hp -= dmg;
        hurtT = 0.4f; invuln = 1.2f;
        emit(EV_HURT);
        burst(px + PW / 2, py + PH / 2, 10, 0xFFFF4d4d, 240, 400, 0.5f, 3);
        if (hp <= 0) { dead = true; deadT = 0; }
        else if (!noKnock) { vy = -520; vx = (facing ? -1 : 1) * 260; }
    }

    public void respawnAtCheck() {
        px = checkX; py = checkY; vx = 0; vy = 0;
        hp = 3; dead = false; deadT = 0; invuln = 1.5f;
        // reset enemies to initial spots
        for (Ghoul g : ghouls) { g.alive = true; g.hp = 2; }
        for (Bat b : bats) { b.alive = true; b.hp = 1; b.x = b.ax; b.y = b.ay; b.diving = false; }
        for (Proj p : projs) p.alive = false;
        if (bossActive && !bossDead) { bossState = B_ENTER; bossStateT = 0; bossHp = bossMax; }
    }

    // ---------------- enemies ----------------
    private void updateGhouls(float dt) {
        for (Ghoul g : ghouls) {
            if (!g.alive) continue;
            g.t += dt;
            if (g.flash > 0) g.flash -= dt;
            // edge detect + patrol/chase
            float speed = 95;
            float dx = (px + PW / 2) - g.x;
            if (Math.abs(dx) < 260 && Math.abs(py - g.y) < 120) { speed = 175; g.dir = dx > 0 ? 1 : -1; }
            // turn at walls/edges
            float aheadX = g.x + g.dir * 22;
            boolean wallAhead = solidPx(aheadX + (g.dir > 0 ? 8 : -8), g.y - 20);
            boolean groundAhead = solidPx(aheadX, g.y + 6);
            if (wallAhead || !groundAhead) g.dir *= -1;
            g.vx = g.dir * speed;
            // gravity
            float oldX = g.x, oldY = g.y;
            g.x += g.vx * dt;
            // simple X collide
            if (solidPx(g.x + (g.dir > 0 ? 14 : -14), g.y - 20)) { g.x = oldX; g.dir *= -1; }
            g.y += 900 * dt;
            // land
            int guard = 0;
            while (!solidPx(g.x, g.y + 2) && guard++ < 8) g.y += 4;
            if (g.y > th * TILE + 100) { g.alive = false; continue; }
            // interact player
            if (!dead && !won && invuln <= 0) {
                if (overlap(px, py, PW, PH, g.x - 16, g.y - 40, 32, 42)) {
                    // stomp?
                    if (vy > 120 && py + PH - g.y < 22) {
                        g.hp--; g.flash = 0.25f; vy = -640; onGround = false;
                        burst(g.x, g.y - 30, 8, 0xFFFFe94d, 220, 300, 0.4f, 3);
                        if (g.hp <= 0) { g.alive = false; emit(EV_STOMP); burst(g.x, g.y - 20, 16, 0xFF7a4fd0, 260, 600, 0.6f, 4); }
                        else emit(EV_BOSSHIT);
                    } else hurt(1, false);
                }
            }
        }
    }

    private void updateBats(float dt) {
        for (Bat b : bats) {
            if (!b.alive) continue;
            b.t += dt;
            if (b.flash > 0) b.flash -= dt;
            float dx = (px + PW / 2) - b.x, dy = (py + PH / 2) - b.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (!b.diving && dist < 300) { b.diving = true; b.dvx = dx / dist * 300; b.dvy = dy / dist * 300; }
            if (b.diving) {
                b.x += b.dvx * dt; b.y += b.dvy * dt;
                if (dist > 480 || dist < 24) b.diving = false;
            } else {
                b.x = b.ax + (float) Math.sin(b.t * 1.3) * 90;
                b.y = b.ay + (float) Math.sin(b.t * 2.1) * 34;
            }
            if (!dead && !won && invuln <= 0 && overlap(px, py, PW, PH, b.x - 16, b.y - 12, 32, 24)) {
                if (vy > 120 && py + PH < b.y + 10) { b.alive = false; vy = -640; emit(EV_STOMP); burst(b.x, b.y, 12, 0xFF3fae9d, 220, 400, 0.5f, 3); }
                else hurt(1, false);
            }
        }
    }

    // ---------------- boss ----------------
    private void updateBoss(float dt) {
        if (level != 2 || bossDead) return;
        if (!bossActive) {
            if (px > 30 * TILE) {
                bossActive = true; bossState = B_ENTER; bossStateT = 0;
                bx = tw * TILE - 200; by = 120;
                hintText = "تنين الرمال غاضب! اضربه بسيفك عندما يقترب!"; hintT = 4f;
            } else return;
        }
        bossT += dt; bossStateT += dt;
        if (bossFlash > 0) bossFlash -= dt;
        float pcx = px + PW / 2;
        switch (bossState) {
            case B_ENTER:
                bx += ((tw * TILE - 420) - bx) * Math.min(1, dt * 2);
                by += (150 - by) * Math.min(1, dt * 2);
                if (bossStateT > 1.6f) { bossState = B_HOVER; bossStateT = 0; }
                break;
            case B_HOVER: {
                by = 150 + (float) Math.sin(bossT * 2) * 30;
                bx += (pcx - bx) * Math.min(1, dt * 0.8);
                if (bossStateT > 1.4f) {
                    bossStateT = 0;
                    if (bossHp <= bossMax / 2 && summonCount < 2 && batsAlive() < 2) { bossState = B_SUMMON; }
                    else if (Math.random() < 0.55) bossState = B_VOLLEY;
                    else bossState = B_TELE;
                }
                break;
            }
            case B_VOLLEY: {
                if (bossStateT > 0.5f && projsFired < volleys * 3) {
                    fireT += dt;
                    if (fireT > 0.28f) {
                        fireT = 0; projsFired++;
                        fireAt(pcx, py + PH / 2);
                        emit(EV_SHOOT);
                    }
                }
                if (projsFired >= volleys * 3 && bossStateT > 1.6f) { bossState = B_HOVER; bossStateT = 0; }
                if (bossStateT < 0.1f) { /* init on enter handled below */ }
                break;
            }
            case B_TELE:
                bx += (pcx - bx) * Math.min(1, dt * 3);
                by += ((py - 20) - by) * Math.min(1, dt * 3);
                if (bossStateT > 0.6f) { bossState = B_SWOOP; bossStateT = 0; swoopDir = pcx > bx ? 1 : -1; }
                break;
            case B_SWOOP:
                bx += swoopDir * 620 * dt;
                by += (float) Math.sin(bossT * 20) * 30 * dt;
                burst(bx - swoopDir * 40, by, 2, 0xFFFF6a3d, 120, 0, 0.3f, 4);
                if (bx < 60 || bx > tw * TILE - 60) { bossState = B_HOVER; bossStateT = 0; }
                if (bossStateT > 2.2f) { bossState = B_HOVER; bossStateT = 0; }
                break;
            case B_SUMMON:
                if (bossStateT > 0.6f && !summoned) {
                    summoned = true; summonCount++;
                    for (int i = 0; i < 2; i++) {
                        Bat b = new Bat();
                        b.ax = b.x = bx + (i == 0 ? -80 : 80); b.ay = b.y = by + 40;
                        bats.add(b);
                        burst(b.x, b.y, 10, 0xFF7c5cff, 200, 200, 0.5f, 3);
                    }
                }
                if (bossStateT > 1.4f) { bossState = B_HOVER; bossStateT = 0; }
                break;
        }
        // contact damage
        if (!dead && !won && invuln <= 0 && overlap(px, py, PW, PH, bx - 44, by - 60, 96, 100)) hurt(1, false);
        // clamp
        if (bx < 40) bx = 40;
        if (bx > tw * TILE - 40) bx = tw * TILE - 40;
        if (by < 60) by = 60;
        if (by > 420) by = 420;
    }

    private int batsAlive() { int n = 0; for (Bat b : bats) if (b.alive) n++; return n; }
    private int projsFired = 0, volleys = 2;
    private float fireT = 0;
    private int swoopDir = 1;
    private boolean summoned = false;

    // called when entering volley — reset counters (invoked from state transitions)
    private void enterVolleyIfNeeded() {}

    private void fireAt(float tx, float ty) {
        Proj p = null;
        for (Proj q : projs) if (!q.alive) { p = q; break; }
        if (p == null) { if (projs.size() > 24) return; p = new Proj(); projs.add(p); }
        p.alive = true; p.t = 0; p.x = bx; p.y = by - 10;
        float dx = tx - p.x, dy = ty - p.y;
        float d = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
        p.vx = dx / d * 400; p.vy = dy / d * 400;
    }

    private void updateProjs(float dt) {
        for (Proj p : projs) {
            if (!p.alive) continue;
            p.t += dt;
            p.x += p.vx * dt; p.y += p.vy * dt;
            burst(p.x, p.y, 1, 0xFFFF9a3d, 30, 0, 0.2f, 3);
            boolean hitWall = solidPx(p.x, p.y);
            if (!dead && !won && invuln <= 0 && overlap(px, py, PW, PH, p.x - 10, p.y - 10, 20, 20)) {
                p.alive = false; hurt(1, false); continue;
            }
            if (hitWall || p.t > 4 || p.x < 0 || p.x > tw * TILE) p.alive = false;
        }
    }

    private void updateCoins(float dt) {
        for (Coin c : coins) {
            if (c.taken) continue;
            c.t += dt;
            if (c.physical) {
                c.vy += GRAV * dt * 0.7f;
                c.x += c.vx * dt; c.y += c.vy * dt;
                if (solidPx(c.x, c.y + 10)) { c.y = ((int) ((c.y + 10) / TILE)) * TILE - 10; c.vy = 0; c.vx *= 0.9f; if (Math.abs(c.vx) < 5) c.physical = false; }
            }
            if (!dead && overlap(px - 6, py - 6, PW + 12, PH + 12, c.x - 13, c.y - 13, 26, 26)) {
                c.taken = true; shards++; emit(EV_COIN);
                burst(c.x, c.y, 6, 0xFFffcf4d, 140, 200, 0.35f, 3);
            }
        }
    }

    private void updatePlats(float dt) {
        for (MPlat m : mplats) {
            m.t += dt;
            m.dx = (float) Math.cos(m.t * m.speed) * m.range * m.speed;
            m.x = m.x0 + (float) Math.sin(m.t * m.speed) * m.range;
        }
    }

    private static boolean overlap(float x1, float y1, float w1, float h1, float x2, float y2, float w2, float h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    // boss state-enter hook: reset volley counters whenever state changes to VOLLEY
    public void notifyState() {}

    // ---------------- getters for renderer ----------------
    public int getHp() { return hp; }
    public boolean isDead() { return dead; }
    public boolean isWon() { return won; }
    public float getVx() { return vx; }
    public float getVy() { return vy; }
    public boolean isOnGround() { return onGround; }
    public boolean isFacingRight() { return facing; }
    public boolean isAttacking() { return attacking; }
    public boolean isInvuln() { return invuln > 0 && !dead; }
    public float getHintT() { return hintT; }
    public float getGoalX() { return goalX; }
    public float getGoalY() { return goalY; }
    public ArrayList<Ghoul> getGhouls() { return ghouls; }
    public ArrayList<Bat> getBats() { return bats; }
    public ArrayList<Coin> getCoins() { return coins; }
    public ArrayList<Vase> getVases() { return vases; }
    public ArrayList<Spike> getSpikes() { return spikes; }
    public ArrayList<Check> getChecks() { return checks; }
    public ArrayList<Heart> getHearts() { return hearts; }
    public ArrayList<Proj> getProjs() { return projs; }
    public ArrayList<MPlat> getMPlats() { return mplats; }
    public Part[] getParts() { return parts; }
    public char[][] getGrid() { return grid; }
    public int getTW() { return tw; }
    public int getTH() { return th; }
    public float getBx() { return bx; }
    public float getBy() { return by; }
    public float getBossFlash() { return bossFlash; }
    public int getBossState() { return bossState; }

    // fix volley counters on transitions — called from update loop wrapper
    private int lastBossState = -1;
    private void volleyGuard() {
        if (bossState != lastBossState) {
            lastBossState = bossState;
            if (bossState == B_VOLLEY) { projsFired = 0; fireT = 0.4f; volleys = bossHp <= bossMax / 2 ? 3 : 2; }
            if (bossState == B_SUMMON) summoned = false;
        }
    }

    // patch: call guard at start of boss update
    private void updateBossWrapped(float dt) { volleyGuard(); updateBoss(dt); }
}
