package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The number on a speed limit sign. American signs are posted in miles per hour, so that is
 * what the sign shows and what you set; trains are held to it in km/h underneath.
 * <p>
 * A sign governs track from where it stands onward, for trains that can read it — the
 * sign faces the oncoming train, like a signal.
 */
public class TileSpeedSign extends TileLineside {

    @Override public String settingsTitle() { return "Speed Limit Sign"; }

    @Override
    public java.util.List<com.dogpound.railmap.settings.Setting> settingDefs() {
        cfg.put("mph", Integer.toString(mph));
        java.util.List<com.dogpound.railmap.settings.Setting> l = new java.util.ArrayList<>();
        l.add(com.dogpound.railmap.settings.Setting.num("Limit", "mph", "Speed limit", "Shown on the sign; trains are held to it (right-click steps it too)", 30, 5, 200, 5, "mph"));
        l.add(com.dogpound.railmap.settings.Setting.info("Limit", "In km/h", Math.round(kmh()) + " km/h"));
        l.add(com.dogpound.railmap.settings.Setting.choice("Limit", "kind", "Sign meaning", "Advance warning and End of limit signs don't hold trains to the number", "Permanent", "Permanent", "Temporary (works)", "Advance warning", "End of limit"));
        l.addAll(super.settingDefs());
        return l;
    }

    @Override
    public void onSettingsChanged(String key) {
        if ("mph".equals(key)) mph = Math.max(5, cfg.num("mph"));
        super.onSettingsChanged(key);
    }

    public String meaning() { return cfg.text("kind"); }
    /** The limits a right-click steps through, mph. */
    private static final int[] STEPS = { 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 70, 79, 90 };

    private int mph = 30;

    public int mph() {
        return mph;
    }

    public double kmh() {
        String k = cfg.text("kind");
        if ("Advance warning".equals(k) || "End of limit".equals(k)) return 999;
        return mph * 1.609344;
    }

    public void step(boolean down) {
        int i = 0;
        for (int k = 0; k < STEPS.length; k++) if (STEPS[k] == mph) i = k;
        i = down ? (i + STEPS.length - 1) % STEPS.length : (i + 1) % STEPS.length;
        mph = STEPS[i];
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState s = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, s, s, 3);
        }
    }

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) SignalRegistry.addSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (world != null && !world.isRemote) SignalRegistry.removeSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (world != null && !world.isRemote) SignalRegistry.removeSpeedSign(world.provider.getDimension(), this);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("mph", mph);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        mph = t.hasKey("mph") ? t.getInteger("mph") : 30;
    }

}
