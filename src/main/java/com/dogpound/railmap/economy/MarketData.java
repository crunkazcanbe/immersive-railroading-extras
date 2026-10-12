package com.dogpound.railmap.economy;

import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.freight.ContractData;
import com.dogpound.railmap.industry.IndustryKind;
import com.dogpound.railmap.industry.TileIndustry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

/**
 * Commodity prices (her ChatGPT list #49), per world, in cents per item. Once an in-game hour every commodity's price
 * moves toward what supply and demand say it is worth: supply = what the industries hold ready to ship + exchange
 * warehouses + what was just delivered / sold; demand = what the consumers lack + the towns' appetite + open contracts.
 * Grain and bread follow an 8-day harvest cycle. A price moves at most 5 % an hour and stays between 0.4x and 3x its
 * base price (bounded and deterministic, so a server economy can't run away). 48 hours of history are kept.
 */
public final class MarketData extends WorldSavedData {
    private static final String NAME = "irextras_market";
    public static final int HIST = 48;

    /** the board's commodities: key (modid:item:meta), base price in cents, harvest-seasonal */
    public static final Object[][] BOARD = {
            {"minecraft:coal:0", 40L, false}, {"minecraft:iron_ore:0", 65L, false}, {"minecraft:iron_ingot:0", 160L, false},
            {"minecraft:log:0", 30L, false}, {"minecraft:planks:0", 12L, false}, {"minecraft:wheat:0", 18L, true},
            {"minecraft:bread:0", 55L, true}, {"minecraft:gravel:0", 8L, false}, {"minecraft:cobblestone:0", 4L, false},
            {"minecraft:concrete_powder:8", 22L, false}, {"minecraft:iron_nugget:0", 17L, false}};

    public static final class Price {
        public String key = "";
        public long base, price;
        public boolean seasonal;
        public double supply, demand;
        public long delivered, bought;           // this hour (sold into the market / bought out of it)
        public long volume;                      // traded today
        public final long[] hist = new long[HIST];
        public int histN;
        public long last;                        // price an hour ago

        public long bid() { return Math.max(1, price * 96 / 100); }
        public long ask() { return Math.max(1, price * 104 / 100 + 1); }
        public double factor() { return base <= 0 ? 1 : price / (double) base; }
        public long high() { long m = price; for (int i = 0; i < Math.min(histN, 24); i++) m = Math.max(m, hist(i)); return m; }
        public long low() { long m = price; for (int i = 0; i < Math.min(histN, 24); i++) m = Math.min(m, hist(i)); return m; }
        /** i hours ago (0 = an hour ago) */
        public long hist(int i) { return i < histN ? hist[(histN - 1 - i) % HIST] : price; }
        /** change over 24 h, tenths of a percent */
        public int change24() { long then = hist(Math.min(23, Math.max(0, histN - 1))); return then <= 0 ? 0 : (int) ((price - then) * 1000 / then); }

        public ItemStack stack() {
            String[] p = key.split(":");
            Item it = Item.REGISTRY.getObject(new ResourceLocation(p[0], p[1]));
            return it == null ? ItemStack.EMPTY : new ItemStack(it, 1, p.length > 2 ? Integer.parseInt(p[2]) : 0);
        }

        public String name() { ItemStack s = stack(); return s.isEmpty() ? key : s.getDisplayName(); }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("k", key); t.setLong("b", base); t.setLong("p", price); t.setBoolean("s", seasonal);
            t.setDouble("su", supply); t.setDouble("de", demand); t.setLong("dl", delivered); t.setLong("bo", bought); t.setLong("v", volume);
            t.setTag("h", Company.longList(java.util.Arrays.stream(hist).boxed().collect(java.util.stream.Collectors.toList())));
            t.setInteger("hn", histN); t.setLong("l", last);
            return t;
        }

        static Price read(NBTTagCompound t) {
            Price p = new Price();
            p.key = t.getString("k"); p.base = t.getLong("b"); p.price = t.getLong("p"); p.seasonal = t.getBoolean("s");
            p.supply = t.getDouble("su"); p.demand = t.getDouble("de"); p.delivered = t.getLong("dl"); p.bought = t.getLong("bo"); p.volume = t.getLong("v");
            List<Long> h = Company.longs(t.getTagList("h", 4));
            for (int i = 0; i < Math.min(HIST, h.size()); i++) p.hist[i] = h.get(i);
            p.histN = t.getInteger("hn"); p.last = t.getLong("l");
            return p;
        }
    }

    private final Map<String, Price> prices = new LinkedHashMap<>();
    private long lastHour = -1;

    public MarketData() { super(NAME); seed(); }
    public MarketData(String n) { super(n); seed(); }

    private void seed() {
        for (Object[] b : BOARD) {
            Price p = new Price();
            p.key = (String) b[0]; p.base = (Long) b[1]; p.price = p.base; p.last = p.base; p.seasonal = (Boolean) b[2];
            prices.put(p.key, p);
        }
    }

    public static MarketData get(World w) {
        MapStorage s = w.getPerWorldStorage();
        MarketData d = (MarketData) s.getOrLoadData(MarketData.class, NAME);
        if (d == null) { d = new MarketData(); s.setData(NAME, d); }
        return d;
    }

    public Collection<Price> all() { return prices.values(); }
    public List<Price> list() { return new ArrayList<>(prices.values()); }

    public static String key(ItemStack s) {
        return s.isEmpty() || s.getItem().getRegistryName() == null ? "" : s.getItem().getRegistryName() + ":" + s.getMetadata();
    }

    /** the price entry for an item; items not on the board are listed the first time someone trades them */
    public Price of(ItemStack s) {
        String k = key(s);
        if (k.isEmpty()) return null;
        Price p = prices.get(k);
        if (p == null) {
            p = new Price();
            p.key = k; p.base = 25; p.price = 25; p.last = 25;
            prices.put(k, p);
            markDirty();
        }
        return p;
    }

    public Price get(String key) { return prices.get(key); }

    /** goods sold into the market or delivered to their buyer: more supply */
    public void sold(ItemStack s, int n) { Price p = of(s); if (p != null) { p.delivered += n; p.volume += n; markDirty(); } }
    /** goods bought out of the market: more demand */
    public void bought(ItemStack s, int n) { Price p = of(s); if (p != null) { p.bought += n; p.volume += n; markDirty(); } }

    /** the market index: average price relative to base, x100 */
    public int index() {
        double s = 0; int n = 0;
        for (Price p : prices.values()) { s += p.factor(); n++; }
        return n == 0 ? 100 : (int) Math.round(s * 100 / n);
    }

    // ---- the hourly move --------------------------------------------------------------------------------------------
    public void tick(World w) {
        long hour = w.getTotalWorldTime() / 1000;
        if (lastHour < 0) { lastHour = hour; markDirty(); return; }
        if (hour == lastHour) return;
        lastHour = hour;
        Map<String, double[]> sd = survey(w);
        long day = w.getTotalWorldTime() / CompanyData.DAY;
        for (Price p : prices.values()) {
            double[] x = sd.getOrDefault(p.key, new double[2]);
            p.supply = x[0] + p.delivered * 0.5 + p.supply * 0.3;
            p.demand = x[1] + p.bought * 0.5 + p.demand * 0.3;
            double target = p.base * clamp(Math.sqrt((p.demand + 48) / (p.supply + 48)), 0.4, 3.0);
            if (p.seasonal) target *= 1 + 0.25 * Math.cos(2 * Math.PI * (day % 8) / 8.0);   // cheap after the harvest (day 4 of 8), dear before it
            long step = Math.max(1, p.price * 5 / 100);
            p.last = p.price;
            p.price = Math.max(1, p.price + Math.max(-step, Math.min(step, Math.round(target - p.price))));
            p.price = Math.max(Math.max(1, p.base * 4 / 10), Math.min(p.base * 3, p.price));
            p.hist[p.histN % HIST] = p.last;
            p.histN++;
            if (p.histN >= HIST * 2) p.histN -= HIST;                       // keep the ring index small, same slot order
            p.delivered = 0; p.bought = 0;
            if (hour % 24 == 0) p.volume = 0;
        }
        markDirty();
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** {supply, demand} per commodity from every loaded industry and open contract */
    private static Map<String, double[]> survey(World w) {
        Map<String, double[]> out = new HashMap<>();
        for (TileEntity te : com.dogpound.railmap.industry.IndustryLink.all(w)) {      // every mod's plants (her list section 13)
            com.dogpound.railmap.industry.IndustryLink.Endpoint e = (com.dogpound.railmap.industry.IndustryLink.Endpoint) te;
            for (ItemStack s : e.makes())
                if (!s.isEmpty()) out.computeIfAbsent(key(s), x -> new double[2])[0] += s.getCount() + 16;      // ready to ship + capacity
            double scale = te instanceof TileIndustry t && t.kind() == IndustryKind.TOWN_MARKET ? Math.max(1, t.population() / 100.0) : 1;
            for (ItemStack s : e.wants())
                if (!s.isEmpty()) out.computeIfAbsent(key(s), x -> new double[2])[1] += s.getCount() * scale;
        }
        for (Contract c : ContractData.get(w).all())
            if (c.state == Contract.State.OPEN || c.state == Contract.State.ACCEPTED)
                out.computeIfAbsent(c.item, x -> new double[2])[1] += (c.qty - c.delivered) * 0.5;
        return out;
    }

    // ---- save ---------------------------------------------------------------------------------------------------------
    @Override
    public void readFromNBT(NBTTagCompound t) {
        NBTTagList l = t.getTagList("prices", 10);
        for (int i = 0; i < l.tagCount(); i++) { Price p = Price.read(l.getCompoundTagAt(i)); prices.put(p.key, p); }
        lastHour = t.hasKey("hour") ? t.getLong("hour") : -1;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (Price p : prices.values()) l.appendTag(p.write());
        t.setTag("prices", l);
        t.setLong("hour", lastHour);
        return t;
    }
}
