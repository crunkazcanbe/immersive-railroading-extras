package com.dogpound.railmap.alarm;

import com.dogpound.railmap.block.StationControls;
import com.dogpound.railmap.controls.ControlState;
import com.dogpound.railmap.controls.IControlled;
import com.dogpound.railmap.grid.Panel;
import com.dogpound.railmap.wire.IWiredGear;
import com.dogpound.railmap.wire.Terminal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.Map;

/**
 * Event Recorder (her ChatGPT list: "event recorder and replay"): a rack with the recorder unit (display, REC lamp,
 * counters, RATE selector, MARK, PRINT), a six-drive disk array whose activity lights flicker as it writes, and a UPS.
 * While it has 12 V it records the whole railway (see Recorder); PRINT feeds an incident-report book out of the printer.
 */
public class TileEventRecorder extends TileEntity implements ITickable, IControlled, IWiredGear {
    private final ControlState ctl = new ControlState();
    private final Terminal term = new Terminal();
    private int age;
    private int marks;

    @Override public ControlState controls() { return ctl; }
    @Override public void controlsChanged() { markDirty(); if (world != null && !world.isRemote) { IBlockState s = world.getBlockState(pos); world.notifyBlockUpdate(pos, s, s, 3); } }

    @Override public int controlValue(String id) { return term.powered() || id.equals("rate") || id.startsWith("m_cab_") ? ctl.get(id) : 0; }   // m_cab_: cabinet doors / MCBs / fuses
    @Override public String ptext(String key) { return term.powered() && com.dogpound.railmap.cabinet.Cabinet.screenOk(ctl, key) ? ctl.text(key) : ""; }

    private int period() { return new int[]{20, 40, 100}[Math.max(0, Math.min(2, ctl.get("rate")))]; }

    @Override
    public String controlPress(String id, EntityPlayer who) {
        if (id.equals("rate")) { ctl.set("rate", (ctl.get("rate") + 1) % 3); return "§eSample every " + new String[]{"1 s", "2 s", "5 s"}[ctl.get("rate")]; }
        if (!term.powered()) return "§cThe recorder is dark - no 12 V";
        switch (id) {
            case "mark":
                marks++;
                Recorder.event(world, "BOOKMARK " + marks + (who == null ? "" : " by " + who.getName()));
                return "§eBookmark " + marks + " set";
            case "print":
                print(who);
                return "§7Printing incident report...";
            default: return "";
        }
    }

    /** the incident report: newest events and the live alarms, as a written book out of the printer */
    private void print(EntityPlayer who) {
        long now = world.getTotalWorldTime();
        NBTTagList pages = new NBTTagList();
        StringBuilder page = new StringBuilder("§lINCIDENT REPORT§r\n" + Recorder.clock(world, now) + "  day " + (world.getWorldTime() / 24000 + 1) + "\n\n");
        AlarmManager am = AlarmManager.get(world);
        page.append("§lLIVE ALARMS§r\n");
        int live = 0;
        for (AlarmManager.Alarm a : am.all())
            if (a.seq(now) != 0 && live++ < 6) page.append(a.sev.name().charAt(0)).append(' ').append(trim(a.text, 40)).append('\n');
        if (live == 0) page.append("none\n");
        pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(page.toString()))));
        List<Recorder.Event> ev = Recorder.events(world);
        int from = Math.max(0, ev.size() - 60);
        StringBuilder p = new StringBuilder("§lEVENTS§r\n");
        int lines = 1;
        for (int i = ev.size() - 1; i >= from; i--) {
            String line = Recorder.clock(world, ev.get(i).tick) + " " + trim(ev.get(i).text, 44) + "\n";
            if (lines >= 7) {
                pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
                p = new StringBuilder();
                lines = 0;
            }
            p.append(line);
            lines++;
        }
        if (ev.isEmpty()) p.append("nothing recorded yet");
        pages.appendTag(new NBTTagString(ITextComponent.Serializer.componentToJson(new TextComponentString(p.toString()))));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NBTTagCompound t = new NBTTagCompound();
        t.setString("title", "Incident Report " + Recorder.clock(world, now));
        t.setString("author", "Event Recorder");
        t.setTag("pages", pages);
        book.setTagCompound(t);
        EnumFacing f = controlFacing();
        EntityItem e = new EntityItem(world, pos.getX() + 0.5 + f.getXOffset() * 0.6, pos.getY() + 1.6, pos.getZ() + 0.5 + f.getZOffset() * 0.6, book);
        e.motionX = f.getXOffset() * 0.06; e.motionZ = f.getZOffset() * 0.06;
        world.spawnEntity(e);
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("alarm.recorder"), SoundCategory.BLOCKS, 0.35f, 1.0f);
        Recorder.event(world, "Incident report printed" + (who == null ? "" : " by " + who.getName()));
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "."; }

    @Override
    public void update() {
        if (world.isRemote) return;
        if (term.tick(world, pos, wiredName())) controlsChanged();
        boolean ch = false;
        if (term.powered()) {
            Recorder.alive(world, period());
            if (Recorder.tick(world)) { ctl.set("disk", 1); ctl.set("hd" + (1 + world.rand.nextInt(6)), 1); ch = true; }
        }
        if (++age % 10 == 0) {
            long now = world.getTotalWorldTime();
            List<Recorder.Sample> s = Recorder.samples(world);
            ch |= StationControls.put(ctl, "power", term.powered());
            ch |= StationControls.put(ctl, "rec", term.powered() && Recorder.recording(world));
            ch |= StationControls.put(ctl, "ups", term.powered());
            ch |= StationControls.put(ctl, "smp", s.size());
            ch |= StationControls.put(ctl, "evt", Recorder.events(world).size());
            ch |= StationControls.put(ctl, "batt", term.powered() ? 100 : 0);
            ch |= StationControls.text(ctl, "span", s.isEmpty() ? "--" : ((now - s.get(0).tick) / 1200) + " MIN");
            List<Recorder.Event> ev = Recorder.events(world);
            ch |= StationControls.text(ctl, "last", ev.isEmpty() ? "NO EVENTS" : Recorder.clock(world, ev.get(ev.size() - 1).tick) + " " + trim(ev.get(ev.size() - 1).text, 30));
            if (age % 20 == 0) { ctl.set("disk", 0); for (int i = 1; i <= 6; i++) ctl.set("hd" + i, 0); ch = true; }
        }
        if (ch) controlsChanged();
    }

    @Override public Terminal term() { return term; }
    @Override public void gearChanged() { controlsChanged(); }
    @Override public String wiredName() { return "Event recorder"; }
    @Override public String gearState() { return Recorder.recording(world) ? "RECORDING · " + Recorder.samples(world).size() + " samples · " + Recorder.events(world).size() + " events" : "not recording"; }
    @Override public Panel wiredPanel() { return layout().all; }
    @Override public String wiredPress(String id, String who) { return com.dogpound.railmap.cabinet.Cabinet.isCabinetId(id) ? "§7Cabinet MCBs and fuses are worked by hand, at the cabinet" : IControlled.operate(this, id, null); }
    @Override public int wiredState(String id) { return controlValue(id); }
    @Override public void gearInfo(Map<String, Object> m) { m.put("samples", Recorder.samples(world).size()); m.put("events", Recorder.events(world).size()); }

    @Override public NBTTagCompound writeToNBT(NBTTagCompound t) { super.writeToNBT(t); ctl.write(t); term.write(t); t.setInteger("marks", marks); return t; }
    @Override public void readFromNBT(NBTTagCompound t) { super.readFromNBT(t); ctl.read(t); term.read(t); if (!t.hasKey("term")) term.legacy = false; marks = t.getInteger("marks"); }
    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }
    @Override public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() { return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag()); }
    @Override public void onDataPacket(net.minecraft.network.NetworkManager n, net.minecraft.network.play.server.SPacketUpdateTileEntity p) { readFromNBT(p.getNbtCompound()); }
}
