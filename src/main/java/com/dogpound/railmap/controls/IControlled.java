package com.dogpound.railmap.controls;

import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.grid.PanelHost;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import java.util.Map;

/**
 * A machine whose modelled buttons, lamps, gauges, keys and levers really work (requested feature). The layout comes from the model generator;
 * the machine says what each control reads (controlValue) and does (controlPress).
 */
public interface IControlled extends PanelHost {
    ControlState controls();
    /** save + send to clients after a control or reading changed */
    void controlsChanged();

    /** what a lamp / gauge / display / switch shows now; default = its stored position */
    default int controlValue(String id) { return controls().get(id); }

    /**
     * Server: the control `id` was operated by `who` (null = remotely, from the console / a Control Button). Return the
     * message to show, or null to get the default behaviour (switches turn, levers pull, E-stops latch).
     */
    default String controlPress(String id, EntityPlayer who) { return null; }

    default Layout layout() {
        TileEntity t = (TileEntity) this;
        return t.getBlockType() == null || t.getBlockType().getRegistryName() == null ? Layout.NONE : Layout.of(t.getBlockType().getRegistryName().getPath());
    }

    default EnumFacing controlFacing() {
        TileEntity t = (TileEntity) this;
        if (t.getWorld() == null) return EnumFacing.NORTH;
        IBlockState s = t.getWorld().getBlockState(t.getPos());
        for (IProperty<?> p : s.getPropertyKeys())
            if (p.getName().equals("facing") && s.getValue(p) instanceof EnumFacing f && f.getAxis() != EnumFacing.Axis.Y) return f;
        return EnumFacing.NORTH;
    }

    @Override default int pv(String key) { return controlValue(key); }
    @Override default String ptext(String key) { return controls().text(key); }
    @Override default boolean pressedRecently(String id, long now) { return id.equals(controls().pressId) && now - controls().pressAt < 6; }
    @Override default Map<String, float[]> anim() { return controls().anim; }

    /** server: operate a control (records the push for the animation, lets the machine act, else the default) */
    static String operate(IControlled m, String id, EntityPlayer who) {
        Panel.C c = m.layout().control(id);
        if (c == null) return "§7No such control";
        TileEntity t = (TileEntity) m;
        ControlState st = m.controls();
        st.pressId = id;
        st.pressAt = t.getWorld().getTotalWorldTime();
        int before = st.get(id);
        String r = m.controlPress(id, who);
        if (r == null) {
            int v = st.get(id);
            switch (c.t) {
                case SELECTOR: case KEY: st.set(id, (v + 1) % Math.max(2, c.positions.length)); r = "§e" + c.label + ": " + pos(c, st.get(id)); break;
                case PULL: case LEVER: case MUSHROOM: st.set(id, v == 0 ? 1 : 0); r = "§e" + c.label + (st.get(id) == 1 ? " pulled" : " back"); break;
                default: r = "§7" + c.label; break;
            }
        }
        String snd = controlSound(c.t, before, st.get(id));
        if (snd != null) t.getWorld().playSound(null, t.getPos(), com.dogpound.railmap.sound.PackSounds.ev(snd), net.minecraft.util.SoundCategory.BLOCKS,
                c.t == Panel.T.PULL ? 1.0f : 0.6f, com.dogpound.railmap.sound.PackSounds.vary(t.getWorld()));
        m.controlsChanged();
        return r;
    }

    /** the sound pack event for operating a control (null: silent - invisible zones, lamps, displays) */
    static String controlSound(Panel.T t, int before, int after) {
        switch (t) {
            case PUSH: return "ctl.push";
            case MUSHROOM: return "ctl.mushroom";
            case SELECTOR: return "ctl.selector";
            case KEY: return "ctl.key";
            case LEVER: return "ctl.lever";
            case PULL: return before == after ? "signal.frame_clank" : after == 1 ? "signal.lever_reverse" : "signal.lever_normal";
            case ZONE: case LAMP: case GAUGE: case DIGITS: case FLAG: case SYNCHRO: case LABEL: case TILE: return null;
            default: return "ctl.push";
        }
    }

    static String pos(Panel.C c, int v) { return v >= 0 && v < c.positions.length ? c.positions[v] : String.valueOf(v); }

    /** which control (if any) the player is aiming at on this machine */
    static Panel.C aimed(IControlled m, EntityPlayer player) {
        Layout l = m.layout();
        if (l.isEmpty()) return null;
        TileEntity t = (TileEntity) m;
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1f), look = player.getLook(1f);
        double ex = eye.x - t.getPos().getX() - 0.5, ey = eye.y - t.getPos().getY(), ez = eye.z - t.getPos().getZ() - 0.5, dx = look.x, dy = look.y, dz = look.z;
        EnumFacing f = m.controlFacing();
        int turns = f.getHorizontalIndex() == 2 ? 0 : f.getHorizontalIndex() == 3 ? 1 : f.getHorizontalIndex() == 0 ? 2 : 3;
        for (int i = 0; i < turns; i++) { double nx = ez, nz = -ex; ex = nx; ez = nz; nx = dz; nz = -dx; dx = nx; dz = nz; }
        double[] eyeM = {(ex + 0.5) * 16, ey * 16, (ez + 0.5) * 16}, lookM = {dx, dy, dz};
        Panel.C best = null;
        double bestD = Double.MAX_VALUE;
        for (Surface s : l.surfaces) {
            double[] d = new double[1];
            Panel.C c = s.hit(eyeM, lookM, d);
            if (c != null && d[0] < bestD) { best = c; bestD = d[0]; }
        }
        return best;
    }
}
