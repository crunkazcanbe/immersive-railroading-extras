package com.dogpound.railmap.doors;

import cam72cam.immersiverailroading.entity.EntityMoveableRollingStock;
import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.settings.Setting;
import com.dogpound.railmap.settings.SettingsStore;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * Station Door Controller: put it on the platform edge. When a train stops alongside it the controller runs the
 * real sequence - arrival chime, doors open on the platform side (each car works out which of its sides faces the
 * platform), dwell, closing chime + warning beeps, doors close, the platform screen doors follow, the train is held
 * until every door is shut, and a redstone signal tells you what's happening. Everything is programmable.
 */
public class TileStationDoors extends TileEntity implements ITickable, com.dogpound.railmap.settings.ISettingsHolder {
    private static final boolean IR = Loader.isModLoaded("immersiverailroading");
    public enum Phase { IDLE, STOPPING, OPENING, OPEN, WARNING, CLOSING, DEPARTING }

    private final SettingsStore cfg = new SettingsStore(this);
    private Phase phase = Phase.IDLE;
    private int timer, still, served, held;
    private boolean lastRedstone;
    private String lastTrain = "";
    private final List<java.util.UUID> cars = new ArrayList<>();

    public Phase phase() { return phase; }

    @Override
    public void update() {
        if (world == null || world.isRemote || !IR || world.getTotalWorldTime() % 2 != 0) return;
        boolean power = world.isBlockPowered(pos);
        boolean pulse = power && !lastRedstone;
        lastRedstone = power;
        String rs = cfg.text("redstoneIn");
        List<EntityRollingStock> here = carsAlongside();
        boolean stopped = !here.isEmpty() && here.stream().allMatch(this::stopped);
        if (timer > 0) timer -= 2;
        switch (phase) {
            case IDLE:
                if (cfg.bool("enabled") && stopped && (!rs.equals("Only when powered") || power) && allowed(here)) {
                    still += 2;
                    if (still >= cfg.num("settle") * 20) { phase = Phase.STOPPING; timer = 0; }
                } else {
                    still = 0;
                }
                if (pulse && rs.equals("Pulse opens") && !here.isEmpty()) phase = Phase.STOPPING;
                break;
            case STOPPING:                                   // arrival chime, then open
                cars.clear();
                for (EntityRollingStock c : here) cars.add(c.getUUID());
                if (!here.isEmpty()) lastTrain = name(here.get(0));
                if (cfg.bool("chimeArrive")) chime();
                timer = cfg.num("openDelay") * 20;
                phase = Phase.OPENING;
                break;
            case OPENING:
                if (timer <= 0) {
                    for (EntityRollingStock c : here) for (String side : sides(c)) TrainDoors.move(world, c, side, true, cfg.num("stroke") / 10f);
                    platformDoors(true);
                    served++;
                    timer = cfg.num("dwell") * 20;
                    phase = Phase.OPEN;
                    markDirty();
                    notifyNeighbours();
                }
                break;
            case OPEN:
                hold(here);
                boolean keep = rs.equals("Hold open while powered") && power;
                if (keep) timer = Math.max(timer, 20);
                if (timer <= cfg.num("warn") * 20 && !keep) {
                    if (cfg.bool("chimeClose")) chime();
                    if (cfg.bool("beeps")) for (EntityRollingStock c : here) TrainDoors.sound(world, c, ModSounds.DOOR_WARN, cfg.num("volume") / 100f);
                    phase = Phase.WARNING;
                }
                if (here.isEmpty()) phase = Phase.CLOSING;
                break;
            case WARNING:
                hold(here);
                if (timer <= 0) {
                    for (EntityRollingStock c : here) for (String side : new String[]{TrainDoors.RIGHT, TrainDoors.LEFT}) TrainDoors.move(world, c, side, false, cfg.num("stroke") / 10f);
                    platformDoors(false);
                    timer = cfg.num("stroke") * 2 + 10;
                    phase = Phase.CLOSING;
                }
                break;
            case CLOSING:
                hold(here);
                if (timer <= 0) {
                    release(here);
                    phase = Phase.DEPARTING;
                    notifyNeighbours();
                }
                break;
            case DEPARTING:                                  // wait for this train to leave before serving the next
                if (here.isEmpty() || here.stream().noneMatch(this::stopped) || !cars.contains(here.get(0).getUUID())) {
                    phase = Phase.IDLE;
                    still = 0;
                    notifyNeighbours();
                }
                break;
        }
    }

    private void chime() {
        world.playSound(null, pos, ModSounds.DOOR_CHIME, SoundCategory.BLOCKS, cfg.num("volume") / 100f, cfg.num("pitch") / 100f);
    }

    private boolean stopped(EntityRollingStock s) {
        return !(s instanceof EntityMoveableRollingStock m) || m.getCurrentSpeed() == null
                || Math.abs(m.getCurrentSpeed().metric()) < cfg.num("stopKmh") / 10.0;
    }

    private boolean allowed(List<EntityRollingStock> here) {
        String only = cfg.text("onlyNamed").trim().toLowerCase(java.util.Locale.ROOT);
        if (only.isEmpty()) return true;
        for (EntityRollingStock s : here) if (name(s).toLowerCase(java.util.Locale.ROOT).contains(only)) return true;
        return false;
    }

    private static String name(EntityRollingStock s) {
        String n = s.getDefinition() == null ? s.getDefinitionID() : s.getDefinition().name();
        return s.tag != null && !s.tag.isEmpty() ? s.tag : n;
    }

    /** IR stock alongside the platform: within reach along the platform and close to it sideways */
    private List<EntityRollingStock> carsAlongside() {
        List<EntityRollingStock> out = new ArrayList<>();
        int reach = cfg.num("reach"), side = cfg.num("gap");
        cam72cam.mod.world.World umc = cam72cam.mod.world.World.get(world);
        for (EntityRollingStock s : umc.getEntities(EntityRollingStock.class)) {
            if (s.isDead()) continue;
            cam72cam.mod.math.Vec3d p = s.getPosition();
            double dx = p.x - (pos.getX() + 0.5), dz = p.z - (pos.getZ() + 0.5), dy = p.y - pos.getY();
            if (Math.abs(dy) > 4 || dx * dx + dz * dz > (double) (reach + side) * (reach + side)) continue;
            out.add(s);
        }
        return out;
    }

    /** which door side(s) of this car face the controller (the platform) */
    private String[] sides(EntityRollingStock c) {
        String mode = cfg.text("side");
        if (mode.equals("Both")) return new String[]{TrainDoors.RIGHT, TrainDoors.LEFT};
        if (mode.equals("Right")) return new String[]{TrainDoors.RIGHT};
        if (mode.equals("Left")) return new String[]{TrainDoors.LEFT};
        double yaw = Math.toRadians(c.getRotationYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = fz, rz = -fx;                                   // model +z ("right") in the world (verified in game)
        cam72cam.mod.math.Vec3d p = c.getPosition();
        double dot = (pos.getX() + 0.5 - p.x) * rx + (pos.getZ() + 0.5 - p.z) * rz;
        boolean right = dot > 0;
        if (cfg.bool("flip")) right = !right;
        return new String[]{right ? TrainDoors.RIGHT : TrainDoors.LEFT};
    }

    /** door interlock: no traction while any door is open (real trains can't take power with doors open) */
    private void hold(List<EntityRollingStock> here) {
        if (!cfg.bool("interlock")) return;
        for (EntityRollingStock s : here) {
            if (s instanceof Locomotive l) {
                l.setThrottle(0);
                l.setTrainBrake(1);
                held++;
            }
        }
    }

    private void release(List<EntityRollingStock> here) {
        if (!cfg.bool("interlock") || !cfg.bool("releaseBrake")) return;
        for (EntityRollingStock s : here) if (s instanceof Locomotive l) l.setTrainBrake(0);
    }

    /** platform screen doors in range follow the train doors */
    private void platformDoors(boolean open) {
        if (!cfg.bool("psd")) return;
        int r = cfg.num("psdReach");
        boolean sound = false;
        for (BlockPos p : BlockPos.getAllInBox(pos.add(-r, -2, -r), pos.add(r, 3, r))) {
            IBlockState st = world.getBlockState(p);
            if (st.getBlock() instanceof BlockPlatformDoor) {
                BlockPlatformDoor.drive(world, p, open);
                sound = true;
            }
        }
        if (sound) world.playSound(null, pos, ModSounds.PSD_MOVE, SoundCategory.BLOCKS, cfg.num("volume") / 100f, 1f);
    }

    private void notifyNeighbours() {
        world.notifyNeighborsOfStateChange(pos, getBlockType(), false);
    }

    /** redstone out: 15 while doors open, 7 while closing/warning, 0 otherwise */
    public int redstoneOut() {
        if (!cfg.bool("redstoneOut")) return 0;
        switch (phase) {
            case OPEN: return 15;
            case WARNING: case CLOSING: case OPENING: return 7;
            default: return 0;
        }
    }

    public String statusLine() {
        return "Station doors · " + phase.name().toLowerCase(java.util.Locale.ROOT) + (lastTrain.isEmpty() ? "" : " · last: " + lastTrain)
                + " · served " + served;
    }

    // ---- Settings Console --------------------------------------------------------------------------------------
    @Override public String settingsTitle() { return "Station Door Controller"; }
    @Override public SettingsStore settings() { return cfg; }

    @Override
    public List<Setting> settingDefs() {
        List<Setting> l = new ArrayList<>();
        String a = "Doors", t = "Timing", s = "Sound", p = "Platform doors", r = "Redstone", f = "Filter", st = "Status";
        l.add(Setting.bool(a, "enabled", "Automatic doors", "Open the train doors when a train stops here", true));
        l.add(Setting.choice(a, "side", "Which side", "Auto = the side of each car facing this block", "Auto", "Auto", "Right", "Left", "Both"));
        l.add(Setting.bool(a, "flip", "Flip auto side", "Use if your doors open on the track side", false));
        l.add(Setting.num(a, "reach", "Platform length", "Cars within this many blocks of the controller", 20, 4, 96, 2, "blocks"));
        l.add(Setting.num(a, "gap", "Platform gap", "How far the train can be from the platform edge", 4, 1, 8, 1, "blocks"));
        l.add(Setting.bool(a, "interlock", "Door interlock", "Hold the brakes and cut power while any door is open", true));
        l.add(Setting.bool(a, "releaseBrake", "Release brake after closing", "Let the train go once the doors are shut", true));
        l.add(Setting.num(t, "settle", "Stopped for", "How long the train must be standing still first", 1, 0, 10, 1, "s"));
        l.add(Setting.num(t, "stopKmh", "Counts as stopped below", "Speed limit for 'stopped' (tenths of km/h)", 3, 1, 30, 1, "x0.1 km/h"));
        l.add(Setting.num(t, "openDelay", "Open delay", "Pause between the chime and the doors opening", 1, 0, 10, 1, "s"));
        l.add(Setting.num(t, "dwell", "Dwell time", "How long the doors stay open", 20, 3, 600, 1, "s"));
        l.add(Setting.num(t, "warn", "Closing warning", "Beeps this long before the doors close", 4, 0, 15, 1, "s"));
        l.add(Setting.num(t, "stroke", "Door speed", "How long one opening / closing stroke takes", 25, 5, 80, 5, "x0.1 s"));
        l.add(Setting.bool(s, "chimeArrive", "Arrival chime", "Three-note chime when the doors are about to open", true));
        l.add(Setting.bool(s, "chimeClose", "Departure chime", "Chime before the doors close", true));
        l.add(Setting.bool(s, "beeps", "Warning beeps", "The door-closing beeps on the train", true));
        l.add(Setting.num(s, "volume", "Volume", "Chimes and door sounds", 100, 0, 300, 10, "%"));
        l.add(Setting.num(s, "pitch", "Chime pitch", "Higher or lower chime", 100, 50, 200, 5, "%"));
        l.add(Setting.bool(p, "psd", "Platform screen doors", "Open the platform doors with the train", true));
        l.add(Setting.num(p, "psdReach", "Platform doors within", "Platform door blocks this close follow this controller", 24, 2, 64, 2, "blocks"));
        l.add(Setting.choice(r, "redstoneIn", "Redstone in", "What a redstone signal on this block does", "Ignore", "Ignore", "Pulse opens", "Hold open while powered", "Only when powered"));
        l.add(Setting.bool(r, "redstoneOut", "Redstone out", "15 = doors open, 7 = moving / warning (comparators, lamps)", true));
        l.add(Setting.text(f, "onlyNamed", "Only trains named", "Leave empty for every train (matches name or tag)", "", 40));
        l.add(Setting.info(st, "Now", phase.name().toLowerCase(java.util.Locale.ROOT)));
        l.add(Setting.info(st, "Trains served", String.valueOf(served)));
        l.add(Setting.info(st, "Last train", lastTrain.isEmpty() ? "-" : lastTrain));
        return l;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setString("phase", phase.name());
        t.setInteger("served", served);
        t.setString("lastTrain", lastTrain);
        cfg.write(t);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        try { phase = Phase.valueOf(t.getString("phase")); } catch (IllegalArgumentException e) { phase = Phase.IDLE; }
        served = t.getInteger("served");
        lastTrain = t.getString("lastTrain");
        cfg.read(t);
    }
}
