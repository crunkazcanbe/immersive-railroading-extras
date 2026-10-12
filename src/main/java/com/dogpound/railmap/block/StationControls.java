package com.dogpound.railmap.block;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.controls.Layout;
import com.dogpound.railmap.controls.Surface;
import com.dogpound.railmap.grid.ItemScrewdriver;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.item.ItemSignalWrench;
import com.dogpound.railmap.wire.ItemWire;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Shared bits for the station, control-room and freight gear's working controls (requested feature). The machines store what each lamp / gauge / display shows in their
 * ControlState on the server (so the client draws the real reading), using {@link #put} / {@link #text}.
 */
@Mod.EventBusSubscriber(modid = RailMap.MODID)
public final class StationControls {
    private StationControls() {}

    /** store a reading; true when it changed (then sync) */
    public static boolean put(ControlState s, String id, int v) {
        if (s.vals.containsKey(id) && s.get(id) == v) return false;
        s.set(id, v);
        return true;
    }

    public static boolean put(ControlState s, String id, boolean v) { return put(s, id, v ? 1 : 0); }

    public static boolean text(ControlState s, String id, String t) {
        if (t.equals(s.texts.get(id))) return false;
        s.texts.put(id, t);
        return true;
    }

    /**
     * Big models reach past their own block: the NX desk's pedestals, the chute station on the silo's leg, the pit's
     * control post, the ticket machine's upper half. GearEvents only works controls on the machine's own block, so a
     * click that lands on whatever block is behind such a control is handed to the machine here.
     * ponytail: scans the loaded tile list per right-click (cheap: clicks are rare); belongs in GearEvents eventually.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void click(PlayerInteractEvent.RightClickBlock e) {
        EntityPlayer p = e.getEntityPlayer();
        World w = e.getWorld();
        if (p.isSneaking() || w.getTileEntity(e.getPos()) instanceof IControlled) return;     // its own block: GearEvents
        Item held = e.getItemStack().getItem();
        if (held instanceof ItemWire || held instanceof ItemScrewdriver || held instanceof ItemSignalWrench) return;
        Vec3d eye = p.getPositionEyes(1f), look = p.getLook(1f);
        double best = e.getHitVec() == null ? Double.MAX_VALUE : eye.distanceTo(e.getHitVec()) * 16 + 0.5;   // px; must be in front of the block hit
        IControlled bm = null;
        Panel.C bc = null;
        for (TileEntity t : w.loadedTileEntityList) {
            if (!(t instanceof IControlled m) || t.isInvalid() || t.getDistanceSq(eye.x, eye.y, eye.z) > 64) continue;
            double[] d = {best};
            Panel.C c = aim(m, eye, look, d);
            if (c != null && d[0] < best) { best = d[0]; bm = m; bc = c; }
        }
        if (bc == null) return;
        e.setCanceled(true);
        e.setCancellationResult(EnumActionResult.SUCCESS);
        if (w.isRemote) return;
        String r = IControlled.operate(bm, bc.id, p);
        if (r != null && !r.isEmpty()) p.sendStatusMessage(new TextComponentString(r), true);
    }

    /** IControlled.aimed with the distance (px): the nearest control the ray hits, or null */
    private static Panel.C aim(IControlled m, Vec3d eye, Vec3d look, double[] dist) {
        Layout l = m.layout();
        if (l.isEmpty()) return null;
        TileEntity t = (TileEntity) m;
        double ex = eye.x - t.getPos().getX() - 0.5, ey = eye.y - t.getPos().getY(), ez = eye.z - t.getPos().getZ() - 0.5, dx = look.x, dy = look.y, dz = look.z;
        EnumFacing f = m.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        for (int i = 0; i < turns; i++) { double nx = ez, nz = -ex; ex = nx; ez = nz; nx = dz; nz = -dx; dx = nx; dz = nz; }
        double[] eyeM = {(ex + 0.5) * 16, ey * 16, (ez + 0.5) * 16}, lookM = {dx, dy, dz};
        Panel.C best = null;
        for (Surface s : l.surfaces) {
            double[] d = new double[1];
            Panel.C c = s.hit(eyeM, lookM, d);
            if (c != null && d[0] < dist[0]) { best = c; dist[0] = d[0]; }
        }
        return best;
    }
}
