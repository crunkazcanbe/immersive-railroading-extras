package com.dogpound.railmap.steam;

import cam72cam.immersiverailroading.entity.Locomotive;
import com.dogpound.railmap.alarm.Recorder;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.Locale;

/**
 * Sand Drier + Sand Tower (batch 5 #2): damp sand from the bins (27 slots; hoppers / pipes / hands) goes through the
 * coal-fired-style drier - here an electric 25 kW stove (mains) - which must be at 120 °C or more to dry it. The dry
 * sand is blown up into the elevated hopper by the AIR LIFT (compressor, mains), then runs by gravity down the delivery
 * hose: swing the SAND VALVE open over the engine standing at the tower and its sandboxes (IR's sandbox slot, the one
 * the fork's prideSand sanders use) fill until full. Wet sand would cake in the sandbox, so only dried sand goes up.
 */
public class TileSandTower extends com.dogpound.railmap.depot.DepotTile {
    static final int HOPPER = 1024;
    private final InvWrapper items;
    private int bin, hopper, delivered, total;
    private float drierC = 20, dryProgress;

    public TileSandTower() {
        super(new String[]{"wet"}, new String[]{"Damp sand bins"}, new int[]{27});
        items = new InvWrapper(store(0)) { @Override public ItemStack insertItem(int slot, ItemStack s, boolean sim) { return isSand(s) ? super.insertItem(slot, s, sim) : s; } };
    }

    static boolean isSand(ItemStack s) { return !s.isEmpty() && s.getItem() == Item.getItemFromBlock(Blocks.SAND); }

    @Override public String wiredName() { return "Sand drier and tower"; }
    @Override protected boolean usesMains() { return true; }
    @Override protected long load() { return (heating() ? 250 : 0) + (airLift() ? 110 : 0) + 1; }
    @Override public String gearState() { return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "drier %.0f °C · bin %d · hopper %d/%d", drierC, bin, hopper, HOPPER); }

    boolean heating() { return Math.floorMod(ctl.get("s_drier"), 2) == 1 && powered() && mainsOk() && ctl.get("m_estop") == 0; }
    boolean airLift() { return ctl.get("k_air") == 1 && powered() && mainsOk() && ctl.get("m_estop") == 0 && bin > 0 && hopper < HOPPER; }
    boolean valve() { return ctl.get("m_sand") == 1; }

    int wet() { int n = 0; for (int i = 0; i < 27; i++) if (isSand(store(0).getStackInSlot(i))) n += store(0).getStackInSlot(i).getCount(); return n; }
    void takeWet() { for (int i = 0; i < 27; i++) { ItemStack s = store(0).getStackInSlot(i); if (isSand(s)) { s.shrink(1); if (s.isEmpty()) store(0).setInventorySlotContents(i, ItemStack.EMPTY); return; } } }

    Locomotive loco() { return Stock.nearest(world, pos, 6, Locomotive.class, l -> l.prideSandLevel() >= 0); }

    @Override
    protected String press(String id, EntityPlayer who) {
        if (id.equals("reset")) { delivered = 0; return "§7Sand meter reset"; }
        return null;
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        switch (id) {
            case "m_estop" -> { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return ctl.get(id) == 1 ? "§cE-STOP - drier and air lift off" : "§aE-stop released"; }
            case "m_sand" -> { ctl.set(id, valve() ? 0 : 1); if (valve()) delivered = 0; return valve() ? "§eSand valve OPEN" : "§7Sand valve shut - " + delivered + " sand delivered"; }
            default -> { return null; }
        }
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        // the drier: heats up to 180 °C on mains, dries one sand per 2 s above 120 °C
        drierC += heating() ? Math.max(0, (185 - drierC) * 0.04f) : -(drierC - 20) * 0.02f;
        if (drierC >= 120 && wet() > 0 && bin < 256) { dryProgress += 0.5f; if (dryProgress >= 1) { dryProgress = 0; takeWet(); bin++; } }
        if (airLift()) { int m = Math.min(4, Math.min(bin, HOPPER - hopper)); bin -= m; hopper += m; if (age % 100 == 0) world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_BREATH, SoundCategory.BLOCKS, 0.6f, 0.5f); }
        Locomotive l = loco();
        boolean flow = false;
        if (valve() && hopper > 0 && l != null && Stock.standing(l)) {
            int want = Math.min(4, hopper);
            cam72cam.mod.item.ItemStack rest = l.cargoItems.insert(2, new cam72cam.mod.item.ItemStack(new ItemStack(Blocks.SAND, want)), false);
            int put = want - rest.internal.getCount();
            hopper -= put; delivered += put; total += put;
            flow = put > 0;
            if (flow && age % 60 == 0) world.playSound(null, pos, SoundEvents.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 0.8f, 0.8f);
            if (put == 0 && age % 100 == 0) Recorder.event(world, "Sand tower: " + Stock.name(l) + " sandboxes full - shut the valve");
        }
        float box = l == null ? -1 : l.prideSandLevel();
        put("pwr", powered()); put("mainsl", mains.ok()); put("heat", heating()); put("dry", drierC >= 120); put("airl", airLift());
        put("flow", flow); put("locol", l != null); put("low", hopper < HOPPER / 10); put("wetl", wet() == 0);
        put("tempg", Math.round(drierC * 100 / 250)); put("wetg", wet() * 100 / (27 * 64)); put("bing", bin * 100 / 256); put("hopg", hopper * 100 / HOPPER);
        put("boxg", box < 0 ? 0 : Math.round(box * 100));
        text("temp", String.format(Locale.ROOT, "%.0f", drierC));
        text("count", String.valueOf(delivered));
        text("line1", l == null ? "NO ENGINE AT TOWER" : cut(Stock.name(l).toUpperCase(Locale.ROOT), 18));
    }

    public boolean flowing() { return ctl.get("flow") == 1; }

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) items : super.getCapability(c, f); }

    @Override protected void saveExtra(NBTTagCompound t) { t.setInteger("bin", bin); t.setInteger("hop", hopper); t.setInteger("del", delivered); t.setInteger("tot", total); t.setFloat("dc", drierC); t.setFloat("dp", dryProgress); }
    @Override protected void loadExtra(NBTTagCompound t) { bin = t.getInteger("bin"); hopper = t.getInteger("hop"); delivered = t.getInteger("del"); total = t.getInteger("tot"); drierC = t.hasKey("dc") ? t.getFloat("dc") : 20; dryProgress = t.getFloat("dp"); }
    @Override public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() { return new net.minecraft.util.math.AxisAlignedBB(pos).grow(2, 3, 2); }
}
