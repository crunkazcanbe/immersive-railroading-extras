package com.dogpound.railmap.industry;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.depot.DepotTile;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRailBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.oredict.OreDictionary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Construction Site Office (her ChatGPT list #95, "industrial expansion projects", and the three production chains):
 * pick a project (a new mine, factory or power station) with PREV / NEXT, turn the AUTHORISE key, and the site posts
 * freight orders for its bill of materials - steel, sleepers and rails from the factory's mills, cable and
 * transformers for the electrics, bricks and ballast. Trains deliver them into the materials compound (any unloader or
 * the compound doors); a rail siding has to be laid to the site before work can start. BUILD consumes the materials over
 * the project's construction hours and the site becomes the new industry. Nothing is abstract: every sleeper, rail and
 * coil of cable on the bill really came off a train.
 * <p>Project management (her "next 48" #11): BUILD starts the works through seven stages - survey, earthworks,
 * foundations, track, electrical fit-out, testing, opening. Each stage builds in its own materials (ballast for the
 * earthworks, sleepers and rails for the track, cable and transformers for the electrics...) as they arrive, needs its
 * own plant on hire (excavator, piling rig, track machine, wiring train, test train), and goes at the pace of the crews
 * on site. Crews and plant are paid by the hour out of the project budget (a county grant covers the estimate; FUND or a
 * project LOAN cover overruns). The works stop - and the delay is logged by cause - for missing materials, a train
 * passing (the track stages; take a POSSESSION to hold the nearby lines' driverless trains), an empty budget or no crew.
 */
public class TileConstructionSite extends DepotTile implements IndustryLink.Endpoint {

    /** one bill-of-materials line: alternatives ("ore:ingotSteel", "minecraft:iron_ingot"), how many, the board label */
    static final class Line {
        final String label; final String[] alts; final int count;
        Line(String label, int count, String... alts) { this.label = label; this.count = count; this.alts = alts; }

        boolean matches(ItemStack s) {
            if (s.isEmpty()) return false;
            for (String a : alts) {
                if (a.startsWith("ore:")) { if (OreDictionary.doesOreNameExist(a.substring(4))) for (int id : OreDictionary.getOreIDs(s)) if (OreDictionary.getOreName(id).equals(a.substring(4))) return true; }
                else {
                    String[] p = a.split("@");
                    if (s.getItem().getRegistryName() != null && s.getItem().getRegistryName().toString().equals(p[0]) && (p.length == 1 || s.getMetadata() == Integer.parseInt(p[1]))) return true;
                }
            }
            return false;
        }

        /** the stack to order: the first alternative that exists in this pack */
        ItemStack want(int n) {
            for (String a : alts) {
                if (a.startsWith("ore:")) {
                    for (ItemStack o : OreDictionary.getOres(a.substring(4))) { ItemStack c = o.copy(); if (c.getMetadata() == OreDictionary.WILDCARD_VALUE) c.setItemDamage(0); c.setCount(n); return c; }
                } else {
                    String[] p = a.split("@");
                    Item it = Item.REGISTRY.getObject(new ResourceLocation(p[0]));
                    if (it != null) return new ItemStack(it, n, p.length > 1 ? Integer.parseInt(p[1]) : 0);
                }
            }
            return ItemStack.EMPTY;
        }
    }

    /** a project: what it becomes, its bill of materials and how many in-game hours of construction */
    static final class Project {
        final IndustryKind kind; final String label; final int hours; final Line[] bom;
        Project(IndustryKind kind, String label, int hours, Line... bom) { this.kind = kind; this.label = label; this.hours = hours; this.bom = bom; }
    }

    private static Line steel(int n) { return new Line("STEEL", n, "irextras:material@1", "ore:ingotSteel", "minecraft:iron_ingot"); }
    private static Line sleepers(int n) { return new Line("SLEEPERS", n, "irextras:material@7", "ore:irTie", "minecraft:wooden_slab"); }
    private static Line rails(int n) { return new Line("RAILS", n, "irextras:material@6", "ore:irRail", "minecraft:rail"); }
    private static Line ballast(int n) { return new Line("BALLAST", n, "ore:gravel", "minecraft:gravel"); }
    private static Line cable(int n) { return new Line("CABLE", n, "irextras:grid_cable"); }
    private static Line motors(int n) { return new Line("MOTORS", n, "irextras:grid_motor"); }
    private static Line frames(int n) { return new Line("FRAMES", n, "irextras:part@1"); }
    private static Line bricks(int n) { return new Line("BRICKS", n, "minecraft:brick_block", "ore:ingotBrick"); }

    static final Project[] PROJECTS = {
            new Project(IndustryKind.COAL_MINE, "COAL MINE", 8, steel(32), sleepers(48), rails(24), ballast(64), motors(2), cable(32)),
            new Project(IndustryKind.IRON_MINE, "IRON MINE", 8, steel(32), sleepers(48), rails(24), ballast(64), motors(2), cable(32)),
            new Project(IndustryKind.QUARRY, "QUARRY", 6, steel(24), sleepers(32), rails(16), motors(2), cable(16)),
            new Project(IndustryKind.LOGGING_CAMP, "LOGGING CAMP", 4, steel(16), sleepers(32), rails(16), ballast(32), new Line("PLANKS", 64, "ore:plankWood", "minecraft:planks")),
            new Project(IndustryKind.FARM, "FARM", 4, sleepers(16), rails(8), ballast(16), new Line("PLANKS", 64, "ore:plankWood", "minecraft:planks"), new Line("FENCING", 32, "ore:fenceWood", "minecraft:fence")),
            new Project(IndustryKind.SAWMILL, "SAWMILL", 10, steel(48), frames(4), motors(4), cable(48), bricks(64), sleepers(32), rails(16)),
            new Project(IndustryKind.FLOUR_MILL, "FLOUR MILL", 10, steel(48), frames(4), motors(4), cable(48), bricks(64), sleepers(32), rails(16)),
            new Project(IndustryKind.CEMENT_WORKS, "CEMENT WORKS", 12, steel(64), frames(6), motors(4), cable(48), bricks(96), sleepers(32), rails(16)),
            new Project(IndustryKind.STEEL_MILL, "STEEL MILL", 16, steel(96), bricks(128), frames(8), motors(6), new Line("TRANSFORMER", 1, "irextras:grid_transformer"), cable(64), sleepers(32), rails(24)),
            new Project(IndustryKind.POWER_STATION, "POWER STATION", 20, steel(128), bricks(128), new Line("TRANSFORMERS", 2, "irextras:grid_transformer"), cable(96),
                    new Line("TURBINE BLADES", 8, "irextras:part@24"), frames(8), sleepers(32), rails(24)),
            new Project(IndustryKind.TOWN_MARKET, "TOWN MARKET", 8, new Line("PLANKS", 128, "ore:plankWood", "minecraft:planks"), bricks(64), new Line("GLASS", 32, "ore:blockGlass", "minecraft:glass"), sleepers(16), rails(8))};


    // ---- project management (her "next 48" #11): stages, crews, plant, budget, delays ------------------------------------
    /** the works, in order; share of the project's hours; the plant each stage needs */
    public static final String[] STAGES = {"SURVEY", "EARTHWORKS", "FOUNDATIONS", "TRACK", "ELECTRICAL", "TESTING", "OPENING"};
    private static final double[] SHARE = {0.08, 0.20, 0.20, 0.20, 0.15, 0.12, 0.05};
    public static final String[] PLANT = {"NONE", "SURVEY KIT", "EXCAVATOR", "PILING RIG", "TRACK MACHINE", "WIRING TRAIN", "TEST TRAIN"};
    private static final int[] STAGE_PLANT = {1, 2, 3, 4, 5, 6, 0};
    /** cents per in-game hour: one crew, one item of plant */
    public static final long CREW_HOUR = 500, PLANT_HOUR = 1500;
    public static final String[] DELAYS = {"MATERIALS", "TRAFFIC", "FUNDS", "NO CREW"};

    /** which stage a bill-of-materials line is used in */
    static int stageOf(Line l) {
        switch (l.label) {
            case "BALLAST": return 1;
            case "SLEEPERS": case "RAILS": return 3;
            case "CABLE": case "MOTORS": case "TRANSFORMER": case "TRANSFORMERS": case "TURBINE BLADES": return 4;
            default: return 2;
        }
    }

    private int project, phase;            // phase 0 planning, 1 authorised (ordering / delivering), 2 building, 3 done
    private long buildStart, buildEnd;
    private boolean railOk;
    private final int[] used = new int[8];   // what has been consumed into the works per BOM line
    private int stage, crews = 4;
    private double stageWork;                // ticks of work done on the current stage (at full rate)
    private long budget, spent, granted;     // cents
    private final double[] delayHours = new double[DELAYS.length];
    private int delay = -1;                  // the cause holding the works right now, or -1
    private boolean legacy, legacyPlaced;
    private java.util.UUID authBy;           // who turned the AUTHORISE key: their company owns the works
    private long plannedEnd;
    private final java.util.Set<java.util.UUID> holding = new java.util.HashSet<>();

    public TileConstructionSite() {
        super(new String[]{"yard", "office"}, new String[]{"Materials compound", "Site office drawers"}, new int[]{27, 9});
    }

    Project proj() { return PROJECTS[Math.floorMod(project, PROJECTS.length)]; }
    public String projectName() { return proj().label; }
    public long budget() { return budget; }
    /** the cost estimate: four crews and the right plant for the project's hours */
    public long estimate() { return Math.round(proj().hours * (4 * CREW_HOUR + PLANT_HOUR)); }
    public void fund(long cents, String why) { budget += cents; Recorder.event(world, "Construction " + proj().label + ": " + com.dogpound.railmap.economy.Coins.fmt(cents) + " into the budget (" + why + ")"); markDirty(); }

    private int plant() { return Math.floorMod(ctl.get("s_plant"), PLANT.length); }
    private boolean possession() { return ctl.get("k_poss") == 1; }

    @Override public String wiredName() { return "Construction site office"; }
    @Override public String gearState() { return proj().label + " · " + new String[]{"planning", "authorised - materials " + pct() + "%", "building: " + STAGES[Math.min(stage, 6)] + " " + buildPct() + "%" + (delay >= 0 ? " DELAYED (" + DELAYS[delay].toLowerCase(Locale.ROOT) + ")" : ""), "complete"}[phase]; }

    // ---- the materials -----------------------------------------------------------------------------------------------
    private int have(Line l) {
        int n = 0;
        IInventory inv = store(0);
        for (int i = 0; i < inv.getSizeInventory(); i++) if (l.matches(inv.getStackInSlot(i))) n += inv.getStackInSlot(i).getCount();
        return n;
    }

    /** this line still has to come (not yet built into the works) */
    private boolean outstanding(int i) { return used[i] < proj().bom[i].count; }

    private int pct() {
        int need = 0, got = 0;
        Line[] bom = proj().bom;
        for (int i = 0; i < bom.length; i++) { need += bom[i].count; got += Math.min(bom[i].count, used[i] + (outstanding(i) ? have(bom[i]) : 0)); }
        return need == 0 ? 100 : got * 100 / need;
    }

    /** works done, % of the whole project */
    private int buildPct() {
        if (phase < 2) return 0;
        if (phase == 3) return 100;
        double d = 0;
        for (int s = 0; s < stage; s++) d += SHARE[s];
        d += SHARE[Math.min(stage, 6)] * Math.min(1, stageWork / stageNeed());
        return (int) Math.min(100, d * 100);
    }

    private double stageNeed() { return Math.max(1, proj().hours * 1000.0 * SHARE[Math.min(stage, 6)]); }

    /** a rail siding (Immersive Railroading or vanilla) within 12 blocks */
    private boolean railNear() {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int dx = -12; dx <= 12; dx++)
            for (int dz = -12; dz <= 12; dz++)
                for (int dy = -3; dy <= 3; dy++) {
                    q.setPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!world.isBlockLoaded(q)) continue;
                    Block b = world.getBlockState(q).getBlock();
                    if (b instanceof BlockRailBase) return true;
                    ResourceLocation r = b.getRegistryName();
                    if (r != null && r.getNamespace().equals("immersiverailroading") && r.getPath().startsWith("block_rail")) return true;
                }
        return false;
    }

    // ---- the railway sees it as a buyer (IndustryLink): automatic freight orders for the bill of materials -------------
    @Override public String endpointName() { return "Site: " + proj().label.toLowerCase(Locale.ROOT); }
    @Override public List<ItemStack> makes() { return Collections.emptyList(); }

    @Override
    public boolean accepts(ItemStack s) {
        if (phase != 1 && phase != 2) return false;
        Line[] bom = proj().bom;
        for (int i = 0; i < bom.length; i++) if (outstanding(i) && bom[i].matches(s) && have(bom[i]) < bom[i].count - used[i]) return true;
        return false;
    }

    @Override
    public List<ItemStack> wants() {
        List<ItemStack> out = new ArrayList<>();
        if (phase != 1 && phase != 2 || ctl.get("m_estop") == 1) return out;
        Line[] bom = proj().bom;
        for (int i = 0; i < bom.length; i++) {
            if (!outstanding(i)) continue;
            int short_ = bom[i].count - used[i] - have(bom[i]);
            if (short_ <= 0) continue;
            ItemStack w = bom[i].want(short_);
            if (!w.isEmpty()) out.add(w);
        }
        return out;
    }

    @Override public String endpointState() { return new String[]{"PLANNING", "MATERIALS " + pct() + "%", (delay >= 0 ? "DELAYED " : "") + STAGES[Math.min(stage, 6)] + " " + buildPct() + "%", "DONE"}[phase]; }

    @Override public void onLoad() { IndustryLink.register(this); }
    @Override public void invalidate() { super.invalidate(); IndustryLink.forget(this); release(); }

    // ---- the compound doors take deliveries from unloaders / pipes (only what the bill still needs) --------------------
    private final IItemHandler intake = new IItemHandler() {
        @Override public int getSlots() { return store(0).getSizeInventory(); }
        @Override public ItemStack getStackInSlot(int i) { return store(0).getStackInSlot(i); }
        @Override public ItemStack insertItem(int i, ItemStack s, boolean sim) {
            if (s.isEmpty() || !accepts(s)) return s;
            ItemStack in = store(0).getStackInSlot(i);
            if (!in.isEmpty() && !(ItemStack.areItemsEqual(in, s) && ItemStack.areItemStackTagsEqual(in, s))) return s;
            int room = Math.min(s.getMaxStackSize(), 64) - in.getCount();
            if (room <= 0) return s;
            int n = Math.min(room, s.getCount());
            if (!sim) { ItemStack put = s.copy(); put.setCount(in.getCount() + n); store(0).setInventorySlotContents(i, put); }
            ItemStack rest = s.copy(); rest.shrink(n);
            return rest;
        }
        @Override public ItemStack extractItem(int i, int n, boolean sim) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int i) { return 64; }
    };

    @Override public boolean hasCapability(Capability<?> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(c, f); }
    @SuppressWarnings("unchecked")
    @Override public <T> T getCapability(Capability<T> c, EnumFacing f) { return c == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ? (T) intake : super.getCapability(c, f); }

    // ---- controls --------------------------------------------------------------------------------------------------
    @Override
    protected String mechanical(String id, EntityPlayer who) {
        if (id.equals("k_auth")) {
            if (phase >= 2) return "§7Work has started - the key is captive";
            if (ctl.get(id) == 1) { ctl.set(id, 0); phase = 0; return "§eProject authorisation withdrawn - no more orders go out"; }
            ctl.set(id, 1); phase = 1;
            if (who != null) authBy = who.getUniqueID();
            if (granted == 0 && com.dogpound.railmap.biz.BizConfig.constructionCosts) {
                granted = estimate() * com.dogpound.railmap.biz.BizConfig.constructionGrantPct / 100;
                budget += granted;
            }
            Recorder.event(world, "Construction: " + proj().label + " authorised at " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
            return "§aAuthorised: " + proj().label + " - the site orders its materials by train · estimate " + com.dogpound.railmap.economy.Coins.fmt(estimate())
                    + (granted > 0 ? ", county grant " + com.dogpound.railmap.economy.Coins.fmt(granted) : "");
        }
        if (id.equals("m_estop")) { ctl.set(id, ctl.get(id) == 1 ? 0 : 1); return ctl.get(id) == 1 ? "§cSTOP WORK - the site is stood down" : "§aWork may resume"; }
        if (id.equals("s_plant")) {
            ctl.set(id, (plant() + 1) % PLANT.length);
            int need = phase == 2 ? STAGE_PLANT[Math.min(stage, 6)] : -1;
            return "§7Plant on hire: §f" + PLANT[plant()] + (need >= 0 ? (plant() == need ? " §a(right for " + STAGES[stage] + ")" : " §e(" + STAGES[stage] + " wants " + PLANT[need] + ")") : "")
                    + (plant() > 0 ? " §7· " + com.dogpound.railmap.economy.Coins.fmt(PLANT_HOUR) + "/h" : "");
        }
        if (id.equals("k_poss")) {
            boolean on = ctl.get(id) == 0;
            ctl.set(id, on ? 1 : 0);
            if (!on) { release(); return "§aPossession given up - the line is open to traffic again"; }
            int n = possess();
            Recorder.event(world, "Construction " + proj().label + ": engineering POSSESSION taken - " + n + " driverless trains held");
            return "§ePOSSESSION taken: " + n + " driverless train(s) of the nearby lines are held at their stops while the works are on the track";
        }
        return null;
    }

    /** hold every driverless train of the lines with a station within 96 blocks */
    private int possess() {
        int dim = world.provider.getDimension(), n = 0;
        com.dogpound.railmap.auto.RailwayData rd = com.dogpound.railmap.auto.RailwayData.get(world);
        for (com.dogpound.railmap.auto.RailwayData.AutoTrain a : rd.trains().values()) {
            com.dogpound.railmap.auto.RailwayData.Line l = rd.line(a.line);
            boolean near = false;
            if (l != null) for (long s : l.stations) if (BlockPos.fromLong(s).distanceSq(pos) < 96 * 96) near = true;
            if (!near) continue;
            com.dogpound.railmap.auto.ConsoleControl.hold(dim, a.loco, true);
            holding.add(a.loco);
            n++;
        }
        return n;
    }

    private void release() {
        if (world == null || holding.isEmpty()) return;
        for (java.util.UUID u : holding) com.dogpound.railmap.auto.ConsoleControl.hold(world.provider.getDimension(), u, false);
        holding.clear();
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        switch (id) {
            case "prev": case "next": {
                if (phase != 0) return "§7Withdraw the authorisation (key) to change the project";
                project = Math.floorMod(project + (id.equals("next") ? 1 : -1), PROJECTS.length);
                markDirty();
                StringBuilder b = new StringBuilder("§eProject: " + proj().label + " §7(" + proj().hours + " h, est. " + com.dogpound.railmap.economy.Coins.fmt(estimate()) + ") -");
                for (Line l : proj().bom) b.append(' ').append(l.count).append(' ').append(l.label.toLowerCase(Locale.ROOT)).append(',');
                return b.substring(0, b.length() - 1);
            }
            case "order": {
                if (phase != 1 && phase != 2) return "§7Authorise the project first (key)";
                com.dogpound.railmap.freight.Contract k = com.dogpound.railmap.freight.ContractData.get(world).orders(world);
                return k == null ? "§7No new orders: everything short is already on order, or no plant makes it yet" : "§aOrder #" + k.id + " posted: " + k.qty + " " + k.itemName + " from " + k.fromName;
            }
            case "build": {
                if (phase == 0) return "§cAuthorise the project first";
                if (phase >= 2) return "§7Already " + (phase == 2 ? "building" : "built");
                if (ctl.get("m_estop") == 1) return "§cSTOP WORK is in";
                if (!railNear()) return "§cNo rail siding within 12 blocks - lay the connecting track first";
                phase = 2; stage = 0; stageWork = 0; buildStart = world.getTotalWorldTime();
                plannedEnd = buildStart + proj().hours * 1000L;
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.construction"), SoundCategory.BLOCKS, 0.9f, 1.0f);
                Recorder.event(world, "Construction: " + proj().label + " started - planned " + proj().hours + " h, " + crews + " crews");
                return "§aWorks begin: SURVEY. Each stage draws its own materials from the compound - " + proj().hours + " h planned with 4 crews and the right plant";
            }
            case "crewup": case "crewdn": {
                crews = Math.max(0, Math.min(8, crews + (id.equals("crewup") ? 1 : -1)));
                return "§7Crews on site: §f" + crews + " §7(" + com.dogpound.railmap.economy.Coins.fmt(crews * CREW_HOUR) + "/h)";
            }
            case "fund": {
                if (!(who instanceof net.minecraft.entity.player.EntityPlayerMP p)) return "§7Funds are paid in person";
                com.dogpound.railmap.economy.Company c = com.dogpound.railmap.economy.CompanyData.get(world).of(p.getUniqueID());
                long amt = 10_000;
                if (c != null && c.can(p.getUniqueID(), r -> r.finance)) {
                    if (!com.dogpound.railmap.economy.CompanyData.get(world).debit(world, c, com.dogpound.railmap.economy.Company.Kind.WORKS, amt, "Construction budget: " + proj().label, false))
                        return "§c" + c.name + " can't spare " + com.dogpound.railmap.economy.Coins.fmt(amt);
                    fund(amt, c.mark);
                } else {
                    if (!com.dogpound.railmap.economy.Coins.charge(p, amt, "Construction budget: " + proj().label)) return "§cFunding takes " + com.dogpound.railmap.economy.Coins.fmt(amt) + " from your bank";
                    fund(amt, p.getName());
                }
                return "§a+" + com.dogpound.railmap.economy.Coins.fmt(amt) + " into the budget - now " + com.dogpound.railmap.economy.Coins.fmt(budget);
            }
            case "loan": {
                if (!(who instanceof net.minecraft.entity.player.EntityPlayerMP p)) return "§7Loans are signed in person";
                com.dogpound.railmap.economy.Company c = com.dogpound.railmap.economy.CompanyData.get(world).of(p.getUniqueID());
                if (c == null || !c.can(p.getUniqueID(), r -> r.finance)) return "§cA project loan is taken by a company's owner or director";
                return com.dogpound.railmap.biz.BizData.get(world).securities.projectLoan(world, c, pos, Math.max(1, estimate() / 2));
            }
            case "report": {
                if (who == null) return "";
                who.sendMessage(new TextComponentString("§6🏗 " + proj().label + " §7- " + endpointState() + " · crews " + crews + " · plant " + PLANT[plant()]));
                who.sendMessage(new TextComponentString(String.format(Locale.ROOT, "§7 budget %s, spent %s (estimate %s, grant %s)", com.dogpound.railmap.economy.Coins.fmt(budget),
                        com.dogpound.railmap.economy.Coins.fmt(spent), com.dogpound.railmap.economy.Coins.fmt(estimate()), com.dogpound.railmap.economy.Coins.fmt(granted))));
                StringBuilder d = new StringBuilder("§7 delays:");
                for (int i = 0; i < DELAYS.length; i++) d.append(' ').append(DELAYS[i].toLowerCase(Locale.ROOT)).append(String.format(Locale.ROOT, " %.1f h", delayHours[i]));
                who.sendMessage(new TextComponentString(d.toString()));
                if (phase == 2) who.sendMessage(new TextComponentString("§7 forecast: " + forecast()));
                return "";
            }
            case "test": return lampTest();
            default: return null;
        }
    }

    /** forecast completion against the plan */
    private String forecast() {
        double rate = rate();
        if (rate <= 0) return "STALLED";
        double left = (1 - buildPct() / 100.0) * proj().hours * 1000 / rate;
        long eta = world.getTotalWorldTime() + (long) left;
        long late = plannedEnd <= 0 ? 0 : (eta - plannedEnd) / 1000;
        return "done " + com.dogpound.railmap.biz.BizData.hhmm(eta) + " day " + eta / 24000 + (late > 0 ? ", " + late + " h LATE" : ", on plan");
    }

    /** how fast the works go now, 1.0 = planned (4 crews, the right plant) */
    private double rate() {
        if (legacy) return 1.0;
        int need = STAGE_PLANT[Math.min(stage, 6)];
        double p = need == 0 ? 1.0 : plant() == need ? 1.0 : plant() == 0 ? 0.4 : 0.5;
        double r = crews / 4.0 * p;
        com.dogpound.railmap.economy.Company c = placerCompany();
        if (c != null && com.dogpound.railmap.biz.BizConfig.researchTree) {
            com.dogpound.railmap.biz.BizData d = com.dogpound.railmap.biz.BizData.get(world);
            if (stage == 0 && d.research.prog(c.id).done[com.dogpound.railmap.biz.Research.Tech.SURVEYING.ordinal()]) r *= 2;
            if (d.research.prog(c.id).done[com.dogpound.railmap.biz.Research.Tech.HEAVY_CIVILS.ordinal()]) r *= 1.25;
            if (stage == 4 && d.research.prog(c.id).done[com.dogpound.railmap.biz.Research.Tech.ELECTRIFICATION.ordinal()]) r *= 1.5;
        }
        return r;
    }

    private com.dogpound.railmap.economy.Company placerCompany() {
        return authBy == null ? null : com.dogpound.railmap.economy.CompanyData.get(world).of(authBy);
    }

    private int take(Line l, int max) {
        int left = max;
        IInventory inv = store(0);
        for (int i = 0; i < inv.getSizeInventory() && left > 0; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!l.matches(s)) continue;
            int t = Math.min(left, s.getCount());
            s.shrink(t); left -= t;
            if (s.isEmpty()) inv.setInventorySlotContents(i, ItemStack.EMPTY);
        }
        inv.markDirty();
        return max - left;
    }

    /** the stage's materials: built in as they arrive; true when all of the stage's lines are in */
    private boolean stageMaterials() {
        boolean all = true;
        Line[] bom = proj().bom;
        for (int i = 0; i < bom.length; i++) {
            if (stageOf(bom[i]) != stage || !outstanding(i)) continue;
            used[i] += take(bom[i], bom[i].count - used[i]);
            if (outstanding(i)) all = false;
        }
        return all;
    }

    /** a train moving within the traffic radius of the site (stage on the track) */
    private boolean trafficNear() {
        int r = com.dogpound.railmap.biz.BizConfig.constructionTrafficRadius;
        if (r <= 0 || possession()) return false;
        try {
            for (cam72cam.immersiverailroading.entity.EntityMoveableRollingStock s : cam72cam.mod.world.World.get(world).getEntities(cam72cam.immersiverailroading.entity.EntityMoveableRollingStock.class)) {
                cam72cam.mod.math.Vec3d p = s.getPosition();
                if (Math.abs(p.x - pos.getX()) > r || Math.abs(p.z - pos.getZ()) > r) continue;
                if (s.getCurrentSpeed() != null && Math.abs(s.getCurrentSpeed().metric()) > 1) return true;
            }
        } catch (RuntimeException | LinkageError ignored) { }
        return false;
    }

    @Override
    protected void work() {
        if (age % 20 != 0) return;
        if (age % 400 == 0) IndustryLink.register(this);
        if ((phase == 1 || phase == 2) && age % 100 == 0) railOk = railNear();
        if (possession() && age % 100 == 0) possess();                           // re-assert after a restart
        if (phase == 2) build();
        Project p = proj();
        text("proj", p.label);
        text("stage", phase == 2 ? STAGES[Math.min(stage, 6)] + (delay >= 0 ? " - DELAY" : "") : new String[]{"PLANNING", "AUTHORISED", "BUILDING", "COMPLETE"}[phase]);
        int mp = pct(), bp = buildPct();
        put("matg", phase == 3 ? 100 : mp); put("buildg", bp);
        text("pct", phase >= 2 ? bp + "%" : mp + "%");
        for (int i = 0; i < 8; i++) {
            if (i < p.bom.length) {
                Line l = p.bom[i];
                int h = Math.min(l.count, used[i] + (outstanding(i) ? have(l) : 0));
                text("r" + i, l.label + " " + h + "/" + l.count);
                put("rl" + i, h >= l.count);
            } else { text("r" + i, ""); put("rl" + i, false); }
        }
        put("pwr", powered()); put("auth", phase >= 1); put("mat", mp >= 100 || phase == 3); put("rail", phase >= 2 || railOk);
        put("bld", phase == 2 && delay < 0 && ctl.get("m_estop") == 0); put("done", phase == 3); put("stop", ctl.get("m_estop") == 1);
        // the programme board
        for (int s = 0; s < STAGES.length; s++) put("st" + s, phase == 3 || phase == 2 && s < stage ? 2 : phase == 2 && s == stage ? 1 : 0);
        for (int i = 0; i < DELAYS.length; i++) put("dl" + i, delay == i);
        put("poss", possession());
        text("pm_stage", phase == 2 ? STAGES[Math.min(stage, 6)] + " " + (int) Math.min(100, stageWork * 100 / stageNeed()) + "%" : phase == 3 ? "OPEN FOR TRAFFIC" : "NOT STARTED");
        text("pm_crew", crews + " CREWS");
        text("pm_plant", PLANT[plant()]);
        text("pm_budget", "BUDGET " + com.dogpound.railmap.economy.Coins.fmt(budget));
        text("pm_spent", "SPENT " + com.dogpound.railmap.economy.Coins.fmt(spent));
        text("pm_fcst", phase == 2 ? forecast().toUpperCase(Locale.ROOT) : phase == 3 ? "COMPLETE" : "EST " + com.dogpound.railmap.economy.Coins.fmt(estimate()));
        double dt = 0;
        for (double v : delayHours) dt += v;
        text("pm_why", delay >= 0 ? "HELD: " + DELAYS[delay] : String.format(Locale.ROOT, "DELAYS %.1f H", dt));
        put("pm_rate", (int) Math.round(rate() * 100));
    }

    /** one second of the works */
    private void build() {
        if (legacy && !legacyPlaced) {                                         // an old save: pick up where the clock says it was
            legacyPlaced = true;
            double f = buildEnd > buildStart ? Math.max(0, Math.min(1, (double) (world.getTotalWorldTime() - buildStart) / (buildEnd - buildStart))) : 0, acc = 0;
            while (stage < 6 && acc + SHARE[stage] <= f) { acc += SHARE[stage]; stage++; }
            stageWork = (f - acc) * proj().hours * 1000.0;
        }
        int was = delay;
        delay = -1;
        if (ctl.get("m_estop") == 1) return;
        if (!legacy && crews == 0) delay = 3;
        else if (!legacy && !stageMaterials()) delay = 0;
        else if (!legacy && stage >= 3 && stage <= 5 && trafficNear()) delay = 1;
        long cost = legacy || !com.dogpound.railmap.biz.BizConfig.constructionCosts ? 0 : Math.round((crews * CREW_HOUR + (plant() > 0 ? PLANT_HOUR : 0)) * 20 / 1000.0);
        if (delay < 0 && cost > budget) delay = 2;
        if (delay >= 0) {
            delayHours[delay] += 20 / 1000.0;
            if (was != delay) {
                Recorder.event(world, "Construction " + proj().label + ": " + STAGES[Math.min(stage, 6)] + " DELAYED - " + DELAYS[delay].toLowerCase(Locale.ROOT));
                AlarmManager.get(world).raise(world, "site:delay:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO,
                        "Construction " + proj().label.toLowerCase(Locale.ROOT) + " delayed: " + DELAYS[delay].toLowerCase(Locale.ROOT), pos, false);
            }
            return;
        }
        if (was >= 0) AlarmManager.get(world).clear("site:delay:" + pos.toLong());
        budget -= cost; spent += cost;
        stageWork += 20 * rate();
        if (stageWork < stageNeed()) return;
        // stage complete
        Recorder.event(world, "Construction " + proj().label + ": " + STAGES[stage] + " complete");
        world.playSound(null, pos, com.dogpound.railmap.ModSounds.BEEP_OK, SoundCategory.BLOCKS, 0.6f, 1.0f);
        com.dogpound.railmap.biz.BizData.get(world).mastery.count(world, placerCompany(), null, "stages", 1);
        stage++; stageWork = 0;
        if (stage >= STAGES.length) finish();
    }

    /** test command: complete the works now (only once building has started) */
    public String finishNow() {
        if (phase != 2) return "§7The site isn't building (" + endpointState() + ") - deliver the materials and press BUILD";
        finish();
        return "§aFinished";
    }

    /** the works are finished: the site becomes the new industry (leftovers stay on the ground) */
    private void finish() {
        Project p = proj();
        phase = 3;
        release();
        Block b = Block.REGISTRY.getObject(new ResourceLocation("irextras", "industry_" + p.kind.id));
        Recorder.event(world, "Construction: " + p.label + " COMPLETE at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + String.format(Locale.ROOT, " - spent %s of %s", com.dogpound.railmap.economy.Coins.fmt(spent), com.dogpound.railmap.economy.Coins.fmt(estimate())));
        com.dogpound.railmap.biz.BizData.get(world).env.built(2.0, p.hours * 12);   // #18: land take + spoil
        AlarmManager.get(world).raise(world, "site:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.INFO, "New " + p.label.toLowerCase(Locale.ROOT) + " complete and open for traffic", pos, false);
        AlarmManager.get(world).clear("site:delay:" + pos.toLong());
        for (EntityPlayer pl : world.playerEntities) if (pl.getDistanceSq(pos) < 128 * 128) pl.sendMessage(new TextComponentString("§a🏗 The new " + p.label.toLowerCase(Locale.ROOT) + " is complete - its trains can run"));
        world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 0.6f, 1.0f);
        if (b != null && b != net.minecraft.init.Blocks.AIR) {
            world.setBlockState(pos, b.getDefaultState(), 3);                     // DepotBlock.breakBlock drops what is left in the compound
        }
    }

    /** project management for the console: name, stage, %, crews, plant, budget, spent, delay, delay hours, forecast */
    public String[] pmRow() {
        double dt = 0;
        for (double v : delayHours) dt += v;
        return new String[]{"pm", proj().label, phase == 2 ? STAGES[Math.min(stage, 6)] : new String[]{"PLANNING", "AUTHORISED", "", "COMPLETE"}[phase], String.valueOf(buildPct()), String.valueOf(crews),
                PLANT[plant()], com.dogpound.railmap.economy.Coins.fmt(budget), com.dogpound.railmap.economy.Coins.fmt(spent), delay >= 0 ? DELAYS[delay] : "",
                String.format(Locale.ROOT, "%.1f", dt), phase == 2 ? forecast() : "", pos.getX() + " " + pos.getY() + " " + pos.getZ(), String.valueOf(pct())};
    }

    @Override protected void saveExtra(NBTTagCompound t) {
        t.setInteger("project", project); t.setInteger("phase", phase); t.setLong("bs", buildStart); t.setLong("be", buildEnd); t.setIntArray("used", used);
        NBTTagCompound pm = new NBTTagCompound();                                // #11 project management
        pm.setInteger("stage", stage); pm.setInteger("crews", crews); pm.setDouble("work", stageWork); pm.setLong("budget", budget); pm.setLong("spent", spent);
        if (authBy != null) pm.setUniqueId("by", authBy);
        pm.setBoolean("lp", legacyPlaced);
        pm.setLong("grant", granted); pm.setBoolean("legacy", legacy); pm.setLong("planned", plannedEnd); pm.setInteger("delay", delay);
        for (int i = 0; i < DELAYS.length; i++) pm.setDouble("d" + i, delayHours[i]);
        t.setTag("pm", pm);
    }

    @Override protected void loadExtra(NBTTagCompound t) {
        project = t.getInteger("project"); phase = t.getInteger("phase"); buildStart = t.getLong("bs"); buildEnd = t.getLong("be");
        int[] u = t.getIntArray("used"); System.arraycopy(u, 0, used, 0, Math.min(u.length, used.length));
        if (t.hasKey("pm")) {
            NBTTagCompound pm = t.getCompoundTag("pm");
            stage = pm.getInteger("stage"); crews = pm.getInteger("crews"); stageWork = pm.getDouble("work"); budget = pm.getLong("budget"); spent = pm.getLong("spent");
            granted = pm.getLong("grant"); legacy = pm.getBoolean("legacy"); plannedEnd = pm.getLong("planned"); delay = pm.getInteger("delay");
            for (int i = 0; i < DELAYS.length; i++) delayHours[i] = pm.getDouble("d" + i);
        } else if (phase == 2) {
            // a save from before project management: everything was built in at BUILD; carry on at the old pace, free
            legacy = true; plannedEnd = buildEnd;
            java.util.Arrays.fill(used, Integer.MAX_VALUE / 2);
        }
        if (t.hasKey("pm") && t.getCompoundTag("pm").hasUniqueId("by")) authBy = t.getCompoundTag("pm").getUniqueId("by");
        legacyPlaced = t.hasKey("pm") && t.getCompoundTag("pm").getBoolean("lp");
    }

}
