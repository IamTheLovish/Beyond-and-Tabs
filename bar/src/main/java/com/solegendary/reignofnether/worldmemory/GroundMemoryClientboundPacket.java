package com.solegendary.reignofnether.worldmemory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * One sync pass's ground memory updates for one player (GroundMemorySync): per chunk either a full snapshot (1 KB)
 * or a list of cells, each with the chunk's decay clock, under one header carrying the server's decay parameters so
 * every client decays exactly as the server does (DecayParams).
 */
public class GroundMemoryClientboundPacket {
    static final int MAX_UPDATES = 256;

    /** One chunk's update: a full snapshot ({@code full} set) or the listed cells. */
    public static final class Update {
        public final int chunkX, chunkZ;
        public final long decayTick;
        public final byte[] full;
        public final int[] cells, values;

        public Update(int chunkX, int chunkZ, long decayTick, byte[] full, int[] cells, int[] values) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.decayTick = decayTick;
            this.full = full;
            this.cells = cells;
            this.values = values;
        }

        /**
         * Applies this update to a client's copy of the chunk (GroundMemoryClient does it per packet; the GameTests do
         * it headless to prove a joiner ends up with the server's bytes). A snapshot replaces the copy and takes the
         * sender's decay clock. A list of cells is brought to the same clock first: the copy and the sender may stand
         * at different decay clocks, so the copy is decayed up to the sender's, or the incoming cells down to the
         * copy's - the same table steps either way, so the bytes agree.
         */
        public void applyTo(GroundMemory mem, DecayParams params) {
            if (full != null) {
                mem.setAll(full);
                mem.setLastDecayTick(decayTick);
                return;
            }
            if (decayTick > mem.lastDecayTick())
                mem.decayTo(decayTick, params);
            long q0 = Math.floorDiv(decayTick, DecayParams.QUANTUM), q1 = Math.floorDiv(mem.lastDecayTick(), DecayParams.QUANTUM);
            for (int k = 0; k < cells.length; k++) {
                int v = values[k];
                if (q1 > q0)
                    v = GroundMemory.decayCell(v, q0, q1, params);
                mem.setPacked(cells[k] & 255, v);
            }
        }

        /** Bytes on the wire (the stress measurement). */
        public int wireBytes() {
            return 10 + 8 + 1 + (full != null ? GroundMemory.BYTES : 2 + cells.length * 5);
        }
    }

    public final DecayParams params;
    public final List<Update> updates;

    public GroundMemoryClientboundPacket(DecayParams params, List<Update> updates) {
        this.params = params;
        this.updates = updates;
    }

    public GroundMemoryClientboundPacket(FriendlyByteBuf buf) {
        params = DecayParams.decode(buf);
        int n = Math.min(buf.readVarInt(), MAX_UPDATES);
        updates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int cx = buf.readVarInt(), cz = buf.readVarInt();
            long decay = buf.readLong();
            byte kind = buf.readByte();
            if (kind == 0) {
                byte[] full = new byte[GroundMemory.BYTES];
                buf.readBytes(full);
                updates.add(new Update(cx, cz, decay, full, null, null));
            } else {
                int m = Math.min(buf.readVarInt(), GroundMemory.CELLS);
                int[] cells = new int[m], values = new int[m];
                for (int k = 0; k < m; k++) {
                    cells[k] = buf.readUnsignedByte();
                    values[k] = buf.readInt();
                }
                updates.add(new Update(cx, cz, decay, null, cells, values));
            }
        }
    }

    public void encode(FriendlyByteBuf buf) {
        params.encode(buf);
        int n = Math.min(updates.size(), MAX_UPDATES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Update u = updates.get(i);
            buf.writeVarInt(u.chunkX);
            buf.writeVarInt(u.chunkZ);
            buf.writeLong(u.decayTick);
            if (u.full != null) {
                buf.writeByte(0);
                buf.writeBytes(u.full, 0, GroundMemory.BYTES);
            } else {
                buf.writeByte(1);
                buf.writeVarInt(u.cells.length);
                for (int k = 0; k < u.cells.length; k++) {
                    buf.writeByte(u.cells[k]);
                    buf.writeInt(u.values[k]);
                }
            }
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GroundMemoryClient.ingest(this))
        );
        ctx.get().setPacketHandled(true);
        return true;
    }
}
