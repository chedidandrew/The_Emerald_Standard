package com.chedidandrew.emeraldstandard.minecraft;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.inventory.SimpleContainerData;

/** Real Minecraft packet-codec check shared by loader builds and server smoke tests. */
public final class BankerMenuPacketCodecSelfTest {
    private BankerMenuPacketCodecSelfTest() {
    }

    public static void main(String[] args) {
        verify();
        System.out.println("PASS Banker menu packet-codec self-test");
    }

    public static void verify() {
        require(packetRoundTrip(0, 0x7FFF) == 0x7FFF
                        && packetRoundTrip(1, 0x8000) == Short.MIN_VALUE
                        && packetRoundTrip(2, 0xFFFF) == -1
                        && packetRoundTrip(3, 0x1_0000) == 0,
                "Minecraft no longer uses the expected signed-short menu-data codec");

        SimpleContainerData serverLogical = new SimpleContainerData(BankerMenu.DATA_COUNT);
        SimpleContainerData clientLogical = new SimpleContainerData(BankerMenu.DATA_COUNT);
        ShortPackedContainerData serverPacked = new ShortPackedContainerData(serverLogical);
        ShortPackedContainerData clientPacked = new ShortPackedContainerData(clientLogical);
        require(serverPacked.getCount()
                        == ContainerDataPacking.wireSlotCount(BankerMenu.DATA_COUNT)
                        && clientPacked.getCount() == serverPacked.getCount(),
                "Banker packed menu slot count changed");

        int[] values = {
            Integer.MIN_VALUE,
            -100_000_000,
            -1,
            0,
            1,
            32_767,
            32_768,
            65_535,
            65_536,
            1_000_000,
            10_000_000,
            100_000_000,
            1_000_000_000,
            Integer.MAX_VALUE
        };
        for (int index = 0; index < values.length; index++) {
            int value = values[index];
            int logicalIndex = index % 2 == 0 ? 0 : BankerMenu.DATA_COUNT - 1;
            serverLogical.set(logicalIndex, value);
            syncLogicalValue(serverPacked, clientPacked, logicalIndex);
            require(clientLogical.get(logicalIndex) == value,
                    "Banker value did not survive the Minecraft menu packet codec: " + value);
        }

        int partialUpdateIndex = 2;
        serverLogical.set(partialUpdateIndex, 0x11223344);
        syncLogicalValue(serverPacked, clientPacked, partialUpdateIndex);
        serverLogical.set(partialUpdateIndex, 0x1122ABCD);
        syncLimb(serverPacked, clientPacked, partialUpdateIndex, 0);
        require(clientLogical.get(partialUpdateIndex) == 0x1122ABCD,
                "A low-limb update overwrote the unchanged high limb");
        serverLogical.set(partialUpdateIndex, 0xFEDCABCD);
        syncLimb(serverPacked, clientPacked, partialUpdateIndex, 1);
        require(clientLogical.get(partialUpdateIndex) == 0xFEDCABCD,
                "A high-limb update overwrote the unchanged low limb");

        long[] values64 = {
            Long.MIN_VALUE,
            -100_000_000L,
            -1L,
            0L,
            1_000_000L,
            100_000_000L,
            4_294_967_295L,
            Long.MAX_VALUE
        };
        for (long value : values64) {
            serverLogical.set(0, (int) value);
            serverLogical.set(1, (int) (value >>> Integer.SIZE));
            syncLogicalValue(serverPacked, clientPacked, 0);
            syncLogicalValue(serverPacked, clientPacked, 1);
            long reconstructed = ((long) clientLogical.get(1) << Integer.SIZE)
                    | Integer.toUnsignedLong(clientLogical.get(0));
            require(reconstructed == value,
                    "Banker long did not survive the Minecraft menu packet codec: " + value);
        }
    }

    private static void syncLogicalValue(
            ShortPackedContainerData server,
            ShortPackedContainerData client,
            int logicalIndex) {
        for (int limb = 0; limb < ContainerDataPacking.LIMBS_PER_INT; limb++) {
            syncLimb(server, client, logicalIndex, limb);
        }
    }

    private static void syncLimb(
            ShortPackedContainerData server,
            ShortPackedContainerData client,
            int logicalIndex,
            int limb) {
        int wireIndex = logicalIndex * ContainerDataPacking.LIMBS_PER_INT + limb;
        client.set(wireIndex, packetRoundTrip(wireIndex, server.get(wireIndex)));
    }

    private static int packetRoundTrip(int dataId, int value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ClientboundContainerSetDataPacket.STREAM_CODEC.encode(
                    buffer, new ClientboundContainerSetDataPacket(7, dataId, value));
            ClientboundContainerSetDataPacket decoded =
                    ClientboundContainerSetDataPacket.STREAM_CODEC.decode(buffer);
            require(decoded.getContainerId() == 7,
                    "Container id changed during Banker packet self-test");
            require(decoded.getId() == dataId,
                    "Data-slot id changed during Banker packet self-test");
            return decoded.getValue();
        } finally {
            buffer.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
