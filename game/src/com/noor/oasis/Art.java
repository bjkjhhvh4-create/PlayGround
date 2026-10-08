package com.noor.oasis;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

/**
 * Art — procedural sprite renderer.
 * All characters/tiles/backgrounds are drawn with vector frames (consistent
 * art style, zero asset downloads). Poses are frame-based like a sprite sheet:
 * IDLE/WALK/RUN/JUMP/FALL/ATTACK/HURT/DEATH/WIN.
 */
public class Art {

    public static final int THEME_OASIS = 0;
    public static final int THEME_CAVE = 1;
    public static final int THEME_THRONE = 2;

    // Player animation ids
    public static final int IDLE = 0, WALK = 1, JUMP = 2, FALL = 3,
            ATTACK = 4, HURT = 5, DEATH = 6, WIN = 7;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();

    // Palette: hero "Noor" — teal robe, gold headband, sand scarf (consistent everywhere)
    private static final int ROBE = 0xFF1fa08a, ROBE_D = 0xFF14705f;
    private static final int SKIN = 0xFFf2c89b, HAIR = 0xFF3a2a1c;
    private static final int GOLD = 0xFFffcf4d, SCARF = 0xFFe8b34a;
    private static final int STEEL = 0xFFcfd8e3, STEEL_D = 0xFF8b98a8;

    private static long lastBgKey = -1;
    private static LinearGradient skyGrad;

    // ---------- backgrounds (parallax) ----------
    public static void drawBackground(Canvas c, int theme, float camX, int w, int h, float time) {
        long key = ((long) theme << 32) | w * 100003L + h;
        if (key != lastBgKey || skyGrad == null) {
            int top, bot;
            if (theme == THEME_CAVE) { top = 0xFF12082b; bot = 0xFF3b1d5e; }
            else if (theme == THEME_THRONE) { top = 0xFF2b0f3a; bot = 0xFFc1502e; }
            else { top = 0xFF3db2e8; bot = 0xFFffe3a3; }
            skyGrad = new LinearGradient(0, 0, 0, h, top, bot, Shader.TileMode.CLAMP);
            lastBgKey = key;
        }
        FILL.setShader(skyGrad);
        c.drawRect(0, 0, w, h, FILL);
        FILL.setShader(null);

        // sun / moon / crystals glow
        P.setColor(theme == THEME_OASIS ? 0xFFFFF3c4 : (theme == THEME_CAVE ? 0xFFb388ff : 0xFFFF8a5c));
        float sx = w * 0.78f - (camX * 0.02f % (w + 400));
        if (sx < -100) sx += w + 400;
        c.drawCircle(sx, h * 0.22f, h * 0.09f, P);
        P.setColor(0x55FFFFFF);
        c.drawCircle(sx, h * 0.22f, h * 0.13f, P);

        // far dunes layer (0.2x)
        drawDuneLayer(c, camX * 0.2f, w, h, h * 0.62f, h * 0.10f,
                theme == THEME_CAVE ? 0xFF2a1650 : 0xFFd9a45b, 0.9f, time);
        // mid layer with palms/crystals silhouettes (0.45x)
        int midCol = theme == THEME_CAVE ? 0xFF241245 : (theme == THEME_THRONE ? 0xFF8a3b4a : 0xFFc98d4e);
        drawDuneLayer(c, camX * 0.45f, w, h, h * 0.74f, h * 0.12f, midCol, 1.7f, time);
        if (theme == THEME_OASIS) drawPalms(c, camX * 0.45f, w, h);
        else drawCrystals(c, camX * 0.45f, w, h, theme);
        // near dark strip behind tiles (0.7x)
        drawDuneLayer(c, camX * 0.7f, w, h, h * 0.88f, h * 0.10f,
                theme == THEME_CAVE ? 0xFF1a0e33 : 0xFFa86f3c, 2.6f, time);
    }

    private static void drawDuneLayer(Canvas c, float off, int w, int h, float baseY, float amp, int col, float freq, float time) {
        P.setColor(col);
        PATH.reset();
        PATH.moveTo(0, h);
        PATH.lineTo(0, baseY);
        for (int x = 0; x <= w + 32; x += 32) {
            float wx = x + off;
            float y = baseY + (float) (Math.sin(wx * 0.008 * freq + time * 0.15) * amp
                    + Math.sin(wx * 0.021 * freq) * amp * 0.35);
            PATH.lineTo(x, y);
        }
        PATH.lineTo(w, h);
        PATH.close();
        c.drawPath(PATH, P);
    }

    private static void drawPalms(Canvas c, float off, int w, int h) {
        P.setColor(0xFF6b4a2a);
        P.setStrokeWidth(10); P.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 6; i++) {
            float bx = ((i * 613 + 200 - off) % (w + 400) + (w + 400)) % (w + 400) - 200;
            float by = h * 0.78f;
            c.drawLine(bx, by, bx + 24, by - 120, P);
            P.setColor(0xFF2e7d46);
            for (int k = 0; k < 5; k++) {
                double a = Math.PI * (0.15 + 0.175 * k);
                c.drawLine(bx + 24, by - 120,
                        bx + 24 + (float) Math.cos(a) * 70, by - 120 - (float) Math.sin(a) * 34, P);
            }
            P.setColor(0xFF6b4a2a);
        }
        P.setStrokeWidth(1);
    }

    private static void drawCrystals(Canvas c, float off, int w, int h, int theme) {
        int col = theme == THEME_CAVE ? 0xFF7c5cff : 0xFFff7a5c;
        for (int i = 0; i < 8; i++) {
            float bx = ((i * 457 + 100 - off) % (w + 300) + (w + 300)) % (w + 300) - 150;
            float bh = 60 + (i * 37 % 90);
            P.setColor(col); P.setAlpha(140);
            PATH.reset();
            PATH.moveTo(bx - 22, h * 0.82f); PATH.lineTo(bx, h * 0.82f - bh);
            PATH.lineTo(bx + 22, h * 0.82f); PATH.close();
            c.drawPath(PATH, P);
            P.setAlpha(255);
        }
    }

    // ---------- tiles ----------
    public static void drawTile(Canvas c, char t, float x, float y, float s, int theme) {
        if (t == '#' || t == '=') {
            int top = theme == THEME_CAVE ? 0xFF6a4bb3 : (theme == THEME_THRONE ? 0xFFb65a3a : 0xFF8a5a2e);
            int body = theme == THEME_CAVE ? 0xFF3d2a6e : (theme == THEME_THRONE ? 0xFF7a3327 : 0xFF6b4423);
            int edge = theme == THEME_CAVE ? 0xFF9d7bff : (theme == THEME_THRONE ? 0xFFff9a5c : 0xFF3fae6a);
            P.setColor(body);
            c.drawRect(x, y, x + s, y + s, P);
            P.setColor(top);
            c.drawRect(x + 2, y + 2, x + s - 2, y + s - 2, P);
            if (t == '#') { // grassy / glowing top edge only on solid tops (drawn always, cheap)
                P.setColor(edge);
                c.drawRect(x, y, x + s, y + s * 0.16f, P);
            } else { // platform: full plank
                P.setColor(edge);
                c.drawRect(x, y, x + s, y + s * 0.22f, P);
            }
        }
    }

    public static void drawSpikes(Canvas c, float x, float y, float s) {
        P.setColor(0xFFd7dee8);
        int n = 3;
        for (int i = 0; i < n; i++) {
            float x0 = x + s * i / n;
            PATH.reset();
            PATH.moveTo(x0, y + s); PATH.lineTo(x0 + s / n / 2, y + s * 0.15f);
            PATH.lineTo(x0 + s / n, y + s); PATH.close();
            c.drawPath(PATH, P);
        }
        P.setColor(0xFF8b98a8);
        c.drawRect(x, y + s * 0.85f, x + s, y + s, P);
    }

    // ---------- player "Noor" ----------
    public static void drawPlayer(Canvas c, float cx, float feetY, float scale,
                                 int anim, int frame, int facing, float time, boolean flash) {
        c.save();
        c.translate(cx, feetY);
        c.scale(facing * scale, scale);
        float ph = time * 10f; // walk phase
        float legA = 0, legB = 0, bodyBob = 0, armA = 0;
        boolean lying = false, kneel = false;
        switch (anim) {
            case IDLE: bodyBob = (float) Math.sin(time * 2.2) * 1.6f; armA = (float) Math.sin(time * 2.2) * 3; break;
            case WALK: legA = (float) Math.sin(ph) * 22; legB = (float) Math.sin(ph + Math.PI) * 22;
                bodyBob = (float) Math.abs(Math.sin(ph)) * -2f; armA = (float) Math.sin(ph + Math.PI) * 16; break;
            case JUMP: legA = -24; legB = 14; armA = -30; break;
            case FALL: legA = 10; legB = 20; armA = 26; break;
            case ATTACK: legA = 12; legB = -10; armA = -70 - (frame % 3) * 14; break;
            case HURT: legA = -8; legB = 8; armA = 40; bodyBob = -2; break;
            case DEATH: lying = true; break;
            case WIN: armA = -150; bodyBob = (float) Math.abs(Math.sin(time * 5)) * -6f; break;
        }
        int robe = flash ? 0xFFFFFFFF : ROBE;
        int skin = flash ? 0xFFFFFFFF : SKIN;
        if (lying) {
            // fallen hero
            P.setColor(robe);
            c.drawRect(-22, -12, 22, 0, P);
            P.setColor(skin);
            c.drawCircle(28, -8, 9, P);
            P.setColor(flash ? 0xFFFFFFFF : HAIR);
            c.drawArc(19, -17, 37, 1, 90, 180, true, P);
            c.restore();
            return;
        }
        if (kneel) { c.restore(); return; }
        float by = bodyBob;
        // legs
        P.setColor(flash ? 0xFFFFFFFF : HAIR);
        P.setStrokeWidth(7); P.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(-3, -22 + by, -3 + legA * 0.35f, 0, P);
        c.drawLine(3, -22 + by, 3 + legB * 0.35f, 0, P);
        P.setStrokeWidth(1);
        // robe body
        P.setColor(robe);
        PATH.reset();
        PATH.moveTo(-11, -22 + by); PATH.lineTo(11, -22 + by);
        PATH.lineTo(14, -46 + by); PATH.lineTo(-14, -46 + by); PATH.close();
        c.drawPath(PATH, P);
        P.setColor(flash ? 0xFFFFFFFF : ROBE_D);
        c.drawRect(-14, -30 + by, -9, -22 + by, P); // shade
        // scarf flutter
        P.setColor(flash ? 0xFFFFFFFF : SCARF);
        float fl = (float) Math.sin(time * 7) * 4 - 6;
        PATH.reset();
        PATH.moveTo(-8, -44 + by); PATH.lineTo(-26, -40 + by + fl); PATH.lineTo(-8, -36 + by);
        PATH.close();
        c.drawPath(PATH, P);
        // back arm
        P.setColor(skin); P.setStrokeWidth(6);
        c.drawLine(0, -42 + by, 10 + armA * 0.1f, -30 + by + armA * 0.12f, P);
        // head
        P.setColor(skin);
        c.drawCircle(0, -55 + by, 10, P);
        // hair
        P.setColor(flash ? 0xFFFFFFFF : HAIR);
        c.drawArc(-10, -67 + by, 10, -47 + by, 180, 180, true, P);
        // gold headband (signature)
        P.setColor(flash ? 0xFFFFFFFF : GOLD);
        c.drawRect(-10, -60 + by, 10, -56 + by, P);
        P.setColor(0xFFc98a1b);
        c.drawCircle(0, -58 + by, 2.2f, P);
        // eye
        P.setColor(0xFF222222);
        c.drawCircle(4.5f, -54 + by, 1.6f, P);
        // front arm + sword
        float hx = 10 + armA * 0.16f, hy = -30 + by + armA * 0.16f;
        P.setColor(skin); P.setStrokeWidth(6);
        c.drawLine(0, -42 + by, hx, hy, P);
        P.setStrokeWidth(1);
        if (anim == ATTACK) {
            float sa = (float) Math.toRadians(-50 - (frame % 3) * 30);
            float sx = hx + (float) Math.cos(sa) * 30, sy = hy + (float) Math.sin(sa) * 30;
            P.setColor(STEEL); P.setStrokeWidth(5);
            c.drawLine(hx, hy, sx, sy, P);
            P.setStrokeWidth(1);
            P.setColor(GOLD);
            c.drawCircle(hx, hy, 3.4f, P);
            // swoosh
            P.setColor(0x88FFFFFF);
            c.drawArc(hx - 34, hy - 34, hx + 34, hy + 34, -90, 70, true, P);
        }
        c.restore();
    }

    // ---------- enemies ----------
    public static void drawGhoul(Canvas c, float cx, float feetY, float scale, float time, int facing, boolean flash) {
        c.save(); c.translate(cx, feetY); c.scale(facing * scale, scale);
        float bob = (float) Math.sin(time * 6) * 2;
        int body = flash ? 0xFFFFFFFF : 0xFF7a4fd0;
        int dark = flash ? 0xFFFFFFFF : 0xFF4a2f8a;
        P.setColor(body);
        c.drawOval(-14, -34 + bob, 14, 0, P);           // round body
        P.setColor(dark);
        c.drawOval(-14, -14, 14, 0, P);
        // feet shuffle
        P.setStrokeWidth(6); P.setStrokeCap(Paint.Cap.ROUND);
        float f = (float) Math.sin(time * 10) * 7;
        c.drawLine(-6, -8, -6 + f, 0, P);
        c.drawLine(6, -8, 6 - f, 0, P);
        P.setStrokeWidth(1);
        // horns
        P.setColor(0xFFe8e0c8);
        PATH.reset(); PATH.moveTo(-10, -32 + bob); PATH.lineTo(-16, -46 + bob); PATH.lineTo(-5, -36 + bob); PATH.close(); c.drawPath(PATH, P);
        PATH.reset(); PATH.moveTo(10, -32 + bob); PATH.lineTo(16, -46 + bob); PATH.lineTo(5, -36 + bob); PATH.close(); c.drawPath(PATH, P);
        // glowing eyes
        P.setColor(flash ? 0xFFFFFFFF : 0xFFFFe94d);
        c.drawCircle(-4, -26 + bob, 3, P);
        c.drawCircle(5, -26 + bob, 3, P);
        P.setColor(0xFF550000);
        c.drawCircle(-4, -26 + bob, 1.2f, P);
        c.drawCircle(5, -26 + bob, 1.2f, P);
        c.restore();
    }

    public static void drawBat(Canvas c, float cx, float cy, float scale, float time, boolean flash) {
        c.save(); c.translate(cx, cy); c.scale(scale, scale);
        float flap = (float) Math.sin(time * 14) * 0.9f;
        int body = flash ? 0xFFFFFFFF : 0xFF3fae9d;
        P.setColor(body);
        // wings
        for (int s = -1; s <= 1; s += 2) {
            PATH.reset();
            PATH.moveTo(s * 4, -4);
            PATH.lineTo(s * 26, -18 - flap * 14);
            PATH.lineTo(s * 20, 2);
            PATH.lineTo(s * 8, 2);
            PATH.close();
            c.drawPath(PATH, P);
        }
        c.drawOval(-7, -10, 7, 6, P);
        // ears + eyes
        P.setColor(flash ? 0xFFFFFFFF : 0xFF1d5a52);
        PATH.reset(); PATH.moveTo(-6, -8); PATH.lineTo(-9, -18); PATH.lineTo(-2, -10); PATH.close(); c.drawPath(PATH, P);
        PATH.reset(); PATH.moveTo(6, -8); PATH.lineTo(9, -18); PATH.lineTo(2, -10); PATH.close(); c.drawPath(PATH, P);
        P.setColor(0xFFFF4d4d);
        c.drawCircle(-2.6f, -3, 1.8f, P);
        c.drawCircle(2.6f, -3, 1.8f, P);
        c.restore();
    }

    public static void drawBoss(Canvas c, float cx, float cy, float scale, float time, int facing, boolean flash, float hpFrac) {
        c.save(); c.translate(cx, cy); c.scale(facing * scale, scale);
        float w = (float) Math.sin(time * 3) * 6;
        int body = flash ? 0xFFFFFFFF : 0xFFc1502e;
        int belly = flash ? 0xFFFFFFFF : 0xFFffcf7a;
        // tail segments
        P.setColor(body);
        for (int i = 0; i < 4; i++) {
            float tx = -34 - i * 20, ty = (float) Math.sin(time * 3 + i * 0.9) * 8;
            c.drawCircle(tx, ty, 16 - i * 2.4f, P);
        }
        // wings
        float flap = (float) Math.sin(time * 5) * 16;
        P.setColor(flash ? 0xFFFFFFFF : 0xFF8a2f3a);
        for (int s = -1; s <= 1; s += 2) {
            PATH.reset();
            PATH.moveTo(s * 6, -20);
            PATH.lineTo(s * 52, -64 - flap);
            PATH.lineTo(s * 40, -8);
            PATH.close();
            c.drawPath(PATH, P);
        }
        // body
        P.setColor(body);
        c.drawOval(-30, -34, 30, 22, P);
        P.setColor(belly);
        c.drawOval(-18, -8, 18, 22, P);
        // head
        P.setColor(body);
        c.drawCircle(32, -30 + w * 0.3f, 17, P);
        // horns + jaw
        P.setColor(0xFFffe9b0);
        PATH.reset(); PATH.moveTo(24, -44); PATH.lineTo(16, -62); PATH.lineTo(32, -48); PATH.close(); c.drawPath(PATH, P);
        P.setColor(flash ? 0xFFFFFFFF : 0xFF7a2018);
        c.drawRect(24, -20 + w * 0.3f, 48, -12 + w * 0.3f, P);
        // eye (glows redder as HP drops)
        P.setColor(hpFrac < 0.35f ? 0xFFFF2222 : 0xFFFFe94d);
        c.drawCircle(36, -34 + w * 0.3f, 5, P);
        P.setColor(0xFF330000);
        c.drawCircle(36, -34 + w * 0.3f, 2, P);
        c.restore();
    }

    public static void drawFireball(Canvas c, float x, float y, float r, float time) {
        P.setColor(0x66ff6a00);
        c.drawCircle(x, y, r * 1.7f, P);
        P.setColor(0xFFff9a3d);
        c.drawCircle(x, y, r * 1.2f, P);
        P.setColor(0xFFffe94d);
        c.drawCircle(x, y, r * 0.6f, P);
    }

    // ---------- items / objects ----------
    public static void drawShard(Canvas c, float cx, float cy, float r, float time) {
        float sq = 0.55f + 0.45f * Math.abs((float) Math.sin(time * 3 + cx * 0.01));
        c.save(); c.translate(cx, cy + (float) Math.sin(time * 2.4 + cy) * 3); c.scale(sq, 1);
        P.setColor(0x66ffcf4d);
        c.drawCircle(0, 0, r * 1.5f, P);
        P.setColor(0xFFffcf4d);
        PATH.reset();
        PATH.moveTo(0, -r); PATH.lineTo(r * 0.7f, 0); PATH.lineTo(0, r); PATH.lineTo(-r * 0.7f, 0);
        PATH.close(); c.drawPath(PATH, P);
        P.setColor(0xFFfff6c4);
        PATH.reset();
        PATH.moveTo(0, -r * 0.55f); PATH.lineTo(r * 0.35f, 0); PATH.lineTo(0, r * 0.55f); PATH.lineTo(-r * 0.35f, 0);
        PATH.close(); c.drawPath(PATH, P);
        c.restore();
    }

    public static void drawHeart(Canvas c, float cx, float cy, float r, float time) {
        float s = 1 + 0.08f * (float) Math.sin(time * 4);
        c.save(); c.translate(cx, cy); c.scale(s, s);
        P.setColor(0xFFff4d6b);
        c.drawCircle(-r * 0.45f, -r * 0.2f, r * 0.55f, P);
        c.drawCircle(r * 0.45f, -r * 0.2f, r * 0.55f, P);
        PATH.reset();
        PATH.moveTo(-r * 0.92f, 0); PATH.lineTo(0, r); PATH.lineTo(r * 0.92f, 0);
        PATH.lineTo(r * 0.45f, -r * 0.2f); PATH.lineTo(-r * 0.45f, -r * 0.2f); PATH.close();
        c.drawPath(PATH, P);
        c.restore();
    }

    public static void drawCheckpoint(Canvas c, float x, float baseY, float s, boolean active, float time) {
        P.setColor(0xFF5a4632); P.setStrokeWidth(6);
        c.drawLine(x, baseY, x, baseY - s * 2.2f, P);
        P.setStrokeWidth(1);
        float wave = active ? (float) Math.sin(time * 6) * 5 : 0;
        P.setColor(active ? 0xFF2dd4a8 : 0xFF8b98a8);
        PATH.reset();
        PATH.moveTo(x, baseY - s * 2.2f);
        PATH.lineTo(x + s * 1.2f, baseY - s * 2.0f + wave);
        PATH.lineTo(x, baseY - s * 1.6f);
        PATH.close(); c.drawPath(PATH, P);
        if (active) {
            P.setColor(0x552dd4a8);
            c.drawCircle(x, baseY - s, s * (1 + 0.1f * (float) Math.sin(time * 4)), P);
        }
    }

    public static void drawGoal(Canvas c, float x, float baseY, float s, float time) {
        // glowing gate
        P.setColor(0x44ffcf4d);
        c.drawRect(x - s * 0.2f, baseY - s * 2.6f, x + s * 1.2f, baseY, P);
        P.setColor(0xFF8a5a2e); P.setStrokeWidth(8);
        c.drawLine(x, baseY, x, baseY - s * 2.6f, P);
        c.drawLine(x + s, baseY, x + s, baseY - s * 2.6f, P);
        c.drawLine(x - 6, baseY - s * 2.6f, x + s + 6, baseY - s * 2.6f, P);
        P.setStrokeWidth(1);
        P.setColor(0xFFffcf4d);
        float tw = 0.6f + 0.4f * (float) Math.sin(time * 5);
        P.setAlpha((int) (120 + 100 * tw));
        c.drawRect(x + 6, baseY - s * 2.5f, x + s - 6, baseY - 4, P);
        P.setAlpha(255);
    }

    public static void drawVase(Canvas c, float x, float baseY, float s) {
        P.setColor(0xFFb5651d);
        c.drawOval(x, baseY - s * 1.1f, x + s, baseY, P);
        P.setColor(0xFF8a4a12);
        c.drawRect(x + s * 0.3f, baseY - s * 1.4f, x + s * 0.7f, baseY - s * 1.0f, P);
        P.setColor(0xFFffcf4d);
        c.drawRect(x + s * 0.15f, baseY - s * 0.7f, x + s * 0.85f, baseY - s * 0.5f, P);
    }
}
