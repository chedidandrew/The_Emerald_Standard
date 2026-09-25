package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.util.*;

/** Frozen terrain work approved before a new lot is committed. Never used by repairs. */
public record SitePreparationPlan(List<Cell> cells) {
    public static final int MAX_CELLS = 32_768;
    public record Cell(long position, String before, String after) {
        public Cell(long position, String before) { this(position, before, "minecraft:air"); }
    }

    public SitePreparationPlan {
        cells = List.copyOf(cells);
        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("Oversized site preparation");
        Set<Long> positions = new HashSet<>();
        for (Cell cell : cells) {
            if (!positions.add(cell.position) || cell.before == null || cell.before.isBlank()
                    || cell.before.length() > 4096 || cell.after == null || cell.after.isBlank()
                    || cell.after.length() > 4096)
                throw new IllegalArgumentException("Invalid site preparation cell");
        }
    }

    public String encode() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(-1); // Versioned terrain-work payload; format-22 removals remain readable.
                out.writeInt(cells.size());
                for (Cell cell : cells) {
                    out.writeLong(cell.position); out.writeUTF(cell.before); out.writeUTF(cell.after);
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static SitePreparationPlan decode(String encoded) {
        if (encoded.length() > 8_000_000) throw new IllegalArgumentException("Oversized site preparation");
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int count = in.readInt();
            boolean terrainWork = count == -1;
            if (terrainWork) count = in.readInt();
            if (count < 0 || count > MAX_CELLS) throw new IOException("Invalid preparation size");
            List<Cell> cells = new ArrayList<>(count);
            for (int i = 0; i < count; i++) cells.add(new Cell(in.readLong(), in.readUTF(),
                    terrainWork ? in.readUTF() : "minecraft:air"));
            if (in.available() != 0) throw new IOException("Trailing preparation data");
            return new SitePreparationPlan(cells);
        } catch (IOException ex) { throw new IllegalArgumentException("Invalid site preparation", ex); }
    }
}
