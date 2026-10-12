package com.dogpound.railmap.road;

import com.dogpound.railmap.graph.TrainNode;
import com.dogpound.railmap.ohle.Reg;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;

/**
 * Tram signal (batch 5 #4): the white-bar aspects tram drivers read (BOStrab F-signals). Horizontal bar = STOP,
 * vertical bar = GO straight, diagonal bar = GO left / right (ROUTE selector), the dot = PREPARE TO STOP (the tram
 * stage is ending), the lit "A" triangle = your call is registered (a detection loop asked the controller for priority).
 * <p>
 * MODE selector on the back: CONTROLLER = shows the tram stage (group T) of the nearest Traffic Signal Controller - the
 * tram signal controller of its junction; SECTION = a stand-alone section signal: STOP while another train is on the
 * 48 blocks of track beyond it, GO when that is clear (single-track street sections, depot fans). Place it beside the
 * track facing the trams it controls; tram-mode autopilots stop at it on STOP and PREPARE.
 */
public class TileTramSignal extends RoadTile {
    static final Reg<TileTramSignal> REG = new Reg<>();
    public static final int DARK = 0, STOP = 1, GO = 2, PREPARE = 3;
    static final String[] NAMES = {"dark", "STOP", "GO", "PREPARE"};
    private long ctlPos = Long.MIN_VALUE;
    private int aspect;

    @Override public void onLoad() { REG.add(this); }
    @Override public void invalidate() { super.invalidate(); REG.remove(this); }
    @Override public void onChunkUnload() { REG.remove(this); }

    public static java.util.Collection<TileTramSignal> all(net.minecraft.world.World w) { return REG.all(w); }

    public int aspect() { return aspect; }
    public String aspectName() { return NAMES[aspect]; }
    public boolean stopForTram() { return aspect == STOP || aspect == PREPARE; }
    public boolean section() { return ctl.get("s_mode") % 2 == 1; }
    public EnumFacing facing() { return controlFacing(); }

    public TileTrafficController controller() {
        if (section() || ctlPos == Long.MIN_VALUE) return null;
        return world.getTileEntity(net.minecraft.util.math.BlockPos.fromLong(ctlPos)) instanceof TileTrafficController c && !c.isInvalid() ? c : null;
    }

    @Override
    protected void serverTick() {
        if (age % 100 == 1 || ctlPos == Long.MIN_VALUE && age % 20 == 1) {
            TileTrafficController c = TileTrafficController.nearest(world, pos);
            ctlPos = c == null ? Long.MIN_VALUE : c.getPos().toLong();
        }
        if (age % 4 != 0) return;
        boolean called = false;
        if (section()) aspect = occupied() ? STOP : GO;
        else {
            TileTrafficController c = controller();
            int a = c == null ? TileTrafficController.Phasing.DARK : c.aspect(TileTrafficController.T);
            aspect = a == TileTrafficController.Phasing.DARK ? DARK : a == TileTrafficController.Phasing.GREEN ? GO : a == TileTrafficController.Phasing.WALK_FLASH ? PREPARE : STOP;
            called = c != null && c.live() && c.demand(TileTrafficController.T);
        }
        int dir = Math.floorMod(ctl.get("s_dir"), 3);
        put("h", aspect == STOP);
        put("v", aspect == GO && dir == 0);
        for (int i = 1; i <= 3; i++) { put("l" + i, aspect == GO && dir == 1); put("r" + i, aspect == GO && dir == 2); }
        put("dot", aspect == PREPARE);
        put("tri", called);
    }

    /** SECTION mode: another train within 48 blocks of track beyond the signal (away from the lenses) */
    private boolean occupied() {
        EnumFacing f = facing();
        double nx = f.getXOffset(), nz = f.getZOffset();
        for (TrainNode t : com.dogpound.railmap.server.TrainTracker.latest(world)) {
            if (Math.abs(t.y - pos.getY()) > 5) continue;
            double rx = t.x - pos.getX() - 0.5, rz = t.z - pos.getZ() - 0.5;
            double along = -(rx * nx + rz * nz), lat = Math.abs(rx * -nz + rz * nx);
            if (along > 1 && along < 48 && lat < 4) return true;
        }
        return false;
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.startsWith("s_") && who == null) return "§7Set at the signal itself";
        if (id.equals("s_mode")) { ctl.set("s_mode", (ctl.get("s_mode") + 1) % 2); return section() ? "§eTram signal: SECTION - STOP while the 48 blocks beyond it are occupied" : "§eTram signal: CONTROLLER - shows the tram stage of the nearest controller"; }
        if (id.equals("s_dir")) { ctl.set("s_dir", (ctl.get("s_dir") + 1) % 3); return "§eRoute aspect: " + new String[]{"STRAIGHT (vertical bar)", "LEFT (diagonal)", "RIGHT (diagonal)"}[ctl.get("s_dir")]; }
        return "§7Tram signal: " + aspectName();
    }

    @Override protected void save(NBTTagCompound t) { t.setInteger("asp", aspect); }
    @Override protected void load(NBTTagCompound t) { aspect = t.getInteger("asp"); }
}
