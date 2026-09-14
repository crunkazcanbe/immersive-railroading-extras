package com.dogpound.railmap.network;

import com.dogpound.railmap.RailMap;
import com.dogpound.railmap.block.TileRailDisplay;
import com.dogpound.railmap.scan.SwitchControl;
import com.dogpound.railmap.server.TrainControl;
import com.dogpound.railmap.server.StationData;
import com.dogpound.railmap.server.Viewers;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → server, the dispatcher's controls:
 * <ul>
 *   <li>{@link #THROW_SWITCH}: cycle the switch at {@code target} (straight → turn → auto);</li>
 *   <li>{@link #SET_STATION}: name (or, with an empty name, un-name) the station at {@code target};</li>
 *   <li>{@link #WATCH}: "I have a map open, keep the train feed coming".</li>
 * </ul>
 * {@code board} is the tile the player is using (null = handheld), used for reach checking and
 * to refresh that board right after the change so the player sees it happen.
 */
public class PacketAction implements IMessage {
    public static final byte THROW_SWITCH = 0, SET_STATION = 1, WATCH = 2, TRAIN_CMD = 3, AUGMENT = 4;
    /** How far from the player (or the board they're at) a target may be. */
    private static final double REACH = 512;

    private byte action;
    private BlockPos board;
    private BlockPos target = BlockPos.ORIGIN;
    private String text = "";
    /** TRAIN_CMD: the stock's entity id and the command ordinal. */
    private int entityId;
    private byte cmd;

    public PacketAction() {}

    public PacketAction(byte action, BlockPos board, BlockPos target, String text) {
        this.action = action;
        this.board = board;
        this.target = target == null ? BlockPos.ORIGIN : target;
        this.text = text == null ? "" : text;
    }

    public static PacketAction watch() {
        return new PacketAction(WATCH, null, null, "");
    }

    /** Drive a train from the board: throttle, brake, reverser, horn, bell, emergency stop. */
    public static PacketAction train(BlockPos board, int entityId, TrainControl.Cmd cmd) {
        PacketAction p = new PacketAction(TRAIN_CMD, board, null, "");
        p.entityId = entityId;
        p.cmd = (byte) cmd.ordinal();
        return p;
    }

    /** Switch an IR loader/unloader augment on or off from the board. */
    public static PacketAction augment(BlockPos board, BlockPos augmentPos) {
        return new PacketAction(AUGMENT, board, augmentPos, "");
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        action = b.readByte();
        board = b.readBoolean() ? BlockPos.fromLong(b.readLong()) : null;
        target = BlockPos.fromLong(b.readLong());
        text = b.readString(32);
        entityId = b.readVarInt();
        cmd = b.readByte();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer b = new PacketBuffer(buf);
        b.writeByte(action);
        b.writeBoolean(board != null);
        if (board != null) b.writeLong(board.toLong());
        b.writeLong(target.toLong());
        b.writeString(text.length() > 32 ? text.substring(0, 32) : text);
        b.writeVarInt(entityId);
        b.writeByte(cmd);
    }

    public static class Handler implements IMessageHandler<PacketAction, IMessage> {
        @Override
        public IMessage onMessage(PacketAction msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> handle(msg, player));
            return null;
        }

        private static void handle(PacketAction msg, EntityPlayerMP player) {
            if (msg.action == WATCH) {
                Viewers.mark(player);
                return;
            }
            if (msg.action == TRAIN_CMD) {
                // Reach is checked by the board below for block targets; a train may be far away
                // by design — that is the whole point of a dispatcher's remote control — but the
                // player must be at a real board to issue it.
                if (msg.board != null && (player.getDistanceSq(msg.board) > 64 * 64
                        || !(player.world.getTileEntity(msg.board) instanceof TileRailDisplay))) return;
                String r = TrainControl.apply(player.world, player, msg.entityId,
                        TrainControl.Cmd.byOrdinal(msg.cmd));
                player.sendStatusMessage(new TextComponentString(r), true);
                return;
            }
            // The board (if any) must be near the player; the target must be near the board/player.
            BlockPos anchor = player.getPosition();
            TileRailDisplay tile = null;
            if (msg.board != null) {
                if (player.getDistanceSq(msg.board) > 64 * 64 || !player.world.isBlockLoaded(msg.board)) return;
                TileEntity te = player.world.getTileEntity(msg.board);
                if (!(te instanceof TileRailDisplay d)) return;
                tile = d.controller();
                anchor = msg.board;
            }
            if (msg.target.distanceSq(anchor) > REACH * REACH || !player.world.isBlockLoaded(msg.target)) return;

            switch (msg.action) {
                case THROW_SWITCH -> {
                    // The interlocking outranks the dispatcher's hand: a lined route can't be broken.
                    String result = com.dogpound.railmap.auto.Interlocking.isLocked(player.world, msg.target)
                            ? "Switch is locked by a route or a driverless train"
                            : SwitchControl.cycle(player.world, msg.target);
                    player.sendStatusMessage(new TextComponentString(result), true);
                }
                case AUGMENT -> {
                    String r = TrainControl.toggleAugment(player.world, msg.target);
                    player.sendStatusMessage(new TextComponentString(r), true);
                }
                case SET_STATION -> {
                    StationData.get(player.world).setName(msg.target, msg.text);
                    String t = msg.text.trim();
                    player.sendStatusMessage(new TextComponentString(t.isEmpty() ? "Station removed" : "Station: " + t), true);
                }
                default -> { return; }
            }
            if (tile != null) tile.rescan(true);
            else PacketRescan.Handler.handheld(player);
        }
    }
}
