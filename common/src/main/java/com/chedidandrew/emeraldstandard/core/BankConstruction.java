package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.util.*;

/** Durable intent, not a cursor: Minecraft's saved blocks remain the progress authority. */
public record BankConstruction(long origin, long bankerAnchor, UUID villageId, int version, List<Cell> cells) {
    public record Cell(long position, String before, String after) { }

    public BankConstruction {
        cells = List.copyOf(cells);
        if (version < 1 || cells.isEmpty() || cells.size() > 50_000)
            throw new IllegalArgumentException("Invalid Bank construction plan");
        Set<Long> positions = new HashSet<>();
        for (Cell cell : cells) {
            if (!positions.add(cell.position) || cell.before == null || cell.after == null
                    || cell.before.length() > 4096 || cell.after.length() > 4096
                    || cell.before.isBlank() || cell.after.isBlank())
                throw new IllegalArgumentException("Invalid Bank construction cell");
        }
    }

    public String encode() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeLong(origin); out.writeLong(bankerAnchor); out.writeUTF(villageId == null ? "" : villageId.toString());
            out.writeInt(version); out.writeInt(cells.size());
            for (Cell cell : cells) {
                out.writeLong(cell.position); out.writeUTF(cell.before); out.writeUTF(cell.after);
            }
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
            if (in.available() != 0) throw new IOException("Trailing Bank construction data");
            return new BankConstruction(origin, anchor, owner.isEmpty() ? null : UUID.fromString(owner), version, cells);
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid Bank construction", ex); }
    }
}
