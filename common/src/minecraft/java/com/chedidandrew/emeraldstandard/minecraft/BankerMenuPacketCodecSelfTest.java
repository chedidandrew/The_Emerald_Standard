package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyState;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
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

        verifyActivityPagingBounds();
        verifyActivitySubtypeMapping();
        verifyActivityFiltering();
        verifyExactAmountButtonPacket();
    }

    private static void verifyActivityPagingBounds() {
        require(BankerMenu.maxActivityOffset(0) == 0
                        && BankerMenu.maxActivityOffset(3) == 0
                        && BankerMenu.maxActivityOffset(5) == 0
                        && BankerMenu.maxActivityOffset(6) == 1
                        && BankerMenu.maxActivityOffset(256) == 251,
                "Activity maximum offset is not page-safe");
        require(BankerMenu.clampActivityOffset(Integer.MIN_VALUE, 256) == 0
                        && BankerMenu.clampActivityOffset(Integer.MAX_VALUE, 256) == 251
                        && BankerMenu.shiftActivityOffset(0, -1, 256) == 0
                        && BankerMenu.shiftActivityOffset(251, 1, 256) == 251,
                "Activity paging accepted an underflow or overflow offset");
        require(BankerMenu.activityVisibleCount(0, 0) == 0
                        && BankerMenu.activityVisibleCount(3, 99) == 3
                        && BankerMenu.activityVisibleCount(256, 251) == 5,
                "Activity paging mishandled an empty, partial, or full ledger");
        require(BankerMenu.activitySourceIndex(256, 0, 0) == 255
                        && BankerMenu.activitySourceIndex(256, 0, 4) == 251
                        && BankerMenu.activitySourceIndex(256, 251, 0) == 4
                        && BankerMenu.activitySourceIndex(256, 251, 4) == 0
                        && BankerMenu.activitySourceIndex(3, 0, 3) == -1,
                "Activity newest-first source indexes escaped the retained ledger");
        require(BankerMenu.clampActivityOffset(251, 3) == 0
                        && BankerMenu.clampActivityOffset(4, 10) == 4,
                "Activity offset did not remain valid after a live ledger-size change");
    }

    private static void verifyActivitySubtypeMapping() {
        require(BankerMenu.activityKindFor(
                        com.chedidandrew.emeraldstandard.core.EconomyState
                                .PortfolioTransactionKind.CASH_IN,
                        "EXCHANGE:gold_ingot") == BankerMenu.ActivityKind.EXCHANGE,
                "A resource exchange was mislabeled as a deposit");
        require(BankerMenu.activityKindFor(
                        com.chedidandrew.emeraldstandard.core.EconomyState
                                .PortfolioTransactionKind.CASH_IN,
                        "EXCHANGE") == BankerMenu.ActivityKind.EXCHANGE,
                "A legacy resource exchange was mislabeled as a deposit");
        require(BankerMenu.activityKindFor(
                        com.chedidandrew.emeraldstandard.core.EconomyState
                                .PortfolioTransactionKind.CASH_IN,
                        "WITHDRAWAL_REFUND")
                        == BankerMenu.ActivityKind.WITHDRAWAL_REFUND,
                "An inventory withdrawal refund was mislabeled as a deposit");
        require(BankerMenu.activitySubjectIndex("EXCHANGE:gold_ingot")
                        == com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.size()
                                + BankerMenu.RESOURCE_NAMES.indexOf("gold_ingot"),
                "An exchange resource did not survive Activity subject encoding");
        require(BankerMenu.activitySubjectIndex("RESTORATION")
                        == com.chedidandrew.emeraldstandard.core.EconomyEngine.ASSETS.size()
                                + BankerMenu.RESOURCE_NAMES.size()
                                + com.chedidandrew.emeraldstandard.core.EconomyState
                                        .DonationPurpose.RESTORATION.ordinal(),
                "A Prosperity Fund purpose did not survive Activity subject encoding");
    }

    private static void verifyActivityFiltering() {
        for (BankerMenu.ActivityKind kind : BankerMenu.ActivityKind.values()) {
            int matchingCategories = 0;
            for (BankerMenu.ActivityFilter filter : BankerMenu.ActivityFilter.values()) {
                if (filter != BankerMenu.ActivityFilter.ALL && filter.includes(kind)) {
                    matchingCategories++;
                }
            }
            require(BankerMenu.ActivityFilter.ALL.includes(kind) && matchingCategories == 1,
                    "Activity kind was missing from, or duplicated across, filters: " + kind);
        }

        List<EconomyState.PortfolioTransaction> ledger = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            ledger.add(activity(
                    index % 2 == 0
                            ? EconomyState.PortfolioTransactionKind.BUY
                            : EconomyState.PortfolioTransactionKind.CASH_IN,
                    index % 2 == 0 ? "INVESTMENT_" + index : "DEPOSIT"));
        }
        List<EconomyState.PortfolioTransaction> investments =
                BankerMenu.filterActivityTransactions(
                        ledger, BankerMenu.ActivityFilter.INVESTMENTS);
        require(investments.size() == 6
                        && investments.get(BankerMenu.activitySourceIndex(6, 0, 0))
                                .symbol.equals("INVESTMENT_10")
                        && investments.get(BankerMenu.activitySourceIndex(6, 0, 4))
                                .symbol.equals("INVESTMENT_2")
                        && investments.get(BankerMenu.activitySourceIndex(6, 1, 4))
                                .symbol.equals("INVESTMENT_0"),
                "Activity filtering did not occur before newest-first pagination");

        EconomyState.PortfolioTransaction exchange = activity(
                EconomyState.PortfolioTransactionKind.CASH_IN, "EXCHANGE");
        EconomyState.PortfolioTransaction refund = activity(
                EconomyState.PortfolioTransactionKind.CASH_IN, "WITHDRAWAL_REFUND");
        require(BankerMenu.filterActivityTransactions(
                                List.of(exchange, refund),
                                BankerMenu.ActivityFilter.EXCHANGE)
                        .equals(List.of(exchange))
                        && BankerMenu.filterActivityTransactions(
                                        List.of(exchange, refund),
                                        BankerMenu.ActivityFilter.CASH_TRANSFERS)
                                .equals(List.of(refund)),
                "Derived exchange or withdrawal-refund subtypes entered the wrong filter");
    }

    private static EconomyState.PortfolioTransaction activity(
            EconomyState.PortfolioTransactionKind kind, String symbol) {
        EconomyState.PortfolioTransaction transaction = new EconomyState.PortfolioTransaction();
        transaction.kind = kind;
        transaction.symbol = symbol;
        return transaction;
    }

    private static void verifyExactAmountButtonPacket() {
        int ordinary = BankerAmountSelection.encodeButtonId(1_000_000);
        int fund = BankerAmountSelection.encodeFundButtonId(1_000_000);
        require(ordinary != fund
                        && BankerAmountSelection.decodeButtonId(fund) == 0
                        && BankerAmountSelection.decodeFundButtonId(ordinary) == 0,
                "Exact transaction and Fund contribution packet ranges overlap");
        for (int encodedButtonId : new int[] {ordinary, fund}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ServerboundContainerButtonClickPacket.STREAM_CODEC.encode(
                        buffer, new ServerboundContainerButtonClickPacket(7, encodedButtonId));
                ServerboundContainerButtonClickPacket decoded =
                        ServerboundContainerButtonClickPacket.STREAM_CODEC.decode(buffer);
                require(decoded.containerId() == 7 && decoded.buttonId() == encodedButtonId,
                        "An exact Banker amount did not survive the menu-button packet codec");
            } finally {
                buffer.release();
            }
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
