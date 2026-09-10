package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.util.*;

/** Durable intent, not a cursor: Minecraft's saved blocks remain the progress authority. */
public record BankConstruction(long origin, long bankerAnchor, UUID villageId, int version, List<Cell> cells,
        Set<Long> handledStorage, boolean legacyLootSuppressed) {
    public record Cell(long position, String before, String after) {
        public boolean storage() {
            String block = after.split("\\[", 2)[0];
            return block.equals("minecraft:chest") || block.equals("minecraft:trapped_chest")
                    || block.equals("minecraft:barrel");
        }
    }

    public BankConstruction(long origin, long bankerAnchor, UUID villageId, int version, List<Cell> cells) {
        this(origin, bankerAnchor, villageId, version, cells, Set.of(), false);
    }

    public BankConstruction {
        cells = List.copyOf(cells);
        handledStorage = Set.copyOf(handledStorage);
        if (version < 1 || cells.isEmpty() || cells.size() > 50_000)
            throw new IllegalArgumentException("Invalid Bank construction plan");
        Set<Long> positions = new HashSet<>();
        for (Cell cell : cells) {
            if (!positions.add(cell.position) || cell.before == null || cell.after == null
                    || cell.before.length() > 4096 || cell.after.length() > 4096
                    || cell.before.isBlank() || cell.after.isBlank())
                throw new IllegalArgumentException("Invalid Bank construction cell");
        }
        Set<Long> storagePositions = new HashSet<>();
        cells.stream().filter(Cell::storage).forEach(cell -> storagePositions.add(cell.position));
        if (!storagePositions.containsAll(handledStorage))
            throw new IllegalArgumentException("Bank loot receipt is outside planned storage");
    }

    public BankConstruction withHandledStorage(long position) {
        Set<Long> next = new HashSet<>(handledStorage);
        next.add(position);
        return new BankConstruction(origin, bankerAnchor, villageId, version, cells, next, legacyLootSuppressed);
    }

    public String encode() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeLong(origin); out.writeLong(bankerAnchor); out.writeUTF(villageId == null ? "" : villageId.toString());
            out.writeInt(version); out.writeInt(cells.size());
            for (Cell cell : cells) {
                out.writeLong(cell.position); out.writeUTF(cell.before); out.writeUTF(cell.after);
            }
            out.writeInt(1); // Optional trailer, introduced with economy format 26.
            out.writeBoolean(legacyLootSuppressed);
            out.writeInt(handledStorage.size());
            for (long position : handledStorage.stream().sorted().toList()) out.writeLong(position);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static BankConstruction decode(String encoded) throws IOException {
        if (encoded.length() > 16_000_000) throw new IOException("Oversized Bank construction");
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            long origin = in.readLong(), anchor = in.readLong(); String owner = in.readUTF(); int version = in.readInt();
            int count = in.readInt();
            if (count < 1 || count > 50_000) throw new IOException("Invalid Bank plan size");
            List<Cell> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cells.add(new Cell(in.readLong(), in.readUTF(), in.readUTF()));
            // Old unfinished plans have no provenance for destroyed/looted containers. Finish any
            // remaining storage empty rather than guessing that it deserves a new loot roll.
            boolean legacy = true;
            Set<Long> handled = new HashSet<>();
            if (in.available() > 0) {
                if (in.readInt() != 1) throw new IOException("Unknown Bank loot receipt version");
                legacy = in.readBoolean();
                int receipts = in.readInt();
                if (receipts < 0 || receipts > count) throw new IOException("Invalid Bank loot receipt count");
                for (int i = 0; i < receipts; i++)
                    if (!handled.add(in.readLong())) throw new IOException("Duplicate Bank loot receipt");
            }
            if (in.available() != 0) throw new IOException("Trailing Bank construction data");
            return new BankConstruction(origin, anchor, owner.isEmpty() ? null : UUID.fromString(owner), version, cells, handled, legacy);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid Bank construction", ex); }
    }
}
