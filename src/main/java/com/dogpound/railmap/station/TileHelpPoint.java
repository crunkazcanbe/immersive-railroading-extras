package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

/**
 * Platform Help Point (her ChatGPT list #81): the blue pillar with HELP and INFO buttons, an induction loop and the
 * MIND THE GAP warning. HELP calls staff (an alarm until it is answered with CANCEL or times out after 2 minutes);
 * INFO tells you the next driverless train due here. With the GAP key on, while a train stands at the platform the
 * warning lamp flashes and the chime sounds as its doors open. Counts towards the station's accessibility rating
 * (help point + platform-gap warning). 12 V.
 */
public class TileHelpPoint extends DepotTile implements IStationFacility {
    private long calling;

    @Override public String wiredName() { return "Help point"; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : calling > 0 ? "HELP CALL" : "ready"; }
    @Override public String facilityKind() { return "Help point"; }
    @Override public boolean facilityWorking() { return powered(); }
    @Override public int facilityPoints() { return 4; }
    @Override public int accessPoints() { return 15; }
    @Override public long dailyCost() { return 50; }
    public boolean gapWarning() { return powered() && ctl.get("k_gap") == 1; }

    private Long station() { return StationFacilities.stationOf(world, pos); }

    /** a driverless train standing at this help point's station right now */
    private boolean trainIn() {
        Long st = station();
        if (st == null) return false;
        for (RailwayData.AutoTrain a : RailwayData.get(world).trains().values()) {
            Autopilot.Run r = Autopilot.run(a.loco);
            if (r != null && r.dwelling && (r.dwellStation == st || BlockPos.fromLong(r.dwellStation).distanceSq(pos) < 24 * 24)) return true;
        }
        return false;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "help" -> {
                calling = world.getTotalWorldTime();
                String where = station() == null ? pos.getX() + " " + pos.getZ() : StationData.get(world).names().getOrDefault(station(), "?");
                AlarmManager.get(world).raise(world, "help:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "HELP POINT at " + where + ": passenger assistance requested" + (who == null ? "" : " (" + who.getName() + ")"), pos, true);
                Recorder.event(world, "Help point call at " + where);
                return "§aConnecting you to staff... (an alarm is up in the control room)";
            }
            case "cancel" -> { calling = 0; AlarmManager.get(world).clear("help:" + pos.toLong()); return "§7Call ended"; }
            case "info" -> {
                Long st = station();
                if (st == null) return "§7This help point is not at a named station";
                RailwayData rd = RailwayData.get(world);
                String best = null;
                double bm = Double.MAX_VALUE;
                for (RailwayData.AutoTrain a : rd.trains().values()) {
                    Autopilot.Run r = Autopilot.run(a.loco);
                    if (r == null) continue;
                    Long next = com.dogpound.railmap.auto.Dispatcher.nextStop(rd, a);
                    if (next == null || next.longValue() != st) continue;
                    if (r.dwelling) return "§aA train is at the platform now: " + (a.label.isEmpty() ? a.line : a.label);
                    if (r.metresToStop >= 0 && r.metresToStop < bm) { bm = r.metresToStop; best = (a.label.isEmpty() ? a.line : a.label) + String.format(java.util.Locale.ROOT, " - %.0f m away, about %d s", r.metresToStop, Math.round(r.metresToStop / Math.max(1, r.speedKmh / 3.6))); }
                }
                return best == null ? "§7No train is on its way here right now" : "§aNext train: " + best;
            }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (calling > 0 && world.getTotalWorldTime() - calling > 2400) { calling = 0; AlarmManager.get(world).clear("help:" + pos.toLong()); }
        boolean in = gapWarning() && trainIn();
        if (in && !(ctl.get("gap") == 1)) world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.8f, 1.0f);
        put("pwr", powered()); put("gap", in); put("call", calling > 0); put("loop", powered());
        Long st = station();
        text("line1", st == null ? "NO STATION" : cut(StationData.get(world).names().getOrDefault(st, "").toUpperCase(java.util.Locale.ROOT), 16));
        text("line2", in ? "MIND THE GAP" : calling > 0 ? "CALLING STAFF" : "PRESS FOR HELP");
    }

    @Override protected void saveExtra(NBTTagCompound t) { t.setLong("calling", calling); }
    @Override protected void loadExtra(NBTTagCompound t) { calling = t.getLong("calling"); }
}
