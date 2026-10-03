package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.WholeBuildingBlueprint.Voxel;
import com.chedidandrew.emeraldstandard.core.WholeBuildingLightingValidator;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Hand-authored first art-direction samples. Never selected by gameplay or saved projects. */
public final class BiomeArchitecturePreview {
    public static final String PROPERTY = "the_emerald_standard.biomeArchitecturePreview";
    public static final String WORLD = "TES_Biome_Architecture_Preview";
    public static final String CATALOG_PROPERTY = PROPERTY + ".fullCatalog";
    public static final int REVISION = 4;
    public record Sample(BiomeDialect style, String role, String id, int width, int depth) { }
    public record Plan(Sample sample, Map<BlockPos, BlockState> cells, Set<BlockPos> access,
            BlockPos entrance, int height) {
        public BlockPos reverseView() {
            int desiredZ = sample.role().equals("BANK") ? entrance.getZ()+6
                    : sample.role().equals("INN") ? entrance.getZ()+7 : entrance.getZ()+5;
            return reachable(this).stream().filter(p -> p.getY()==entrance.getY())
                    .filter(p -> cells.get(p)==null || cells.get(p).isAir())
                    .min(Comparator.comparingInt((BlockPos p) -> Math.abs(p.getX()-entrance.getX())*3
                            + Math.abs(p.getZ()-desiredZ)).thenComparingInt(BlockPos::getZ)
                            .thenComparingInt(BlockPos::getX)).orElseThrow();
        }
        public List<StructureGalleryBlock> blocks(BlockPos origin) {
            return cells.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparingInt(BlockPos::getZ)
                            .thenComparingInt(BlockPos::getX)))
                    .map(e -> new StructureGalleryBlock(origin.offset(e.getKey()), e.getValue())).toList();
        }
    }
    public static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>();
        for (BiomeDialect style : BiomeDialect.values()) {
            if (style == BiomeDialect.PLAINS) {
                for (String role : List.of("HOUSE", "BANK", "INN"))
                    samples.add(PlainsLegacyArchitecturePreview.plan(role).sample());
                continue;
            }
            samples.add(new Sample(style, "HOUSE", style.id() + "_hearth_home", 13, 13));
            samples.add(new Sample(style, "BANK", style.id() + "_village_bank", 17, 17));
            if (style == BiomeDialect.PLAINS || style == BiomeDialect.DESERT)
                samples.add(new Sample(style, "INN", style.id() + "_guesthouse", 21, 17));
            if (style == BiomeDialect.DESERT)
                samples.add(new Sample(style, "SMITHY", "desert_forge_court", 17, 15));
        }
        return List.copyOf(samples);
    }
    public static Plan plan(Sample sample) {
        if(sample.id().startsWith("catalog_")) return BiomeArchitectureCatalogPreview.plan(sample);
        if (sample.style()==BiomeDialect.PLAINS) return PlainsLegacyArchitecturePreview.plan(sample.role());
        Builder b = new Builder(sample);
        switch (sample.role()) {
            case "BANK" -> bank(b);
            case "INN" -> inn(b);
            case "SMITHY" -> smithy(b);
            default -> home(b);
        }
        b.details();
        b.regionalCraft();
        Plan plan = b.finish();
        validate(plan);
        return plan;
    }
    public static List<Sample> reviewSamples() {
        return Boolean.getBoolean(CATALOG_PROPERTY) ? BiomeArchitectureCatalogPreview.samples() : samples();
    }

    private static void home(Builder b) {
        // Each family has its own envelope/arrival, not just a different roof material.
        switch (b.s.style()) {
            case DESERT -> {
                b.room(0, 9, 0, 12, 4); b.terrace(0, 9, 0, 12, 5);
                b.floor(10, 12, 0, 12); b.pergola(10, 12, 1, 10, 4);
                b.counter(10, 11, 1, 8); b.put(11, 2, 8, Blocks.POTTED_CACTUS);
                b.door(5, 0); b.window(0, 5, true); b.window(9, 5, true);
                b.beds(1, 8, 2); b.kitchen(6, 10); b.table(6, 5, 2);
                b.storage(1, 5); b.niche(1, 2); b.lamp(7, 3, 2); b.lamp(7, 3, 7);
            }
            case SAVANNA -> {
                b.room(0, 12, 3, 12, 4); b.floor(0, 12, 0, 2);
                b.hip(0, 12, 1, 12, 5); b.posts(new int[]{0, 12}, new int[]{0, 3}, 4);
                b.door(6, 3); b.window(0, 7, true); b.window(12, 7, true);
                b.beds(1, 9, 2); b.kitchen(9, 10); b.table(7, 6, 2);
                b.storage(10, 8); b.niche(1, 4); b.lamp(10, 3, 4); b.lamp(7, 3, 10);
                b.bench(2, 1, 3, Direction.SOUTH);
                b.lamp(3, 3, 1); b.lamp(9, 3, 1);
            }
            case TAIGA -> {
                b.room(1, 11, 2, 12, 4); b.gable(1, 11, 2, 12, 5, false);
                b.floor(3, 9, 0, 1); b.porch(3, 9, 0, 2, 3);
                b.door(6, 2); b.window(1, 6, true); b.window(11, 6, true);
                b.beds(2, 9, 2); b.kitchen(8, 10); b.table(7, 6, 2);
                b.storage(9, 8); b.niche(2, 3); b.lamp(9, 3, 3); b.lamp(7, 3, 10);
            }
            case SNOWY -> {
                b.room(0, 12, 2, 12, 4); b.gable(0, 12, 2, 12, 5, true);
                b.floor(4, 8, 0, 1); b.porch(4, 8, 0, 2, 3);
                b.door(6, 2); b.window(0, 6, true); b.window(12, 6, true);
                b.beds(1, 9, 2); b.kitchen(9, 10); b.table(7, 6, 2);
                b.storage(10, 8); b.niche(1, 3); b.lamp(10, 3, 3); b.lamp(7, 3, 10);
                b.put(1, 1, 6, Blocks.SMOKER); b.access.add(new BlockPos(2, 1, 6));
            }
            case PLAINS -> {
                b.room(0, 12, 2, 12, 4); b.gable(0, 12, 2, 12, 5, false);
                b.floor(3, 9, 0, 1); b.porch(3, 9, 0, 2, 3);
                b.door(6, 2); b.window(0, 6, true); b.window(12, 6, true);
                b.beds(1, 9, 2); b.kitchen(9, 10); b.table(7, 6, 2);
                b.storage(10, 8); b.niche(1, 3); b.lamp(10, 3, 3); b.lamp(7, 3, 10);
            }
        }
        int front = b.s.style() == BiomeDialect.DESERT ? 0 : b.s.style() == BiomeDialect.SAVANNA ? 3 : 2;
        b.window(b.s.style() == BiomeDialect.DESERT ? 2 : 3, front, false);
        if (b.s.style() != BiomeDialect.DESERT) b.window(9, front, false);
        b.lamp(b.s.style() == BiomeDialect.TAIGA ? 3 : 2, 3, 7);
    }

    private static void bank(Builder b) {
        b.room(0, 16, 2, 16, 5);
        if (b.s.style() == BiomeDialect.DESERT) {
            b.terrace(0, 16, 2, 16, 6);
            b.floor(3, 13, 0, 1); b.pergola(3, 13, 0, 1, 4);
        } else if (b.s.style() == BiomeDialect.SAVANNA) {
            b.hip(0, 16, 0, 16, 6); b.floor(0, 16, 0, 1);
            b.posts(new int[]{1, 15}, new int[]{0}, 5);
        } else {
            b.gable(0, 16, 2, 16, 6, b.s.style() == BiomeDialect.SNOWY);
            b.floor(4, 12, 0, 1); b.porch(4, 12, 0, 2, 4);
        }
        b.door(8, 2); b.window(0, 6, true); b.window(16, 6, true);
        b.window(4, 2, false); b.window(12, 2, false);
        b.put(8, 4, 2, Blocks.GLAZED_TERRACOTTA.green());
        for (int x : new int[]{6, 10}) for (int y=1; y<=5; y++) b.put(x,y,2,b.p.log);
        b.window(0, 13, true); b.window(16, 13, true);
        // One framed teller line, central customer aisle, lateral staff entrances.
        for (int x = 3; x <= 13; x++) {
            if (x == 8) b.put(x, 1, 10, BankerProfessionSupport.exchangeDeskOrLectern());
            else { b.put(x, 1, 10, b.p.trim); b.put(x, 2, 10, b.p.slab); }
        }
        for (int x : new int[]{3, 13}) {
            for (int y = 1; y <= 4; y++) b.put(x, y, 10, b.p.log);
        }
        for (int x = 3; x <= 13; x++) b.put(x, 5, 10, b.p.log);
        b.put(8, 4, 10, Blocks.GLAZED_TERRACOTTA.green());
        b.access.add(new BlockPos(8, 1, 9)); b.access.add(new BlockPos(8, 1, 11));
        b.table(3, 5, 2); b.bench(11, 5, 3, Direction.NORTH);
        // Deliberate ledger wall and record cabinets, all within a reachable staff area.
        for (int x = 5; x <= 11; x++) {
            b.put(x, 1, 15, Blocks.BOOKSHELF); b.put(x, 2, 15, Blocks.CHISELED_BOOKSHELF);
            b.put(x, 3, 15, b.p.slab); b.access.add(new BlockPos(x, 1, 14));
        }
        b.storage(2, 13); b.storage(13, 13); b.niche(1, 8);
        for (int x : new int[]{2, 14}) for (int z : new int[]{3, 8, 12}) b.lamp(x, 3, z);
        b.lamp(8, 3, 13); b.lamp(8, 4, 4);
        // Color is an inset floor, not an opaque green block obstacle.
        for (int z = 3; z <= 9; z++) b.put(8, 0, z, Blocks.DYED_TERRACOTTA.green());
    }

    private static void inn(Builder b) {
        b.room(0, 20, 0, 16, 4);
        if (b.s.style() == BiomeDialect.DESERT) b.terrace(0, 20, 0, 16, 5);
        else b.gable(0, 20, 0, 16, 5, false);
        b.door(10, 0);
        b.window(0, 5, true); b.window(20, 5, true);
        b.window(0, 13, true); b.window(20, 13, true);
        // Guest corridor behind a complete screen: lodging is not part of the taproom.
        for (int x = 1; x < 20; x++) if (x != 10)
            for (int y = 1; y <= 3; y++) b.put(x, y, 9, b.p.wall);
        for (int x : new int[]{5, 15}) for (int z = 10; z <= 15; z++)
            for (int y = 1; y <= 3; y++) b.put(x, y, z, b.p.wall);
        // Open side entries into four guest bays from the central corridor.
        for (int x : new int[]{5, 15}) for (int y = 1; y <= 2; y++) b.remove(x, y, 11);
        b.beds(1, 13, 1); b.beds(6, 13, 1); b.beds(12, 13, 1); b.beds(16, 13, 1);
        b.storage(2, 12); b.storage(7, 12); b.storage(12, 12); b.storage(17, 12);
        b.table(3, 4, 3); b.table(15, 4, 3);
        b.kitchen(2, 7); b.counter(14, 17, 1, 7);
        b.put(17, 2, 7, Blocks.FLOWER_POT);
        for (int x : new int[]{3, 10, 17}) for (int z : new int[]{2, 8, 12, 15})
            b.lamp(x, x==10&&z==2?4:3, z);
        b.niche(1, 2);
    }

    private static void smithy(Builder b) {
        // Sandstone work range plus a shaded, open forge court — no recolored timber gable.
        b.room(0, 7, 0, 14, 4); b.terrace(0, 7, 0, 14, 5);
        b.floor(8, 16, 0, 14); b.pergola(8, 16, 0, 14, 4);
        b.door(4, 0); b.window(0, 7, true);
        for (int y = 1; y <= 2; y++) b.remove(7, y, 5);
        b.put(1, 1, 10, Blocks.SMITHING_TABLE); b.access.add(new BlockPos(2, 1, 10));
        b.storage(2, 12); b.counter(1, 3, 1, 3);
        // Forge hood on full stone carcass; separate shaping and quenching stations.
        for (int x = 11; x <= 15; x++) {
            b.put(x, 1, 12, b.p.trim); b.put(x, 2, 12, b.p.trim);
            for (int y = 3; y <= 6; y++) b.put(x, y, 12, b.p.wall);
        }
        b.put(12, 1, 12, Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT, true));
        b.access.add(new BlockPos(12, 1, 11));
        b.put(10, 1, 8, Blocks.ANVIL); b.access.add(new BlockPos(10, 1, 7));
        b.put(14, 1, 8, Blocks.CAULDRON);
        b.put(12, 1, 5, Blocks.GRINDSTONE); b.access.add(new BlockPos(12, 1, 4));
        b.lamp(3, 3, 2); b.lamp(3, 3, 8); b.lamp(3, 3, 13);
        b.lamp(9, 3, 2); b.lamp(15, 3, 5); b.lamp(9, 3, 11);
    }

    record Palette(Block wall, Block log, Block floor, Block trim, Block stairs,
            Block slab, Block roof, Block roofStairs, Block roofSlab, Block door) { }
    private static Palette palette(BiomeDialect style) {
        return switch (style) {
            case DESERT -> new Palette(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE,
                    Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE, Blocks.SANDSTONE_STAIRS,
                    Blocks.SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE, Blocks.SANDSTONE_STAIRS,
                    Blocks.SANDSTONE_SLAB, Blocks.OAK_DOOR);
            case SAVANNA -> new Palette(Blocks.ACACIA_PLANKS, Blocks.ACACIA_LOG,
                    Blocks.ACACIA_PLANKS, Blocks.COBBLESTONE, Blocks.ACACIA_STAIRS,
                    Blocks.ACACIA_SLAB, Blocks.ACACIA_PLANKS, Blocks.ACACIA_STAIRS,
                    Blocks.ACACIA_SLAB, Blocks.ACACIA_DOOR);
            case TAIGA -> new Palette(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR);
            case SNOWY -> new Palette(Blocks.SPRUCE_PLANKS, Blocks.STRIPPED_SPRUCE_LOG,
                    Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE, Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB, Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_STAIRS,
                    Blocks.SPRUCE_SLAB, Blocks.SPRUCE_DOOR);
            case PLAINS -> new Palette(Blocks.OAK_PLANKS, Blocks.OAK_LOG,
                    Blocks.OAK_PLANKS, Blocks.COBBLESTONE, Blocks.OAK_STAIRS,
                    Blocks.OAK_SLAB, Blocks.OAK_PLANKS, Blocks.OAK_STAIRS,
                    Blocks.OAK_SLAB, Blocks.OAK_DOOR);
        };
    }
    static final class Builder {
        final Sample s; final Palette p; final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        final Set<BlockPos> access = new HashSet<>(); BlockPos entrance;
        Builder(Sample s) { this.s = s; p = palette(s.style()); }
        void put(int x, int y, int z, Block block) { put(x, y, z, block.defaultBlockState()); }
        void put(int x, int y, int z, BlockState state) { cells.put(new BlockPos(x, y, z), state); }
        void remove(int x, int y, int z) { cells.remove(new BlockPos(x, y, z)); }
        void floor(int x0, int x1, int z0, int z1) {
            for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) put(x,0,z,p.floor);
        }
        void room(int x0, int x1, int z0, int z1, int h) {
            floor(x0,x1,z0,z1);
            for (int x=x0; x<=x1; x++) for (int z=z0; z<=z1; z++) {
                if (x!=x0 && x!=x1 && z!=z0 && z!=z1) continue;
                put(x,0,z,p.trim);
                for (int y=1; y<=h; y++) put(x,y,z,
                        (x==x0 || x==x1) && (z==z0 || z==z1) ? p.log : y==1 ? p.trim : p.wall);
            }
        }
        void door(int x,int z) {
            put(x,1,z,p.door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH));
            put(x,2,z,p.door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH)
                    .setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            entrance=new BlockPos(x,1,z+1);
        }
        void window(int x,int z,boolean side) {
            for (int y=2; y<=3; y++) for (int d=-1; d<=1; d++)
                put(side?x:x+d,y,side?z+d:z,Blocks.GLASS_PANE);
        }
        void posts(int[] xs,int[] zs,int h) {
            for (int x:xs) for (int z:zs) for (int y=1;y<=h;y++) put(x,y,z,p.log);
        }
        void porch(int x0,int x1,int z0,int z1,int h) {
            posts(new int[]{x0,x1},new int[]{z0},h);
            for (int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) put(x,h+1,z,p.roofSlab);
        }
        void pergola(int x0,int x1,int z0,int z1,int h) {
            posts(new int[]{x0,x1},new int[]{z0,z1},h);
            for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++)
                if(x==x0||x==x1||z==z0||z==z1||z%2==0) put(x,h+1,z,p.slab);
        }
        void terrace(int x0,int x1,int z0,int z1,int y) {
            for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
                put(x,y,z,p.roof);
                if(x==x0||x==x1||z==z0||z==z1) put(x,y+1,z,p.roofSlab);
            }
            for(int x:new int[]{x0,x1}) for(int z:new int[]{z0,z1}) put(x,y+1,z,p.roof);
        }
        void gable(int x0,int x1,int z0,int z1,int y,boolean snow) {
            // Taiga has a tall log gable; snowy uses a lower two-course step profile.
            boolean shallow=s.style()==BiomeDialect.SNOWY
                    || (s.style()==BiomeDialect.PLAINS && s.role().equals("BANK"));
            int mid=(x0+x1)/2;
            for(int x=x0;x<=x1;x++) {
                int rise=shallow?Math.min(x-x0,x1-x)/2:Math.min(x-x0,x1-x);
                int roofY=y+rise;
                for(int z=z0;z<=z1;z++) {
                    put(x,roofY,z,x==mid?p.roofSlab.defaultBlockState():p.roofStairs.defaultBlockState()
                            .setValue(StairBlock.FACING,x<mid?Direction.EAST:Direction.WEST));
                    if(snow) {
                        put(x,roofY,z,p.roof);
                        put(x,roofY+1,z,Blocks.SNOW.defaultBlockState());
                    }
                }
                for(int fy=y;fy<roofY;fy++) for(int z:new int[]{z0,z1}) put(x,fy,z,p.wall);
                if(roofY>y) for(int z:new int[]{z0,z1}) put(x,roofY-1,z,p.log);
            }
            for(int z:new int[]{z0,z1}) {
                int peak=y+(shallow?(mid-x0)/2:mid-x0);
                for(int fy=y;fy<peak;fy++) put(mid,fy,z,p.log);
                if(peak>=y+3) put(mid,y+1,z,Blocks.GLASS);
            }
        }
        void hip(int x0,int x1,int z0,int z1,int y) {
            for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
                int ring=Math.min(Math.min(x-x0,x1-x),Math.min(z-z0,z1-z));
                int top=y+Math.min(2,ring);
                // Closed stepped hip: full inner bearing removes slab-to-stair air gaps.
                for(int fy=y;fy<=top;fy++) put(x,fy,z,p.roof);
                put(x,top+1,z,p.roofSlab);
            }
        }
        void beds(int x,int z,int count) {
            for(int i=0;i<count;i++) {
                int bx=x+i*3;
                BlockState bed=Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
                put(bx,1,z,bed.setValue(BedBlock.PART,BedPart.FOOT));
                put(bx,1,z+1,bed.setValue(BedBlock.PART,BedPart.HEAD));
                access.add(new BlockPos(bx+1,1,z));
                // A full bedside cabinet meets both floor and pot; a top slab here floated.
                put(bx,1,z-1,Blocks.BARREL);
                put(bx,2,z-1, s.style()==BiomeDialect.DESERT ? Blocks.POTTED_CACTUS : Blocks.POTTED_DANDELION);
            }
        }
        void counter(int x0,int x1,int y,int z) {
            if(x0>x1) throw new IllegalArgumentException("Reversed counter endpoints");
            for(int x=x0;x<=x1;x++) {
                put(x,y,z,p.trim); put(x,y+1,z,p.slab.defaultBlockState().setValue(SlabBlock.TYPE,SlabType.BOTTOM));
            }
        }
        void kitchen(int x,int z) {
            put(x,1,z,Blocks.SMOKER); access.add(new BlockPos(x,1,z-1));
            put(x+1,1,z,Blocks.CRAFTING_TABLE); access.add(new BlockPos(x+1,1,z-1));
            put(x,2,z,p.trim); put(x+1,2,z,p.slab);
            int roof=cells.keySet().stream().filter(pos->pos.getX()==x&&pos.getZ()==z)
                    .mapToInt(BlockPos::getY).max().orElse(4);
            for(int y=3;y<=roof+1;y++) put(x,y,z,p.trim);
        }
        void storage(int x,int z) {
            put(x,1,z,Blocks.BARREL); access.add(new BlockPos(x,1,z-1));
            put(x+1,1,z,Blocks.BARREL); access.add(new BlockPos(x+1,1,z-1));
            put(x,2,z,p.slab); put(x+1,2,z,p.slab);
        }
        void table(int x,int z,int length) {
            // Grounded trestles/pedestals and thin tops, rather than unsupported slab spans.
            for(int i=0;i<length;i++) {
                put(x+i,1,z,s.style()==BiomeDialect.DESERT ? Blocks.CUT_SANDSTONE : fence());
                put(x+i,2,z,s.style()==BiomeDialect.DESERT ? Blocks.OAK_PRESSURE_PLATE
                        : s.style()==BiomeDialect.SAVANNA ? Blocks.ACACIA_PRESSURE_PLATE : Blocks.SPRUCE_PRESSURE_PLATE);
            }
            bench(x,z-1,length,Direction.SOUTH); bench(x,z+1,length,Direction.NORTH);
        }
        void bench(int x,int z,int length,Direction facing) {
            for(int i=0;i<length;i++) put(x+i,1,z,p.stairs.defaultBlockState().setValue(StairBlock.FACING,facing));
        }
        void niche(int x,int z) {
            put(x,1,z,p.trim); put(x,2,z,Blocks.POTTED_FERN);
            if(s.style()==BiomeDialect.DESERT) put(x,2,z,Blocks.POTTED_CACTUS);
        }
        void lamp(int x,int y,int z) {
            // A wall-to-wall ceiling tie carries the pendant; no freestanding lamp pedestals.
            BlockState beam=p.log.defaultBlockState();
            if(beam.hasProperty(BlockStateProperties.AXIS)) beam=beam.setValue(BlockStateProperties.AXIS,Direction.Axis.X);
            for(int bx=0;bx<s.width();bx++) put(bx,y+1,z,beam);
            put(x,y,z,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
        void details() {
            boolean desert=s.style()==BiomeDialect.DESERT;
            Block fence=s.style()==BiomeDialect.SAVANNA ? Blocks.ACACIA_FENCE
                    : s.style()==BiomeDialect.TAIGA||s.style()==BiomeDialect.SNOWY ? Blocks.SPRUCE_FENCE : Blocks.OAK_FENCE;
            // Side rails frame existing verandas; the center arrival stays wide and open.
            int[] edges=s.role().equals("HOUSE") ? (desert ? new int[]{10,12}
                    : s.style()==BiomeDialect.SAVANNA ? new int[]{1,11}
                    : s.style()==BiomeDialect.TAIGA ? new int[]{3,9} : new int[]{4,8})
                    : s.role().equals("BANK") ? new int[]{4,12} : new int[0];
            for(int x:edges) {
                for(int z=0;z<=1;z++)
                    if(!cells.containsKey(new BlockPos(x,1,z)) && cells.containsKey(new BlockPos(x,0,z)))
                        put(x,1,z,desert ? Blocks.SANDSTONE_WALL : fence);
            }
            // Pendant porch lanterns genuinely hang from existing canopy/beam members.
            for(int x:edges) {
                int lampZ=desert && s.role().equals("HOUSE") ? 2 : 1;
                for(int y=3;y<=4;y++) {
                    BlockPos at=new BlockPos(x,y,lampZ);
                    BlockState support=cells.get(at.above());
                    if(!cells.containsKey(at)&&support!=null) {
                        put(x,y,lampZ,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
                        break;
                    }
                }
            }
            // Supported potted plants on actual cabinets, not random full cubes in the aisle.
            Block plant=desert ? Blocks.POTTED_CACTUS : s.style()==BiomeDialect.SAVANNA ? Blocks.POTTED_ACACIA_SAPLING
                    : s.style()==BiomeDialect.TAIGA ? Blocks.POTTED_SPRUCE_SAPLING
                    : s.style()==BiomeDialect.SNOWY ? Blocks.POTTED_FERN : Blocks.POTTED_POPPY;
            List<BlockPos> cabinets=cells.entrySet().stream().filter(e->e.getValue().is(Blocks.BARREL))
                    .map(Map.Entry::getKey).sorted(Comparator.comparingInt((BlockPos pos)->pos.getZ())
                            .thenComparingInt(BlockPos::getX)).toList();
            for(int i=0;i<cabinets.size();i+=2) {
                BlockPos at=cabinets.get(i).above();
                if(cells.get(at)!=null && cells.get(at).getBlock() instanceof SlabBlock) {
                    // The pot sits directly on the barrel. Raising its slab cap left a half-block gap.
                    put(at.getX(),at.getY(),at.getZ(),plant);
                }
            }
            // Rugs follow the circulation spine; only empty supported floor cells are eligible.
            int center=entrance.getX(), end=s.role().equals("BANK")?9:s.role().equals("INN")?8:7;
            Block rug=desert ? Blocks.CARPET.brown() : s.style()==BiomeDialect.SAVANNA ? Blocks.CARPET.orange()
                    : s.style()==BiomeDialect.TAIGA ? Blocks.CARPET.green() : Blocks.CARPET.red();
            for(int x:new int[]{center-1,center+1}) for(int z=entrance.getZ()+1;z<end;z++) {
                BlockPos at=new BlockPos(x,1,z);
                if(!cells.containsKey(at)&&walkable(cells,at)) put(x,1,z,rug);
            }
            // Low planted porch boxes: two ends, never the doorway or a furniture approach.
            if(s.role().equals("BANK")||(!desert&&s.role().equals("HOUSE"))) {
                for(int x:new int[]{edges[0]+1,edges[1]-1}) {
                    BlockPos at=new BlockPos(x,1,1);
                    if(!cells.containsKey(at)&&cells.containsKey(at.below())&&!access.contains(at)) {
                        put(x,1,1,desert?Blocks.CUT_SANDSTONE:p.log);
                        put(x,2,1,desert?Blocks.POTTED_CACTUS
                                : s.style()==BiomeDialect.TAIGA||s.style()==BiomeDialect.SNOWY ? Blocks.POTTED_FERN
                                : Blocks.POTTED_POPPY);
                    }
                }
            }
        }
        Block fence() { return s.style()==BiomeDialect.SAVANNA ? Blocks.ACACIA_FENCE : Blocks.SPRUCE_FENCE; }

        void pier(int x,int z,int height,Block material) {
            for(int y=1;y<=height;y++) put(x,y,z,material);
        }
        void framedFront(int center,int z) {
            // In-plane jambs and sills don't create stray entrance obstacles.
            for(int x:new int[]{center-2,center+2}) for(int y=1;y<=4;y++) put(x,y,z,p.log);
            for(int x=center-1;x<=center+1;x++) {
                put(x,1,z,p.trim); put(x,4,z,p.log);
            }
        }
        void recordsAlcove(int x0,int x1,int z,Block frame) {
            pier(x0,z,3,frame); pier(x1,z,3,frame);
            for(int x=x0+1;x<x1;x++) {
                put(x,1,z,Blocks.BOOKSHELF); put(x,2,z,Blocks.CHISELED_BOOKSHELF);
                put(x,3,z,p.slab); access.add(new BlockPos(x,1,z-1));
            }
            for(int x=x0;x<=x1;x++) put(x,4,z,frame);
        }
        void stove(int x,int z) {
            put(x,1,z,Blocks.SMOKER.defaultBlockState().setValue(BlockStateProperties.LIT,true));
            access.add(new BlockPos(x,1,z-1));
            int roof=cells.keySet().stream().filter(at->at.getX()==x&&at.getZ()==z)
                    .mapToInt(BlockPos::getY).max().orElse(5);
            for(int y=2;y<=roof+1;y++) put(x,y,z,Blocks.STONE_BRICKS);
            for(int side:new int[]{x-1,x+1}) {
                pier(side,z,2,p.trim); put(side,3,z,Blocks.STONE_BRICK_SLAB);
            }
        }
        void regionalCraft() {
            if(s.role().equals("BANK")) {
                framedFront(4,2); framedFront(12,2);
                // A defined public room, teller rail and archive remain distinct and usable.
                for(int x:new int[]{3,13}) pier(x,10,4,p.log);
                for(int x=3;x<=13;x++) if(x!=8) put(x,2,10,
                        s.style()==BiomeDialect.DESERT ? Blocks.SANDSTONE_SLAB : p.slab);
                for(int x:new int[]{4,12}) {
                    put(x,3,10,Blocks.LANTERN); // on the counter's full-height end post below
                    put(x,2,10,p.log);
                }
                switch(s.style()) {
                    case DESERT -> {
                        for(int x:new int[]{2,6,10,14}) {
                            pier(x,2,5,Blocks.CUT_SANDSTONE); put(x,4,2,Blocks.CHISELED_SANDSTONE);
                            put(x,7,2,Blocks.CUT_SANDSTONE); put(x,7,16,Blocks.CUT_SANDSTONE);
                        }
                        for(int x=0;x<=16;x++) put(x,5,2,Blocks.CHISELED_SANDSTONE);
                        for(int x:new int[]{4,12}) put(x,4,0,Blocks.SANDSTONE_STAIRS.defaultBlockState()
                                .setValue(StairBlock.HALF,Half.TOP).setValue(StairBlock.FACING,x==4?Direction.WEST:Direction.EAST));
                        recordsAlcove(1,5,8,Blocks.CUT_SANDSTONE);
                        for(int x=11;x<=15;x++) {
                            put(x,1,7,Blocks.CUT_SANDSTONE); put(x,2,7,Blocks.SANDSTONE_SLAB);
                        }
                        put(14,2,7,Blocks.POTTED_CACTUS);
                        for(int x:new int[]{1,15}) for(int z=3;z<=15;z++) put(x,0,z,Blocks.CUT_SANDSTONE);
                    }
                    case SAVANNA -> {
                        // Vanilla's gray bark structure with restrained terracotta upper panels.
                        for(int x:new int[]{2,6,10,14}) pier(x,2,5,Blocks.ACACIA_LOG);
                        for(int x=1;x<16;x++) if(x!=6&&x!=8&&x!=10) put(x,5,2,Blocks.TERRACOTTA);
                        for(int x=2;x<=5;x++) put(x,1,0,Blocks.ACACIA_FENCE);
                        for(int x=11;x<=14;x++) put(x,1,0,Blocks.ACACIA_FENCE);
                        for(int x:new int[]{1,15}) for(int y=2;y<=4;y++) put(x,y,1,Blocks.ACACIA_FENCE);
                        recordsAlcove(1,5,8,Blocks.ACACIA_LOG);
                        for(int x=11;x<=15;x++) put(x,1,7,Blocks.BARREL);
                        put(11,2,7,Blocks.POTTED_ACACIA_SAPLING);
                        put(15,2,7,Blocks.POTTED_DEAD_BUSH);
                        for(int x=2;x<=14;x++) if(x<7||x>9) put(x,0,8,Blocks.TERRACOTTA);
                    }
                    case TAIGA -> {
                        stoneFront(); shutters(4,2); shutters(12,2);
                        stove(2,7); recordsAlcove(11,15,7,Blocks.SPRUCE_LOG);
                        for(int x=1;x<=15;x++) put(x,5,2,Blocks.SPRUCE_LOG.defaultBlockState()
                                .setValue(BlockStateProperties.AXIS,Direction.Axis.X));
                        for(int x:new int[]{1,15}) for(int z=3;z<=15;z++) put(x,0,z,Blocks.COBBLESTONE);
                        put(1,0,13,Blocks.MOSSY_COBBLESTONE); put(15,0,5,Blocks.MOSSY_COBBLESTONE);
                    }
                    case SNOWY -> {
                        // Sheltered wind porch and a warm reading/stove side, not a Taiga recolor.
                        for(int x:new int[]{4,12}) for(int z=0;z<=1;z++) pier(x,z,2,Blocks.COBBLESTONE);
                        for(int x:new int[]{4,12}) put(x,3,0,Blocks.SNOW);
                        for(int x:new int[]{2,6,10,14}) pier(x,2,5,Blocks.STRIPPED_SPRUCE_LOG);
                        recordsAlcove(1,5,8,Blocks.STRIPPED_SPRUCE_LOG); stove(14,7);
                        for(int x=1;x<=15;x++) if(x!=6&&x!=8&&x!=10) put(x,5,2,Blocks.SPRUCE_PLANKS);
                        for(int x:new int[]{0,16}) for(int z=3;z<=15;z++) {
                            BlockState at=cells.get(new BlockPos(x,3,z));
                            if(at!=null&&!at.is(Blocks.GLASS_PANE)) put(x,3,z,Blocks.WOOL.white());
                        }
                        for(int z=4;z<=8;z+=2) for(int x:new int[]{7,9}) {
                            BlockState at=cells.get(new BlockPos(x,1,z));
                            if(at!=null&&at.getBlock() instanceof CarpetBlock) put(x,1,z,Blocks.CARPET.white());
                        }
                    }
                    default -> throw new IllegalStateException("Plains must use its untouched native copy");
                }
            } else if(s.role().equals("HOUSE")) {
                int front=s.style()==BiomeDialect.DESERT?0:s.style()==BiomeDialect.SAVANNA?3:2;
                framedFront(s.style()==BiomeDialect.DESERT?2:3,front);
                if(s.style()!=BiomeDialect.DESERT) framedFront(9,front);
                switch(s.style()) {
                    case DESERT -> {
                        for(int x:new int[]{0,9}) for(int z=2;z<=10;z+=4) put(x,4,z,Blocks.CHISELED_SANDSTONE);
                        for(int x:new int[]{2,7}) put(x,6,0,Blocks.CUT_SANDSTONE);
                        put(1,1,3,Blocks.BOOKSHELF); put(1,2,3,Blocks.POTTED_DEAD_BUSH);
                    }
                    case SAVANNA -> {
                        for(int x:new int[]{0,12}) for(int z=4;z<=11;z++) put(x,4,z,Blocks.TERRACOTTA);
                        for(int x=1;x<=4;x++) put(x,1,0,Blocks.ACACIA_FENCE);
                        for(int x=8;x<=11;x++) put(x,1,0,Blocks.ACACIA_FENCE);
                        put(10,1,6,Blocks.BOOKSHELF); put(10,2,6,Blocks.POTTED_ACACIA_SAPLING);
                    }
                    case TAIGA -> {
                        shutters(3,front); shutters(9,front);
                        for(int x=2;x<=10;x++) if(x!=6) put(x,1,front,Blocks.COBBLESTONE);
                        put(2,1,front,Blocks.MOSSY_COBBLESTONE);
                        put(2,1,5,Blocks.BOOKSHELF); put(2,2,5,Blocks.LANTERN);
                    }
                    case SNOWY -> {
                        for(int x:new int[]{0,12}) for(int z=3;z<=11;z++) {
                            BlockState at=cells.get(new BlockPos(x,3,z));
                            if(at!=null&&!at.is(Blocks.GLASS_PANE)) put(x,3,z,Blocks.WOOL.white());
                        }
                        put(1,2,6,Blocks.STONE_BRICKS); put(1,3,6,Blocks.STONE_BRICK_SLAB);
                        put(10,1,6,Blocks.BOOKSHELF); put(10,2,6,Blocks.POTTED_FERN);
                    }
                    default -> throw new IllegalStateException("Plains must use its untouched native copy");
                }
            } else if(s.role().equals("INN")) {
                for(int x:new int[]{1,6,14,19}) pier(x,8,3,Blocks.CUT_SANDSTONE);
                for(int x=1;x<20;x++) put(x,4,8,Blocks.CHISELED_SANDSTONE);
                recordsAlcove(6,8,7,Blocks.CUT_SANDSTONE);
                for(int x:new int[]{1,19}) put(x,6,0,Blocks.CUT_SANDSTONE);
            } else if(s.role().equals("SMITHY")) {
                for(int z=1;z<=13;z+=4) put(7,4,z,Blocks.CHISELED_SANDSTONE);
                put(3,1,12,Blocks.BARREL); put(3,2,12,Blocks.STONE_PRESSURE_PLATE);
                put(11,1,3,Blocks.BARREL); put(11,2,3,Blocks.FLOWER_POT);
            }
        }
        void stoneFront() {
            for(int x=1;x<16;x++) for(int y=1;y<=3;y++) {
                BlockState at=cells.get(new BlockPos(x,y,2));
                if(at!=null&&at.is(p.wall)) put(x,y,2,Blocks.COBBLESTONE);
            }
        }
        void shutters(int center,int z) {
            for(int x:new int[]{center-2,center+2}) for(int y=2;y<=3;y++)
                put(x,y,z-1,Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                        .setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.NORTH));
        }
        Plan finish() {
            return new Plan(s,Map.copyOf(cells),Set.copyOf(access),entrance,
                    cells.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0)+1);
        }
    }

    public static void validate(Plan p) {
        validateFurnitureSupport(p);
        Set<BlockPos> reach=reachable(p);
        if(!reach.containsAll(p.access())) {
            Set<BlockPos> missing=new HashSet<>(p.access()); missing.removeAll(reach);
            throw new IllegalStateException(p.sample().id()+" blocked furniture approaches: "+missing);
        }
        for(BlockPos pos:p.cells().keySet()) if(pos.getX()<0||pos.getZ()<0
                ||pos.getX()>=p.sample().width()||pos.getZ()>=p.sample().depth())
            throw new IllegalStateException("Preview exceeds declared envelope: "+p.sample().id());
        Map<Voxel,Integer> dampening=new HashMap<>();
        List<WholeBuildingLightingValidator.LightEmitter> lights=new ArrayList<>();
        p.cells().forEach((pos,state)->{
            Voxel voxel=new Voxel(pos.getX(),pos.getY(),pos.getZ());
            if(state.getLightEmission()>0) lights.add(new WholeBuildingLightingValidator.LightEmitter(voxel,state.getLightEmission()));
            if(state.getLightDampening()>0) dampening.put(voxel,state.getLightDampening());
        });
        WholeBuildingLightingValidator.validate(new WholeBuildingLightingValidator.LightingSnapshot(
                p.sample().id(),new WholeBuildingLightingValidator.Bounds(new Voxel(-1,-1,-1),
                new Voxel(p.sample().width(),p.height()+1,p.sample().depth())),
                reach.stream().map(pos->new Voxel(pos.getX(),pos.getY(),pos.getZ())).collect(java.util.stream.Collectors.toSet()),
                dampening,lights)).requireSpawnSafe();
    }
    static void validateFurnitureSupport(Plan p) {
        // Native survival alone admits slabs hovering in mid-air. Check these interior fixtures
        // separately from deliberately spanning roof beams, arches and porch canopies.
        if(p.sample().style()==BiomeDialect.PLAINS) return;
        p.cells().forEach((pos,state)->{
            boolean pot=state.getBlock() instanceof FlowerPotBlock;
            boolean lowSlab=state.getBlock() instanceof SlabBlock&&pos.getY()>0&&pos.getY()<=3;
            boolean tableTop=state.getBlock() instanceof BasePressurePlateBlock&&pos.getY()>0&&pos.getY()<=3;
            if(!pot&&!lowSlab&&!tableTop) return;
            BlockState below=p.cells().get(pos.below());
            boolean meetsTop=below!=null&&below.isFaceSturdy(EmptyBlockGetter.INSTANCE,pos.below(),Direction.UP);
            // Fence trestles have a one-block visible post, despite their taller collision fence.
            if(tableTop&&below!=null&&below.getBlock() instanceof FenceBlock) meetsTop=true;
            boolean grounded=meetsTop;
            for(BlockPos foot=pos.below();grounded&&foot.getY()>=0;foot=foot.below()) {
                BlockState bearing=p.cells().get(foot);
                boolean leg=tableTop&&foot.equals(pos.below())&&bearing!=null&&bearing.getBlock() instanceof FenceBlock;
                grounded=bearing!=null&&(leg||bearing.isFaceSturdy(EmptyBlockGetter.INSTANCE,foot,Direction.UP));
            }
            if(!grounded||(lowSlab&&state.getValue(SlabBlock.TYPE)==SlabType.TOP))
                throw new IllegalStateException(p.sample().id()+" unsupported interior fixture at "+pos+": "+state);
        });
    }
    private static Set<BlockPos> reachable(Plan p) {
        Set<BlockPos> reach=new HashSet<>(); ArrayDeque<BlockPos> queue=new ArrayDeque<>();
        queue.add(p.entrance());
        while(!queue.isEmpty()) {
            BlockPos pos=queue.removeFirst();
            if(!walkable(p.cells(),pos)||!reach.add(pos)) continue;
            for(Direction d:Direction.Plane.HORIZONTAL) queue.add(pos.relative(d));
        }
        return reach;
    }
    private static boolean walkable(Map<BlockPos,BlockState> cells,BlockPos pos) {
        BlockState floor=cells.get(pos.below());
        return floor!=null && floor.isFaceSturdy(EmptyBlockGetter.INSTANCE,pos.below(),Direction.UP)
                && clear(cells.get(pos)) && clear(cells.get(pos.above()));
    }
    private static boolean clear(BlockState state) {
        return state==null||state.isAir()||state.getBlock() instanceof DoorBlock||state.getBlock() instanceof CarpetBlock;
    }
}
