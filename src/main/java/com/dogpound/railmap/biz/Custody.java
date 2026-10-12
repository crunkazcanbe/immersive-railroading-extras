package com.dogpound.railmap.biz;

import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.freight.CargoRules;
import com.dogpound.railmap.freight.Contract;
import com.dogpound.railmap.item.ItemContainer;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Cargo quality, contamination and chain of custody (her "next 48" #17), plus the cross-mode tracking of #15.
 * Every shipping container (by its BIC number) and every freight-contract consignment has a record:
 * <ul>
 *   <li><b>handling history</b>: every hand it passed through - ship, quay, tractor, gate, crane, rail car, strip - with
 *   when, where and who;</li>
 *   <li><b>seal</b>: SEAL/DEST fits a numbered seal; stuffing or stripping a sealed box breaks it (and records by whom);</li>
 *   <li><b>cleanliness</b> of the box (and of each rail car): dirty bulk (coal, ore, scrap) leaves it dirty; food or grain
 *   stuffed into a dirty box is CONTAMINATED; WASH cleans it;</li>
 *   <li><b>moisture</b>: grain and flour sweat in a box standing in a wet yard (rainfall of the biome, rain now) - over 14 %
 *   it is spoiled; <b>temperature</b>: food more than 3 days in transit in a hot biome spoils;</li>
 *   <li><b>segregation</b>: dangerous goods (explosives, acids, radioactives) never travel with food.</li>
 * </ul>
 * When the box is stripped (or the consignment delivered) the consignee inspects it. Anything wrong opens an
 * INVESTIGATION that walks the custody chain to find who was holding the box when it went wrong (that party's
 * insurance pays a cargo claim, #33). With rejectBadCargo on, the consignee refuses it: the goods stay in the box.
 */
public final class Custody implements BizData.Part {
    public static final class Rec {
        public String id = "";                    // BIC number, or "K<contract id>"
        public String owner = "", consignee = "";
        public int seal;                          // 0 = unsealed
        public boolean sealBroken;
        public String sealBrokenBy = "";
        public double clean = 100, moisture = 8, tempC = 15;
        public boolean food, haz, grain, dirty;
        public long stuffed, sealed;
        public String status = "OK";              // OK, FLAGGED, REJECTED, CLEARED
        public final List<String> events = new ArrayList<>();   // "tick|mode|place|who|text"
        public String lastHolder = "";
        public long dwellSince;
        // batch 3 #21-23: where it was last seen, cold chain and contamination
        public String lastMode = "", lastPlace = "";
        public long lastPos;
        public double coldQ = 100;                // perishable quality while reefer-carried (%)
        public boolean reefer;                    // plugged in as a reefer at least once with this load
        public String contam = "";                // contamination finding (food after hazardous goods without a STEAM certificate)
        public long cert;                         // last STEAM wash certificate (tick), 0 = none
        public String lastLoad = "";              // CLEAN, GENERAL, FOOD, BULK, HAZ
    }

    public static final class Case {
        public int id;
        public String rec = "", findings = "", liable = "", state = "OPEN";   // OPEN, CLOSED
        public long opened;
        public long value;                       // cents of goods affected
        public boolean claimed;
    }

    public final Map<String, Rec> recs = new LinkedHashMap<>();
    public final List<Case> cases = new ArrayList<>();
    /** rail car -> cleanliness 0-100 */
    public final Map<UUID, Double> cars = new HashMap<>();
    /** rail car -> class of its last load (CLEAN GENERAL FOOD BULK HAZ), and when it got a STEAM wash certificate (batch 3 #23) */
    public final Map<UUID, String> carLast = new HashMap<>();
    public final Map<UUID, Long> carCert = new HashMap<>();
    private int nextCase = 1, nextSeal = 100001;

    @Override public String key() { return "custody"; }

    public Rec rec(String id) { return recs.computeIfAbsent(id, k -> { Rec r = new Rec(); r.id = k; return r; }); }
    public Rec box(ItemStack box) { Rec r = rec(ItemContainer.id(box)); if (r.owner.isEmpty()) r.owner = ItemContainer.ownerName(box); return r; }

    /** batch 3 #22: a container by its number - exact, or the unique one starting with it (spaces ignored) */
    public Rec where(String q) {
        String k = q == null ? "" : q.replace(" ", "").toUpperCase(Locale.ROOT);
        if (k.isEmpty()) return null;
        Rec r = recs.get(k);
        if (r != null) return r;
        Rec hit = null;
        for (Rec x : recs.values()) if (x.id.startsWith(k)) { if (hit != null) return null; hit = x; }
        return hit;
    }

    /** ISO 6346: 3-letter owner, category U/J/Z, 6-digit serial, check digit */
    public static boolean validId(String id) {
        if (id == null || id.length() != 11) return false;
        for (int i = 0; i < 4; i++) if (id.charAt(i) < 'A' || id.charAt(i) > 'Z') return false;
        if ("UJZ".indexOf(id.charAt(3)) < 0) return false;
        for (int i = 4; i < 11; i++) if (!Character.isDigit(id.charAt(i))) return false;
        return ItemContainer.check(id) == id.charAt(10) - '0';
    }

    /** the class of a load (for the next one's contamination rule) */
    public static String loadClass(boolean food, boolean dirty, boolean haz) { return haz ? "HAZ" : dirty ? "BULK" : food ? "FOOD" : "GENERAL"; }

    /** a STEAM wash certificate still valid at `now` */
    public static boolean certValid(long cert, long now) { return cert > 0 && now - cert <= (long) com.dogpound.railmap.freight.CargoConfig.certDays * BizData.DAY; }

    /** food going into a car / box whose last load was hazardous needs a valid certificate: the finding, or "" */
    public static String contamRule(String last, long cert, long now) {
        if (!com.dogpound.railmap.freight.CargoConfig.contamination || !"HAZ".equals(last) || certValid(cert, now)) return "";
        return "food loaded after hazardous goods without a STEAM wash certificate";
    }

    /** add a handling event (and keep the newest 40) */
    public void event(World w, Rec r, String mode, BlockPos at, String who, String text) {
        r.events.add(w.getTotalWorldTime() + "|" + mode + "|" + (at == null ? "" : Towns.nearestStation(w, at, at.getX() + " " + at.getZ())) + "|" + who + "|" + text);
        while (r.events.size() > 40) r.events.remove(0);
        if (!who.isEmpty()) r.lastHolder = who;
        r.lastMode = mode;
        r.lastPlace = at == null ? r.lastPlace : Towns.nearestStation(w, at, at.getX() + " " + at.getZ());
        if (at != null) r.lastPos = at.toLong();
        r.dwellSince = w.getTotalWorldTime();
        if (recs.size() > 600) recs.keySet().removeIf(k -> recs.size() > 500 && recs.get(k).status.equals("OK") && recs.get(k).events.size() < 2);
    }

    public void event(World w, ItemStack box, String mode, BlockPos at, String who, String text) { if (BizConfig.enabled && ItemContainer.is(box)) event(w, box(box), mode, at, who, text); }

    // ---- what goods are -----------------------------------------------------------------------------------------------
    public static boolean isFood(ItemStack s) {
        if (s.getItem() instanceof ItemFood) return true;
        return CargoRules.cargo(s) == CargoRules.Cargo.GRAIN;
    }

    public static boolean isHaz(ItemStack s) {
        String id = s.getItem().getRegistryName() == null ? "" : s.getItem().getRegistryName().getPath();
        return id.contains("tnt") || id.contains("gunpowder") || id.contains("explosive") || id.contains("dynamite") || id.contains("acid") || id.contains("uranium")
                || id.contains("plutonium") || id.contains("nitro") || id.contains("sulfur") || id.contains("chlorine") || id.contains("radioactive") || id.contains("firework");
    }

    public static boolean isDirty(ItemStack s) { return CargoRules.cargo(s) == CargoRules.Cargo.BULK; }

    /** the box was stuffed with `goods` by `who` */
    public void stuffed(World w, ItemStack box, List<ItemStack> goods, BlockPos at, String who) {
        if (!BizConfig.enabled) return;
        Rec r = box(box);
        boolean food = false, haz = false, grain = false, dirty = false;
        for (ItemStack s : goods) { food |= isFood(s); haz |= isHaz(s); grain |= CargoRules.cargo(s) == CargoRules.Cargo.GRAIN; dirty |= isDirty(s); }
        r.food |= food; r.haz |= haz; r.grain |= grain; r.dirty |= dirty;
        if (r.stuffed == 0) r.stuffed = w.getTotalWorldTime();
        if (r.seal > 0 && !r.sealBroken) { r.sealBroken = true; r.sealBrokenBy = who; event(w, r, "SEAL", at, who, "SEAL " + r.seal + " BROKEN - box opened to stuff"); }
        String note = "";
        if (food && r.clean < 60) note += " CONTAMINATION RISK: food into a dirty box (clean " + Math.round(r.clean) + "%);";
        if (food && r.contam.isEmpty()) { r.contam = contamRule(r.lastLoad, r.cert, w.getTotalWorldTime()); if (!r.contam.isEmpty()) note += " " + r.contam.toUpperCase(Locale.ROOT) + ";"; }
        if (r.food && r.haz) note += " SEGREGATION BREACH: dangerous goods with food;";
        event(w, r, "STUFF", at, who, "stuffed " + ItemContainer.manifest(box) + note);
    }

    public void stripped(World w, ItemStack box, BlockPos at, String who) {
        if (!BizConfig.enabled) return;
        Rec r = box(box);
        if (r.dirty) r.clean = Math.max(0, r.clean - 35);
        if (r.seal > 0 && !r.sealBroken) { r.sealBroken = true; r.sealBrokenBy = ""; }
        event(w, r, "STRIP", at, who, "stripped" + (r.dirty ? " (dirty bulk - box needs washing)" : ""));
        r.lastLoad = loadClass(r.food, r.dirty, r.haz);
        r.food = r.haz = r.grain = r.dirty = false; r.moisture = 8; r.stuffed = 0; r.seal = 0; r.sealBroken = false; r.status = "OK";
        r.coldQ = 100; r.reefer = false; r.contam = "";
    }

    public int seal(World w, ItemStack box, BlockPos at, String who) {
        Rec r = box(box);
        r.seal = nextSeal++; r.sealBroken = false; r.sealed = w.getTotalWorldTime();
        event(w, r, "SEAL", at, who, "sealed, seal no. " + r.seal + " for " + ItemContainer.destName(box));
        r.consignee = ItemContainer.destName(box);
        return r.seal;
    }

    public void wash(World w, ItemStack box, BlockPos at, String who) { Rec r = box(box); r.clean = 100; event(w, r, "WASH", at, who, "washed and inspected clean"); }

    /** a washout plant program on a box (batch 3 #23): cleanliness reached, STEAM certifies it for food */
    public void wash(World w, ItemStack box, BlockPos at, String who, double clean, boolean steam) {
        Rec r = box(box);
        r.clean = Math.max(r.clean, clean);
        if (steam) { r.cert = w.getTotalWorldTime(); r.lastLoad = "CLEAN"; }
        else if ("BULK".equals(r.lastLoad) && clean >= 90) r.lastLoad = "CLEAN";
        event(w, r, "WASH", at, who, (steam ? "STEAM sanitised - wash certificate issued" : "washed") + " (" + Math.round(r.clean) + "% clean)");
    }

    /** a box stood in a yard at `at` for `ticks` (terminals and the port call this for what they hold) */
    public void exposure(World w, ItemStack box, BlockPos at, long ticks) {
        if (!BizConfig.enabled || !ItemContainer.is(box)) return;
        Rec r = recs.get(ItemContainer.id(box));
        if (r == null || r.stuffed == 0) return;
        net.minecraft.world.biome.Biome b = w.getBiome(at);
        double perDay = (b.getRainfall() * 2.0 + (w.isRaining() && b.canRain() ? 3.0 : 0)) * (r.grain ? 1 : 0.3);
        r.moisture = Math.min(40, r.moisture + perDay * ticks / BizData.DAY);
        double k = Math.min(1, ticks / 2400.0);
        r.tempC = r.tempC * (1 - k) + (b.getTemperature(at) * 30) * k;
    }

    // ---- inspection at the consignee ------------------------------------------------------------------------------------
    /** the findings for a box (empty = nothing wrong) */
    public List<String> inspect(World w, Rec r) {
        List<String> f = new ArrayList<>();
        if (r.sealBroken) f.add("seal " + r.seal + " broken" + (r.sealBrokenBy.isEmpty() ? "" : " by " + r.sealBrokenBy));
        if (r.food && r.clean < 60) f.add("food contaminated by a dirty box (" + Math.round(r.clean) + "% clean)");
        if (r.food && r.haz) f.add("dangerous goods stowed with food");
        if (r.grain && r.moisture > 14) f.add(String.format(Locale.ROOT, "grain wet: %.1f%% moisture", r.moisture));
        if (r.reefer && r.coldQ < com.dogpound.railmap.freight.CargoConfig.coldChainFindingPct) f.add(String.format(Locale.ROOT, "cold chain broken: perishables at %.0f%% quality", r.coldQ));
        if (!r.contam.isEmpty()) f.add(r.contam);
        if (r.food && !r.reefer && r.stuffed > 0 && w.getTotalWorldTime() - r.stuffed > 3 * BizData.DAY && r.tempC > 25) f.add(String.format(Locale.ROOT, "perishables spoiled: %.0f C, %d days", r.tempC, (w.getTotalWorldTime() - r.stuffed) / BizData.DAY));
        return f;
    }

    /** the consignee strips a box: null = accept; else why it is refused (rejectBadCargo) - an investigation opens either way */
    public String delivery(World w, ItemStack box, BlockPos at, String who, long valueCents) {
        if (!BizConfig.enabled || !ItemContainer.is(box)) return null;
        Rec r = box(box);
        List<String> f = inspect(w, r);
        if (f.isEmpty()) { event(w, r, "INSPECT", at, who, "inspected at delivery - clean, dry, sealed correctly"); return null; }
        open(w, r, String.join("; ", f), valueCents, at);
        if (BizConfig.rejectBadCargo) { r.status = "REJECTED"; event(w, r, "REJECT", at, who, "REJECTED by the consignee: " + String.join("; ", f)); return String.join("; ", f); }
        r.status = "FLAGGED";
        event(w, r, "INSPECT", at, who, "accepted under protest: " + String.join("; ", f));
        return null;
    }

    private void open(World w, Rec r, String findings, long value, BlockPos at) {
        Case c = new Case();
        c.id = nextCase++; c.rec = r.id; c.findings = findings; c.opened = w.getTotalWorldTime(); c.value = value;
        c.liable = liable(r, findings);
        cases.add(c);
        while (cases.size() > 120) cases.remove(0);
        AlarmManager.get(w).raise(w, "cargo:" + r.id, AlarmManager.Cat.FREIGHT, AlarmManager.Sev.MINOR, "Cargo investigation #" + c.id + " " + r.id + ": " + findings, at, false);
        Recorder.event(w, "Cargo investigation #" + c.id + " opened on " + r.id + ": " + findings + " - liable: " + c.liable);
    }

    /** walk the custody chain: who held the box when it went wrong */
    static String liable(Rec r, String findings) {
        if (findings.contains("seal") && !r.sealBrokenBy.isEmpty()) return r.sealBrokenBy;
        String stuffer = "", longest = "";
        long best = -1, prevT = -1;
        String prevWho = "";
        for (String e : r.events) {
            String[] p = e.split("\\|", 5);
            if (p.length < 5) continue;
            long t = Long.parseLong(p[0]);
            if (p[1].equals("STUFF")) stuffer = p[3];
            if (prevT >= 0 && t - prevT > best && !prevWho.isEmpty()) { best = t - prevT; longest = prevWho; }
            prevT = t; prevWho = p[3];
        }
        if (findings.contains("wet") || findings.contains("spoiled")) return longest.isEmpty() ? r.owner : longest;
        return stuffer.isEmpty() ? r.owner : stuffer;
    }

    // ---- freight contracts (wagons) -------------------------------------------------------------------------------------
    /** cars get dirty unloading bulk, food loaded into a dirty car is contaminated; true = the consignee refuses it */
    public boolean contractMoved(World w, Contract k, boolean loading, BlockPos yard, UUID car, List<ItemStack> moved) {
        if (!BizConfig.enabled) return false;
        Rec r = rec("K" + k.id);
        if (r.owner.isEmpty()) { r.owner = k.byName; r.consignee = k.toName; }
        boolean food = false, dirty = false, haz = false;
        for (ItemStack s : moved) if (k.matches(s)) { food |= isFood(s); dirty |= isDirty(s); haz |= isHaz(s); }
        double clean = cars.getOrDefault(car, 100.0);
        String c8 = car.toString().substring(0, 8);
        if (loading) {
            r.food |= food; r.haz |= haz;
            if (food && r.contam.isEmpty()) r.contam = contamRule(carLast.get(car), carCert.getOrDefault(car, 0L), w.getTotalWorldTime());
            if (food) com.dogpound.railmap.freight.Reefer.loaded(w, car);
            if (food && clean < 60) { r.clean = Math.min(r.clean, clean); }
            event(w, r, "RAIL LOAD", yard, k.byName, "loaded on car " + c8 + (food && clean < 60 ? " - CAR DIRTY (" + Math.round(clean) + "%)" : ""));
            return false;
        }
        if (dirty) cars.put(car, Math.max(0, clean - 30));
        carLast.put(car, loadClass(food, dirty, haz));
        double q = com.dogpound.railmap.freight.Reefer.quality(w, car);
        if (q >= 0 && r.food) { r.reefer = true; r.coldQ = Math.min(r.coldQ, q); }
        List<String> f = inspect(w, r);
        if (f.isEmpty()) { event(w, r, "RAIL UNLOAD", yard, k.byName, "unloaded from car " + c8 + " - accepted"); return false; }
        if (!r.status.equals("FLAGGED") && !r.status.equals("REJECTED")) open(w, r, String.join("; ", f), k.pay, yard);
        if (BizConfig.rejectBadCargo) { r.status = "REJECTED"; event(w, r, "REJECT", yard, k.byName, "REFUSED at " + k.toName + ": " + String.join("; ", f)); return true; }
        r.status = "FLAGGED";
        event(w, r, "RAIL UNLOAD", yard, k.byName, "accepted under protest: " + String.join("; ", f));
        return false;
    }

    public String washCar(UUID car) { cars.put(car, 100.0); carLast.put(car, "CLEAN"); return "§aCar " + car.toString().substring(0, 8) + " washed out"; }

    /** a washout plant program on a rail car (batch 3 #23) */
    public void washCar(World w, UUID car, double clean, boolean steam) {
        cars.put(car, Math.max(cars.getOrDefault(car, 100.0), clean));
        if (steam) { carCert.put(car, w.getTotalWorldTime()); carLast.put(car, "CLEAN"); }
        else if (!"HAZ".equals(carLast.get(car)) && clean >= 90) carLast.put(car, "CLEAN");
    }

    /** investigations close after a day: the liable party's insurer pays the cargo claim */
    @Override
    public void daily(World w, BizData d, long day) {
        for (Case c : cases) {
            if (!c.state.equals("OPEN") || w.getTotalWorldTime() - c.opened < BizData.DAY) continue;
            c.state = "CLOSED";
            Rec r = recs.get(c.rec);
            if (r != null && r.status.equals("FLAGGED")) r.status = "CLEARED";
            if (!c.claimed) { c.claimed = true; d.insurance.cargoClaim(w, d, c); }
        }
    }

    // ---- save -------------------------------------------------------------------------------------------------------
    @Override
    public void read(NBTTagCompound t) {
        recs.clear(); cases.clear(); cars.clear();
        NBTTagList l = t.getTagList("r", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Rec r = new Rec();
            r.id = c.getString("id"); r.owner = c.getString("o"); r.consignee = c.getString("cn"); r.seal = c.getInteger("seal"); r.sealBroken = c.getBoolean("sb");
            r.sealBrokenBy = c.getString("sbb"); r.clean = c.getDouble("cl"); r.moisture = c.getDouble("mo"); r.tempC = c.getDouble("tc");
            r.food = c.getBoolean("fd"); r.haz = c.getBoolean("hz"); r.grain = c.getBoolean("gr"); r.dirty = c.getBoolean("dt");
            r.stuffed = c.getLong("su"); r.sealed = c.getLong("se"); r.status = c.getString("st"); r.lastHolder = c.getString("lh"); r.dwellSince = c.getLong("dw");
            r.lastMode = c.getString("lm"); r.lastPlace = c.getString("lp"); r.lastPos = c.getLong("lpos"); r.coldQ = c.hasKey("cq") ? c.getDouble("cq") : 100;
            r.reefer = c.getBoolean("rf"); r.contam = c.getString("ct"); r.cert = c.getLong("cert"); r.lastLoad = c.getString("ll");
            NBTTagList ev = c.getTagList("ev", 8);
            for (int k = 0; k < ev.tagCount(); k++) r.events.add(ev.getStringTagAt(k));
            recs.put(r.id, r);
        }
        l = t.getTagList("cases", 10);
        for (int i = 0; i < l.tagCount(); i++) {
            NBTTagCompound c = l.getCompoundTagAt(i);
            Case k = new Case();
            k.id = c.getInteger("id"); k.rec = c.getString("r"); k.findings = c.getString("f"); k.liable = c.getString("l"); k.state = c.getString("s");
            k.opened = c.getLong("o"); k.value = c.getLong("v"); k.claimed = c.getBoolean("c");
            cases.add(k);
        }
        NBTTagCompound cs = t.getCompoundTag("cars");
        for (String k : cs.getKeySet()) try { cars.put(UUID.fromString(k), cs.getDouble(k)); } catch (IllegalArgumentException ignored) { }
        carLast.clear(); carCert.clear();
        NBTTagCompound cl = t.getCompoundTag("carLast"), cc = t.getCompoundTag("carCert");
        for (String k : cl.getKeySet()) try { carLast.put(UUID.fromString(k), cl.getString(k)); } catch (IllegalArgumentException ignored) { }
        for (String k : cc.getKeySet()) try { carCert.put(UUID.fromString(k), cc.getLong(k)); } catch (IllegalArgumentException ignored) { }
        nextCase = Math.max(1, t.getInteger("nc"));
        nextSeal = Math.max(100001, t.getInteger("ns"));
    }

    @Override
    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        NBTTagList l = new NBTTagList();
        for (Rec r : recs.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("id", r.id); c.setString("o", r.owner); c.setString("cn", r.consignee); c.setInteger("seal", r.seal); c.setBoolean("sb", r.sealBroken);
            c.setString("sbb", r.sealBrokenBy); c.setDouble("cl", r.clean); c.setDouble("mo", r.moisture); c.setDouble("tc", r.tempC);
            c.setBoolean("fd", r.food); c.setBoolean("hz", r.haz); c.setBoolean("gr", r.grain); c.setBoolean("dt", r.dirty);
            c.setLong("su", r.stuffed); c.setLong("se", r.sealed); c.setString("st", r.status); c.setString("lh", r.lastHolder); c.setLong("dw", r.dwellSince);
            c.setString("lm", r.lastMode); c.setString("lp", r.lastPlace); c.setLong("lpos", r.lastPos); c.setDouble("cq", r.coldQ);
            c.setBoolean("rf", r.reefer); c.setString("ct", r.contam); c.setLong("cert", r.cert); c.setString("ll", r.lastLoad);
            NBTTagList ev = new NBTTagList();
            for (String s : r.events) ev.appendTag(new NBTTagString(s));
            c.setTag("ev", ev);
            l.appendTag(c);
        }
        t.setTag("r", l);
        l = new NBTTagList();
        for (Case k : cases) {
            NBTTagCompound c = new NBTTagCompound();
            c.setInteger("id", k.id); c.setString("r", k.rec); c.setString("f", k.findings); c.setString("l", k.liable); c.setString("s", k.state);
            c.setLong("o", k.opened); c.setLong("v", k.value); c.setBoolean("c", k.claimed);
            l.appendTag(c);
        }
        t.setTag("cases", l);
        NBTTagCompound cs = new NBTTagCompound();
        for (Map.Entry<UUID, Double> e : cars.entrySet()) cs.setDouble(e.getKey().toString(), e.getValue());
        t.setTag("cars", cs);
        NBTTagCompound cl = new NBTTagCompound(), cc = new NBTTagCompound();
        for (Map.Entry<UUID, String> e : carLast.entrySet()) cl.setString(e.getKey().toString(), e.getValue());
        for (Map.Entry<UUID, Long> e : carCert.entrySet()) cc.setLong(e.getKey().toString(), e.getValue());
        t.setTag("carLast", cl); t.setTag("carCert", cc);
        t.setInteger("nc", nextCase); t.setInteger("ns", nextSeal);
        return t;
    }
}
