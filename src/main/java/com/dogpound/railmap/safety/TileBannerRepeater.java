package com.dogpound.railmap.safety;

import com.dogpound.railmap.signal.Aspect;
import com.dogpound.railmap.signal.TileSignalMast;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Banner Repeating Signal (her ChatGPT list #24): an illuminated white disc with a black arm, placed on the approach
 * to a signal that can't be seen early enough. Arm horizontal ("ON") = the signal ahead is at danger or caution; arm
 * inclined 45 degrees ("OFF") = it shows a proceed aspect. Link it with the Signal Wrench (pick the signal, click the
 * disc). The sighting survey counts the banner: the driver who sees the banner in time has sighted the signal, so the
 * early-braking restriction goes. No 12 V = the arm drops to ON and the lamp goes out (fail-safe).
 */
public class TileBannerRepeater extends SafetyTile {
    private long signal;

    public long signal() { return signal; }

    @Override public String wiredName() { return "Banner repeater"; }

    @Override public String gearState() {
        TileSignalMast m = mast();
        return signal == 0 ? "not linked to a signal" : m == null ? "signal not loaded" : ctl.get("arm") == 45 ? "OFF (signal clear)" : "ON (signal at danger / caution)";
    }

    @Override public void gearInfo(Map<String, Object> m) { m.put("signal", signal == 0 ? "" : Geo.xz(signal)); m.put("arm", ctl.get("arm") == 45 ? "OFF" : "ON"); }

    private TileSignalMast mast() {
        if (signal == 0) return null;
        BlockPos p = BlockPos.fromLong(signal);
        TileEntity t = world.isBlockLoaded(p) ? world.getTileEntity(p) : null;
        return t instanceof TileSignalMast m ? m : null;
    }

    @Override
    public int controlValue(String id) {
        if (id.equals("arm")) return powered() ? ctl.get("arm") : 90;           // unpowered: the arm falls to ON
        return super.controlValue(id);
    }

    @Override
    protected void serverTick() {
        if (age % 10 != 0) return;
        TileSignalMast m = mast();
        Aspect a = m == null ? Aspect.STOP : m.aspect();
        boolean off = powered() && m != null && !a.isStop() && a != Aspect.APPROACH && a != Aspect.MEDIUM_APPROACH && a != Aspect.RESTRICTING && a != Aspect.DARK;
        put("arm", off ? 45 : 90);
        put("lit", powered());
        put("pwr", powered());
        text("sig", m == null ? "--" : SignalSighting.name(m));
    }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("test")) return lampTest();
        return "§7" + wiredName() + ": " + gearState();
    }

    @Override
    public String link(String control, BlockPos pick, EntityPlayer who) {
        if (!(world.getTileEntity(pick) instanceof TileSignalMast)) return "§7Pick the signal this banner repeats";
        signal = pick.equals(BlockPos.fromLong(signal)) ? 0 : pick.toLong();
        controlsChanged();
        return signal == 0 ? "§7Banner unlinked" : "§aBanner repeats the signal at " + pick.getX() + "," + pick.getZ() + " - its sighting now counts for that signal";
    }

    @Override protected void save(NBTTagCompound t) { t.setLong("sig", signal); }
    @Override protected void load(NBTTagCompound t) { signal = t.getLong("sig"); }
}
