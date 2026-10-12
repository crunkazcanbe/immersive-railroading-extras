package com.dogpound.railmap.freight;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.RailMapConfig;
import com.dogpound.railmap.industry.IndustryKind;
import com.dogpound.railmap.industry.IndustryLink;
import com.dogpound.railmap.industry.TileIndustry;
import net.minecraft.tileentity.TileEntity;
import com.dogpound.railmap.server.StationData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Freight contracts, per world (her ChatGPT list: "freight contracts from industry demand"). Industries that need goods
 * post contracts for them from industries that make them; you accept one at a Freight Office (which prints the waybill),
 * load the goods at the origin (the cars get billed to the contract), unload them at the destination, and get paid in
 * Pride coins - in full on time, less the later you are.
 */
public final class ContractData extends WorldSavedData {
    private static final String NAME = "railmap_contracts";
    private final List<Contract> contracts = new ArrayList<>();
    private int nextId = 101;
    private long lastPost;

    public ContractData() { super(NAME); }
    public ContractData(String n) { super(n); }

    public static ContractData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        ContractData d = (ContractData) s.getOrLoadData(ContractData.class, NAME);
        if (d == null) { d = new ContractData(); s.setData(NAME, d); }
        return d;
    }

    public List<Contract> all() { return contracts; }
    public Contract byId(int id) { for (Contract k : contracts) if (k.id == id) return k; return null; }

    /** live contracts first (open, in progress), then the latest finished ones */
    public List<Contract> board() {
        List<Contract> out = new ArrayList<>();
        for (Contract k : contracts) if (k.state == Contract.State.ACCEPTED) out.add(k);
        for (Contract k : contracts) if (k.state == Contract.State.OPEN) out.add(k);
        List<Contract> done = new ArrayList<>();
        for (Contract k : contracts) if (k.state != Contract.State.ACCEPTED && k.state != Contract.State.OPEN) done.add(k);
        Collections.reverse(done);
        out.addAll(done);
        return out;
    }

    // ---- the clock ------------------------------------------------------------------------------------------------
    public void tick(World w) {
        if (!RailMapConfig.freightContracts) return;
        long now = w.getTotalWorldTime();
        for (Contract k : contracts) {
            if (k.state == Contract.State.OPEN && now - k.posted > RailMapConfig.contractOpenHours * 1000L) { k.state = Contract.State.EXPIRED; markDirty(); }
            if (k.state == Contract.State.ACCEPTED && now > k.deadline + RailMapConfig.contractGraceHours * 1000L) {
                k.state = Contract.State.EXPIRED;
                markDirty();
                tell(w, k, "§cContract #" + k.id + " failed - " + k.delivered + " of " + k.qty + " " + k.itemName + " reached " + k.toName + " in time");
            }
        }
        while (contracts.size() > 60) contracts.remove(0);                      // keep the history short
        long open = contracts.stream().filter(k -> k.state == Contract.State.OPEN).count();
        if (open < RailMapConfig.contractMaxOpen && now - lastPost > RailMapConfig.contractEveryMinutes * 1200L) {
            lastPost = now;
            Contract k = post(w);
            if (k != null) RailMap.LOG.info("[IR Extras] freight contract #{} posted: {} x{} {} -> {}", k.id, k.itemName, k.qty, k.fromName, k.toName);
        }
        if (now % 1200 == 600) {                                                 // once a minute: anyone running low orders it
            Contract k = orders(w);
            if (k != null) {
                RailMap.LOG.info("[IR Extras] freight ORDER #{}: {} x{} {} -> {}", k.id, k.itemName, k.qty, k.fromName, k.toName);
                com.dogpound.railmap.alarm.Recorder.event(w, "Freight order #" + k.id + ": " + k.toName + " is short of " + k.itemName + " - " + k.qty + " from " + k.fromName);
            }
        }
    }

    /** an industry that needs something posts a contract to fetch it from one that makes it (any mod's plant: IndustryLink) */
    public Contract post(World w) {
        List<TileEntity> list = IndustryLink.all(w);
        if (list.size() < 2) return null;
        List<Object[]> pairs = new ArrayList<>();                              // {from, to, goods}
        for (TileEntity a : list)
            for (ItemStack out : ((IndustryLink.Endpoint) a).makes())
                for (TileEntity b : list) {
                    if (a == b || out.isEmpty()) continue;
                    ItemStack one = out.copy(); one.setCount(1);
                    if (!((IndustryLink.Endpoint) b).accepts(one)) continue;
                    if (busy(a.getPos(), b.getPos(), key(one))) continue;          // one live contract per lane and item
                    pairs.add(new Object[]{a, b, one});
                }
        if (pairs.isEmpty()) return null;
        Object[] pick = pairs.get(w.rand.nextInt(pairs.size()));
        TileEntity a = (TileEntity) pick[0], b = (TileEntity) pick[1];
        ItemStack s = (ItemStack) pick[2];
        boolean town = b instanceof TileIndustry ti && ti.kind() == IndustryKind.TOWN_MARKET;
        return make(w, a, b, key(s), s.getDisplayName(), 16 * (2 + w.rand.nextInt(town ? 6 : 12)), s, false);
    }

    private static String key(ItemStack s) { return s.getItem().getRegistryName() + ":" + s.getMetadata(); }

    /** write a contract from a to b for qty of the goods (an item key, or "fluid:<name>" in litres) */
    private Contract make(World w, TileEntity a, TileEntity b, String item, String itemName, int qty, ItemStack priced, boolean order) {
        Contract k = new Contract();
        k.id = nextId++;
        k.item = item;
        k.itemName = itemName;
        k.order = order;
        k.from = a.getPos(); k.to = b.getPos();
        k.fromName = place(w, a); k.toName = place(w, b);
        k.distance = (int) Math.sqrt(a.getPos().distanceSq(b.getPos()));
        k.qty = qty;
        // the freight rate by distance, plus half the goods' market price (her list #49: contracts pay at market price -
        // a shortage at the buyer makes the goods dearer and the haul worth more); fluids by the 1000 L
        com.dogpound.railmap.economy.MarketData.Price mp = priced.isEmpty() ? null : com.dogpound.railmap.economy.MarketData.get(w).of(priced);
        double units = k.fluid() ? qty / 1000.0 * 8 : qty;
        k.pay = Math.round(units * (8 + k.distance / 12.0 + (mp == null ? 0 : mp.price * 0.5)) * RailMapConfig.contractPayScale * (order ? 1.25 : 1));   // an order pays a rush premium
        k.posted = w.getTotalWorldTime();
        long ready = ((IndustryLink.Endpoint) a).readyAt();                   // her list #93: timed to the factory's next batch
        k.readyAt = ready > k.posted ? ready : 0;
        k.deadline = Math.max(k.posted, k.readyAt) + 6000 + k.distance * 30L + (long) units * 60L;
        contracts.add(k);
        markDirty();
        return k;
    }

    /**
     * Automatic freight orders (her list #89 / #87 / #91): every endpoint that is short of something (a factory's input
     * bin, depot stores under their reorder level, a fuel point's tank) orders it from the nearest plant that makes it -
     * the one with goods ready first. One live order per buyer and item.
     */
    public Contract orders(World w) {
        if (!RailMapConfig.industryOrders) return null;
        List<TileEntity> list = IndustryLink.all(w);
        for (TileEntity b : list) {
            IndustryLink.Endpoint buyer = (IndustryLink.Endpoint) b;
            for (ItemStack want : buyer.wants()) {
                if (want.isEmpty()) continue;
                ItemStack one = want.copy(); one.setCount(1);
                String item = key(one);
                if (live(b.getPos(), item)) continue;
                TileEntity best = null; int bestReady = -1; double bd = Double.MAX_VALUE;
                for (TileEntity a : list) {
                    if (a == b) continue;
                    for (ItemStack m : ((IndustryLink.Endpoint) a).makes()) {
                        if (m.isEmpty() || !m.isItemEqual(one)) continue;
                        double d = a.getPos().distanceSq(b.getPos());
                        if (m.getCount() > bestReady || m.getCount() == bestReady && d < bd) { best = a; bestReady = m.getCount(); bd = d; }
                    }
                }
                if (best == null) continue;
                int qty = Math.max(16, (want.getCount() + 15) / 16 * 16);
                return make(w, best, b, item, one.getDisplayName(), qty, one, true);
            }
            for (net.minecraftforge.fluids.FluidStack want : buyer.fluidsWanted()) {
                if (want == null || want.getFluid() == null) continue;
                String item = "fluid:" + want.getFluid().getName();
                if (live(b.getPos(), item)) continue;
                TileEntity best = null; double bd = Double.MAX_VALUE;
                for (TileEntity a : list) {
                    if (a == b) continue;
                    for (net.minecraftforge.fluids.FluidStack m : ((IndustryLink.Endpoint) a).fluidsMade())
                        if (m != null && m.getFluid() == want.getFluid() && a.getPos().distanceSq(b.getPos()) < bd) { best = a; bd = a.getPos().distanceSq(b.getPos()); }
                }
                if (best == null) continue;
                int litres = Math.max(4000, (want.amount + 999) / 1000 * 1000);
                return make(w, best, b, item, "L " + want.getLocalizedName(), litres, ItemStack.EMPTY, true);
            }
        }
        return null;
    }

    /** a live (open or accepted) contract already brings this item to this buyer */
    private boolean live(BlockPos to, String item) {
        for (Contract k : contracts)
            if ((k.state == Contract.State.OPEN || k.state == Contract.State.ACCEPTED) && k.to.equals(to) && k.item.equals(item)) return true;
        return false;
    }

    private boolean busy(BlockPos from, BlockPos to, String item) {
        for (Contract k : contracts)
            if ((k.state == Contract.State.OPEN || k.state == Contract.State.ACCEPTED) && k.from.equals(from) && k.to.equals(to) && k.item.equals(item)) return true;
        return false;
    }

    /** "Steel Mill (Ashford)" - the industry, and the station it is served from */
    private static String place(World w, TileEntity t) {
        StationData sd = StationData.get(w);
        String best = "";
        double bd = (double) RailMapConfig.stationReach * RailMapConfig.stationReach * 4;
        for (Map.Entry<Long, String> e : sd.names().entrySet()) {
            double d = BlockPos.fromLong(e.getKey()).distanceSq(t.getPos());
            if (d < bd) { bd = d; best = e.getValue(); }
        }
        return ((IndustryLink.Endpoint) t).endpointName() + (best.isEmpty() ? "" : " (" + best + ")");
    }

    // ---- the office -----------------------------------------------------------------------------------------------
    public String accept(World w, int id, EntityPlayer p) {
        Contract k = byId(id);
        if (k == null || k.state != Contract.State.OPEN) return "§7That contract is no longer open";
        k.state = Contract.State.ACCEPTED;
        k.by = p == null ? null : p.getUniqueID();
        k.byName = p == null ? "the yard" : p.getName();
        markDirty();
        return "§aContract #" + k.id + " accepted - " + k.qty + " " + k.itemName + " from " + k.fromName + " to " + k.toName + ", due in " + k.timeLeft(w.getTotalWorldTime());
    }

    public String decline(int id) {
        Contract k = byId(id);
        if (k == null || k.state != Contract.State.OPEN) return "§7That contract is no longer open";
        k.state = Contract.State.DECLINED;
        markDirty();
        return "§7Contract #" + k.id + " declined";
    }

    /** pay a player everything their delivered contracts earned that they haven't been paid yet */
    public long collect(EntityPlayerMP p) {
        long sum = 0;
        for (Contract k : contracts)
            if (k.state == Contract.State.DONE && !k.collected && p.getUniqueID().equals(k.by) && reward(p, k.paid, "Freight contract #" + k.id)) {
                k.collected = true;
                sum += k.paid;
            }
        if (sum > 0) markDirty();
        return sum;
    }

    public long uncollected(UUID who) {
        long s = 0;
        for (Contract k : contracts) if (k.state == Contract.State.DONE && !k.collected && who != null && who.equals(k.by)) s += k.paid;
        return s;
    }

    // ---- the yard: loaders bill cars, unloaders deliver ------------------------------------------------------------
    /** a freight terminal moved `moved` between the car `car` and the stockpile of the block at `yard` */
    public void onMoved(World w, boolean loading, BlockPos yard, UUID car, List<ItemStack> moved) {
        if (car == null || moved.isEmpty()) return;
        moved = com.dogpound.railmap.item.ItemContainer.unpack(moved);          // goods inside shipping containers count too (her list #47)
        long now = w.getTotalWorldTime();
        for (Contract k : contracts) {
            if (k.state != Contract.State.ACCEPTED) continue;
            int n = 0;
            for (ItemStack s : moved) if (k.matches(s)) n += s.getCount();
            if (n <= 0) continue;
            if (loading && IndustryLink.serves(w, k.from, yard)) {
                int bill = Math.min(n, k.qty - k.loaded);
                if (bill <= 0) continue;
                com.dogpound.railmap.biz.BizData.get(w).custody.contractMoved(w, k, true, yard, car, moved);   // #17 chain of custody
                k.cars.merge(car, bill, Integer::sum);
                k.loaded += bill;
                markDirty();
            } else if (!loading && IndustryLink.serves(w, k.to, yard) && k.cars.containsKey(car)) {
                int got = Math.min(n, Math.min(k.cars.get(car), k.qty - k.delivered));
                if (got <= 0) continue;
                if (com.dogpound.railmap.biz.BizData.get(w).custody.contractMoved(w, k, false, yard, car, moved)) continue;   // #17: refused by the consignee (rejectBadCargo)
                k.delivered += got;
                int left = k.cars.get(car) - got;
                if (left <= 0) k.cars.remove(car); else k.cars.put(car, left);
                markDirty();
                if (k.delivered >= k.qty) complete(w, k, now);
            }
        }
    }

    /** a tank car loaded / emptied at `yard` (her list #87: diesel deliveries tracked by tank car) */
    public void onFluid(World w, boolean loading, BlockPos yard, UUID car, net.minecraftforge.fluids.FluidStack moved) {
        if (car == null || moved == null || moved.amount <= 0) return;
        long now = w.getTotalWorldTime();
        for (Contract k : contracts) {
            if (k.state != Contract.State.ACCEPTED || !k.matchesFluid(moved)) continue;
            if (loading && IndustryLink.serves(w, k.from, yard)) {
                int bill = Math.min(moved.amount, k.qty - k.loaded);
                if (bill <= 0) continue;
                k.cars.merge(car, bill, Integer::sum);
                k.loaded += bill;
                markDirty();
            } else if (!loading && IndustryLink.serves(w, k.to, yard) && k.cars.containsKey(car)) {
                int got = Math.min(moved.amount, Math.min(k.cars.get(car), k.qty - k.delivered));
                if (got <= 0) continue;
                k.delivered += got;
                int left = k.cars.get(car) - got;
                if (left <= 0) k.cars.remove(car); else k.cars.put(car, left);
                markDirty();
                if (k.delivered >= k.qty) complete(w, k, now);
            }
        }
    }

    private void complete(World w, Contract k, long now) {
        k.state = Contract.State.DONE;
        k.paid = k.payNow(now);
        k.cars.clear();
        if (!k.fluid()) com.dogpound.railmap.economy.MarketData.get(w).sold(k.stack(), k.qty);   // the buyer is supplied: its price eases
        if (k.by != null) k.paid = com.dogpound.railmap.economy.CompanyData.get(w).contractPaid(w, k.by, k.paid, k.id);   // the hauler's company takes its share
        EntityPlayer p = k.by == null ? null : w.getPlayerEntityByUUID(k.by);
        if (p instanceof EntityPlayerMP mp && reward(mp, k.paid, "Freight contract #" + k.id)) k.collected = true;
        if (p != null) com.dogpound.railmap.api.QuestEvents.fire(w, p, null, "freight_delivered", "id", k.id, "qty", k.qty, "item", k.itemName, "ontime", now > k.deadline ? 0 : 1);
        tell(w, k, "§a📦 Contract #" + k.id + " delivered" + (now > k.deadline ? " §e(late)§a" : " on time") + " - " + k.qty + " " + k.itemName + " to " + k.toName
                + " · §6" + Contract.money(k.paid) + " coins" + (k.collected ? " paid" : " waiting at any Freight Office (COLLECT PAY)"));
        com.dogpound.railmap.program.ProgramData.get(w).alarm(w, "Freight contract #" + k.id + " delivered (" + k.itemName + " to " + k.toName + ")", k.to);
        markDirty();
    }

    private static void tell(World w, Contract k, String msg) {
        EntityPlayer p = k.by == null ? null : w.getPlayerEntityByUUID(k.by);
        if (p != null) p.sendMessage(new TextComponentString(msg));
    }

    // ---- Pride coins (Pride Realms, by reflection: without it the money just isn't paid) ----------------------------
    private static Method apiReward;
    private static boolean apiTried;

    static boolean reward(EntityPlayerMP p, long cents, String why) {
        if (!apiTried) {
            apiTried = true;
            try { apiReward = Class.forName("com.dogpound.realmcoin.api.PrideApi").getMethod("reward", EntityPlayerMP.class, long.class, String.class); }
            catch (Throwable t) { RailMap.LOG.info("[IR Extras] Pride Realms not installed - freight contracts can't pay coins"); }
        }
        if (apiReward == null) return false;
        try { apiReward.invoke(null, p, cents, why); return true; }
        catch (Throwable t) { RailMap.LOG.warn("[IR Extras] contract payment failed: {}", t.toString()); return false; }
    }

    // ---- save -----------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        contracts.clear();
        NBTTagList l = t.getTagList("contracts", 10);
        for (int i = 0; i < l.tagCount(); i++) contracts.add(Contract.read(l.getCompoundTagAt(i)));
        nextId = Math.max(101, t.getInteger("next"));
        lastPost = t.getLong("lastPost");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Contract k : contracts) l.appendTag(k.write());
        t.setTag("contracts", l);
        t.setInteger("next", nextId);
        t.setLong("lastPost", lastPost);
        return t;
    }
}
