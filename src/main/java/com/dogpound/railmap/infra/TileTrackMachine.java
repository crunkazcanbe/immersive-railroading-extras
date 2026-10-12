package com.dogpound.railmap.infra;

import cam72cam.immersiverailroading.entity.EntityRollingStock;
import cam72cam.mod.math.Vec3d;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.block.BlockStationDevice;
import com.dogpound.railmap.scan.SwitchControl;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import java.util.Locale;

/**
 * A self-propelled on-track works machine (#42: the rail grinder and the rail welder). It stands ON the rails (place
 * it on top of a rail, facing along the track) and drives itself along them block by block - straight on, up or down
 * a gradient, or one block sideways round a curve - and stops at the end of the track, at a train ahead, or on its
 * emergency stop. Its own diesel engine (fuel: diesel / biodiesel / oil from any mod's bucket, or coal / charcoal /
 * blaze rods into the filler) drives it, runs its work heads and charges its 12 V battery, which keeps the panel lit
 * with the engine off (the 12 V terminal can also charge a flat battery). With InfraConfig.worksNeedPossession it
 * refuses to work outside a possession.
 */
public abstract class TileTrackMachine extends InfraTile {
    protected double fuel, batt = 80;
    protected boolean engine, travel;
    protected String state = "PARKED";
    protected long nextStep;
    protected int moved;
    private String cover = "";
    private long coverAt;

    protected TileTrackMachine() { super(null); }

    /** the onboard battery runs the panel; the 12 V terminal or the alternator charge it */
    @Override public boolean powered() { return batt > 2 || term.powered(); }

    protected static final double TANK = 400;

    protected boolean estop() { return ctl.get("m_estop") == 1; }

    /** a possession covers it (or none is needed) */
    protected boolean possessionOk() {
        long now = world.getTotalWorldTime();
        if (now - coverAt > 40) { cover = Possessions.cover(world, pos); coverAt = now; }
        return !InfraConfig.worksNeedPossession || !cover.isEmpty();
    }

    protected String coverText() { return cover; }

    /** litres per second the engine burns at this work load (0 idle .. 1 full) */
    protected void burn(double load) {
        if (!engine) return;
        fuel = Math.max(0, fuel - (0.015 + 0.11 * load));
        batt = Math.min(100, batt + 0.5);
        if (fuel <= 0) { engine = false; state = "OUT OF FUEL"; world.playSound(null, pos, com.dogpound.railmap.ModSounds.DIESEL_STOP, SoundCategory.BLOCKS, 0.8f, 1f); log("engine stopped - out of fuel"); }
    }

    /** once a second for the panel battery */
    protected void battery() {
        if (!engine) batt = Math.max(0, batt - 0.004);
        if (term.powered() && !engine) batt = Math.min(100, batt + 0.2);
    }

    protected String startStop(EntityPlayer p) {
        if (engine) {
            engine = false; travel = false; state = "ENGINE OFF";
            world.playSound(null, pos, com.dogpound.railmap.ModSounds.DIESEL_STOP, SoundCategory.BLOCKS, 0.8f, 1f);
            return "§eEngine stopped";
        }
        if (estop()) return "§cE-STOP is latched - pull it out first";
        if (fuel < 1) return "§cNo fuel - fill it at the filler (diesel / oil bucket, coal, charcoal)";
        if (batt < 10) return "§cBattery flat - charge it on 12 V (terminal) first";
        batt -= 5;
        engine = true; state = "ENGINE RUNNING";
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.DIESEL_START, SoundCategory.BLOCKS, 0.9f, 1f);
        log("engine started by " + who(p));
        return "§aEngine running";
    }

    /** the filler: whatever the player holds */
    protected String refuel(EntityPlayer p) {
        if (p == null) return "§7Refuelling is done at the filler";
        ItemStack h = p.getHeldItemMainhand();
        double add = 0;
        FluidStack fs = h.isEmpty() ? null : FluidUtil.getFluidContained(h);
        if (fs != null) {
            String n = fs.getFluid().getName().toLowerCase(Locale.ROOT);
            if (n.contains("diesel") || n.contains("oil") || n.contains("fuel") || n.contains("ethanol")) add = fs.amount / 1000.0 * 200;
            else return "§c" + fs.getLocalizedName() + " is not fuel";
        } else if (h.getItem() == Items.COAL) add = 40;
        else if (h.getItem() == Items.BLAZE_ROD) add = 60;
        else return String.format(Locale.ROOT, "§7Fuel %.0f / %.0f L - hold a diesel / oil bucket, coal or charcoal to fill", fuel, TANK);
        if (fuel >= TANK - 1) return "§7Tank full";
        add = Math.min(add, TANK - fuel);
        fuel += add;
        if (!p.capabilities.isCreativeMode) {
            if (fs != null) { ItemStack empty = h.getItem().getContainerItem(h); h.shrink(1); if (!empty.isEmpty() && !p.inventory.addItemStackToInventory(empty)) p.dropItem(empty, false); }
            else h.shrink(1);
        }
        return String.format(Locale.ROOT, "§aFuelled +%.0f L (%.0f / %.0f L)", add, fuel, TANK);
    }

    // ---- driving along the rails -----------------------------------------------------------------------------------------
    protected EnumFacing heading() {
        IBlockState s = world.getBlockState(pos);
        EnumFacing f = s.getPropertyKeys().contains(BlockStationDevice.FACING) ? s.getValue(BlockStationDevice.FACING) : EnumFacing.NORTH;
        return ctl.get("s_dir") == 1 ? f.getOpposite() : f;
    }

    /** the next spot along the track, or null (end of track / something in the way); why[0] says which */
    protected BlockPos next(String[] why) {
        EnumFacing f = heading();
        BlockPos a = pos.offset(f);
        BlockPos[] tries = {a, a.up(), a.down(), a.offset(f.rotateY()), a.offset(f.rotateYCCW()), a.offset(f.rotateY()).down(), a.offset(f.rotateYCCW()).down()};
        for (BlockPos c : tries) {
            if (!world.isBlockLoaded(c) || !SwitchControl.isRail(world, c.down())) continue;
            if (!world.isAirBlock(c)) { why[0] = "OBSTRUCTED"; return null; }
            if (trainNear(c)) { why[0] = "TRAIN AHEAD"; return null; }
            return c;
        }
        why[0] = "END OF TRACK";
        return null;
    }

    private boolean trainNear(BlockPos c) {
        try {
            for (EntityRollingStock s : cam72cam.mod.world.World.get(world).getEntities(EntityRollingStock.class)) {
                Vec3d v = s.getPosition();
                if (!s.isDead() && Math.abs(v.y - c.getY()) < 4 && (v.x - c.getX() - 0.5) * (v.x - c.getX() - 0.5) + (v.z - c.getZ() - 0.5) * (v.z - c.getZ() - 0.5) < 16) return true;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return false;
    }

    /** move the whole machine (block + tile state) to c. The caller must return straight after: this tile is gone */
    protected void moveTo(BlockPos c) {
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        IBlockState st = world.getBlockState(pos);
        BlockPos old = pos;
        moved++;
        tag.setInteger("moved", moved);
        world.setBlockState(c, st, 3);
        TileEntity nt = world.getTileEntity(c);
        if (nt instanceof TileTrackMachine m) {
            tag.setInteger("x", c.getX()); tag.setInteger("y", c.getY()); tag.setInteger("z", c.getZ());
            m.readFromNBT(tag);
            m.markDirty();
            m.controlsChanged();
        }
        world.setBlockToAir(old);
    }

    protected void log(String s) {
        InfraData.get(world).log(world, wiredName() + ": " + s);
        Recorder.event(world, wiredName() + " at " + pos.getX() + "," + pos.getZ() + ": " + s);
    }

    /** the panel parts every machine has */
    protected void commonPanel() {
        put("eng", engine); put("nopos", InfraConfig.worksNeedPossession && cover.isEmpty()); put("poss", !cover.isEmpty());
        put("pwr", powered()); put("g_fuel", (int) Math.round(fuel / TANK * 100)); put("g_batt", (int) Math.round(batt));
        text("fuel", String.format(Locale.ROOT, "FUEL %.0f L", fuel));
        text("state", state);
    }

    protected void storeMachine(NBTTagCompound t) {
        t.setDouble("fuel", fuel); t.setDouble("batt", batt); t.setBoolean("eng", engine); t.setBoolean("travel", travel); t.setString("state", state); t.setInteger("moved", moved);
        t.setLong("nextStep", nextStep);                                   // carried across a move: the drive keeps its pace
    }

    protected void restoreMachine(NBTTagCompound t) {
        fuel = t.getDouble("fuel"); batt = t.hasKey("batt") ? t.getDouble("batt") : 80; engine = t.getBoolean("eng"); travel = t.getBoolean("travel");
        state = t.hasKey("state") ? t.getString("state") : "PARKED"; moved = t.getInteger("moved"); nextStep = t.getLong("nextStep");
    }
}
