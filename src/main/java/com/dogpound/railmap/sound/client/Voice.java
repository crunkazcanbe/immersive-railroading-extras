package com.dogpound.railmap.sound.client;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.sound.SoundConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The station announcer's voice, client side. Real PA systems are concatenative: every word and stock phrase is
 * recorded once and chained. This does the same with the recorded library (tools/gen_voice.py, assets/irextras/voice/
 * <voice>.txt = token + length in ms): any announcement text is split into the longest known phrases, then words;
 * numbers, times ("10:15" = ten fifteen, "08:05" = eight oh five) and platforms are spoken from number words;
 * punctuation gives the natural pauses. Words the library doesn't know (an unusual station name) are skipped, or
 * spelled out when {@link SoundConfig#spellUnknownWords} is on. Played positionally at the speaker, in the VOICE
 * sound category (the vanilla Voice/Speech slider works too).
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID, value = Side.CLIENT)
public final class Voice {
    private Voice() {}

    private static final Map<String, Map<String, Integer>> LIB = new HashMap<>();
    private static final int MAX_PHRASE = 12;
    private static final List<Speech> ACTIVE = new ArrayList<>();

    private static Map<String, Integer> lib(String voice) {
        return LIB.computeIfAbsent(voice, v -> {
            Map<String, Integer> m = new HashMap<>();
            try (InputStream in = Voice.class.getResourceAsStream("/assets/irextras/voice/" + v + ".txt")) {
                if (in != null) {
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (String l; (l = r.readLine()) != null; ) {
                        int t = l.indexOf('\t');
                        if (t > 0) m.put(l.substring(0, t), Integer.parseInt(l.substring(t + 1).trim()));
                    }
                }
            } catch (Exception e) {
                RailMap.LOG.warn("IR Extras voice library {} unreadable: {}", v, e.toString());
            }
            return m;
        });
    }

    /** one clip in an announcement: a library token, or a pause (token null) */
    private static final class Clip {
        final String token; final int ms;
        Clip(String token, int ms) { this.token = token; this.ms = ms; }
    }

    private static final class Speech {
        final BlockPos pos; final String voice, text; final float volume; final Deque<Clip> clips; final long startAt; long nextAt;
        Speech(BlockPos pos, String voice, String text, float volume, Deque<Clip> clips, long startAt) {
            this.pos = pos; this.voice = voice; this.text = text; this.volume = volume; this.clips = clips; this.startAt = startAt; this.nextAt = startAt;
        }
    }

    /**
     * speak text at a PA speaker. delayMs: let the chime finish first. Several speakers of one station saying the
     * same thing at once all speak (the real platform echo), but only the 4 nearest the player.
     */
    public static void speak(BlockPos pos, String text, int delayMs, float volume) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || text == null || text.isEmpty()) return;
        if (mc.player.getDistanceSqToCenter(pos) > 80 * 80) return;
        String v = SoundConfig.voiceName == null || SoundConfig.voiceName.isEmpty() ? "cori" : SoundConfig.voiceName;
        if (lib(v).isEmpty()) v = "cori";
        Map<String, Integer> lib = lib(v);
        if (lib.isEmpty()) return;
        long now = System.currentTimeMillis();
        int same = 0;
        for (Speech s : ACTIVE) if (s.text.equals(text) && Math.abs(s.startAt - now - delayMs) < 1000) same++;
        if (same >= 4) return;
        Deque<Clip> clips = plan(text, lib);
        if (!clips.isEmpty()) ACTIVE.add(new Speech(pos.toImmutable(), v, text, volume, clips, now + delayMs));
    }

    @SubscribeEvent
    public static void frame(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.START || ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) { ACTIVE.clear(); return; }
        long now = System.currentTimeMillis();
        for (Iterator<Speech> it = ACTIVE.iterator(); it.hasNext(); ) {
            Speech s = it.next();
            while (now >= s.nextAt && !s.clips.isEmpty()) {
                Clip c = s.clips.poll();
                if (c.token != null) {
                    ResourceLocation rl = new ResourceLocation(RailMap.MODID, "voice." + s.voice + "." + c.token);
                    mc.getSoundHandler().playSound(new PositionedSoundRecord(rl, SoundCategory.VOICE, s.volume, 1f, false, 0,
                            ISound.AttenuationType.LINEAR, s.pos.getX() + 0.5f, s.pos.getY() + 0.5f, s.pos.getZ() + 0.5f));
                }
                s.nextAt += c.ms;
            }
            if (s.clips.isEmpty()) it.remove();
        }
    }

    // ---- text -> clips ---------------------------------------------------------------------------------------------
    static Deque<Clip> plan(String text, Map<String, Integer> lib) {
        List<String> toks = words(text);
        Deque<Clip> out = new ArrayDeque<>();
        int i = 0;
        while (i < toks.size()) {
            String t = toks.get(i);
            if (t.startsWith("|")) { out.add(new Clip(null, Integer.parseInt(t.substring(1)))); i++; continue; }
            int took = 0;
            for (int n = Math.min(MAX_PHRASE, toks.size() - i); n >= 2 && took == 0; n--) {   // longest phrase first
                StringBuilder b = new StringBuilder();
                boolean ok = true;
                for (int k = 0; k < n; k++) {
                    String w = toks.get(i + k);
                    if (w.startsWith("|")) { ok = false; break; }
                    if (k > 0) b.append('_');
                    b.append(w);
                }
                Integer ms = ok ? lib.get(b.toString()) : null;
                if (ms != null) { out.add(new Clip(b.toString(), ms + 20)); took = n; }
            }
            if (took > 0) { i += took; continue; }
            Integer ms = lib.get(t);
            if (ms != null) out.add(new Clip(t, Math.max(120, ms - 50)));   // the next word starts in this one's decay, like speech
            else if (SoundConfig.spellUnknownWords)
                for (char ch : t.toCharArray()) { Integer lm = lib.get("letter_" + ch); if (lm != null) out.add(new Clip("letter_" + ch, lm + 20)); }
            i++;
        }
        while (!out.isEmpty() && out.peekLast().token == null) out.pollLast();
        return out;
    }

    /** lower-case words, numbers spelled out, "|ms" pause markers for punctuation */
    static List<String> words(String text) {
        List<String> o = new ArrayList<>();
        String s = text.replaceAll("§.", " ").replace("&", " and ").replace("%", " percent ").replace("+", " plus ");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i <= s.length(); i++) {
            char c = i < s.length() ? s.charAt(i) : ' ';
            boolean timeColon = c == ':' && cur.length() > 0 && Character.isDigit(cur.charAt(cur.length() - 1))
                    && i + 1 < s.length() && Character.isDigit(s.charAt(i + 1));
            if (Character.isLetterOrDigit(c) || c == '\'' || timeColon) { cur.append(c); continue; }
            flush(cur.toString(), o);
            cur.setLength(0);
            if (c == ',' || c == ';' || c == '-' && i > 0 && s.charAt(i - 1) == ' ') o.add("|260");
            else if (c == '.' || c == '!' || c == '?' || c == ':') o.add("|480");
        }
        return o;
    }

    private static void flush(String w, List<String> o) {
        if (w.isEmpty()) return;
        w = w.toLowerCase(Locale.ROOT).replace("'", "");
        if (w.matches("\\d{1,2}:\\d{2}")) {                                    // a clock time
            String[] hm = w.split(":");
            int h = Integer.parseInt(hm[0]), m = Integer.parseInt(hm[1]);
            number(h, o);
            if (m == 0) o.add("hundred");
            else { if (m < 10) o.add("oh"); number(m, o); }
            return;
        }
        if (w.matches("\\d+")) {
            if (w.length() > 1 && w.startsWith("0")) { for (char ch : w.toCharArray()) number(ch - '0', o); return; }
            if (w.length() > 6) { for (char ch : w.toCharArray()) number(ch - '0', o); return; }
            number(Integer.parseInt(w), o);
            return;
        }
        if (w.matches("\\d+[a-z]+")) {                                         // 3a, 12b: platform letters
            String n = w.replaceAll("[a-z]+$", "");
            number(Integer.parseInt(n), o);
            for (char ch : w.substring(n.length()).toCharArray()) o.add("letter_" + ch);
            return;
        }
        if (w.matches(".*\\d.*")) {                                             // mixed: split digits and letters
            for (String part : w.split("(?<=\\d)(?=[a-z])|(?<=[a-z])(?=\\d)")) flush(part, o);
            return;
        }
        o.add(w);
    }

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    static void number(int n, List<String> o) {
        if (n < 0) { o.add("minus"); n = -n; }
        if (n >= 1000) { number(n / 1000, o); o.add("thousand"); n %= 1000; if (n == 0) return; if (n < 100) o.add("and"); }
        if (n >= 100) { o.add(ONES[n / 100]); o.add("hundred"); n %= 100; if (n == 0) return; o.add("and"); }
        if (n < 20) { o.add(ONES[n]); return; }
        o.add(TENS[n / 10]);
        if (n % 10 != 0) o.add(ONES[n % 10]);
    }
}
