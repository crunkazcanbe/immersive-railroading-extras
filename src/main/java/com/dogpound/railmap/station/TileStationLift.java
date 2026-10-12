package com.dogpound.railmap.station;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Station Lift landing (her ChatGPT list #81): put one on each level, stacked in the same column (any height apart) -
 * street, concourse, platforms. CALL brings the car; the floor buttons send it, carrying everyone standing in front of
 * the landing doors to that floor's landing (2 s a floor, doors chime). The indicator shows where the car is. A lift
 * needs 12 V and mains; with either gone, or the OUT OF SERVICE key turned, it is out of service - and the station
 * loses its step-free access (its accessibility rating drops, step-free passengers stop coming). The ALARM button in
 * the car raises an alarm.
 */
public class TileStationLift extends DepotTile implements IStationFacility {
    /** the car of each lift shaft: (dim, x, z) -> {floor, target, ticks to go} */
    private static final Map<String, int[]> CARS = new HashMap<>();
    private static final Map<String, List<UUID>> RIDERS = new HashMap<>();

    @Override public String wiredName() { return "Station lift"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { int[] c = car(); return c[0] != c[1] ? 30 : 2; }
    @Override public String gearState() { return !facilityWorking() ? "OUT OF SERVICE" : "car at floor " + (car()[0] + 1) + " of " + floors().size(); }

    String key() { return world.provider.getDimension() + ":" + pos.getX() + ":" + pos.getZ(); }
    int[] car() { return CARS.computeIfAbsent(key(), k -> new int[]{0, 0, 0}); }

    private List<TileStationLift> floorCache;
    private long floorAt = -100;

    /** every landing in this shaft, bottom up (looked up at most every 2 s) */
    List<TileStationLift> floors() {
        if (floorCache != null && world.getTotalWorldTime() - floorAt < 40) return floorCache;
        floorAt = world.getTotalWorldTime();
        List<TileStationLift> o = floorCache = new ArrayList<>();
        for (int y = 0; y < 256; y++) {
            TileEntity t = world.isBlockLoaded(new BlockPos(pos.getX(), y, pos.getZ())) ? world.getTileEntity(new BlockPos(pos.getX(), y, pos.getZ())) : null;
            if (t instanceof TileStationLift l) o.add(l);
        }
        return o;
    }

    int myFloor() { List<TileStationLift> f = floors(); return Math.max(0, f.indexOf(this)); }

    EnumFacing front() { return controlFacing(); }

    @Override public boolean facilityWorking() { return powered() && mainsOk() && ctl.get("k_oos") == 0; }
    @Override public String facilityKind() { return "Lift"; }
    @Override public int facilityPoints() { return 8; }
    @Override public int accessPoints() { return 40; }
    @Override public boolean stepFree() { return true; }
    @Override public long dailyCost() { return 400; }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("alarm")) {
            AlarmManager.get(world).raise(world, "liftalarm:" + key(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MAJOR, "LIFT ALARM pressed at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " - passenger needs help", pos, false);
            return "§cAlarm raised - help is on its way";
        }
        if (!facilityWorking()) return "§cLIFT OUT OF SERVICE";
        int[] c = car();
        List<TileStationLift> fl = floors();
        int target;
        if (id.equals("call")) target = myFloor();
        else if (id.length() == 2 && id.charAt(0) == 'f' && Character.isDigit(id.charAt(1))) target = id.charAt(1) - '1';
        else return null;
        if (target >= fl.size()) return "§7There are only " + fl.size() + " floors";
        if (c[0] != c[1]) return "§7The lift is moving";
        if (target == c[0] && !id.equals("call")) return "§7Already at floor " + (target + 1);
        List<UUID> ride = new ArrayList<>();
        if (!id.equals("call") && c[0] == myFloor()) {
            BlockPos at = pos.offset(front());
            for (EntityPlayer p : world.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB(at).grow(0.6, 1, 0.6))) ride.add(p.getUniqueID());
        }
        RIDERS.put(key(), ride);
        c[1] = target;
        c[2] = Math.max(20, Math.abs(target - c[0]) * 40);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("station.lift_run"), SoundCategory.BLOCKS, 0.7f, 1.0f);
        return id.equals("call") ? "§aLift called" : "§aGoing to floor " + (target + 1) + (ride.isEmpty() ? "" : " with " + ride.size() + " aboard");
    }

    @Override
    protected void work() {
        if (age % 5 != 0) return;
        int[] c = car();
        List<TileStationLift> fl = null;
        if (c[0] != c[1] && myFloor() == 0) {                     // the bottom landing drives the car
            if (!facilityWorking()) { c[1] = c[0]; RIDERS.remove(key()); }
            else if ((c[2] -= 5) <= 0) {
                fl = floors();
                int dest = Math.min(c[1], fl.size() - 1);
                c[0] = dest; c[1] = dest;
                TileStationLift to = fl.get(dest);
                world.playSound(null, to.getPos(), com.dogpound.railmap.sound.PackSounds.ev("station.lift_ding"), SoundCategory.BLOCKS, 0.8f, 1.0f);
                for (UUID u : RIDERS.getOrDefault(key(), Collections.emptyList())) {
                    EntityPlayer p = world.getPlayerEntityByUUID(u);
                    if (p instanceof EntityPlayerMP mp) {
                        BlockPos d = to.pos.offset(to.front());
                        mp.connection.setPlayerLocation(d.getX() + 0.5, d.getY(), d.getZ() + 0.5, mp.rotationYaw, mp.rotationPitch);
                    }
                }
                RIDERS.remove(key());
                world.playSound(null, to.pos, com.dogpound.railmap.ModSounds.BLOCK_BELL, SoundCategory.BLOCKS, 0.8f, 1.0f);
            }
        }
        int mine = myFloor();
        put("pwr", powered()); put("mainsl", mains.ok()); put("oos", !facilityWorking());
        put("here", c[0] == mine && c[0] == c[1]); put("up", c[1] > c[0]); put("dn", c[1] < c[0]);
        put("doors", c[0] == mine && c[0] == c[1] && facilityWorking());
        text("floor", facilityWorking() ? String.valueOf(c[0] + 1) : "--");
        text("line1", facilityWorking() ? (c[0] == c[1] ? "FLOOR " + (c[0] + 1) : c[1] > c[0] ? "GOING UP" : "GOING DOWN") : "OUT OF SERVICE");
    }

    public static void clear() { CARS.clear(); RIDERS.clear(); }

    @Override protected void saveExtra(NBTTagCompound t) { }
    @Override protected void loadExtra(NBTTagCompound t) { }
}
