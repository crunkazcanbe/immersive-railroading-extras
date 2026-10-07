package com.dogpound.railmap.program;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/**
 * One line of a signal-box program: WHEN condition [AND condition] THEN action [ELSE action].
 * Plain cards, no code: every field is a choice, a number or a place picked on the map.
 */
public final class Rule {
    public enum Cond {
        ALWAYS("Always"), CHANNEL_ON("Channel is ON"), CHANNEL_OFF("Channel is OFF"),
        OCCUPIED("Track circuit occupied"), CLEAR("Track circuit clear"),
        TRAIN_NEAR("A train is near"), TRAIN_STOPPED("A train is stopped near"),
        SIGNAL_RED("Signal shows Stop"), SIGNAL_PROCEED("Signal shows a proceed"),
        SWITCH_STRAIGHT("Switch is Straight"), SWITCH_TURN("Switch is Turn"),
        REDSTONE("Redstone is powered at"), DAYTIME("It is daytime"), NIGHT("It is night"),
        ROUTE_SET("A route is set from signal");
        public final String label;
        Cond(String l) { label = l; }
        public boolean needsPos() { return this != ALWAYS && this != CHANNEL_ON && this != CHANNEL_OFF && this != DAYTIME && this != NIGHT; }
        public boolean needsChannel() { return this == CHANNEL_ON || this == CHANNEL_OFF; }
        public boolean needsRadius() { return this == TRAIN_NEAR || this == TRAIN_STOPPED; }
    }

    public enum Act {
        NOTHING("Do nothing"), SIGNAL_STOP("Hold signal at Stop"), SIGNAL_RESTRICTING("Signal: Restricting"),
        SIGNAL_APPROACH("Signal: Approach"), SIGNAL_CLEAR("Signal: Clear"), SIGNAL_AUTO("Give signal back to the track"),
        SWITCH_STRAIGHT("Throw switch Straight"), SWITCH_TURN("Throw switch Turn"), SWITCH_AUTO("Switch back to automatic"),
        CHANNEL_ON("Turn channel ON"), CHANNEL_OFF("Turn channel OFF"),
        ROUTE("Set route from signal to signal"), CANCEL_ROUTE("Cancel route from signal"),
        ANNOUNCE("Announce in chat"), ALARM("Raise an alarm");
        public final String label;
        Act(String l) { label = l; }
        public boolean needsPos() { return this.name().startsWith("SIGNAL") || this.name().startsWith("SWITCH") || this == ROUTE || this == CANCEL_ROUTE || this == ANNOUNCE; }
        public boolean needsPos2() { return this == ROUTE; }
        public boolean needsChannel() { return this == CHANNEL_ON || this == CHANNEL_OFF; }
        public boolean needsText() { return this == ANNOUNCE || this == ALARM; }
    }

    public int id;
    public String name = "";
    public boolean enabled = true;
    public Cond cond = Cond.ALWAYS, cond2 = null;
    public long condPos, cond2Pos;
    public int condChannel = 1, cond2Channel = 1, radius = 16;
    public Act act = Act.NOTHING, elseAct = Act.NOTHING;
    public long actPos, actPos2, elsePos, elsePos2;
    public int actChannel = 1, elseChannel = 1;
    public String text = "", elseText = "";
    /** last evaluated result (server), so edge-only actions fire once per change */
    public transient int last = -1;

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("id", id); t.setString("name", name); t.setBoolean("on", enabled);
        t.setString("c", cond.name()); if (cond2 != null) t.setString("c2", cond2.name());
        t.setLong("cp", condPos); t.setLong("c2p", cond2Pos); t.setInteger("cc", condChannel); t.setInteger("c2c", cond2Channel); t.setInteger("r", radius);
        t.setString("a", act.name()); t.setString("e", elseAct.name());
        t.setLong("ap", actPos); t.setLong("ap2", actPos2); t.setLong("ep", elsePos); t.setLong("ep2", elsePos2);
        t.setInteger("ac", actChannel); t.setInteger("ec", elseChannel); t.setString("at", text); t.setString("et", elseText);
        return t;
    }

    public static Rule read(NBTTagCompound t) {
        Rule r = new Rule();
        r.id = t.getInteger("id"); r.name = t.getString("name"); r.enabled = t.getBoolean("on");
        r.cond = cond(t.getString("c"), Cond.ALWAYS); r.cond2 = t.hasKey("c2") ? cond(t.getString("c2"), null) : null;
        r.condPos = t.getLong("cp"); r.cond2Pos = t.getLong("c2p"); r.condChannel = t.getInteger("cc"); r.cond2Channel = t.getInteger("c2c");
        r.radius = t.hasKey("r") ? t.getInteger("r") : 16;
        r.act = act(t.getString("a")); r.elseAct = act(t.getString("e"));
        r.actPos = t.getLong("ap"); r.actPos2 = t.getLong("ap2"); r.elsePos = t.getLong("ep"); r.elsePos2 = t.getLong("ep2");
        r.actChannel = t.getInteger("ac"); r.elseChannel = t.getInteger("ec"); r.text = t.getString("at"); r.elseText = t.getString("et");
        return r;
    }

    private static Cond cond(String s, Cond d) { try { return Cond.valueOf(s); } catch (RuntimeException e) { return d; } }
    private static Act act(String s) { try { return Act.valueOf(s); } catch (RuntimeException e) { return Act.NOTHING; } }

    public static String where(long p) {
        if (p == 0) return "(pick on map)";
        BlockPos b = BlockPos.fromLong(p);
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    /** one-line summary for the rule list */
    public String summary() {
        StringBuilder s = new StringBuilder("WHEN ").append(cond.label);
        if (cond.needsChannel()) s.append(" ").append(condChannel);
        if (cond2 != null) s.append(" AND ").append(cond2.label);
        s.append(" THEN ").append(act.label);
        if (act.needsChannel()) s.append(" ").append(actChannel);
        if (elseAct != Act.NOTHING) s.append(" ELSE ").append(elseAct.label);
        return s.toString();
    }
}
