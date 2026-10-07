package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.auto.ArrivalEvents;
import com.dogpound.railmap.auto.Autopilot;
import com.dogpound.railmap.auto.Dispatcher;
import com.dogpound.railmap.auto.RailwayData;
import com.dogpound.railmap.server.StationData;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A station arrivals board: which driverless trains are coming, where they're going, and when.
 * <p>
 * It belongs to the nearest named station. Boards placed side by side facing the same way
 * join into one wide board; the left-most one (as you look at it) does the work and draws the
 * whole thing. When a train pulls in, the board chimes and flashes "NOW ARRIVING".
 */
public class TileArrivalsBoard extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private final com.dogpound.railmap.settings.SettingsStore cfg = new com.dogpound.railmap.settings.SettingsStore(this);
    public static final String[] THEMES = {"Amber LED", "Green LED", "White", "Blue LCD", "Pride"};

    @Override public String settingsTitle() { return "Arrivals Board"; }
    @Override public com.dogpound.railmap.settings.SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { if (isController()) refresh(); else sync(); }

    @Override
    public List<com.dogpound.railmap.settings.Setting> settingDefs() {
        List<com.dogpound.railmap.settings.Setting> l = new ArrayList<>();
        String sh = "Show", lk = "Look", sd = "Sound", st = "Status";
        l.add(com.dogpound.railmap.settings.Setting.text(sh, "title", "Station title", "Blank = the nearest named station", "", 32));
        l.add(com.dogpound.railmap.settings.Setting.num(sh, "rows", "Trains listed", "", 6, 1, 6, 1, "rows"));
        l.add(com.dogpound.railmap.settings.Setting.bool(sh, "later", "List trains not due yet", "Show LATER trains (on the line but not heading here next)", true));
        l.add(com.dogpound.railmap.settings.Setting.num(sh, "due", "Show DUE within", "Seconds out when a train turns to DUE", 45, 10, 300, 5, "s"));
        l.add(com.dogpound.railmap.settings.Setting.bool(sh, "waiting", "Passengers waiting line", "Footer with how many tickets are waiting", true));
        l.add(com.dogpound.railmap.settings.Setting.text(sh, "footer", "Footer text", "Shown when nobody is waiting", "BUY TICKETS AT THE MACHINE", 40));
        l.add(com.dogpound.railmap.settings.Setting.num(sh, "banner", "NOW ARRIVING banner", "How long it flashes", 10, 0, 60, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.num(sh, "refresh", "Refresh every", "", 2, 1, 30, 1, "s"));
        l.add(com.dogpound.railmap.settings.Setting.choice(lk, "theme", "Display", "Colours of the board", "Amber LED", THEMES));
        l.add(com.dogpound.railmap.settings.Setting.bool(lk, "clock24", "24-hour clock", "", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(lk, "clock", "Clock in the header", "", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(lk, "flaps", "Split-flap cells", "Draw the flap tiles behind the text", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(lk, "upper", "UPPER CASE", "", true));
        l.add(com.dogpound.railmap.settings.Setting.bool(sd, "chime", "Arrival chime", "Ding... dong when a train pulls in", true));
        l.add(com.dogpound.railmap.settings.Setting.choice(sd, "chimeSound", "Chime sound", "", "Harp", "Harp", "Bell", "Chime", "Pling", "Flute"));
        l.add(com.dogpound.railmap.settings.Setting.num(sd, "chimeVol", "Volume", "", 140, 0, 300, 10, "%"));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Station", stationName.isEmpty() ? "none in range" : stationName));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Trains on the board", String.valueOf(rows.size())));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Passengers waiting", String.valueOf(waiting)));
        l.add(com.dogpound.railmap.settings.Setting.info(st, "Joined boards", isController() ? width() + " wide (this one draws)" : "part of a wider board"));
        return l;
    }

    public String theme() { return cfg.text("theme"); }
    public static final int MAX_ROWS = 6;

    private long station = Long.MIN_VALUE;
    private String stationName = "";
    /** Each row: train, destination, when. */
    private final List<String[]> rows = new ArrayList<>();
    private int waiting;
    private String arriving = "";
    private long arrivingUntil;
    private int chimeStep = -1;
    private int ticks;

    public long station() { return station; }
    public String stationName() { String t = cfg.text("title").trim(); return t.isEmpty() ? stationName : t; }
    public List<String[]> rows() { return rows; }
    public int waiting() { return waiting; }

    public String arriving() {
        return world != null && world.getTotalWorldTime() < arrivingUntil ? arriving : "";
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getBlock() instanceof BlockArrivalsBoard ? s.getValue(BlockArrivalsBoard.FACING) : EnumFacing.NORTH;
    }

    /** As the viewer faces the board, "right" along its face. */
    public EnumFacing right() {
        return facing().rotateYCCW();
    }

    /** True when there is no joined board to our left: this one draws. */
    public boolean isController() {
        TileEntity left = world.getTileEntity(pos.offset(right().getOpposite()));
        return !(left instanceof TileArrivalsBoard b) || b.facing() != facing();
    }

    /** How many boards (including this one) run to the right. */
    public int width() {
        int w = 1;
        EnumFacing r = right();
        while (w < 8) {
            TileEntity te = world.getTileEntity(pos.offset(r, w));
            if (!(te instanceof TileArrivalsBoard b) || b.facing() != facing()) break;
            w++;
        }
        return w;
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        ticks++;
        if (chimeStep >= 0) {
            // Ding... dong.
            net.minecraft.util.SoundEvent snd = switch (cfg.text("chimeSound")) {
                case "Bell" -> SoundEvents.BLOCK_NOTE_BELL;
                case "Chime" -> SoundEvents.BLOCK_NOTE_CHIME;
                case "Pling" -> SoundEvents.BLOCK_NOTE_PLING;
                case "Flute" -> SoundEvents.BLOCK_NOTE_FLUTE;
                default -> SoundEvents.BLOCK_NOTE_HARP;
            };
            float vol = cfg.num("chimeVol") / 100f;
            if (cfg.bool("chime") && vol > 0) {
                if (chimeStep == 0) world.playSound(null, pos, snd, SoundCategory.BLOCKS, vol, 1.19f);
                if (chimeStep == 9) world.playSound(null, pos, snd, SoundCategory.BLOCKS, vol, 0.94f);
            }
            if (++chimeStep > 12) chimeStep = -1;
        }
        if (ticks % Math.max(20, cfg.num("refresh") * 20) != 0 || !isController()) return;
        refresh();
    }

    private void refresh() {
        StationData stations = StationData.get(world);
        long best = Long.MIN_VALUE;
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach;
        for (Map.Entry<Long, String> e : stations.names().entrySet()) {
            BlockPos s = BlockPos.fromLong(e.getKey());
            double d = s.distanceSq(pos);
            if (d < bd) { bd = d; best = e.getKey(); }
        }
        station = best;
        stationName = best == Long.MIN_VALUE ? "" : stations.names().get(best);
        rows.clear();
        if (best != Long.MIN_VALUE) {
            RailwayData data = RailwayData.get(world);
            List<Object[]> found = new ArrayList<>();
            for (RailwayData.AutoTrain a : data.trains().values()) {
                Autopilot.Run r = Autopilot.run(a.loco);
                if (r == null || r.entityId < 0) continue;
                Long next = Dispatcher.nextStop(data, a);
                boolean servesHere = next != null && next == best;
                RailwayData.Line line = data.line(a.line);
                boolean onLine = line != null && line.stations.contains(best);
                if (!servesHere && !onLine && !a.calls.contains(best)) continue;
                if (!servesHere && !a.calls.contains(best) && !cfg.bool("later")) continue;
                String name = a.label.isEmpty() ? "Train " + r.entityId : a.label;
                String dest = destination(stations, data, a, best);
                String when;
                double sortKey;
                if (r.dwelling && r.dwellStation == best) {
                    when = "BOARDING";
                    sortKey = -1;
                } else if (servesHere && r.metresToStop >= 0) {
                    double mps = Math.max(r.speedKmh, a.maxKmh * 0.6) / 3.6;
                    double secs = r.metresToStop / Math.max(1, mps);
                    when = secs < cfg.num("due") ? "DUE" : Math.max(1, Math.round(secs / 60)) + " MIN";
                    sortKey = secs;
                } else {
                    when = "LATER";
                    sortKey = 1e9;
                }
                found.add(new Object[]{ sortKey, new String[]{ name, dest, when } });
            }
            found.sort((x, y) -> Double.compare((double) x[0], (double) y[0]));
            for (Object[] o : found) {
                if (rows.size() >= Math.min(MAX_ROWS, Math.max(1, cfg.num("rows")))) break;
                rows.add((String[]) o[1]);
            }
            waiting = data.waitingAt(best);
        }
        sync();
    }

    private static String destination(StationData stations, RailwayData data, RailwayData.AutoTrain a, long here) {
        if (!a.calls.isEmpty()) return Autopilot.nameOf(stations, a.calls.get(a.calls.size() - 1));
        RailwayData.Line line = data.line(a.line);
        if (line != null && !line.stations.isEmpty()) {
            if (a.mode == RailwayData.Mode.SHUTTLE) {
                long end = a.direction > 0 ? line.stations.get(line.stations.size() - 1) : line.stations.get(0);
                return end == here ? line.name : Autopilot.nameOf(stations, end);
            }
            return line.name;
        }
        return a.home == 0 ? "" : Autopilot.nameOf(stations, a.home);
    }

    /** A driverless train stopped at our station. */
    public void chime(String train) {
        TileArrivalsBoard c = this;
        arriving = train;
        arrivingUntil = world.getTotalWorldTime() + cfg.num("banner") * 20L;
        chimeStep = 0;
        if (c.isController()) refresh();
        else sync();
    }

    private void sync() {
        markDirty();
        IBlockState s = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, s, s, 3);
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) ArrivalEvents.add(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) ArrivalEvents.remove(world.provider.getDimension(), this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) ArrivalEvents.remove(world.provider.getDimension(), this);
    }

    // ---- sync ----------------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setLong("st", station);
        t.setString("sn", stationName);
        NBTTagList l = new NBTTagList();
        for (String[] r : rows) {
            l.appendTag(new NBTTagString(r[0] + "" + r[1] + "" + r[2]));
        }
        t.setTag("rows", l);
        t.setInteger("wait", waiting);
        t.setString("arr", arriving);
        t.setLong("arrU", arrivingUntil);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        station = t.getLong("st");
        stationName = t.getString("sn");
        rows.clear();
        NBTTagList l = t.getTagList("rows", Constants.NBT.TAG_STRING);
        for (int i = 0; i < l.tagCount(); i++) {
            String[] parts = l.getStringTagAt(i).split("", -1);
            if (parts.length == 3) rows.add(parts);
        }
        waiting = t.getInteger("wait");
        arriving = t.getString("arr");
        arrivingUntil = t.getLong("arrU");
        cfg.read(t);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(8, 1, 8);
    }
}
