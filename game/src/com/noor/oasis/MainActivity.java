package com.noor.oasis;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** MainActivity — menus, story intro, settings, save, Arabic TTS voice. */
public class MainActivity extends Activity implements GameView.Listener {

    private static final String PREF = "noor_save";
    private SharedPreferences pref;
    private FrameLayout root;
    private AudioSys audio = new AudioSys();
    private GameView gameView;
    private TextToSpeech tts;
    private MediaPlayer voicePlayer;
    private float voiceVol = 0.8f;
    private int currentLevel;
    private int runShards; // shards held in current run

    private static final String STORY =
            "منذ زمنٍ بعيد، كانت واحة النور مشرقةً بشمسٍ ذهبية دافئة.\n\n" +
            "وفي ليلةٍ مظلمة، هجم تنين الرمال وسرق شظايا الشمس، " +
            "ونشر غيلان الرمال في كل مكان.\n\n" +
            "أنت (نور)، حارس الواحة الصغير.. اجمع شظايا الشمس، " +
            "اعبر الواحة وكهف البلور، واهزم التنين لتعيد النور!";

    private static final String WIN_STORY =
            "سقط تنين الرمال! عادت شظايا الشمس إلى السماء، " +
            "وأشرقت الواحة من جديد.\n\nشكرًا لك يا نور.. يا حارس الواحة!";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        pref = getSharedPreferences(PREF, MODE_PRIVATE);
        voiceVol = pref.getFloat("voice", 0.8f);
        audio.init();
        audio.setVolumes(pref.getFloat("music", 0.7f), pref.getFloat("sfx", 0.8f));
        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(0xFF1a0f2e);
        setContentView(root);
        initTts();
        showMenu();
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int st) {
                    if (st == TextToSpeech.SUCCESS) {
                        try {
                            Locale ar = new Locale("ar");
                            int r = tts.setLanguage(ar);
                            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                                tts.setLanguage(Locale.getDefault());
                        } catch (Exception ignored) {}
                    }
                }
            });
        } catch (Exception ignored) {}
    }

    /** Play voice asset if present, else Arabic TTS. Files in assets/voice/ override TTS. */
    private void speak(String asset, String text) {
        stopVoice();
        if (voiceVol <= 0.01f) return;
        try {
            AssetFileDescriptor fd = getAssets().openFd("voice/" + asset);
            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            fd.close();
            voicePlayer.setVolume(voiceVol, voiceVol);
            voicePlayer.prepare();
            voicePlayer.start();
            return;
        } catch (Exception ignored) { /* no asset -> TTS */ }
        try {
            if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "noor");
        } catch (Exception ignored) {}
    }

    private void stopVoice() {
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
        try { if (voicePlayer != null) { voicePlayer.stop(); voicePlayer.release(); } } catch (Exception ignored) {}
        voicePlayer = null;
    }

    @Override public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) {
            View d = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            d.setSystemUiVisibility(flags);
        }
    }

    @Override protected void onPause() { super.onPause(); stopVoice(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        stopVoice();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        audio.release();
    }

    // ---------------- UI builders ----------------
    private LinearLayout screen() {
        LinearLayout L = new LinearLayout(this);
        L.setOrientation(LinearLayout.VERTICAL);
        L.setGravity(Gravity.CENTER);
        L.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        L.setPadding(40, 30, 40, 30);
        L.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return L;
    }

    private TextView title(String t, int size) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(size);
        v.setTextColor(0xFFffcf4d); v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, 10, 0, 10);
        return v;
    }

    private TextView body(String t, int size) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(size);
        v.setTextColor(0xFFFFFFFF);
        v.setGravity(Gravity.CENTER);
        v.setLineSpacing(6, 1.15f);
        v.setPadding(10, 10, 10, 10);
        return v;
    }

    private Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t); b.setTextSize(20);
        b.setTextColor(0xFF1a0f2e);
        b.setTypeface(null, Typeface.BOLD);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0xFFffcf4d); d.setCornerRadius(28);
        if (Build.VERSION.SDK_INT >= 16) b.setBackground(d);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(30, 10, 30, 10);
        b.setLayoutParams(p);
        b.setOnClickListener(l);
        b.setPadding(20, 18, 20, 18);
        return b;
    }

    private Button btnGhost(String t, View.OnClickListener l) {
        Button b = btn(t, l);
        GradientDrawable d = new GradientDrawable();
        d.setColor(0x00000000); d.setCornerRadius(28);
        d.setStroke(3, 0xFFffcf4d);
        if (Build.VERSION.SDK_INT >= 16) b.setBackground(d);
        b.setTextColor(0xFFffcf4d);
        return b;
    }

    private void setScreen(View v) { root.removeAllViews(); root.addView(v); }

    // ---------------- screens ----------------
    private void showMenu() {
        stopVoice();
        audio.startMusic(0);
        LinearLayout L = screen();
        L.addView(title("نور: حارس الواحة", 38));
        L.addView(body("مغامرة منصات عربية — اجمع شظايا الشمس واهزم تنين الرمال!", 17));
        int best = pref.getInt("best", 0);
        if (best > 0) L.addView(body("أفضل حصيلة شظايا: " + best, 16));
        L.addView(btn("لعب جديد", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showIntro(0, 0); }
        }));
        final int unlocked = pref.getInt("unlocked", 0);
        if (unlocked > 0 || pref.getInt("best", 0) > 0) {
            L.addView(btn("متابعة (المرحلة " + (unlocked + 1) + ")", new View.OnClickListener() {
                @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showIntro(unlocked, pref.getInt("best", 0)); }
            }));
        }
        L.addView(btnGhost("اختيار المرحلة", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showLevels(); }
        }));
        L.addView(btnGhost("الإعدادات", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showSettings(false); }
        }));
        L.addView(btnGhost("حول اللعبة", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showAbout(); }
        }));
        setScreen(L);
    }

    private void showLevels() {
        LinearLayout L = screen();
        L.addView(title("اختيار المرحلة", 30));
        final int unlocked = pref.getInt("unlocked", 0);
        String[] names = {"1- واحة النخيل", "2- كهف البلور", "3- عرش الرمال (الزعيم)"};
        for (int i = 0; i < 3; i++) {
            final int lv = i;
            boolean open = i <= unlocked;
            Button b = open ? btn(names[i], new View.OnClickListener() {
                @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showIntro(lv, 0); }
            }) : btnGhost(names[i] + " (مغلقة)", null);
            b.setEnabled(open);
            L.addView(b);
        }
        L.addView(btnGhost("رجوع", new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        }));
        setScreen(L);
    }

    private void showIntro(final int level, final int shards) {
        LinearLayout L = screen();
        L.addView(title(level == 0 ? "بداية الحكاية" : Game.LEVEL_NAMES[level], 30));
        ScrollView sv = new ScrollView(this);
        TextView story = body(level == 0 ? STORY : Game.LEVEL_HINTS[level], 19);
        sv.addView(story);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(sp);
        L.addView(sv);
        L.addView(btn(level == 0 ? "ابدأ المغامرة!" : "ابدأ المرحلة!", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); startGame(level, shards); }
        }));
        L.addView(btnGhost("رجوع", new View.OnClickListener() {
            @Override public void onClick(View v) { stopVoice(); showMenu(); }
        }));
        setScreen(L);
        speak("intro.wav", level == 0 ? STORY : Game.LEVEL_HINTS[level]);
    }

    private void showSettings(final boolean fromGame) {
        LinearLayout L = screen();
        L.addView(title("الإعدادات", 30));
        L.addView(makeSeek("موسيقى", pref.getFloat("music", 0.7f), new SetVol() {
            @Override public void apply(float v) { pref.edit().putFloat("music", v).apply(); audio.setVolumes(v, pref.getFloat("sfx", 0.8f)); }
        }));
        L.addView(makeSeek("مؤثرات صوتية", pref.getFloat("sfx", 0.8f), new SetVol() {
            @Override public void apply(float v) { pref.edit().putFloat("sfx", v).apply(); audio.setVolumes(pref.getFloat("music", 0.7f), v); }
        }));
        L.addView(makeSeek("التعليق الصوتي", voiceVol, new SetVol() {
            @Override public void apply(float v) { voiceVol = v; pref.edit().putFloat("voice", v).apply(); }
        }));
        L.addView(body("التحكم: ◀ ▶ للحركة — قفز / هجوم / دفع\nاضرب الأعداء بالسيف أو اقفز فوقهم!", 16));
        L.addView(btnGhost("رجوع", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); if (fromGame) showPause(runShards); else showMenu(); }
        }));
        setScreen(L);
    }

    interface SetVol { void apply(float v); }

    private View makeSeek(String name, float val, final SetVol s) {
        LinearLayout L = new LinearLayout(this);
        L.setOrientation(LinearLayout.VERTICAL);
        L.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView t = body(name, 18);
        L.addView(t);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress((int) (val * 100));
        sb.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) s.apply(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) { audio.play(AudioSys.S_CLICK); }
        });
        L.addView(sb);
        return L;
    }

    private void showAbout() {
        LinearLayout L = screen();
        L.addView(title("حول اللعبة", 30));
        L.addView(body("نور: حارس الواحة — لعبة منصات ثنائية الأبعاد باللغة العربية.\n\n" +
                "3 مراحل + زعيم أخير (تنين الرمال).\n" +
                "كل الرسومات والموسيقى مولّدة إجرائيًا داخل اللعبة (أصول أصلية 100%).\n\n" +
                "الإصدار 1.0", 18));
        L.addView(btnGhost("رجوع", new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        }));
        setScreen(L);
    }

    // ---------------- game flow ----------------
    private void startGame(int level, int shards) {
        stopVoice();
        currentLevel = level;
        runShards = shards;
        gameView = new GameView(this, level, shards, audio, this);
        gameView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        setScreen(gameView);
    }

    private void showPause(int shards) {
        runShards = shards;
        LinearLayout L = screen();
        L.addView(title("إيقاف مؤقت", 32));
        L.addView(body("المرحلة: " + Game.LEVEL_NAMES[currentLevel] + "\nالشظايا: " + shards, 18));
        L.addView(btn("متابعة", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); setScreen(gameView); gameView.resumeGame(); }
        }));
        L.addView(btnGhost("إعادة من نقطة الحفظ", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); setScreen(gameView); gameView.retryFromCheck(); }
        }));
        L.addView(btnGhost("الإعدادات", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showSettings(true); }
        }));
        L.addView(btnGhost("القائمة الرئيسية", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); saveBest(runShards); showMenu(); }
        }));
        setScreen(L);
    }

    private AlertDialog dlg;
    private void dismissDlg() { try { if (dlg != null) dlg.dismiss(); } catch (Exception ignored) {} dlg = null; }

    @Override public void onPauseGame(int shards) { showPause(shards); }

    @Override public void onGameOver(final int shards) {
        runShards = shards;
        LinearLayout L = screen();
        L.addView(title("انتهت اللعبة!", 34));
        L.addView(body("لا تستسلم يا نور.. حاول مجددًا!\nالشظايا: " + shards, 18));
        L.addView(btn("إعادة المحاولة", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); setScreen(gameView); gameView.retryFromCheck(); }
        }));
        L.addView(btnGhost("القائمة الرئيسية", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); saveBest(shards); showMenu(); }
        }));
        setScreen(L);
    }

    @Override public void onLevelDone(int level, int shards) {
        saveBest(shards);
        int next = Math.min(2, level + 1);
        if (pref.getInt("unlocked", 0) < next) pref.edit().putInt("unlocked", next).apply();
        final int nl = next;
        LinearLayout L = screen();
        L.addView(title("أحسنت! أكملت المرحلة", 30));
        L.addView(body(Game.LEVEL_NAMES[level] + " — تمت!\nالشظايا: " + shards, 19));
        L.addView(btn("المرحلة التالية: " + Game.LEVEL_NAMES[nl], new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showIntro(nl, 0); }
        }));
        L.addView(btnGhost("إعادة اللعب", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); startGame(level, 0); }
        }));
        L.addView(btnGhost("القائمة الرئيسية", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showMenu(); }
        }));
        setScreen(L);
    }

    @Override public void onWinGame(int shards) {
        saveBest(shards);
        pref.edit().putInt("unlocked", 2).apply();
        LinearLayout L = screen();
        L.addView(title("النصر! عاد النور!", 32));
        ScrollView sv = new ScrollView(this);
        sv.addView(body(WIN_STORY + "\n\nالشظايا: " + shards, 19));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(sp);
        L.addView(sv);
        L.addView(btn("العب مجددًا", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showIntro(0, 0); }
        }));
        L.addView(btnGhost("القائمة الرئيسية", new View.OnClickListener() {
            @Override public void onClick(View v) { audio.play(AudioSys.S_CLICK); showMenu(); }
        }));
        setScreen(L);
        speak("win.wav", WIN_STORY);
    }

    private void saveBest(int shards) {
        if (shards > pref.getInt("best", 0)) pref.edit().putInt("best", shards).apply();
    }
}
