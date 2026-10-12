package com.dogpound.railmap.station;

import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Station Master's Desk: the brain of one station. It names the station (registers it with the
 * railway so boards, tickets and driverless trains know it), says what kind of station it is, finds its
 * platforms (Platform Number Signs nearby), watches trains arrive and leave, announces them and keeps
 * the day's numbers.
 */
public class TileStationMaster extends TileEntity implements ITickable, ISettingsHolder, com.dogpound.railmap.wire.IWiredGear, com.dogpound.railmap.controls.IControlled {
    // ---- working controls (requested feature): the PC and monitor power LEDs run off the office's 12 V (timetable
    // monitor only once the station is named); the microphone's press-to-talk speaks on every PA speaker of the station.
    private final com.dogpound.railmap.controls.ControlState ctl = new com.dogpound.railmap.controls.ControlState();
    @Override public com.dogpound.railmap.controls.ControlState controls() { return ctl; }
    @Override public void controlsChanged() { gearChanged(); }

    @Override
    public int controlValue(String id) {
        boolean pw = term.powered();
        switch (id) {
            case "pc": case "map": case "cctv": return pw ? 1 : 0;
            case "timetable": return pw && !name().isEmpty() ? 1 : 0;
            default: return ctl.get(id);
        }
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!id.equals("talk")) return null;
        if (!term.powered()) return "§cNo 12 V - the PA microphone is dead";
        if (name().isEmpty()) return "§7Name the station first (Settings Console)";
        String say = "Attention please. This is the station master at " + name() + "."
                + (standing.isEmpty() ? "" : " The train at platform " + standing.values().iterator().next() + " will depart shortly.");
        int n = 0, r = cfg.num("reach");
        for (TileEntity te : world.loadedTileEntityList)
            if (te instanceof com.dogpound.railmap.block.TilePaSpeaker sp && te.getPos().distanceSq(pos) <= (double) r * r
                    && (sp.stationName().isEmpty() || sp.stationName().equals(name())) && sp.page(say)) n++;
        return n == 0 ? "§7No powered PA speakers at this station" : "§eAnnounced on " + n + " PA speaker" + (n == 1 ? "" : "s");
    }

    @Override public com.dogpound.railmap.grid.Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.controls.IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    // ---- 12 V terminal (requested feature): needs 12 V DC instrument power; with none it is office dark
    private final com.dogpound.railmap.wire.Terminal term = new com.dogpound.railmap.wire.Terminal();
    @Override public com.dogpound.railmap.wire.Terminal term() { return term; }
    @Override public void gearChanged() { markDirty(); if (world != null && !world.isRemote) { net.minecraft.block.state.IBlockState gs = world.getBlockState(pos); world.notifyBlockUpdate(pos, gs, gs, 3); } }
    @Override public String wiredName() { return "Station master's office"; }
    @Override public String gearState() {
        if (!term.powered()) return "NO 12 V - office dark";
        com.dogpound.railmap.economy.PassengerData.Stn px = world == null ? null : com.dogpound.railmap.economy.PassengerData.get(world).stn(pos.toLong());
        return arrivals + " arr / " + departures + " dep · " + standing.size() + " at platforms" + (px == null ? "" : String.format(java.util.Locale.ROOT, " · %d waiting · %.0f%% satisfied", Math.round(px.waitingTotal()), px.sat));
    }

    public static final String[] TYPES = {"Through station", "Terminal (dead end)", "Multi-platform hub", "Underground", "Elevated",
            "Rural halt", "Metropolitan terminal", "Freight station", "Industrial siding"};

    private final SettingsStore cfg = new SettingsStore(this);
    private String registered = "";
    private int ticks, arrivals, departures;
    /** train id -> platform it is standing at */
    private final Map<Integer, String> standing = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    public String name() { return cfg.text("name").trim(); }

    // ---- every loaded desk, so driverless trains can ask "what station is this?" cheaply ----
    private static final Map<Integer, Set<TileStationMaster>> LOADED = new HashMap<>();

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) LOADED.computeIfAbsent(world.provider.getDimension(), k -> new HashSet<>()).add(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null) { Set<TileStationMaster> s = LOADED.get(world.provider.getDimension()); if (s != null) s.remove(this); }
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null) { Set<TileStationMaster> s = LOADED.get(world.provider.getDimension()); if (s != null) s.remove(this); }
    }

    /** the desk running the station at this stop (within its own station size), or null */
    public static TileStationMaster at(net.minecraft.world.World w, BlockPos stop) {
        Set<TileStationMaster> s = LOADED.get(w.provider.getDimension());
        if (s == null) return null;
        TileStationMaster best = null;
        double bd = Double.MAX_VALUE;
        for (TileStationMaster t : s) {
            if (t.isInvalid()) continue;
            double r = t.cfg.num("reach"), d = t.pos.distanceSq(stop);
            if (d <= r * r && d < bd) { bd = d; best = t; }
        }
        return best;
    }

    /** the kind of station (Settings Console) - the passenger model sizes its catchment by it */
    public String type() { return cfg.text("type"); }

    public boolean terminal() { return cfg.text("type").startsWith("Terminal") || cfg.text("type").startsWith("Metropolitan"); }

    /** seconds a driverless train should stand here (0 = the train's own setting) */
    public int dwell() { return cfg.num("dwell"); }

    /** request stop: should a driverless train run straight through? (nobody waiting, nobody aboard) */
    public boolean skipRequestStop(java.util.List<EntityPlayer> aboard) {
        if (!cfg.text("type").startsWith("Rural") || !cfg.bool("request")) return false;
        if (!aboard.isEmpty()) return false;
        double r = cfg.num("reach");
        for (EntityPlayer p : world.playerEntities) if (p.getDistanceSq(pos) < r * r) return false;
        return true;
    }

    @Override
    public void update() {
        if (world != null && !world.isRemote) { if (term.tick(world, pos, wiredName())) gearChanged(); if (!term.powered()) return; }
        if (world == null || world.isRemote) return;
        if (!name().equals(registered)) register();
        if (++ticks % 20 != 0) return;
        if (ticks % 100 == 0 && !registered.isEmpty()) com.dogpound.railmap.economy.PassengerData.get(world).setZone(pos.toLong(), cfg.num("zone"));
        watchTrains();
    }

    private void register() {
        StationData d = StationData.get(world);
        if (!registered.isEmpty()) d.setName(pos, "");
        if (!name().isEmpty()) d.setName(pos, name());
        registered = name();
        gearChanged();                                                       // the timetable monitor lights
    }

    void unregister() {
        if (world != null && !world.isRemote && !registered.isEmpty()) StationData.get(world).setName(pos, "");
    }

    /** platform signs of this station: number -> sign position */
    private Map<String, BlockPos> platforms() {
        Map<String, BlockPos> out = new HashMap<>();
        int r = cfg.num("reach");
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileStationPiece p) || !p.kind().equals("platform_sign")) continue;
            if (te.getPos().distanceSq(pos) > (double) r * r) continue;
            out.put(p.cfg().num("number") + p.cfg().text("suffix"), te.getPos());
        }
        return out;
    }

    private void watchTrains() {
        Map<String, BlockPos> plats = platforms();
        Set<Integer> seen = new HashSet<>();
        try {
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            if (w == null) return;
            int r = cfg.num("reach");
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : w.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                if (!(s instanceof cam72cam.immersiverailroading.entity.Locomotive)) continue;     // one entry per train: its loco
                cam72cam.mod.math.Vec3d p = s.getPosition();
                double dx = p.x - pos.getX(), dz = p.z - pos.getZ();
                if (dx * dx + dz * dz > (double) r * r || Math.abs(p.y - pos.getY()) > 12) continue;
                double kmh = s instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                        ? Math.abs(m.getCurrentSpeed().metric()) : 0;
                int id = s.getUUID().hashCode();
                if (kmh > 3) continue;
                seen.add(id);
                if (standing.containsKey(id)) continue;
                String plat = "?";
                double best = Double.MAX_VALUE;
                for (Map.Entry<String, BlockPos> e : plats.entrySet()) {
                    double d = e.getValue().distanceSq(p.x, p.y, p.z);
                    if (d < best) { best = d; plat = e.getKey(); }
                }
                standing.put(id, plat);
                arrivals++;
                String train = s.getDefinition() == null ? "train" : s.getDefinition().name();
                event(String.format("%s: the %s has arrived at platform %s", stamp(), train, plat),
                        "§b🚉 " + name() + ": §fthe §e" + train + "§f is now at §aplatform " + plat
                                + (terminal() ? "§f. This train terminates here - all change please." : ""));
            }
        } catch (LinkageError | RuntimeException ignored) { return; }
        for (Integer id : new ArrayList<>(standing.keySet())) {
            if (seen.contains(id)) continue;
            String plat = standing.remove(id);
            departures++;
            event(String.format("%s: departure from platform %s", stamp(), plat), "§b🚉 " + name() + ": §fthe train at §aplatform " + plat + "§f has departed");
        }
    }

    private String stamp() {
        long t = (world.getWorldTime() + 6000) % 24000;
        return String.format("%02d:%02d", t / 1000, (t % 1000) * 60 / 1000);
    }

    private void event(String logLine, String chat) {
        log.add(0, logLine);
        while (log.size() > 12) log.remove(log.size() - 1);
        markDirty();
        if (!cfg.bool("announce") || name().isEmpty()) return;
        double r = cfg.num("announceReach");
        for (EntityPlayer pl : world.playerEntities)
            if (pl.getDistanceSq(pos) < r * r) pl.sendMessage(new TextComponentString(chat));
        if (cfg.bool("chime")) world.playSound(null, pos, com.dogpound.railmap.ModSounds.PA_CHIME, SoundCategory.BLOCKS, 2f, 1.0f);
    }

    public String statusLine() {
        return "§b" + (name().isEmpty() ? "Unnamed station - sneak-right-click with the Signal Wrench to name it" : name())
                + " §7· " + cfg.text("type") + " · " + platforms().size() + " platform(s) · " + arrivals + " arrivals";
    }

    // ---- Settings Console ------------------------------------------------------------------------------
    @Override public String settingsTitle() { return "Station Master's Desk"; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        l.add(Setting.text("Station", "name", "Station name", "Registers the station: boards, tickets and driverless trains use it", "", 32));
        l.add(Setting.choice("Station", "type", "Kind of station", "", TYPES[0], TYPES));
        l.add(Setting.num("Station", "zone", "Fare zone", "Zone fares, travel passes and fare gates count zones crossed (1 = the centre)", 1, 1, 9, 1, ""));
        l.add(Setting.num("Trains", "dwell", "Driverless trains stand for", "0 = each train's own dwell time", 0, 0, 300, 5, "s"));
        l.add(Setting.bool("Trains", "request", "Request stop (Rural halt)", "Driverless trains only stop if someone is waiting here or riding", true));
        l.add(Setting.num("Station", "reach", "Station size", "Platforms and trains within this many blocks belong to this station", 48, 8, 256, 8, "blocks"));
        l.add(Setting.bool("Announcements", "announce", "Announce arrivals and departures", "In chat to players nearby", true));
        l.add(Setting.bool("Announcements", "chime", "Chime before announcements", "", true));
        l.add(Setting.num("Announcements", "announceReach", "Heard within", "", 64, 8, 256, 8, "blocks"));
        if (world != null && !world.isRemote) {
            Map<String, BlockPos> plats = platforms();
            l.add(Setting.info("Today", "Platforms found", plats.isEmpty() ? "none - place Platform Number Signs" : String.join(", ", new java.util.TreeSet<>(plats.keySet()))));
            l.add(Setting.info("Today", "Arrivals / departures", arrivals + " / " + departures));
            l.add(Setting.info("Today", "Trains at platforms now", String.valueOf(standing.size())));
            com.dogpound.railmap.economy.PassengerData.Stn px = com.dogpound.railmap.economy.PassengerData.get(world).stn(pos.toLong());
            if (px != null) {                                                    // her list #10 / #50: the passengers here
                l.add(Setting.info("Passengers", "Satisfaction", String.format(java.util.Locale.ROOT, "%.0f%% %s", px.sat, px.rating())));
                l.add(Setting.info("Passengers", "Punctuality / crowding / waiting", String.format(java.util.Locale.ROOT, "%.0f%% / %.0f%% / %.0f%%", px.punct, px.crowd, px.wait)));
                l.add(Setting.info("Passengers", "Facilities / cleanliness", String.format(java.util.Locale.ROOT, "%.0f%% / %.0f%%", px.fac, px.clean)));
                l.add(Setting.info("Passengers", "Catchment", px.pop + " people"));
                l.add(Setting.info("Passengers", "Waiting now", String.valueOf(Math.round(px.waitingTotal()))));
                l.add(Setting.info("Passengers", "Boarded / alighted today", Math.round(px.boardedDay) + " / " + Math.round(px.alightedDay)));
                l.add(Setting.info("Passengers", "Left behind / walked away", Math.round(px.leftDay) + " / " + Math.round(px.walkedDay)));
                l.add(Setting.info("Passengers", "Fare dodgers (est.)", String.valueOf(Math.round(px.evadersDay))));
                l.add(Setting.info("Passengers", "Complaints today", Math.round(px.complaintsDay) + (px.topReason().isEmpty() ? "" : " - mostly " + px.topReason())));
                l.add(Setting.info("Passengers", "Fares taken today", com.dogpound.railmap.economy.Coins.fmt(px.revenueDay)));
            }
        com.dogpound.railmap.wire.IWiredGear.settings(l, "Today", this);
            for (int i = 0; i < log.size(); i++) l.add(Setting.info("Log", i == 0 ? "Latest" : "", log.get(i)));
        }
        return l;
    }

    @Override public SettingsStore settings() { return cfg; }
    @Override public void onSettingsChanged(String key) { markDirty(); }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        term.write(t);
        cfg.write(t);
        ctl.write(t);
        t.setString("registered", registered);
        t.setInteger("arr", arrivals);
        t.setInteger("dep", departures);
        NBTTagList l = new NBTTagList();
        for (String s : log) l.appendTag(new NBTTagString(s));
        t.setTag("log", l);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        term.read(t);
        cfg.read(t);
        ctl.read(t);
        registered = t.getString("registered");
        arrivals = t.getInteger("arr");
        departures = t.getInteger("dep");
        log.clear();
        NBTTagList l = t.getTagList("log", 8);
        for (int i = 0; i < l.tagCount(); i++) log.add(l.getStringTagAt(i));
    }
}
