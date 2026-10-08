package com.noor.oasis;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * AudioSys — fully procedural audio, no files needed.
 * Music: pentatonic sequencer per theme. SFX: synthesized PCM, mixed in one thread.
 */
public class AudioSys {

    public static final int S_JUMP = 0, S_COIN = 1, S_HIT = 2, S_SWORD = 3, S_DIE = 4,
            S_WIN = 5, S_LOSE = 6, S_CHECK = 7, S_BREAK = 8, S_SHOOT = 9,
            S_BOSSHIT = 10, S_HEART = 11, S_DASH = 12, S_LAND = 13, S_CLICK = 14;

    private static final int SR = 22050;
    private float musicVol = 0.7f, sfxVol = 0.8f;

    private short[][] sfxBank;
    private final ConcurrentLinkedQueue<short[]> sfxQueue = new ConcurrentLinkedQueue<>();
    private Thread sfxThread;
    private volatile boolean sfxRun;

    private Thread musicThread;
    private volatile boolean musicRun;
    private volatile int musicTheme;

    // pentatonic-ish arabic hijaz flavor per theme (semitone offsets from root)
    private static final int[][] SCALES = {
            {0, 2, 3, 7, 8, 12, 14, 15},     // oasis: bright
            {0, 1, 5, 6, 7, 12, 13, 17},     // cave: dark
            {0, 1, 4, 5, 7, 8, 11, 12},      // throne: hijaz
    };
    private static final int[] MELODY = {0, 2, 4, 2, 5, 4, 2, 1, 0, 2, 4, 5, 7, 5, 4, 2};
    private static final int[] BASSN = {0, 0, 4, 4, 5, 5, 4, 3};

    public void init() {
        buildSfx();
        startSfxThread();
    }

    public void setVolumes(float m, float s) { musicVol = m; sfxVol = s; }

    public void play(int id) {
        if (id >= 0 && id < sfxBank.length && sfxBank[id] != null) sfxQueue.offer(sfxBank[id]);
    }

    public void startMusic(int theme) {
        if (musicRun && musicTheme == theme) return;
        stopMusic();
        musicTheme = theme; musicRun = true;
        musicThread = new Thread(musicLoop, "noor-music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void stopMusic() {
        musicRun = false;
        if (musicThread != null) { try { musicThread.join(400); } catch (Exception ignored) {} musicThread = null; }
    }

    public void release() {
        stopMusic();
        sfxRun = false;
        if (sfxThread != null) { try { sfxThread.join(400); } catch (Exception ignored) {} sfxThread = null; }
    }

    // ---------- SFX synthesis ----------
    private void buildSfx() {
        sfxBank = new short[15][];
        sfxBank[S_JUMP] = chirp(300, 700, 0.14f, 0.5f);
        sfxBank[S_COIN] = twoTone(1320, 1760, 0.07f, 0.07f, 0.45f);
        sfxBank[S_HIT] = noise(0.22f, 0.6f, true);
        sfxBank[S_SWORD] = noise(0.12f, 0.4f, false);
        sfxBank[S_DIE] = chirp(500, 90, 0.5f, 0.55f);
        sfxBank[S_WIN] = arp(new int[]{523, 659, 784, 1047}, 0.11f, 0.5f);
        sfxBank[S_LOSE] = arp(new int[]{392, 330, 262, 196}, 0.16f, 0.5f);
        sfxBank[S_CHECK] = twoTone(660, 990, 0.1f, 0.12f, 0.45f);
        sfxBank[S_BREAK] = noise(0.18f, 0.5f, false);
        sfxBank[S_SHOOT] = chirp(900, 240, 0.18f, 0.4f);
        sfxBank[S_BOSSHIT] = twoTone(220, 140, 0.08f, 0.08f, 0.55f);
        sfxBank[S_HEART] = arp(new int[]{523, 784}, 0.1f, 0.5f);
        sfxBank[S_DASH] = noise(0.16f, 0.35f, false);
        sfxBank[S_LAND] = noise(0.07f, 0.3f, false);
        sfxBank[S_CLICK] = twoTone(880, 660, 0.04f, 0.04f, 0.35f);
    }

    private short[] chirp(float f0, float f1, float dur, float amp) {
        int n = (int) (SR * dur);
        short[] b = new short[n];
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float f = f0 + (f1 - f0) * i / n;
            ph += 2 * Math.PI * f / SR;
            float env = 1 - (float) i / n;
            b[i] = (short) (Math.sin(ph) * amp * env * 32767);
        }
        return b;
    }

    private short[] twoTone(float f0, float f1, float d0, float d1, float amp) {
        int n0 = (int) (SR * d0), n1 = (int) (SR * d1);
        short[] b = new short[n0 + n1];
        for (int i = 0; i < n0; i++) b[i] = (short) (Math.sin(2 * Math.PI * f0 * i / SR) * amp * 32767);
        for (int i = 0; i < n1; i++) b[n0 + i] = (short) (Math.sin(2 * Math.PI * f1 * i / SR) * amp * (1 - (float) i / n1) * 32767);
        return b;
    }

    private short[] arp(int[] freqs, float step, float amp) {
        int n = (int) (SR * step * freqs.length);
        short[] b = new short[n];
        for (int s = 0; s < freqs.length; s++) {
            int off = (int) (SR * step * s);
            int len = (int) (SR * step);
            for (int i = 0; i < len && off + i < n; i++)
                b[off + i] = (short) (Math.sin(2 * Math.PI * freqs[s] * i / SR) * amp * (1 - (float) i / len) * 32767);
        }
        return b;
    }

    private short[] noise(float dur, float amp, boolean low) {
        int n = (int) (SR * dur);
        short[] b = new short[n];
        float last = 0;
        for (int i = 0; i < n; i++) {
            float w = (float) (Math.random() * 2 - 1);
            if (low) { last = last * 0.7f + w * 0.3f; w = last * 2; }
            b[i] = (short) (w * amp * (1 - (float) i / n) * 32767);
        }
        return b;
    }

    private void startSfxThread() {
        sfxRun = true;
        sfxThread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    int minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                    AudioTrack tr = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(minBuf, 8192), AudioTrack.MODE_STREAM);
                    tr.play();
                    short[] mix = new short[2048];
                    short[][] active = new short[8][];
                    int[] pos = new int[8];
                    while (sfxRun) {
                        short[] next;
                        while ((next = sfxQueue.poll()) != null) {
                            boolean placed = false;
                            for (int i = 0; i < active.length; i++)
                                if (active[i] == null) { active[i] = next; pos[i] = 0; placed = true; break; }
                            if (!placed) { active[0] = next; pos[0] = 0; }
                        }
                        boolean any = false;
                        for (int i = 0; i < mix.length; i++) mix[i] = 0;
                        for (int v = 0; v < active.length; v++) {
                            if (active[v] == null) continue;
                            any = true;
                            for (int i = 0; i < mix.length && pos[v] < active[v].length; i++, pos[v]++)
                                mix[i] += active[v][pos[v]] / 8;
                            if (pos[v] >= active[v].length) active[v] = null;
                        }
                        if (any) {
                            float g = sfxVol;
                            if (g != 1f) for (int i = 0; i < mix.length; i++) mix[i] = (short) (mix[i] * g);
                            tr.write(mix, 0, mix.length);
                        } else {
                            try { Thread.sleep(15); } catch (Exception ignored) {}
                        }
                    }
                    tr.stop(); tr.release();
                } catch (Exception ignored) {}
            }
        }, "noor-sfx");
        sfxThread.setDaemon(true);
        sfxThread.start();
    }

    // ---------- music sequencer ----------
    private final Runnable musicLoop = new Runnable() {
        @Override public void run() {
            try {
                int minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioTrack tr = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuf, SR / 2), AudioTrack.MODE_STREAM);
                tr.play();
                int step = 0;
                float stepDur = 0.21f; // ~140bpm 16ths-ish
                int[] scale = SCALES[musicTheme % SCALES.length];
                int root = musicTheme == 1 ? 110 : 165; // A2 / E3
                while (musicRun) {
                    int n = (int) (SR * stepDur);
                    short[] buf = new short[n];
                    int mi = MELODY[step % MELODY.length];
                    int bi = BASSN[(step / 2) % BASSN.length];
                    float mf = root * 2 * semi(scale[mi % scale.length] + 12 * (mi / scale.length));
                    float bf = root * semi(scale[bi % scale.length]);
                    boolean hat = (step % 2 == 1);
                    for (int i = 0; i < n; i++) {
                        float t = (float) i / SR;
                        float lead = (float) (Math.sin(2 * Math.PI * mf * t) * 0.30
                                + Math.sin(2 * Math.PI * mf * 2 * t) * 0.08);
                        float bass = (float) Math.sin(2 * Math.PI * bf * t) * 0.26f;
                        float h = hat && i < n / 4 ? (float) (Math.random() * 2 - 1) * 0.06f : 0;
                        float env = 1 - (float) i / n * 0.5f;
                        buf[i] = (short) ((lead + bass + h) * env * musicVol * 32767 * 0.5f);
                    }
                    tr.write(buf, 0, n);
                    step++;
                }
                tr.stop(); tr.release();
            } catch (Exception ignored) {}
        }
    };

    private static float semi(int s) { return (float) Math.pow(2, s / 12.0); }
}
