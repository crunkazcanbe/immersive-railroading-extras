package com.dogpound.railmap.stnlife6;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.economy.Coins;
import com.dogpound.railmap.ems.RoadPath;
import com.dogpound.railmap.road.EntityRoadVehicle;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Taxi rank call point (batch 6c #12): pick a destination (DEST < >: every named station, bus bay and other rank within
 * reach), press CALL and a taxi (a yellow road-traffic car) is sent from round the corner to the rank road; it waits
 * LifeConfig.taxiWait seconds. Get in (right-click it) and it drives you there with the meter running (flag fall +
 * per block x the TARIFF: 1 day, 2 night x1.5, 3 holiday x2), takes the fare in Pride coins and comes back to the rank.
 * The meter screen shows the estimate before you go and the running fare after; takings and trips are kept. An unpaid
 * fare is a MINOR alarm. RANK key closes it. 12 V + mains.
 */
public class TileTaxiRank extends LifeTile {
    static final int[] TARIFF = {100, 150, 200};
    private final List<BlockPos> dests = new ArrayList<>();
    private final List<String> dnames = new ArrayList<>();
    private int sel, trips, unpaid;
    private long takings, lastFare;
    private UUID taxi;
    private String last = "";

    @Override public String wiredName() { return "Taxi rank " + pos.getX() + "," + pos.getZ(); }
    @Override public String gearState() { return !live() ? "NO SUPPLY" : status() + " · " + trips + " trips · takings " + Coins.fmt(takings); }

    long tariff() { return TARIFF[Math.floorMod(ctl.get("s_tariff"), TARIFF.length)]; }
    boolean open() { return ctl.get("k_rank") == 1; }

    EntityServiceVehicle cab() {
        if (taxi == null) return null;
        for (Entity e : world.loadedEntityList) if (e instanceof EntityServiceVehicle v && !v.isDead && v.getUniqueID().equals(taxi)) return v;
        return null;
    }

    String status() {
        EntityServiceVehicle c = cab();
        if (c == null) return open() ? "NO TAXI - PRESS CALL" : "RANK CLOSED";
        if (c.hired) return "ON HIRE TO " + StnUtil.up(StnUtil.cut(c.destName, 12));
        if (c.returning) return "RETURNING";
        if (c.stopIdx >= c.stops.size()) return "TAXI WAITING " + c.waitLeft / 20 + "S";
        BlockPos t = c.target();
        return "TAXI COMING " + (t == null ? 0 : Roads.secs(Math.sqrt(c.getDistanceSq(t)) * 1.3, com.dogpound.railmap.road.RoadConfig.speed)) + "S";
    }

    private void destinations() {
        dests.clear(); dnames.clear();
        for (Map.Entry<Long, String> e : StationData.get(world).names().entrySet()) {
            BlockPos p = BlockPos.fromLong(e.getKey());
            if (p.distanceSq(pos) > 9 && p.distanceSq(pos) < 1200 * 1200) { dests.add(p); dnames.add(e.getValue() + " station"); }
        }
        for (TileBusBay b : StnUtil.loaded(world, TileBusBay.class)) if (b.getPos().distanceSq(pos) > 64) { dests.add(b.getPos()); dnames.add(b.stopName()); }
        for (TileTaxiRank r : StnUtil.loaded(world, TileTaxiRank.class)) if (r != this) { dests.add(r.getPos()); dnames.add("Taxi rank " + r.getPos().getX() + "," + r.getPos().getZ()); }
        if (sel >= dests.size()) sel = 0;
    }

    long estimate() {
        if (dests.isEmpty()) return 0;
        return (LifeConfig.taxiFlag + (long) (Roads.run(pos, dests.get(sel)) * LifeConfig.taxiPerBlock)) * tariff() / 100;
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("k_") || id.startsWith("s_")) return null;
        String d = dead();
        if (d != null) return d;
        switch (id) {
            case "dprev", "dnext" -> {
                destinations();
                if (dests.isEmpty()) return "§cNo destinations - name a station or place bus bays / ranks";
                sel = Math.floorMod(sel + (id.equals("dnext") ? 1 : -1), dests.size());
                refresh();
                return "§eTo " + dnames.get(sel) + " - about " + Coins.fmt(estimate());
            }
            case "call" -> {
                if (!open()) return "§cThe rank is closed (RANK key)";
                EntityServiceVehicle c = cab();
                if (c != null) return "§7A taxi is already on its way / waiting - " + status().toLowerCase(Locale.ROOT);
                String r = send();
                refresh();
                return r;
            }
            case "cancel" -> {
                EntityServiceVehicle c = cab();
                if (c == null || c.hired) return "§7Nothing to cancel";
                c.setDead(); taxi = null;
                return "§eTaxi cancelled";
            }
            default -> { return null; }
        }
    }

    /** call a taxi: it comes from a road 24-48 blocks away (or pulls out of the cab office at the rank) */
    private String send() {
        BlockPos stand = Roads.near(world, pos, 6);
        if (stand == null) return "§cNo road within 6 blocks of the rank for a taxi to stand on";
        BlockPos from = null;
        Random rng = world.rand;
        for (int i = 0; i < 12 && from == null; i++) {
            double a = rng.nextDouble() * Math.PI * 2, r = 24 + rng.nextDouble() * 24;
            BlockPos c = Roads.near(world, stand.add(Math.cos(a) * r, 0, Math.sin(a) * r), 3);
            if (c != null && world.isBlockLoaded(c)) from = c;
        }
        if (from == null) from = stand;
        EntityServiceVehicle v = new EntityServiceVehicle(world, EntityRoadVehicle.Kind.CAR, 6, pos, EntityServiceVehicle.TAXI);   // COLOURS[6] = taxi yellow
        v.stops.add(stand); v.names.add("rank");
        v.setLocationAndAngles(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, (float) Math.toDegrees(Math.atan2(-(stand.getX() - from.getX()), stand.getZ() - from.getZ())), 0);
        if (!RoadPath.standable(world, from) || !world.spawnEntity(v)) return "§cNo taxi available";
        taxi = v.getUniqueID();
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        return "§aTaxi called - " + (from == stand ? "pulling out of the cab office" : "about " + Roads.secs(Roads.run(from, stand), com.dogpound.railmap.road.RoadConfig.speed) + " s away");
    }

    /** a player gets into our waiting taxi: hire it to the selected destination */
    String board(EntityPlayer p, EntityServiceVehicle v) {
        if (!live()) return "§cThe rank's booking system is dead - no fares taken";
        if (v.stopIdx < v.stops.size()) return "§7Wait for the taxi to pull up at the rank";
        destinations();
        if (dests.isEmpty()) return "§cNo destination booked";
        BlockPos to = Roads.near(world, dests.get(sel), 10);
        if (to == null) to = dests.get(sel);
        String name = dnames.get(sel);
        if (!v.hire(p, to, name, tariff())) return "§cThat taxi can't take you";
        Recorder.event(world, "Taxi hired at rank " + pos.getX() + "," + pos.getZ() + " by " + p.getName() + " to " + name);
        return "§aTaxi to " + name + " - meter running (tariff " + (ctl.get("s_tariff") + 1) + ")";
    }

    void paid(String who, long fare, boolean ok, String dest, double blocks) {
        trips++; lastFare = fare;
        last = (ok ? "PAID " : "UNPAID ") + Coins.fmt(fare);
        if (ok) takings += fare;
        else {
            unpaid++;
            AlarmManager.get(world).raise(world, "taxi:" + pos.toLong() + ":" + trips, AlarmManager.Cat.INCIDENT, AlarmManager.Sev.MINOR, "TAXI FARE UNPAID: " + who + " to " + dest + " " + Coins.fmt(fare), pos, false);
        }
        Recorder.event(world, String.format(Locale.ROOT, "Taxi: %s to %s, %.0f blocks, fare %s%s", who, dest, blocks, Coins.fmt(fare), ok ? "" : " UNPAID"));
        markDirty();
    }

    @Override
    protected void work() {
        if (age % 10 != 4) return;
        if (age % 200 == 4) destinations();
        refresh();
    }

    private void refresh() {
        EntityServiceVehicle c = cab();
        text("sign", "TAXI RANK");
        text("dest", dests.isEmpty() ? "NO DESTINATIONS" : StnUtil.up(StnUtil.cut(dnames.get(sel), 20)));
        text("meter", c != null && c.hired ? "FARE " + Coins.fmt(c.fare) : "EST " + Coins.fmt(estimate()) + " T" + (ctl.get("s_tariff") + 1));
        text("status", status());
        text("takings", trips + " TRIPS " + Coins.fmt(takings));
        text("last", last.isEmpty() ? "-" : last);
        put("l_avail", c != null && !c.hired && !c.returning && c.stopIdx >= c.stops.size());
        put("l_coming", c != null && !c.hired && (c.returning || c.stopIdx < c.stops.size()));
        put("l_hire", c != null && c.hired);
        put("l_closed", !open());
        put("g_eta", c == null || c.target() == null ? 0 : (int) Math.min(100, Math.sqrt(c.getDistanceSq(c.target()))));
    }

    @Override protected void write(NBTTagCompound t) { t.setInteger("sel", sel); t.setInteger("trips", trips); t.setInteger("unpaid", unpaid); t.setLong("take", takings); t.setLong("lastF", lastFare); t.setString("last", last); }
    @Override protected void read(NBTTagCompound t) { sel = t.getInteger("sel"); trips = t.getInteger("trips"); unpaid = t.getInteger("unpaid"); takings = t.getLong("take"); lastFare = t.getLong("lastF"); last = t.getString("last"); }
}
