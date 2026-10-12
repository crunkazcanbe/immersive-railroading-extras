package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Server -> a rider: the driver's cab display (moving block / train protection). See auto.MovingBlock, client.CabDisplay. */
public class PacketCab implements IMessage {
    public String mode = "", limitedBy = "", nextStop = "", note = "";
    public float speed, target, limit, distanceToGo = -1, gap = -1, stopM = -1;
    /** train simulation (sim.CabData): driver-aid / adhesion / engine lamps (CabData bits), advisory, engine, consist */
    public int aids;
    public float adviceKmh = -1, drawbar, buff, coupler = 1f;
    public String advice = "", eng1 = "", eng2 = "", phys = "", adh = "", fault = "";
    /** driver's display (her list item 108 / 109): auxiliaries, MU consist, planning area, gauges */
    public int auxBits, faultCount;
    public float mrBar = -1, bcBar = -1, batV = -1, cabC = -999, auxKw, auxCap, throttle, brake, reverser;
    public String aux = "", mu = "", unit = "", faults = "";
    /** planning area: upcoming targets, metres ahead; kind 0 speed restriction, 1 signal at danger, 2 station stop */
    public float[] planD = new float[0], planKmh = new float[0];
    public byte[] planKind = new byte[0];
    /** gradient profile ahead: {metres from, per mille} pairs */
    public float[] grade = new float[0];

    public PacketCab() {}

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeString(cut(mode));
        b.writeString(cut(limitedBy));
        b.writeString(cut(nextStop));
        b.writeString(cut(note));
        b.writeFloat(speed);
        b.writeFloat(target);
        b.writeFloat(limit);
        b.writeFloat(distanceToGo);
        b.writeFloat(gap);
        b.writeFloat(stopM);
        b.writeInt(aids);
        b.writeFloat(adviceKmh);
        b.writeFloat(drawbar);
        b.writeFloat(buff);
        b.writeFloat(coupler);
        for (String s : new String[]{advice, eng1, eng2, phys, adh, fault}) b.writeString(cut(s));
        b.writeInt(auxBits); b.writeInt(faultCount);
        for (float f : new float[]{mrBar, bcBar, batV, cabC, auxKw, auxCap, throttle, brake, reverser}) b.writeFloat(f);
        b.writeString(cut(aux)); b.writeString(long_(mu)); b.writeString(cut(unit)); b.writeString(long_(faults));
        int n = Math.min(12, planD.length);
        b.writeByte(n);
        for (int i = 0; i < n; i++) { b.writeFloat(planD[i]); b.writeFloat(planKmh[i]); b.writeByte(planKind[i]); }
        int g = Math.min(20, grade.length / 2);
        b.writeByte(g);
        for (int i = 0; i < g * 2; i++) b.writeFloat(grade[i]);
    }

    private static String long_(String s) { return s == null ? "" : s.length() > 900 ? s.substring(0, 900) : s; }

    private static String cut(String s) { return s == null ? "" : s.length() > 120 ? s.substring(0, 120) : s; }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        mode = b.readString(256);
        limitedBy = b.readString(256);
        nextStop = b.readString(256);
        note = b.readString(256);
        speed = b.readFloat();
        target = b.readFloat();
        limit = b.readFloat();
        distanceToGo = b.readFloat();
        gap = b.readFloat();
        stopM = b.readFloat();
        aids = b.readInt();
        adviceKmh = b.readFloat();
        drawbar = b.readFloat();
        buff = b.readFloat();
        coupler = b.readFloat();
        advice = b.readString(256);
        eng1 = b.readString(256);
        eng2 = b.readString(256);
        phys = b.readString(256);
        adh = b.readString(256);
        fault = b.readString(256);
        if (!b.isReadable()) return;
        auxBits = b.readInt(); faultCount = b.readInt();
        mrBar = b.readFloat(); bcBar = b.readFloat(); batV = b.readFloat(); cabC = b.readFloat(); auxKw = b.readFloat(); auxCap = b.readFloat();
        throttle = b.readFloat(); brake = b.readFloat(); reverser = b.readFloat();
        aux = b.readString(256); mu = b.readString(1024); unit = b.readString(256); faults = b.readString(1024);
        int n = b.readByte();
        planD = new float[n]; planKmh = new float[n]; planKind = new byte[n];
        for (int i = 0; i < n; i++) { planD[i] = b.readFloat(); planKmh[i] = b.readFloat(); planKind[i] = b.readByte(); }
        int g = b.readByte();
        grade = new float[g * 2];
        for (int i = 0; i < g * 2; i++) grade[i] = b.readFloat();
    }

    public static class Handler implements IMessageHandler<PacketCab, IMessage> {
        @Override
        public IMessage onMessage(PacketCab msg, MessageContext ctx) {
            RailMap.proxy.acceptCab(msg);
            return null;
        }
    }
}
