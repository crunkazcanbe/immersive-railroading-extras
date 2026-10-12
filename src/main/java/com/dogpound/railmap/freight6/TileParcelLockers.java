package com.dogpound.railmap.freight6;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.alarm.AlarmManager;
import com.dogpound.railmap.alarm.Recorder;
import com.dogpound.railmap.mail.ItemMail;
import com.dogpound.railmap.mail.MailData;
import com.dogpound.railmap.stn.StnUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Station Parcel Lockers (batch 6 #29): a bank of 24 steel compartments (12 small, 8 medium, 4 large) round a
 * touch-screen / keypad tower. Parcels arrive three ways:
 *   - the station's Sorting Office puts mail for people without a RealmCoin mailbox in a compartment (not the pickup
 *     shelf) and messages them a 6-digit code;
 *   - a courier holding a Pride Post parcel / letter presses DROP: it goes in a free compartment for its addressee;
 *   - players who REGISTER here get their waiting RealmCoin delivery-robot orders dropped into a compartment.
 * To collect: key the code on the keypad, OK - that door springs open and the compartment opens for you. Three wrong
 * codes lock the keypad out for a minute (tamper alarm). Compartments not collected in F6Config.lockerHoldDays show
 * UNCOLLECTED on the console. 12 V (no power: the doors stay locked, the codes are kept).
 */
public class TileParcelLockers extends com.dogpound.railmap.depot.DepotTile {
    public static final int N = 24;
    static final int[] SIZE = new int[N];
    static { for (int i = 0; i < N; i++) SIZE[i] = i < 12 ? 9 : i < 20 ? 18 : 27; }

    /** a compartment's booking */
    public static final class Box {
        public UUID owner;
        public String name = "", code = "", from = "";
        public int mailId;
        public long since;
        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            if (owner != null) { t.setLong("hi", owner.getMostSignificantBits()); t.setLong("lo", owner.getLeastSignificantBits()); }
            t.setString("n", name); t.setString("c", code); t.setString("f", from); t.setInteger("m", mailId); t.setLong("s", since);
            return t;
        }
        static Box read(NBTTagCompound t) {
            Box b = new Box();
            if (t.hasKey("hi")) b.owner = new UUID(t.getLong("hi"), t.getLong("lo"));
            b.name = t.getString("n"); b.code = t.getString("c"); b.from = t.getString("f"); b.mailId = t.getInteger("m"); b.since = t.getLong("s");
            return b;
        }
    }

    final Box[] boxes = new Box[N];
    final Set<UUID> registered = new HashSet<>();
    private String entry = "";
    private int wrong, open = -1;
    private long lockedUntil, openedAt;
    private String msg = "";
    private long delivered, collected;

    static String[] ids(String p) { String[] s = new String[N]; for (int i = 0; i < N; i++) s[i] = p + i; return s; }
    static String[] names() { String[] s = new String[N]; for (int i = 0; i < N; i++) s[i] = "Compartment " + (i + 1) + (i < 12 ? " (S)" : i < 20 ? " (M)" : " (L)"); return s; }

    public TileParcelLockers() { super(ids("comp"), names(), SIZE.clone()); }

    @Override public String wiredName() { return "Parcel lockers"; }
    @Override public String gearState() { return !powered() ? "NO 12 V - doors locked" : String.format(Locale.ROOT, "%d / %d compartments in use · %d delivered · %d collected", used(), N, delivered, collected); }

    public int used() { int n = 0; for (Box b : boxes) if (b != null) n++; return n; }
    public Box box(int i) { return boxes[i]; }
    public String station() { Map.Entry<Long, String> e = com.dogpound.railmap.stn.StnData.station(world, pos, 128); return e == null ? "" : e.getValue(); }
    long stationKey() { Map.Entry<Long, String> e = com.dogpound.railmap.stn.StnData.station(world, pos, 128); return e == null ? 0 : e.getKey(); }

    /** a free compartment big enough for `stacks` stacks (smallest first), -1 = none */
    int free(int stacks) { for (int i = 0; i < N; i++) if (boxes[i] == null && SIZE[i] >= stacks && empty(i)) return i; return -1; }
    boolean empty(int i) { InventoryBasic inv = store(i); for (int k = 0; k < inv.getSizeInventory(); k++) if (!inv.getStackInSlot(k).isEmpty()) return false; return true; }

    /** book a compartment for `who` with these items; returns the compartment or -1 */
    public int deposit(UUID who, String name, String from, List<ItemStack> items, int mailId) {
        int i = free(Math.max(1, items.size()));
        if (i < 0) return -1;
        InvWrapper h = new InvWrapper(store(i));
        for (ItemStack s : items) { ItemStack r = Cars.put(h, s.copy()); if (!r.isEmpty()) net.minecraft.inventory.InventoryHelper.spawnItemStack(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, r); }
        Box b = new Box();
        b.owner = who; b.name = name; b.from = from; b.mailId = mailId; b.since = world.getTotalWorldTime();
        Set<String> taken = new HashSet<>();
        for (Box x : boxes) if (x != null) taken.add(x.code);
        do b.code = String.format(Locale.ROOT, "%06d", world.rand.nextInt(1_000_000)); while (taken.contains(b.code));
        boxes[i] = b;
        delivered++;
        markDirty();
        String where = "Parcel locker " + (i + 1) + " at " + (station().isEmpty() ? pos.getX() + "," + pos.getZ() : station());
        EntityPlayer p = who == null || world.getMinecraftServer() == null ? null : world.getMinecraftServer().getPlayerList().getPlayerByUUID(who);
        if (p != null) p.sendMessage(new TextComponentString("§d📦 A parcel from " + from + " is waiting in " + where + " - your code is §f§l" + b.code));
        Recorder.event(world, "Parcel lockers: " + from + " parcel for " + name + " in compartment " + (i + 1));
        world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_ok"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.2f);
        return i;
    }

    /** the Sorting Office's hook: a letter / parcel for someone without a mailbox goes in the lockers of its station */
    public static boolean fromMail(World w, long station, MailData.Mail m) {
        if (!F6Config.mailToLockers || station == 0 || m.toPlayer == null || m.stack.isEmpty()) return false;
        for (TileParcelLockers l : StnUtil.loaded(w, TileParcelLockers.class)) {
            if (l.stationKey() != station || !l.powered()) continue;
            int i = l.deposit(m.toPlayer, com.dogpound.railmap.mail.Mail.addressee(m.to), "Pride Post (" + m.fromName + ")", Collections.singletonList(m.stack), m.id);
            if (i < 0) continue;
            m.stack = ItemStack.EMPTY; m.state = MailData.PICKUP; m.office = l.getPos().toLong();
            MailData.get(w).track(m, w.getTotalWorldTime(), "in parcel locker " + (i + 1) + " at " + l.station() + " (code sent)");
            return true;
        }
        return false;
    }

    @Override
    protected String press(String id, EntityPlayer who) {
        long now = world.getTotalWorldTime();
        if (id.startsWith("k") && id.length() == 2 && Character.isDigit(id.charAt(1))) {
            if (now < lockedUntil) return "§cKeypad locked out - wait";
            if (entry.length() < 6) entry += id.charAt(1);
            msg = "";
            world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.keypad"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1f);
            return "";
        }
        if (id.startsWith("door")) {
            int i = Integer.parseInt(id.substring(4));
            if (i == open && who != null && now - openedAt < 400) { who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + i, world, pos.getX(), pos.getY(), pos.getZ()); return ""; }
            return boxes[i] == null ? "§7Compartment " + (i + 1) + " is free" : "§7Compartment " + (i + 1) + " is locked - key your code on the keypad";
        }
        switch (id) {
            case "clr" -> { entry = ""; msg = ""; return "§7Cleared"; }
            case "ok" -> {
                if (now < lockedUntil) return "§cKeypad locked out - wait";
                String c = entry;
                entry = "";
                for (int i = 0; i < N; i++) if (boxes[i] != null && boxes[i].code.equals(c)) {
                    open = i; openedAt = now; wrong = 0; msg = "OPEN " + (i + 1);
                    world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.door_open"), net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1.5f);
                    if (who != null) who.openGui(RailMap.instance, RailMap.GUI_OFFICE_STORE + i, world, pos.getX(), pos.getY(), pos.getZ());
                    return "§aCompartment " + (i + 1) + " open - from " + boxes[i].from;
                }
                wrong++;
                msg = "WRONG CODE";
                world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("ui.beep_error"), net.minecraft.util.SoundCategory.BLOCKS, 0.6f, 1f);
                if (wrong >= 3) {
                    lockedUntil = now + 1200; wrong = 0; msg = "LOCKED OUT";
                    AlarmManager.get(world).raise(world, "locker:" + pos.toLong(), AlarmManager.Cat.GENERAL, AlarmManager.Sev.MINOR, "Parcel lockers at " + (station().isEmpty() ? pos.getX() + "," + pos.getZ() : station()) + ": 3 WRONG CODES - keypad locked (tamper)", pos, false);
                }
                return "§cWrong code";
            }
            case "drop" -> {
                if (who == null) return "§7A courier drops parcels in person";
                ItemStack h = who.getHeldItemMainhand();
                if (!ItemMail.isMail(h)) return "§7Hold a Pride Post parcel or letter (addressed) to drop it off";
                String to = com.dogpound.railmap.mail.Mail.addressee(ItemMail.to(h));
                UUID u = com.dogpound.railmap.mail.Mail.playerId(world, to);
                if (u == null) return "§cNo player called " + to;
                int i = deposit(u, to, who.getName(), Collections.singletonList(h.copy()), ItemMail.mailId(h));
                if (i < 0) return "§cNo free compartment";
                who.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, ItemStack.EMPTY);
                return "§aDropped in compartment " + (i + 1) + " for " + to + " (they get the code)";
            }
            case "register" -> {
                if (who == null) return "§7Register in person";
                if (registered.remove(who.getUniqueID())) { markDirty(); return "§7Unregistered: your robot orders no longer come here"; }
                registered.add(who.getUniqueID()); markDirty();
                return "§aRegistered: RealmCoin delivery-robot orders waiting for you are dropped into these lockers" + (Robots.ok() ? "" : " §7(RealmCoin not installed)");
            }
            case "mine" -> {
                if (who == null) return "";
                StringBuilder s = new StringBuilder();
                for (int i = 0; i < N; i++) if (boxes[i] != null && who.getUniqueID().equals(boxes[i].owner)) s.append(" ").append(i + 1);
                return s.length() == 0 ? "§7Nothing waiting for you here" : "§aWaiting for you in compartment" + s + " - the code is in your chat (or /irextras lockers codes)";
            }
            default -> { return null; }
        }
    }

    /** the compartment just emptied by its owner: booking released */
    void sweep() {
        for (int i = 0; i < N; i++) if (boxes[i] != null && empty(i)) {
            Box b = boxes[i];
            boxes[i] = null; collected++;
            if (b.mailId > 0) { MailData d = MailData.get(world); MailData.Mail m = d.mail.get(b.mailId); if (m != null) { m.state = MailData.COLLECTED; d.track(m, world.getTotalWorldTime(), "collected from parcel locker " + (i + 1)); d.deliveredTotal++; } }
            Recorder.event(world, "Parcel lockers: compartment " + (i + 1) + " collected by " + b.name);
            markDirty();
        }
    }

    @Override
    protected void work() {
        long now = world.getTotalWorldTime();
        if (open >= 0 && now - openedAt > 400) { open = -1; msg = ""; world.playSound(null, pos, com.dogpound.railmap.sound.PackSounds.ev("machine.door_close"), net.minecraft.util.SoundCategory.BLOCKS, 0.5f, 1.5f); }
        if (age % 20 != 0) return;
        sweep();
        if (age % 600 == 0 && F6Config.robotOrdersToLockers && powered()) Robots.pull(this);
        int s = 0, m = 0, l = 0, late = 0;
        for (int i = 0; i < N; i++) {
            if (boxes[i] == null) { if (i < 12) s++; else if (i < 20) m++; else l++; }
            else if (now - boxes[i].since > F6Config.lockerHoldDays * 24000L) late++;
            put("c" + i, boxes[i] == null ? 0 : i == open ? 2 : 1);
        }
        put("pwr", powered()); put("lockl", now < lockedUntil); put("latel", late > 0); put("openl", open >= 0); put("full", used() >= N);
        text("entry", entry.isEmpty() ? "------" : "******".substring(0, entry.length()) + "------".substring(entry.length()));
        text("line1", now < lockedUntil ? "LOCKED OUT" : msg.isEmpty() ? "ENTER CODE" : msg);
        text("line2", "FREE S" + s + " M" + m + " L" + l);
        text("line3", cut(station().toUpperCase(Locale.ROOT), 18));
        text("used", String.valueOf(used()));
    }

    /** the compartment doors open only by code (DepotTile's ZONE ids for storages are not on the model) */
    @Override protected boolean keepTextUnpowered() { return false; }

    @Override protected void saveExtra(NBTTagCompound t) {
        NBTTagList l = new NBTTagList();
        for (int i = 0; i < N; i++) if (boxes[i] != null) { NBTTagCompound c = boxes[i].write(); c.setInteger("i", i); l.appendTag(c); }
        t.setTag("boxes", l);
        NBTTagList r = new NBTTagList();
        for (UUID u : registered) { NBTTagCompound c = new NBTTagCompound(); c.setLong("hi", u.getMostSignificantBits()); c.setLong("lo", u.getLeastSignificantBits()); r.appendTag(c); }
        t.setTag("reg", r);
        t.setLong("lock", lockedUntil); t.setLong("del", delivered); t.setLong("col", collected);
    }
    @Override protected void loadExtra(NBTTagCompound t) {
        Arrays.fill(boxes, null);
        NBTTagList l = t.getTagList("boxes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) { NBTTagCompound c = l.getCompoundTagAt(i); int k = c.getInteger("i"); if (k >= 0 && k < N) boxes[k] = Box.read(c); }
        registered.clear();
        NBTTagList r = t.getTagList("reg", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < r.tagCount(); i++) registered.add(new UUID(r.getCompoundTagAt(i).getLong("hi"), r.getCompoundTagAt(i).getLong("lo")));
        lockedUntil = t.getLong("lock"); delivered = t.getLong("del"); collected = t.getLong("col");
    }

    /** the client must never learn the codes: strip them from the sync tag */
    @Override public NBTTagCompound getUpdateTag() {
        NBTTagCompound t = super.getUpdateTag();
        NBTTagList l = t.getTagList("boxes", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < l.tagCount(); i++) l.getCompoundTagAt(i).setString("c", "");
        t.removeTag("inv");
        return t;
    }

    /** RealmCoin's waiting robot orders, by reflection (IR Extras never needs RealmCoin) */
    static final class Robots {
        private static boolean tried;
        private static Method get, take;
        private static Field from, items;

        static boolean ok() {
            if (!tried) {
                tried = true;
                if (Loader.isModLoaded("realmcoin")) try {
                    Class<?> dd = Class.forName("com.dogpound.realmcoin.delivery.DeliveryData");
                    Class<?> pe = Class.forName("com.dogpound.realmcoin.delivery.DeliveryData$Pending");
                    get = dd.getMethod("get", World.class); take = dd.getMethod("takePending", UUID.class);
                    from = pe.getField("from"); items = pe.getField("items");
                } catch (Throwable t) { get = null; RailMap.LOG.info("[IR Extras] parcel lockers: RealmCoin delivery not found ({})", t.toString()); }
            }
            return get != null;
        }

        @SuppressWarnings("unchecked")
        static void pull(TileParcelLockers l) {
            if (!ok() || l.registered.isEmpty()) return;
            try {
                World w0 = l.world.getMinecraftServer() == null ? l.world : l.world.getMinecraftServer().getWorld(0);
                Object data = get.invoke(null, w0);
                for (UUID u : new ArrayList<>(l.registered)) {
                    if (l.used() > N - 3) return;                                     // keep room: the orders wait at the robot depot
                    List<Object> orders = (List<Object>) take.invoke(data, u);
                    for (Object o : orders) {
                        List<ItemStack> its = (List<ItemStack>) items.get(o);
                        String name = l.world.getMinecraftServer() == null ? "" : String.valueOf(l.world.getMinecraftServer().getPlayerProfileCache().getProfileByUUID(u) == null ? "" : l.world.getMinecraftServer().getPlayerProfileCache().getProfileByUUID(u).getName());
                        for (int k = 0; k < its.size(); k += 27) {
                            List<ItemStack> part = its.subList(k, Math.min(its.size(), k + 27));
                            if (l.deposit(u, name, "delivery robot (" + from.get(o) + ")", part, 0) < 0)
                                for (ItemStack s : part) net.minecraft.inventory.InventoryHelper.spawnItemStack(l.world, l.pos.getX() + 0.5, l.pos.getY() + 1, l.pos.getZ() + 0.5, s);
                        }
                    }
                }
            } catch (Throwable t) { RailMap.LOG.warn("[IR Extras] parcel lockers robot pull failed: {}", t.toString()); }
        }
    }
}
