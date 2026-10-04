package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;

/** One-time, explicit release exporter. Gameplay never invokes the mutable review generators. */
public final class ApprovedArchitectureExporter {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        var registration=AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        registration.setAccessible(true); registration.invoke(null);
        System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
        Path directory=Path.of(args[0]); Files.createDirectories(directory);
        List<String> manifest=new ArrayList<>();
        manifest.add("# Frozen approved architecture revision 13: id role dialect width depth height minimumTier beds sha256");
        int doorstepRugs=0;
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            var plan=BiomeArchitecturePreview.plan(sample);
            if(sample.style()==VillageArchitecture.BiomeDialect.PLAINS) PreviewFacadePrograms.validate(plan);
            else BiomeArchitecturePreview.validate(plan);
            var site=PreviewOutdoorPrograms.site(plan);
            Map<BlockPos,net.minecraft.world.level.block.state.BlockState> cells=new HashMap<>(plan.cells());
            cells.putAll(site.cells());
            doorstepRugs+=NativeDoorwayClearance.setBackRugs(cells);
            int minX=cells.keySet().stream().mapToInt(BlockPos::getX).min().orElseThrow();
            int minZ=cells.keySet().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
            int width=cells.keySet().stream().mapToInt(BlockPos::getX).max().orElseThrow()-minX+1;
            int depth=cells.keySet().stream().mapToInt(BlockPos::getZ).max().orElseThrow()-minZ+1;
            int height=cells.keySet().stream().mapToInt(BlockPos::getY).max().orElseThrow()+1;
            String id="biome_"+sample.style().id()+"_"+(sample.id().startsWith("compact_")
                    ? sample.id().substring(("compact_"+sample.style().id()+"_").length())
                    : BiomeArchitectureCatalogPreview.masterId(sample));
            var ordered=cells.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator
                    .comparingInt((BlockPos at)->at.getY()).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX))).toList();
            // Only independent planting is postponed. Every bearing, room, light, bed and route
            // already exists at completion of stage zero; later prosperity adds safe cosmetics.
            Set<BlockPos> ornaments=new LinkedHashSet<>();
            ordered.stream().filter(e->e.getValue().getBlock() instanceof FlowerPotBlock)
                    .limit(2).forEach(e->ornaments.add(e.getKey()));
            var bytes=new ByteArrayOutputStream();
            try(var out=new DataOutputStream(bytes)) {
                out.writeInt(0x5445530C); out.writeUTF(id); out.writeUTF(sample.role()); out.writeUTF(sample.style().id());
                out.writeInt(width); out.writeInt(depth); out.writeInt(height);
                writePos(out,plan.entrance().offset(-minX,0,-minZ));
                var reachable=BiomeArchitecturePreview.reachable(plan);
                var access=plan.access().stream().filter(reachable::contains)
                        .sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
                out.writeInt(access.size()); for(var at:access) writePos(out,at.offset(-minX,0,-minZ));
                var air=new HashSet<BlockPos>();
                for(var at:BiomeArchitecturePreview.reachable(plan)) {
                    if(!cells.containsKey(at)) air.add(at);
                    if(!cells.containsKey(at.above())) air.add(at.above());
                }
                for(var at:site.routes()) { if(!cells.containsKey(at)) air.add(at); if(!cells.containsKey(at.above())) air.add(at.above()); }
                var clear=air.stream().sorted(Comparator.comparingLong(BlockPos::asLong)).toList();
                out.writeInt(clear.size()); for(var at:clear) writePos(out,at.offset(-minX,0,-minZ));
                out.writeInt(ordered.size()); int stage=0;
                for(var cell:ordered) {
                    writePos(out,cell.getKey().offset(-minX,0,-minZ));
                    out.writeUTF(BlockStateParser.serialize(cell.getValue()));
                    out.writeByte(ornaments.contains(cell.getKey())?++stage:0);
                }
            }
            byte[] content=bytes.toByteArray();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            try(var gzip=new GZIPOutputStream(Files.newOutputStream(directory.resolve(id+".bin.gz")))) { gzip.write(content); }
            int beds=(int)cells.values().stream().filter(s->s.getBlock() instanceof BedBlock&&s.getValue(BedBlock.PART)==BedPart.HEAD).count();
            int tier=PreviewArchitectureTiers.size(sample).minimumTier;
            manifest.add(String.join("\t",id,sample.role(),sample.style().id(),""+width,""+depth,""+height,""+tier,""+beds,hash));
        }
        Files.write(directory.resolve("catalog.tsv"),manifest,StandardCharsets.UTF_8);
        System.out.println("Exported "+(manifest.size()-1)+" immutable approved buildings with full yards and SHA-256 identities.");
        System.out.println("Set back "+doorstepRugs+" doorway-adjacent rug tiles for native villager clearance; other cells unchanged.");
    }
    private static void writePos(DataOutputStream out,BlockPos at) throws IOException {
        out.writeInt(at.getX()); out.writeInt(at.getY()); out.writeInt(at.getZ());
    }
}
