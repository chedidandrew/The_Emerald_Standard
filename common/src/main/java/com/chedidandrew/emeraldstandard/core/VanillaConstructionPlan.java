package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/**
 * A resolved, immutable building, not a live template reference. Contains block states only:
 * template entities, inventories, loot tables, commands and arbitrary block-entity NBT never enter
 * a construction plan. Old projects keep their exact cells when resources are changed.
 */
public final class VanillaConstructionPlan {
    public static final String SCHEMA = "vanilla_v1";
    public static final int MAX_CELLS = 12000;
    public record Cell(int x, int y, int z, String state) {}
    private final String templateId, style, role;
    private final int width, height, depth, beds;
    private final List<Cell> cells;
    private final String encoded, hash;

    public VanillaConstructionPlan(String templateId, String style, String role,
            int width, int height, int depth, int beds, List<Cell> cells) {
        if (!templateId.matches("minecraft:village/(plains|desert|savanna|taiga|snowy)/[a-z0-9_/]+")
                || !templateId.startsWith("minecraft:village/" + style + "/")
                || !Set.of("residence", "food", "craft", "trade", "civic").contains(role)
                || width < 1 || width > 48 || depth < 1 || depth > 48 || height < 1 || height > 32
                || beds < 0 || beds > 32 || cells.isEmpty() || cells.size() > MAX_CELLS)
            throw new IllegalArgumentException("Invalid vanilla construction plan");
        Set<String> positions = new HashSet<>();
        for (Cell cell : cells) {
            if (cell.x < 0 || cell.x >= width || cell.z < 0 || cell.z >= depth
                    || cell.y < -8 || cell.y >= height || cell.state.length() > 256
                    || !cell.state.matches("minecraft:[a-z0-9_]+(\\[[a-z0-9_=,]+\\])?")
                    || cell.state.startsWith("minecraft:jigsaw")
                    || cell.state.startsWith("minecraft:structure_block")
                    || cell.state.startsWith("minecraft:command_block")
                    || !positions.add(cell.x + "," + cell.y + "," + cell.z))
                throw new IllegalArgumentException("Invalid vanilla construction cell");
        }
        this.templateId = templateId; this.style = style; this.role = role;
        this.width = width; this.height = height; this.depth = depth; this.beds = beds;
        this.cells = List.copyOf(cells);
        byte[] bytes = bytes();
        try { this.hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        try {
            var out = new ByteArrayOutputStream();
            try (var zip = new DeflaterOutputStream(out)) { zip.write(bytes); }
            this.encoded = Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    private byte[] bytes() {
        try {
            var bytes = new ByteArrayOutputStream();
            var out = new DataOutputStream(bytes);
            out.writeInt(1); out.writeUTF(templateId); out.writeUTF(style); out.writeUTF(role);
            out.writeInt(width); out.writeInt(height); out.writeInt(depth); out.writeInt(beds);
            List<String> palette = cells.stream().map(Cell::state).distinct().toList();
            out.writeInt(palette.size());
            for (String state : palette) out.writeUTF(state);
            out.writeInt(cells.size());
            for (Cell c : cells) {
                out.writeByte(c.x); out.writeByte(c.y); out.writeByte(c.z);
                out.writeShort(palette.indexOf(c.state));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new UncheckedIOException(impossible); }
    }

    public static VanillaConstructionPlan decode(String value) {
        if (value == null || value.length() > 500000) throw new IllegalArgumentException("Oversized saved plan");
        try {
            var zip = new InflaterInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(value)));
            byte[] bytes;
            try (zip) { bytes = zip.readNBytes(400001); }
            if (bytes.length > 400000) throw new IOException("Oversized inflated plan");
            var in = new DataInputStream(new ByteArrayInputStream(bytes));
            if (in.readInt() != 1) throw new IOException("Unknown plan version");
            String id = in.readUTF(), style = in.readUTF(), role = in.readUTF();
            int w = in.readInt(), h = in.readInt(), d = in.readInt(), beds = in.readInt();
            int count = in.readInt();
            if (count < 1 || count > 1024) throw new IOException("Invalid palette");
            List<String> palette = new ArrayList<>();
            for (int i = 0; i < count; i++) palette.add(in.readUTF());
            count = in.readInt();
            if (count < 1 || count > MAX_CELLS) throw new IOException("Invalid cell count");
            List<Cell> cells = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int x = in.readByte(), y = in.readByte(), z = in.readByte(), state = in.readUnsignedShort();
                if (state >= palette.size()) throw new IOException("Invalid palette index");
                cells.add(new Cell(x, y, z, palette.get(state)));
            }
            if (in.available() != 0) throw new IOException("Trailing plan data");
            return new VanillaConstructionPlan(id, style, role, w, h, d, beds, cells);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid saved vanilla construction plan", error);
        }
    }
    public enum Kind {
        HOUSE("Village house"), FARM("Farm"), ANIMAL_PEN("Animal pen"), STABLE("Stable"),
        ARMORER("Armorer"), BUTCHER("Butcher shop"), FISHER("Fisher cottage"),
        CARTOGRAPHER("Cartographer"), FLETCHER("Fletcher"), LIBRARY("Library"),
        MASON("Mason"), SHEPHERD("Shepherd"), TANNERY("Tannery"), TEMPLE("Temple"),
        TOOLSMITH("Toolsmith"), WEAPONSMITH("Weaponsmith"), SQUARE("Village square");
        private final String title;
        Kind(String title) { this.title = title; }
        public String title() { return title; }
    }
    public Kind kind() {
        if (role.equals("residence")) return Kind.HOUSE;
        if (templateId.contains("farm")) return Kind.FARM;
        if (templateId.contains("animal_pen")) return Kind.ANIMAL_PEN;
        if (templateId.contains("stable")) return Kind.STABLE;
        if (templateId.contains("armorer")) return Kind.ARMORER;
        if (templateId.contains("butcher")) return Kind.BUTCHER;
        if (templateId.contains("fisher")) return Kind.FISHER;
        if (templateId.contains("cartographer")) return Kind.CARTOGRAPHER;
        if (templateId.contains("fletcher")) return Kind.FLETCHER;
        if (templateId.contains("library")) return Kind.LIBRARY;
        if (templateId.contains("mason")) return Kind.MASON;
        if (templateId.contains("shepherd")) return Kind.SHEPHERD;
        if (templateId.contains("tannery")) return Kind.TANNERY;
        if (templateId.contains("temple")) return Kind.TEMPLE;
        if (templateId.contains("tool_smith")) return Kind.TOOLSMITH;
        if (templateId.contains("weapon")) return Kind.WEAPONSMITH;
        return Kind.SQUARE;
    }
    /** Separate from economic ProjectType ordinals; safe in the existing map integer payload. */
    public int labelCode() { return 1000 + kind().ordinal(); }
    public static Kind labelKind(int code) {
        return code >= 1000 && code - 1000 < Kind.values().length ? Kind.values()[code - 1000] : null;
    }
    public String templateId() { return templateId; }
    public String style() { return style; }
    public String role() { return role; }
    public int width() { return width; }
    public int height() { return height; }
    public int depth() { return depth; }
    public int beds() { return beds; }
    public List<Cell> cells() { return cells; }
    public String encode() { return encoded; }
    public String hash() { return hash; }

    public boolean eligible(VillageProsperityEngine.ProjectType type) {
        return switch (type) {
            case COTTAGE -> role.equals("residence") && beds <= 2;
            case HOUSE -> role.equals("residence");
            // Inns and TES landmarks retain their identity; a vanilla house is not a hotel.
            case GRANARY -> role.equals("food");
            case SMITHY -> role.equals("craft");
            case MARKET_SQUARE -> role.equals("trade") || role.equals("civic");
            default -> false;
        };
    }
}
