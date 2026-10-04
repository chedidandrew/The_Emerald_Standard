package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.EmptyBlockGetter;

/** Frozen release geometry, not a procedural generator. Loaded only after block registration. */
final class ApprovedVillageStructures {
    record Data(int revision,ApprovedArchitectureCatalog.Entry identity, BlockPos entrance,Set<BlockPos> access,
            Set<BlockPos> air,List<AuthoredVillageStructures.Cell> base,
            List<AuthoredVillageStructures.Cell> first,List<AuthoredVillageStructures.Cell> second) {
        Map<BlockPos,BlockState> cells() {
            return CELL_CACHE.computeIfAbsent(revision+":"+identity.id(),ignored->collectCells());
        }
        private Map<BlockPos,BlockState> collectCells() {
            Map<BlockPos,BlockState> result=new LinkedHashMap<>();
            for(var stage:List.of(base,first,second)) for(var cell:stage)
                result.put(new BlockPos(cell.x(),cell.y(),cell.z()),cell.state());
            return Collections.unmodifiableMap(result);
        }
        BlockPos walkwayExit() {
            return EXIT_CACHE.computeIfAbsent(revision+":"+identity.id(),ignored->findWalkwayExit());
        }
        BlockPos door() {
            // Review entrance metadata is the first indoor standing cell, not the door itself.
            return cells().entrySet().stream().filter(e->e.getValue().getBlock() instanceof DoorBlock
                    &&e.getValue().getValue(DoorBlock.HALF)==net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                    .map(Map.Entry::getKey).min(Comparator.<BlockPos>comparingDouble(entrance::distSqr)
                            .thenComparingLong(BlockPos::asLong)).orElseThrow();
        }
        private BlockPos findWalkwayExit() {
            var cells=cells();
            // The doorstep's X is not necessarily a clear yard exit: reviewed lamps and
            // planters can occupy that column. Keep the authored grade and use a clear
            // front-edge column, preferring the actual route and then the nearest exit.
            return java.util.stream.IntStream.range(0,identity.width()).mapToObj(x->new BlockPos(x,0,0))
                    .filter(at->!cells.containsKey(at)&&!cells.containsKey(at.above()))
                    .filter(at->{var ground=cells.get(at.below());return ground!=null
                            &&ground.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.below(),net.minecraft.core.Direction.UP);})
                    .min(Comparator.<BlockPos>comparingInt(at->air.contains(at)?0:1)
                            .thenComparingInt(at->Math.abs(at.getX()-entrance.getX())).thenComparingInt(BlockPos::getX))
                    .orElseThrow(()->new IllegalStateException("No clear reviewed yard exit: "+identity.id()));
        }
    }
    private static final Map<String,Data> CACHE=new ConcurrentHashMap<>();
    private static final Map<String,Map<BlockPos,BlockState>> CELL_CACHE=new ConcurrentHashMap<>();
    private static final Map<String,BlockPos> EXIT_CACHE=new ConcurrentHashMap<>();
    static Data data(String id) { return data(id,ApprovedArchitectureCatalog.REVISION); }
    static Data data(String id,int revision) { return CACHE.computeIfAbsent(revision+":"+id,ignored->load(id,revision)); }
    private static Data load(String id,int revision) {
        var entry=ApprovedArchitectureCatalog.entry(id,revision);
        if(entry==null) throw new IllegalArgumentException("Unknown approved building "+id);
        try(var resource=ApprovedVillageStructures.class.getResourceAsStream(ApprovedArchitectureCatalog.resource(revision)+id+".bin.gz")) {
            if(resource==null) throw new IOException("Missing frozen building "+id);
            byte[] bytes;
            try(var gzip=new GZIPInputStream(resource)) { bytes=gzip.readNBytes(4_000_001); }
            if(bytes.length>4_000_000||!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(entry.sha256()))
                throw new IOException("Frozen building differs from approved release "+id);
            try(var in=new DataInputStream(new ByteArrayInputStream(bytes))) {
                if(in.readInt()!=0x5445530C||!in.readUTF().equals(id)||!in.readUTF().equals(entry.role())
                        ||!in.readUTF().equals(entry.dialect())||in.readInt()!=entry.width()
                        ||in.readInt()!=entry.depth()||in.readInt()!=entry.height()) throw new IOException("Invalid building header");
                BlockPos entrance=position(in); Set<BlockPos> access=positions(in),air=positions(in);
                var stages=List.of(new ArrayList<AuthoredVillageStructures.Cell>(),new ArrayList<AuthoredVillageStructures.Cell>(),
                        new ArrayList<AuthoredVillageStructures.Cell>());
                Set<BlockPos> occupied=new HashSet<>();
                int count=in.readInt(); if(count<=0||count>100_000) throw new IOException("Invalid cell count");
                for(int index=0;index<count;index++) {
                    BlockPos at=position(in); String serialized=in.readUTF(); int stage=in.readUnsignedByte();
                    if(stage>2||!occupied.add(at)||at.getX()<0||at.getX()>=entry.width()||at.getZ()<0||at.getZ()>=entry.depth()
                            ||at.getY()< -8||at.getY()>=entry.height()) throw new IOException("Invalid building cell "+at);
                    BlockState state=BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,serialized,false).blockState();
                    stages.get(stage).add(new AuthoredVillageStructures.Cell(at.getX(),at.getY(),at.getZ(),state,phase(at,state)));
                }
                if(in.available()!=0||air.stream().anyMatch(occupied::contains)) throw new IOException("Invalid circulation manifest");
                return new Data(revision,entry,entrance,access,air,List.copyOf(stages.get(0)),List.copyOf(stages.get(1)),List.copyOf(stages.get(2)));
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot admit approved architecture "+id,e); }
    }
    private static AuthoredVillageStructures.Phase phase(BlockPos at,BlockState state) {
        // Keep the release marker even on fluids and fixtures. The construction scheduler
        // classifies their native state, and must distinguish this immutable plan from v1-11.
        return AuthoredVillageStructures.Phase.APPROVED_STRUCTURE;
    }
    private static BlockPos position(DataInputStream in) throws IOException { return new BlockPos(in.readInt(),in.readInt(),in.readInt()); }
    private static Set<BlockPos> positions(DataInputStream in) throws IOException {
        int count=in.readInt(); if(count<0||count>100_000) throw new IOException("Invalid coordinate count");
        Set<BlockPos> result=new HashSet<>(); for(int index=0;index<count;index++) if(!result.add(position(in))) throw new IOException("Duplicate coordinate");
        return Set.copyOf(result);
    }
    static AuthoredVillageStructures.Blueprint plan(VillageProsperityEngine.ProjectType type,String id,int revision,String palette,String dressing,
            VillageArchitecture.Character character) {
        Data data=data(id,revision); var e=data.identity;
        if(!e.role().equals(type.name())) throw new IllegalArgumentException("Approved building role mismatch");
        if(!palette.equals(VillageArchitecture.PALETTE_BALANCED)||!dressing.equals(VillageArchitecture.DRESSING_PROSPEROUS))
            throw new IllegalArgumentException("Unsupported approved building palette/dressing");
        var metadata=new AuthoredVillageStructures.FrozenMetadata(type,true,data.entrance,data.access,data.air,Set.of(),List.of());
        var materials=AuthoredVillageStructures.planMaterials(11,palette,character,VillageArchitecture.BiomeDialect.fromId(e.dialect()));
        materials=new AuthoredVillageStructures.Materials(materials.dialect(),
                materials.dialect()==VillageArchitecture.BiomeDialect.TAIGA?Blocks.COBBLESTONE:materials.foundation(),
                materials.floor(),materials.wall(),materials.timber(),materials.roofStairs(),materials.roofSlab(),materials.fence(),materials.accent(),
                materials.dialect()==VillageArchitecture.BiomeDialect.DESERT?Blocks.JUNGLE_DOOR:materials.door(),materials.entryStairs(),materials.chimney());
        return new AuthoredVillageStructures.Blueprint(id,revision,palette,dressing,e.width(),e.depth(),e.height(),data.base,data.first,data.second,materials,metadata);
    }
}
