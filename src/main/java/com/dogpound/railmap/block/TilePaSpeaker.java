package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;
import java.util.Map;

/**
 * The station public-address speaker. When a train calls at the station this speaker belongs to,
 * it plays the two-tone chime and reads the announcement to everyone on the platform.
 */
public class TilePaSpeaker extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    private int calls;
    private long lastSaid;


    private long station = Long.MIN_VALUE;
    private String stationName = "";
    private int resolveTimer;
    private String lastCall = "";

    public long station() {
        return station;
    }

    public String stationName() {
        return stationName;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        if (--resolveTimer <= 0) {
            resolveTimer = 100;
            resolveStation();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            com.dogpound.railmap.auto.ArrivalEvents.addSpeaker(world.provider.getDimension(), this);
        }
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            com.dogpound.railmap.auto.ArrivalEvents.removeSpeaker(world.provider.getDimension(), this);
        }
        super.invalidate();
    }

    private void resolveStation() {
        StationData stations = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (d < bd) {
                bd = d;
                best = e.getKey();
            }
        }
        if (best != station) {
            station = best;
            stationName = best == Long.MIN_VALUE ? "" : stations.names().get(best);
            markDirty();
        }
    }

    /** Called when a train pulls in at this speaker's station. */
    public void announceArrival(String train) {
        if (cfg.bool("arrivals")) say(fill(cfg.text("arrivalText"), train));
    }

    public void announceDeparture(String train) {
        if (cfg.bool("departures")) say(fill(cfg.text("departureText"), train));
    }

    private String fill(String template, String train) {
        String time = world == null ? "" : clock(world.getWorldTime());
        return template.replace("{train}", train).replace("{station}", stationName.isEmpty() ? "this station" : stationName).replace("{time}", time);
    }

    private static String clock(long t) {
        long h = (t / 1000 + 6) % 24, m = (t % 1000) * 60 / 1000;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
    }

    // ---- Settings Console ------------------------------------------------------------------
    private static final String[] COLORS = {"Aqua", "White", "Yellow", "Green", "Gold", "Light purple"};
    private static final TextFormatting[] FMT = {TextFormatting.AQUA, TextFormatting.WHITE, TextFormatting.YELLOW, TextFormatting.GREEN, TextFormatting.GOLD, TextFormatting.LIGHT_PURPLE};

    @Override
    public String settingsTitle() { return "PA Speaker"; }

    @Override
    public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        String a = "Announcements", s = "Sound", r = "Reach", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.bool(a, "arrivals", "Announce arrivals", "Speak when a train calls at this station", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(a, "departures", "Announce departures", "Speak when a train is about to leave", true));
        l.add(com.dogpound.railmap.settings.Setting.text(a, "arrivalText", "Arrival words", "{train} {station} {time} are filled in", "The train now arriving at {station} is the {train}.", 120));
        l.add(com.dogpound.railmap.settings.Setting.text(a, "departureText", "Departure words", "{train} {station} {time} are filled in", "The {train} is ready to depart {station}. Mind the doors.", 120));
        l.add(com.dogpound.railmap.settings.Setting.num(a, "repeat", "Say it", "How many times each announcement is read", 1, 1, 3, 1, "times"));
        l.add(com.dogpound.railmap.settings.Setting.num(a, "gap", "Quiet gap", "Shortest time between two announcements", 3, 0, 60, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.choice(a, "color", "Text colour", "Colour of the [PA] text in chat", "Aqua", COLORS));
        l.add(com.dogpound.railmap.settings.Setting.bool(a, "actionbar", "Show above the hotbar too", "Also flash the announcement above the hotbar", false));
        l.add(com.dogpound.railmap.settings.Setting.choice(s, "chime", "Chime", "Sound played before speaking", "Two-tone", "Two-tone", "Bell", "Ding-dong", "Airport", "Pling", "None"));
        l.add(com.dogpound.railmap.settings.Setting.num(s, "volume", "Volume", "How loud the chime is", 100, 0, 300, 10, "%"));
        l.add(com.dogpound.railmap.settings.Setting.num(s, "pitch", "Pitch", "Chime pitch", 120, 50, 200, 5, "%"));
        l.add(com.dogpound.railmap.settings.Setting.num(r, "earshot", "Earshot", "Players this close hear the announcement", 24, 4, 128, 4, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.num(r, "height", "Earshot up / down", "Height of the hearing area", 12, 2, 64, 2, "blocks"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Station", stationName.isEmpty() ? "none in range" : stationName));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Announcements made", String.valueOf(calls)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Last announcement", lastCall.isEmpty() ? "-" : lastCall));
        return l;
    }

    private void say(String text) {
        if (world == null || world.isRemote) {
            return;
        }
        long now = world.getTotalWorldTime();
        if (now - lastSaid < cfg.num("gap") * 20L) return;
        lastSaid = now;
        lastCall = text;
        calls++;
        float vol = cfg.num("volume") / 100f, pitch = cfg.num("pitch") / 100f;
        net.minecraft.util.SoundEvent chime = switch (cfg.text("chime")) {
            case "Bell" -> SoundEvents.BLOCK_NOTE_BELL;
            case "Ding-dong" -> SoundEvents.BLOCK_NOTE_XYLOPHONE;
            case "Airport" -> SoundEvents.BLOCK_NOTE_FLUTE;
            case "Pling" -> SoundEvents.BLOCK_NOTE_PLING;
            case "None" -> null;
            default -> SoundEvents.BLOCK_NOTE_CHIME;
        };
        if (chime != null && vol > 0) world.playSound(null, pos, chime, SoundCategory.BLOCKS, vol, pitch);
        int ear = cfg.num("earshot");
        AxisAlignedBB earshot = new AxisAlignedBB(pos).grow(ear, cfg.num("height"), ear);
        List<EntityPlayer> near = world.getEntitiesWithinAABB(EntityPlayer.class, earshot);
        int ci = 0;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i].equals(cfg.text("color"))) ci = i;
        for (EntityPlayer p : near) {
            for (int k = 0; k < Math.max(1, cfg.num("repeat")); k++)
                p.sendMessage(new TextComponentString(FMT[ci] + "[PA] " + TextFormatting.WHITE + text));
            if (cfg.bool("actionbar")) p.sendStatusMessage(new TextComponentString(FMT[ci] + text), true);
        }
    }

    public String statusLine() {
        if (station == Long.MIN_VALUE) {
            return "PA speaker — no station in range";
        }
        return "PA speaker · " + stationName + (lastCall.isEmpty() ? "" : " · last: " + lastCall);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setLong("station", station);
        t.setString("stationName", stationName);
        t.setInteger("calls", calls);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        station = t.hasKey("station") ? t.getLong("station") : Long.MIN_VALUE;
        stationName = t.getString("stationName");
        calls = t.getInteger("calls");
        cfg.read(t);
    }
}
