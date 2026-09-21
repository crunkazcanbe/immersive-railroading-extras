package com.dogpound.railmap.signal;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/**
 * One trackside sign that can be any face in {@link Face}. Right-click with the Signal Wrench to
 * cycle the face; the CUSTOM face carries free text the player types in. Everything is drawn live
 * by {@code RailSignRenderer} — a coloured board plus text — so a new sign is just a new enum row,
 * no new texture. Resizable like the rest of the lineside furniture ({@link IScalable}).
 */
public class TileRailSign extends TileEntity implements IScalable {

    /**
     * The whole railroad sign vocabulary. Each face is a board colour, a text colour, a border,
     * and up to three lines. Order matters — the wrench cycles through it.
     */
    public enum Face {
        // ---- regulatory ----
        STOP("STOP", 0xC1272D, 0xFFFFFF, 0xFFFFFF, "STOP"),
        STOP_PROCEED("Stop then proceed", 0xC1272D, 0xFFFFFF, 0xFFFFFF, "STOP", "THEN", "PROCEED"),
        YIELD("Yield", 0xFFFFFF, 0xC1272D, 0xC1272D, "YIELD"),
        YARD_LIMIT("Yard Limit", 0xFFFFFF, 0x101010, 0x101010, "YARD", "LIMIT"),
        RESTRICTED("Restricted Speed", 0xFFC91F, 0x101010, 0x101010, "RESTRICTED", "SPEED"),
        DO_NOT_HUMP("Do Not Hump", 0xFFFFFF, 0xC1272D, 0xC1272D, "DO NOT", "HUMP"),
        BLUE_FLAG("Blue Flag", 0x2456C4, 0xFFFFFF, 0xFFFFFF, "STOP", "MEN AT", "WORK"),
        NO_TRESPASSING("No Trespassing", 0xFFFFFF, 0xC1272D, 0x101010, "NO", "TRESPASSING"),
        PRIVATE("Private Property", 0xFFFFFF, 0x101010, 0x101010, "PRIVATE", "PROPERTY"),
        // ---- warning (yellow) ----
        STOP_AHEAD("Stop Ahead", 0xFFC91F, 0x101010, 0x101010, "STOP", "AHEAD"),
        SIGNAL_AHEAD("Signal Ahead", 0xFFC91F, 0x101010, 0x101010, "SIGNAL", "AHEAD"),
        CROSSING_AHEAD("Crossing Ahead", 0xFFC91F, 0x101010, 0x101010, "RXR", "AHEAD"),
        CURVE("Curve", 0xFFC91F, 0x101010, 0x101010, "CURVE"),
        STEEP_GRADE("Steep Grade", 0xFFC91F, 0x101010, 0x101010, "STEEP", "GRADE"),
        TUNNEL("Tunnel", 0xFFC91F, 0x101010, 0x101010, "TUNNEL", "AHEAD"),
        BRIDGE("Bridge", 0xFFC91F, 0x101010, 0x101010, "BRIDGE"),
        FLANGER("Flanger", 0xFFFFFF, 0x101010, 0x101010, "F"),
        // ---- operating / lineside ----
        WHISTLE("Whistle Post (W)", 0xFFFFFF, 0x101010, 0x101010, "W"),
        WHISTLE_X("Whistle X", 0xFFFFFF, 0x101010, 0x101010, "W", "X"),
        CROSSBUCK("Crossbuck", 0xFFFFFF, 0xC1272D, 0xC1272D, "RAILROAD", "CROSSING"),
        EXEMPT("Exempt", 0xFFFFFF, 0x101010, 0x101010, "EXEMPT"),
        RESUME_SPEED("Resume Speed", 0x2E8B57, 0xFFFFFF, 0xFFFFFF, "RESUME", "SPEED"),
        BEGIN("Begin", 0x2E8B57, 0xFFFFFF, 0xFFFFFF, "BEGIN"),
        END("End", 0x101010, 0xFFFFFF, 0xFFFFFF, "END"),
        END_OF_TRACK("End of Track", 0xC1272D, 0xFFFFFF, 0xFFFFFF, "END OF", "TRACK"),
        CLEARANCE("Clearance Point", 0xFFFFFF, 0x101010, 0x101010, "CLEARANCE", "POINT"),
        DERAIL("Derail", 0xFFC91F, 0x101010, 0x101010, "DERAIL"),
        // ---- electrification ----
        HIGH_VOLTAGE("High Voltage", 0xFFC91F, 0xC1272D, 0x101010, "DANGER", "HIGH", "VOLTAGE"),
        THIRD_RAIL("Third Rail", 0xFFC91F, 0xC1272D, 0x101010, "THIRD RAIL", "DANGER"),
        // ---- station / wayfinding (blue) ----
        STATION("Station Name", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "STATION"),
        PLATFORM_1("Platform 1", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PLATFORM", "1"),
        PLATFORM_2("Platform 2", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PLATFORM", "2"),
        PLATFORM_3("Platform 3", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PLATFORM", "3"),
        PLATFORM_4("Platform 4", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PLATFORM", "4"),
        EXIT("Exit", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "EXIT", "<-"),
        TICKETS("Tickets", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "TICKETS", "^"),
        TRANSFER("Transfer", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "TRANSFER", "->"),
        PLATFORMS("Platforms", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PLATFORMS", "->"),
        WAITING_ROOM("Waiting Room", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "WAITING", "ROOM"),
        RESTROOMS("Restrooms", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "RESTROOMS"),
        INFORMATION("Information", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "INFO", "?"),
        NO_ENTRY("No Entry", 0xC1272D, 0xFFFFFF, 0xFFFFFF, "NO", "ENTRY"),
        TELEPHONE("Telephone", 0x1B3A6B, 0xFFFFFF, 0xFFFFFF, "PHONE"),
        // ---- the editable one ----
        CUSTOM("Custom Text", 0x101010, 0xFFFFFF, 0xFFFFFF, "CLICK TO", "EDIT");

        public final String label;
        public final int bg, fg, border;
        public final String[] lines;

        Face(String label, int bg, int fg, int border, String... lines) {
            this.label = label;
            this.bg = bg;
            this.fg = fg;
            this.border = border;
            this.lines = lines;
        }
    }

    private static final Face[] FACES = Face.values();

    private int face;
    private String custom = "";
    private float scale = 1f;

    public Face face() {
        return FACES[Math.max(0, Math.min(FACES.length - 1, face))];
    }

    public boolean isCustom() {
        return face() == Face.CUSTOM;
    }

    public String customText() {
        return custom;
    }

    public void setCustomText(String text) {
        this.custom = text == null ? "" : text;
        sync();
    }

    /** Wrench cycle: advance to the next face. */
    public String cycle(boolean backwards) {
        face = ((face + (backwards ? -1 : 1)) % FACES.length + FACES.length) % FACES.length;
        sync();
        return "Sign: " + face().label;
    }

    public EnumFacing facing() {
        IBlockState s = world.getBlockState(pos);
        return s.getProperties().containsKey(BlockRailSign.FACING)
                ? s.getValue(BlockRailSign.FACING) : EnumFacing.NORTH;
    }

    private void sync() {
        markDirty();
        if (world != null && !world.isRemote) {
            IBlockState st = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, st, st, 3);
        }
    }

    @Override
    public float scale() {
        return scale;
    }

    @Override
    public void setScale(float s) {
        scale = IScalable.clamp(s);
        sync();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound t) {
        super.writeToNBT(t);
        t.setInteger("face", face);
        t.setString("custom", custom);
        if (scale != 1f) t.setFloat("scale", scale);
        return t;
    }

    @Override
    public void readFromNBT(NBTTagCompound t) {
        super.readFromNBT(t);
        face = t.getInteger("face");
        custom = t.getString("custom");
        scale = t.hasKey("scale") ? IScalable.clamp(t.getFloat("scale")) : 1f;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
