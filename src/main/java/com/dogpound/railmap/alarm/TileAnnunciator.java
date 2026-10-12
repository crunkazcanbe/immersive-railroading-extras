package com.dogpound.railmap.alarm;

import com.dogpound.railmap.ModSounds;
import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;

import java.util.Map;

/**
 * Alarm Annunciator (her ChatGPT list: "unified alarm manager"; Requested: as complex as possible, buttons and screens):
 * a control-room annunciator cabinet. Twelve backlit windows, one per alarm category, follow the real sequence - new =
 * fast flash + horn + strobe, ACKNOWLEDGE = steady, cleared = slow flash until RESET. SILENCE stops the horn until the
 * next new alarm, LAMP TEST lights everything. Counters and a message line show the newest alarm. Runs on 12 V.
 */
public class TileAnnunciator extends TileEntity implements ITickable, IControlled, IWiredGear {
    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private long testUntil;
    private int age;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }

    @Override
    public int controlValue(String id) {
        if (!term.powered()) return 0;
        if (id.equals("lamptest")) return ctl.get("lamptest");
        return ctl.get(id);
    }

    @Override public String ptext(String key) { return term.powered() ? ctl.text(key) : ""; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (!term.powered()) return "§cThe annunciator is dark - no 12 V";
        AlarmManager m = AlarmManager.get(world);
        switch (id) {
            case "ack": { int n = m.count(1, world.getTotalWorldTime()); m.ack(-1, who == null ? "remote" : who.getName()); refresh();
                if (n > 0 && who != null) com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "alarm_ack", "count", n);
                return "§eAll alarms acknowledged"; }
            case "silence": m.silence(); refresh(); return "§7Horn silenced (until the next new alarm)";
            case "reset": { int n = m.count(3, world.getTotalWorldTime()); m.reset(); refresh();
                if (n > 0 && who != null) com.dogpound.railmap.api.QuestEvents.fire(world, who, pos, "alarm_reset", "count", n);
                return "§aCleared alarms reset"; }
            case "test": testUntil = world.getTotalWorldTime() + 60; ctl.set("lamptest", 1); return "§7Lamp test";
            default: return "";
        }
    }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        long now = world.getTotalWorldTime();
        AlarmManager m = AlarmManager.get(world);
        if (term.powered() && m.horn(now) && now % 30 == 0) world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.annunciator"), SoundCategory.BLOCKS, 1.2f, 1.0f);
        if (++age % 10 == 0 || testUntil == now) refresh();
    }

    private void refresh() {
        long now = world.getTotalWorldTime();
        AlarmManager m = AlarmManager.get(world);
        boolean ch = false;
        for (AlarmManager.Cat c : AlarmManager.Cat.values()) ch |= StationControls.put(ctl, "t" + c.name().toLowerCase(java.util.Locale.ROOT), m.tile(c, now));
        ch |= StationControls.put(ctl, "strobe", m.horn(now));
        ch |= StationControls.put(ctl, "horn", m.horn(now));
        ch |= StationControls.put(ctl, "power", true);
        ch |= StationControls.put(ctl, "nnew", m.count(1, now));
        ch |= StationControls.put(ctl, "nack", m.count(2, now));
        ch |= StationControls.put(ctl, "nclr", m.count(3, now));
        AlarmManager.Alarm a = m.latest(now);
        String t = a == null ? "ALL NORMAL" : "#" + a.id + " " + a.sev + (a.count > 1 ? " x" + a.count : "") + " - " + a.cat.tile.replace("|", " ");
        ch |= StationControls.text(ctl, "msg1", t);
        ch |= StationControls.text(ctl, "msg2", a == null ? "" : a.text.length() > 46 ? a.text.substring(0, 45) + "." : a.text);
        if (testUntil > 0 && testUntil <= now) { testUntil = 0; ctl.set("lamptest", 0); ch = true; }
        if (ch) controlsChanged();
    }

    // ---- 12 V + console -------------------------------------------------------------------------------------------
    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Alarm annunciator"; }
    @Override public String gearState() {
        long now = world.getTotalWorldTime();
        AlarmManager m = AlarmManager.get(world);
        return m.count(1, now) + " new · " + m.count(2, now) + " standing · " + m.count(3, now) + " cleared";
    }
    @Override public int wiredLamp() { return term.burnt || !term.powered() ? 3 : AlarmManager.get(world).horn(world.getTotalWorldTime()) ? 2 : 1; }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("new", ctl.get("nnew")); m.put("standing", ctl.get("nack")); m.put("cleared", ctl.get("nclr")); }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); term.write(t); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); term.read(t); if (!t.hasKey("term")) term.legacy = false; }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
