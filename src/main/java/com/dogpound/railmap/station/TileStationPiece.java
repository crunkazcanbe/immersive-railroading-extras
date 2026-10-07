package com.dogpound.railmap.station;

import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.settings.ISettingsHolder;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The live station pieces: name board (station name), platform sign (number + "train at platform"), clock. */
public class TileStationPiece extends TileEntity implements ITickable, ISettingsHolder {
    private String kind = "clock";
    private final SettingsStore cfg = new SettingsStore(this);
    /** what the server worked out: the station name / the train standing at this platform */
    private String station = "", train = "";
    private int ticks;

    public TileStationPiece() { }
    public TileStationPiece(String kind) { this.kind = kind; }

    public String kind() { return kind; }
    public String station() { String t = cfg.text("text").trim(); return t.isEmpty() ? station : t; }
    public String train() { return train; }
    public SettingsStore cfg() { return cfg; }

    @Override
    public void update() {
        if (world == null || world.isRemote || ++ticks % 20 != 0) return;
        String st = nearestStation(), tr = kind.equals("platform_sign") ? trainHere() : "";
        if (!st.equals(station) || !tr.equals(train)) {
            station = st; train = tr;
            markDirty();
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    /** the nearest named station (a Station Master's Desk registers its name there too) */
    private String nearestStation() {
        StationData d = StationData.get(world);
        String best = "";
        double bd = 96 * 96;
        for (Map.Entry<Long, String> e : d.names().entrySet()) {
            double dd = BlockPos.fromLong(e.getKey()).distanceSq(pos);
            if (dd < bd) { bd = dd; best = e.getValue(); }
        }
        return best;
    }

    /** an IR train stopped (or nearly) within the platform reach */
    private String trainHere() {
        if (!cfg.bool("detect")) return "";
        double r = cfg.num("reach");
        try {
            cam72cam.mod.world.World w = cam72cam.mod.world.World.get(world);
            if (w == null) return "";
            for (cam72cam.immersiverailroading.entity.EntityRollingStock s : w.getEntities(cam72cam.immersiverailroading.entity.EntityRollingStock.class)) {
                cam72cam.mod.math.Vec3d p = s.getPosition();
                if (Math.abs(p.y - pos.getY()) > 6) continue;
                double dx = p.x - pos.getX() - 0.5, dz = p.z - pos.getZ() - 0.5;
                if (dx * dx + dz * dz > r * r) continue;
                if (s instanceof cam72cam.immersiverailroading.entity.EntityMoveableRollingStock m && m.getCurrentSpeed() != null
                        && Math.abs(m.getCurrentSpeed().metric()) > 8) continue;
                return s.getDefinition() == null ? "Train" : s.getDefinition().name();
            }
        } catch (LinkageError | RuntimeException ignored) { }
        return "";
    }

    public String statusLine() {
        switch (kind) {
            case "name_sign": return "§b" + (station().isEmpty() ? "No station nearby - place a Station Master's Desk" : station());
            case "platform_sign": return "§ePlatform " + cfg.num("number") + cfg.text("suffix") + " §7· " + (train.isEmpty() ? "clear" : "§a" + train + " at platform");
            default: return "§7Station clock · " + cfg.text("source") + " time";
        }
    }

    // ---- Settings Console ------------------------------------------------------------------------------
    @Override
    public String settingsTitle() {
        return kind.equals("name_sign") ? "Station Name Board" : kind.equals("platform_sign") ? "Platform Sign" : "Station Clock";
    }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        switch (kind) {
            case "name_sign":
                l.add(Setting.text("Board", "text", "Name shown", "Blank = the nearest station's name", "", 32));
                l.add(Setting.bool("Board", "upper", "CAPITALS", "Old railway boards are all capitals", false));
                l.add(Setting.num("Board", "size", "Letter size", "", 100, 50, 200, 10, "%"));
                l.add(Setting.bool("Board", "lit", "Lit at night", "", true));
                break;
            case "platform_sign":
                l.add(Setting.num("Sign", "number", "Platform number", "", 1, 0, 99, 1, ""));
                l.add(Setting.text("Sign", "suffix", "Letter", "e.g. a / b for split platforms", "", 2));
                l.add(Setting.bool("Sign", "detect", "Show the train at the platform", "Names the train standing here", true));
                l.add(Setting.num("Sign", "reach", "Platform length", "How far along the platform a train counts as 'here'", 24, 4, 96, 4, "blocks"));
                l.add(Setting.bool("Sign", "lit", "Lit at night", "", true));
                break;
            default:
                l.add(Setting.choice("Clock", "source", "Time", "Game = the sun's time of day, Real = your computer's clock", "Game", "Game", "Real"));
                l.add(Setting.bool("Clock", "seconds", "Seconds hand", "", true));
                l.add(Setting.bool("Clock", "lit", "Lit face at night", "", true));
                break;
        }
        if (world != null && !world.isRemote) l.add(Setting.info("Status", "Station", station.isEmpty() ? "none within 96 blocks" : station));
        return l;
    }

    @Override public SettingsStore settings() { return cfg; }

    @Override
    public void onSettingsChanged(String key) {
        ticks = 19;
        markDirty();
    }

    // ---- sync -------------------------------------------------------------------------------------------
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("kind", kind);
        t.setString("station", station);
        t.setString("train", train);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        if (t.hasKey("kind")) kind = t.getString("kind");
        station = t.getString("station");
        train = t.getString("train");
        cfg.read(t);
    }

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public SPacketUpdateTileEntity getUpdatePacket() { return new SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) { readFromNBT(pkt.getNbtCompound()); }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new AxisAlignedBB(pos).grow(1.5, 2.5, 1.5);
    }
}
