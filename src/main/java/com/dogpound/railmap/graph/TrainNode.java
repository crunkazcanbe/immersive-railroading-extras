package com.dogpound.railmap.graph;

import net.minecraft.network.PacketBuffer;

/**
 * One piece of rolling stock as seen at the last train tick. Trains move, so these ride in a
 * small, frequent packet ({@code PacketTrains}) rather than inside the network snapshot.
 * Per-loco control readouts are only filled for {@link Kind#isLoco()} kinds.
 */
public final class TrainNode {
    public enum Kind {
        LOCO_STEAM, LOCO_DIESEL, HANDCAR, TENDER, FREIGHT, TANK, PASSENGER, OTHER;

        public boolean isLoco() {
            return this == LOCO_STEAM || this == LOCO_DIESEL || this == HANDCAR;
        }
    }

    public final int id;
    public final float x, y, z;
    /** Heading in degrees, IR convention (0 = +Z/south, 90 = -X/west... whatever IR does, used relatively). */
    public final float yaw;
    /** Signed: positive = moving toward its front. km/h. */
    public final float speedKmh;
    public final Kind kind;
    /** Definition display name, e.g. "K4 Pacific". */
    public final String name;
    /** Player-set nameplate (IR "tag"), often empty. */
    public final String tag;
    /** Cargo or fluid fill 0-100 (-1 = n/a). */
    public final int cargoPct;
    public final int passengers;
    /** Cars in the consist this stock belongs to (including itself). */
    public final int consist;
    /** True for the first unit of a consist: gets the label, the others just a blip. */
    public final boolean lead;
    public final float throttle, reverser, brake;
    /** Best guess at the next station ahead ("" if stopped or nothing ahead). */
    public final String heading;
    /** Worst part wear 0-150 (-1 = unknown): maintenance (railway list §13). */
    public int wear = -1;
    /** Electric locos: power source + battery, e.g. "wire · battery 87%" ("" = not electric). §10/§11 */
    public String power = "";

    public TrainNode(int id, float x, float y, float z, float yaw, float speedKmh, Kind kind, String name,
                     String tag, int cargoPct, int passengers, int consist, boolean lead,
                     float throttle, float reverser, float brake, String heading) {
        this.id = id;
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw;
        this.speedKmh = speedKmh;
        this.kind = kind;
        this.name = name == null ? "" : name;
        this.tag = tag == null ? "" : tag;
        this.cargoPct = cargoPct;
        this.passengers = passengers;
        this.consist = consist;
        this.lead = lead;
        this.throttle = throttle; this.reverser = reverser; this.brake = brake;
        this.heading = heading == null ? "" : heading;
    }

    public String displayName() {
        return tag.isEmpty() ? name : tag;
    }

    public boolean moving() {
        return Math.abs(speedKmh) > 0.5f;
    }

    public void write(PacketBuffer b) {
        b.writeVarInt(id);
        b.writeFloat(x); b.writeFloat(y); b.writeFloat(z);
        b.writeFloat(yaw);
        b.writeFloat(speedKmh);
        b.writeByte(kind.ordinal());
        b.writeString(cap(name));
        b.writeString(cap(tag));
        b.writeByte(cargoPct);
        b.writeByte(passengers);
        b.writeByte(consist);
        b.writeBoolean(lead);
        b.writeFloat(throttle); b.writeFloat(reverser); b.writeFloat(brake);
        b.writeString(cap(heading));
        b.writeShort(wear);
        b.writeString(cap(power));
    }

    public static TrainNode read(PacketBuffer b) {
        int id = b.readVarInt();
        float x = b.readFloat(), y = b.readFloat(), z = b.readFloat();
        float yaw = b.readFloat();
        float spd = b.readFloat();
        int k = b.readByte() & 0xff;
        Kind[] kinds = Kind.values();
        Kind kind = k < kinds.length ? kinds[k] : Kind.OTHER;
        String name = b.readString(256);
        String tag = b.readString(256);
        int cargo = b.readByte();
        int pax = b.readByte() & 0xff;
        int consist = b.readByte() & 0xff;
        boolean lead = b.readBoolean();
        float th = b.readFloat(), rv = b.readFloat(), br = b.readFloat();
        String heading = b.readString(256);
        TrainNode n = new TrainNode(id, x, y, z, yaw, spd, kind, name, tag, cargo, pax, consist, lead, th, rv, br, heading);
        n.wear = b.readShort();
        n.power = b.readString(256);
        return n;
    }

    /** packet strings are length-limited: long train names ("... full train (Pride Rail - Trans flag)") disconnected the client */
    private static String cap(String s) {
        return s == null ? "" : s.length() > 250 ? s.substring(0, 250) : s;
    }
}
