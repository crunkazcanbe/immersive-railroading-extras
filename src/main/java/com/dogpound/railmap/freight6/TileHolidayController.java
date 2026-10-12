package com.dogpound.railmap.freight6;

import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Festive Lighting Controller (batch 6 #30): the station's seasonal lighting panel - astronomical time switch, pattern
 * sequencer, lighting contactors and a little calendar display. SEASON: AUTO (the real date: Christmas 1 Dec - 6 Jan,
 * Halloween 15 Oct - 2 Nov) or forced XMAS / HALLOW / OFF. TIME: DUSK (lights on from sunset to sunrise) or ALWAYS.
 * PATTERN: STEADY / CHASE / TWINKLE / FADE. SHOW runs a 30 s light show with chimes, GREET plays the season's
 * greeting on the PA. Every festive block within 48 blocks (tree, string lights, pumpkin display) is fed from it -
 * with no controller they stay dark. 12 V + mains (the lighting contactors).
 */
public class TileHolidayController extends com.dogpound.railmap.depot.DepotTile {
    static final String[] SEASON = {"AUTO", "XMAS", "HALLOW", "OFF"};
    static final String[] PATTERN = {"STEADY", "CHASE", "TWINKLE", "FADE"};
    private long showUntil;
    private boolean wasOn;
    private int fed;

    public TileHolidayController() { super(new String[]{"spares"}, new String[]{"Spare bulbs and fuses"}, new int[]{9}); }

    @Override public String wiredName() { return "Festive lighting controller"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return lightsOn() ? 10 + fed * 2L : 1; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : Season.NAMES[season()] + " · " + (lightsOn() ? "LIGHTS ON " + PATTERN[pattern()] : "lights off") + " · feeds " + fed; }

    /** the season this panel runs */
    public int season() {
        return switch (Math.floorMod(ctl.get("s_season"), 4)) { case 1 -> Season.CHRISTMAS; case 2 -> Season.HALLOWEEN; case 3 -> Season.NONE; default -> Season.now(); };
    }
    public int pattern() { return Math.floorMod(ctl.get("s_pattern"), 4); }
    public boolean show() { return world != null && world.getTotalWorldTime() < showUntil; }
    boolean dark() { long t = world.getWorldTime() % 24000; return t > 12300 && t < 23700 || world.isRaining() && world.isThundering(); }
    public boolean lightsOn() { return powered() && mainsOk() && season() != Season.NONE && (ctl.get("k_time") == 1 || dark() || show()); }

    /** the controller feeding a festive block at p (nearest within 48, null = none) */
    public static TileHolidayController near(World w, BlockPos p) {
        List<TileHolidayController> l = StnUtil.near(w, TileHolidayController.class, p, 48);
        TileHolidayController best = null;
        double bd = Double.MAX_VALUE;
        for (TileHolidayController c : l) { double d = c.getPos().distanceSq(p); if (d < bd) { bd = d; best = c; } }
        return best;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "show" -> {
                if (season() == Season.NONE) return "§7Out of season - turn SEASON to XMAS or HALLOW";
                showUntil = world.getTotalWorldTime() + 600;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev(season() == Season.CHRISTMAS ? "station.chime_bell" : "station.chime_two_tone"), net.minecraft.util.SoundCategory.BLOCKS, 1f, season() == Season.CHRISTMAS ? 1.3f : 0.6f);
                Recorder.event(world, "Festive light show at " + pos.getX() + "," + pos.getZ() + " (" + Season.NAMES[season()] + ")");
                return "§aLight show - 30 s";
            }
            case "greet" -> {
                String s = season() == Season.CHRISTMAS ? "§c🎄 §aSeason's greetings from all of us at the railway - mind the gap, and merry Christmas!" : season() == Season.HALLOWEEN ? "§6🎃 §5Passengers are reminded that the last ghost train leaves at midnight..." : "";
                if (s.isEmpty()) return "§7Out of season";
                for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < 96 * 96) p.sendMessage(new net.minecraft.util.text.TextComponentString(s));
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.chime_dingdong"), net.minecraft.util.SoundCategory.BLOCKS, 1.2f, 1f);
                return "";
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        boolean on = lightsOn();
        if (on != wasOn) { wasOn = on; Recorder.event(world, "Festive lights " + (on ? "switched on" : "off") + " at " + pos.getX() + "," + pos.getZ()); }
        fed = StnUtil.near(world, TileFestive.class, pos, 48).size();
        LocalDate d = LocalDate.now();
        int s = season();
        put("pwr", powered()); put("mainsl", mains.ok()); put("onl", on); put("showl", show()); put("xmasl", s == Season.CHRISTMAS); put("hallowl", s == Season.HALLOWEEN);
        put("duskl", dark()); put("autol", Math.floorMod(ctl.get("s_season"), 4) == 0);
        for (int i = 0; i < 4; i++) put("p" + i, pattern() == i && on);
        put("loadg", Math.min(100, fed * 8));
        text("date", String.format(Locale.ROOT, "%02d.%02d", d.getDayOfMonth(), d.getMonthValue()));
        text("days", String.valueOf(Season.daysTo(d, s == Season.HALLOWEEN ? Season.HALLOWEEN : Season.CHRISTMAS)));
        text("line1", Season.NAMES[s] + (Math.floorMod(ctl.get("s_season"), 4) == 0 ? " (DATE)" : " (FORCED)"));
        text("line2", (on ? "ON " : "OFF ") + PATTERN[pattern()] + (show() ? " SHOW" : ""));
        text("line3", "FEEDS " + fed + " DISPLAYS");
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("show", showUntil); }
    @Override protected void loadExtra(NBTTagCompound t) { showUntil = t.getLong("show"); }
}
