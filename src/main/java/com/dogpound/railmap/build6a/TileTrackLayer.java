package com.dogpound.railmap.build6a;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTile;
import com.dogpound.railmap.factory.ore.ItemMaterial;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Track-laying machine (batch 6a #1): the head of a new-track-construction train. It stands at the railhead (the end
 * of the line, on the formation) facing back down the track it came along; the operator sets gauge, panel length and
 * curve, and it lays the next panel of real Immersive Railroading track ahead of itself out of its stores - steel rail
 * sections (irRail) from the rail racks, creosoted sleepers (irTie) from the sleeper magazine, rail clips, and ballast
 * from the hopper when the BALLAST DROP lever is down - exactly what IR's own track blueprint would charge. Each panel
 * takes time (thread the rails, space the sleepers, clip up), then the machine drives forward onto the new railhead.
 * AUTO keeps laying panel after panel until the target count, an empty store, an obstruction or missing formation
 * stops it. Diesel-hydraulic: piped diesel, a battery isolator, an engine key; its own 12 V from the battery.
 */
public class TileTrackLayer extends DepotTile {
    static final double[] GAUGES = {1.435, 1.067, 0.914, 0.632};
    static final String[] GAUGE_N = {"STANDARD", "CAPE", "3 FT", "MINIMUM"};
    static final int[] PANELS = {6, 10, 16, 24};
    static final int[] RADII = {100, 200, 400, 50, 25};
    static final int[] TARGETS = {5, 10, 25, 50, 99};

    final Diesel diesel = new Diesel();
    private final Routed items;
    int phase, progress, need, autoLeft, panels, metres, lastLen;
    double fx = 0.5, fz = 0.5;
    float yaw = Float.NaN;
    String fault = "";
    final List<String> log = new ArrayList<>();

    static final Predicate<ItemStack> RAIL = s -> B6.ore(s, "irRail");
    static final Predicate<ItemStack> TIE = s -> B6.ore(s, "irTie");
    static final Predicate<ItemStack> GRAVEL = s -> !s.isEmpty() && s.getItem() == Item.getItemFromBlock(Blocks.GRAVEL);
    static final Predicate<ItemStack> CLIP = s -> !s.isEmpty() && s.getItem() instanceof ItemMaterial && ItemMaterial.of(s) == ItemMaterial.M.CLIPS;

    public TileTrackLayer() {
        super(new String[]{"rails", "ties", "ballast", "clips"}, new String[]{"Rail racks", "Sleeper magazine", "Ballast hopper", "Clip bins"}, new int[]{27, 27, 18, 9});
        items = new Routed(new net.minecraft.inventory.IInventory[]{store(0), store(1), store(2), store(3)}, new boolean[]{false, false, false, false}, RAIL, TIE, GRAVEL, CLIP);
    }

    @Override public String wiredName() { return "Track laying machine"; }
    @Override public String gearState() {
        return !powered() ? "NO 12 V" : String.format(Locale.ROOT, "%s · %d panels / %d m laid · rails %d sleepers %d · diesel %d%%", phase == 1 ? "LAYING" : fault.isEmpty() ? "ready" : fault, panels, metres, rails(), ties(), diesel.fuelPct());
    }

    int rails() { return B6.count(store(0), RAIL); }
    int ties() { return B6.count(store(1), TIE); }
    int gravel() { return B6.count(store(2), GRAVEL); }
    int clips() { return B6.count(store(3), CLIP); }

    double gauge() { return GAUGES[Math.floorMod(ctl.get("s_gauge"), GAUGES.length)]; }
    int panelLen() { return PANELS[Math.floorMod(ctl.get("s_panel"), PANELS.length)]; }
    /** IrLay curve code: 0 left, 1 straight, 2 right (selector: STRAIGHT, LEFT, RIGHT) */
    int curve() { int c = Math.floorMod(ctl.get("s_curve"), 3); return c == 0 ? 1 : c == 1 ? 0 : 2; }
    int radius() { return RADII[Math.floorMod(ctl.get("s_radius"), RADII.length)]; }
    boolean ballastDrop() { return ctl.get("m_ballast") == 1; }
    float heading() { return Float.isNaN(yaw) ? B6.yawOf(facing().getOpposite()) : yaw; }
    EnumFacing facing() { return controlFacing(); }

    IrLay.Plan plan() { return IrLay.plan(world, pos, fx, fz, heading(), gauge(), panelLen(), curve(), radius(), ballastDrop()); }

    /** null = the panel can go down, else why not */
    String check(IrLay.Plan p) {
        if (p.err != null) return p.err;
        if (rails() < p.rails) return "RAILS LOW: needs " + p.rails + " rail sections, has " + rails();
        if (ties() < p.ties) return "SLEEPERS LOW: needs " + p.ties + " sleepers, has " + ties();
        if (ballastDrop() && gravel() < p.bed) return "BALLAST LOW: needs " + p.bed + " gravel, has " + gravel();
        int c = (p.ties + 3) / 4;
        if (B6Config.layerNeedsClips && clips() < c) return "CLIPS LOW: needs " + c + " rail clips, has " + clips();
        String b = IrLay.blocked(world, p, pos);
        if (b != null) return b;
        if (!world.isBlockLoaded(p.next)) return "RAILHEAD NOT LOADED";
        if (!world.getBlockState(p.next).getBlock().isReplaceable(world, p.next)) return "NO ROOM AT THE NEW RAILHEAD (" + p.next.getX() + "," + p.next.getY() + "," + p.next.getZ() + ")";
        if (!world.isSideSolid(p.next.down(), EnumFacing.UP)) return "NO FORMATION AT THE NEW RAILHEAD - the machine would drive off the end";
        return null;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "lay" -> { autoLeft = 0; return begin(who); }
            case "start" -> {
                if (Math.floorMod(ctl.get("s_mode"), 2) != 1) return "§7Turn MODE to AUTO first";
                autoLeft = TARGETS[Math.floorMod(ctl.get("s_target"), TARGETS.length)];
                String r = begin(who);
                if (phase != 1) autoLeft = 0;
                return r;
            }
            case "stop" -> { if (phase == 1) { phase = 0; event("STOPPED by " + who(who) + " - panel abandoned, nothing used"); } autoLeft = 0; return "§eLaying stopped"; }
            case "reset" -> { fault = ""; AlarmManager.get(world).clear(akey()); return "§7Fault reset"; }
            default -> { return null; }
        }
    }

    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("m_estop")) {
            ctl.set(id, ctl.get(id) == 1 ? 0 : 1);
            if (ctl.get(id) == 1) { phase = 0; autoLeft = 0; }
            return ctl.get(id) == 1 ? "§cE-STOP - engine down, laying stopped" : "§aE-stop released";
        }
        if ((id.equals("s_gauge") || id.equals("s_panel") || id.equals("s_curve") || id.equals("s_radius")) && phase == 1) return "§cNot while a panel is going down";
        return null;
    }

    String begin(EntityPlayer who) {
        if (phase == 1) return "§7Already laying";
        if (!diesel.running) return "§cStart the engine first (battery ON, key to RUN): " + diesel.why;
        IrLay.Plan p = plan();
        String why = check(p);
        if (why != null) { fail(why); return "§c" + why; }
        fault = ""; AlarmManager.get(world).clear(akey());
        phase = 1; progress = 0; lastLen = panelLen(); need = Math.max(20, lastLen * B6Config.layTicksPerMetre);
        world.playSound(null, pos, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.8f, 0.6f);
        return String.format(Locale.ROOT, "§aLaying a %d m %s panel (%s gauge): %d rails, %d sleepers%s", lastLen, curveName(), GAUGE_N[Math.floorMod(ctl.get("s_gauge"), 4)], p.rails, p.ties, p.bed > 0 ? ", " + p.bed + " ballast" : "");
    }

    String curveName() { int c = curve(); return c == 1 ? "straight" : (c == 0 ? "left" : "right") + " R" + radius(); }

    void fail(String why) {
        fault = why; phase = 0; autoLeft = 0;
        AlarmManager.get(world).raise(world, akey(), AlarmManager.Cat.MACHINE, AlarmManager.Sev.MINOR, "TRACK LAYER " + pos.getX() + "," + pos.getZ() + ": " + why, pos, false);
        event("FAULT: " + why);
    }

    String akey() { return "b6layer:" + pos.toLong(); }

    void event(String s) {
        log.add(0, s);
        while (log.size() > 6) log.remove(log.size() - 1);
        Recorder.event(world, "Track layer " + pos.getX() + "," + pos.getZ() + ": " + s);
    }

    @Override
    protected void work() {
        if (age % 20 == 0) diesel.tick(term, ctl.get("k_batt") == 1, ctl.get("k_engine") == 1, ctl.get("m_estop") == 1, phase == 1);
        if (phase == 1) {
            if (!diesel.running) { fail("ENGINE STOPPED mid-panel: " + diesel.why); }
            else if (++progress >= need) { commit(); return; }   // commit may move the machine: nothing after it
            if (progress % 30 == 0) world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.25f, 1.6f);
        }
        if (age % 10 == 0) show();
    }

    /** the panel goes down: use the stores, lift the machine, IR builds, the machine drives onto the new railhead */
    void commit() {
        IrLay.Plan p = plan();
        String why = check(p);
        if (why != null) { fail(why); show(); return; }
        B6.take(store(0), RAIL, p.rails);
        B6.take(store(1), TIE, p.ties);
        if (p.bed > 0) B6.take(store(2), GRAVEL, p.bed);
        B6.take(store(3), CLIP, (p.ties + 3) / 4);
        panels++; metres += lastLen;
        BlockPos from = pos;
        event(String.format(Locale.ROOT, "LAID %d m %s at %d,%d (%d rails, %d sleepers)", lastLen, curveName(), from.getX(), from.getZ(), p.rails, p.ties));
        if (autoLeft > 0) autoLeft--;
        boolean more = autoLeft > 0;
        phase = 0;
        fx = p.nextFx; fz = p.nextFz; yaw = p.nextYaw;
        fault = "";
        AlarmManager.get(world).clear(akey());
        show();
        net.minecraft.world.World w = world;
        NBTTagCompound tag = B6.lift(w, from);
        if (tag == null) return;
        boolean built = IrLay.build(p);
        BlockPos to = built ? p.next : from;
        tag.setInteger("x", to.getX()); tag.setInteger("y", to.getY()); tag.setInteger("z", to.getZ());
        net.minecraft.block.state.IBlockState st = net.minecraft.block.Block.getStateById(tag.getInteger("_state"));
        if (built) st = st.withProperty(com.dogpound.railmap.block.BlockStationDevice.FACING, B6.facingOf(p.nextYaw).getOpposite());
        w.setBlockState(to, st, 3);
        if (w.getTileEntity(to) instanceof TileTrackLayer n) {
            n.readFromNBT(tag);
            if (!built) n.fail("IR refused to build the panel");
            else if (more) { n.begin(null); if (n.phase != 1) n.autoLeft = 0; }
            n.markDirty();
        }
        w.playSound(null, to, SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.BLOCKS, 0.5f, 0.7f);
    }

    void show() {
        boolean on = powered();
        put("pwr", on); put("eng", diesel.running); put("batl", diesel.battery < 25); put("fuell", diesel.fuelPct() < 10);
        IrLay.Plan p = age % 40 == 0 || cache == null ? (cache = plan()) : cache;
        int r = rails(), t = ties(), g = gravel();
        put("raill", p.err == null && r < p.rails); put("tiel", p.err == null && t < p.ties); put("balll", ballastDrop() && p.err == null && g < p.bed);
        put("work", phase == 1); put("fault", !fault.isEmpty()); put("autol", autoLeft > 0);
        put("obst", fault.startsWith("OBSTRUCTION") || fault.startsWith("NO ROOM")); put("form", fault.startsWith("NO FORMATION"));
        put("done", phase == 0 && fault.isEmpty() && panels > 0);
        put("fuelg", diesel.fuelPct()); put("batg", Math.round(diesel.battery)); put("rpmg", Math.round(diesel.rpm * 100 / 2200));
        put("oilg", Math.round(diesel.oilBar * 100 / 7)); put("coolg", Math.round(diesel.coolC * 100 / 120));
        put("railg", Math.min(100, r * 100 / 64)); put("tieg", Math.min(100, t * 100 / 128)); put("progg", phase == 1 ? progress * 100 / Math.max(1, need) : 0);
        text("metres", String.valueOf(metres)); text("panels", String.valueOf(panels));
        String[] l = new String[4];
        switch (Math.floorMod(ctl.get("page"), 4)) {
            case 0 -> {
                l[0] = phase == 1 ? "LAYING PANEL " + progress * 100 / Math.max(1, need) + "%" : !fault.isEmpty() ? "FAULT" : diesel.running ? "READY - ENGINE RUNNING" : "ENGINE STOPPED";
                l[1] = cut(fault.isEmpty() ? diesel.why.toUpperCase(Locale.ROOT) : fault.toUpperCase(Locale.ROOT), 30);
                l[2] = String.format(Locale.ROOT, "NEXT %dM %s", panelLen(), curveName().toUpperCase(Locale.ROOT));
                l[3] = String.format(Locale.ROOT, "GAUGE %s %dMM HDG %03d", GAUGE_N[Math.floorMod(ctl.get("s_gauge"), 4)], Math.round(p.gaugeM * 1000), Math.round(heading()));
            }
            case 1 -> {
                l[0] = String.format(Locale.ROOT, "RAILS %d / NEED %d", r, p.rails);
                l[1] = String.format(Locale.ROOT, "SLEEPERS %d / NEED %d", t, p.ties);
                l[2] = String.format(Locale.ROOT, "BALLAST %d / NEED %d %s", g, p.bed, ballastDrop() ? "" : "(OFF)");
                l[3] = String.format(Locale.ROOT, "CLIPS %d  DIESEL %d L", clips(), diesel.tank.getFluidAmount());
            }
            case 2 -> {
                l[0] = String.format(Locale.ROOT, "LAID %d PANELS %d M", panels, metres);
                l[1] = autoLeft > 0 ? "AUTO: " + autoLeft + " PANELS TO GO" : Math.floorMod(ctl.get("s_mode"), 2) == 1 ? "AUTO TARGET " + TARGETS[Math.floorMod(ctl.get("s_target"), 5)] : "MANUAL - PRESS LAY";
                l[2] = String.format(Locale.ROOT, "ENG %d RPM OIL %s BAR", Math.round(diesel.rpm), B6.f1(diesel.oilBar));
                l[3] = String.format(Locale.ROOT, "COOL %dC BATT %d%%", Math.round(diesel.coolC), Math.round(diesel.battery));
            }
            default -> { for (int i = 0; i < 4; i++) l[i] = i < log.size() ? cut(log.get(i).toUpperCase(Locale.ROOT), 30) : ""; }
        }
        for (int i = 0; i < 4; i++) text("line" + (i + 1), l[i]);
    }

    private transient IrLay.Plan cache;

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) {
        if (c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return (T) items;
        if (c == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) return (T) diesel.tank;
        return super.getCapability(c, f);
    }

    @Override
    protected void saveExtra(NBTTagCompound t) {
        diesel.write(t);
        t.setInteger("phase", phase); t.setInteger("prog", progress); t.setInteger("need", need); t.setInteger("auto", autoLeft);
        t.setInteger("panels", panels); t.setInteger("metres", metres); t.setInteger("len", lastLen);
        t.setDouble("fx", fx); t.setDouble("fz", fz); t.setFloat("yaw", yaw); t.setString("fault", fault);
        t.setString("log", String.join("\n", log));
    }

    @Override
    protected void loadExtra(NBTTagCompound t) {
        diesel.read(t);
        phase = t.getInteger("phase"); progress = t.getInteger("prog"); need = t.getInteger("need"); autoLeft = t.getInteger("auto");
        panels = t.getInteger("panels"); metres = t.getInteger("metres"); lastLen = t.getInteger("len");
        fx = t.hasKey("fx") ? t.getDouble("fx") : 0.5; fz = t.hasKey("fz") ? t.getDouble("fz") : 0.5; yaw = t.hasKey("yaw") ? t.getFloat("yaw") : Float.NaN;
        fault = t.getString("fault");
        log.clear();
        for (String s : t.getString("log").split("\n")) if (!s.isEmpty()) log.add(s);
        cache = null;
    }
}
