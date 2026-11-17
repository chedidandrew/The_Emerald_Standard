package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.mojang.serialization.JsonOps;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** The exact historical motif, all palettes/sides/transforms, and guarded one-shot live finishing. */
final class HitchingRailFinishingSelfTest {
    static void verify(ServerLevel level) {
        int catalogHitches=0;
        Set<VillageArchitecture.BiomeDialect> covered=new HashSet<>();
        for(var descriptor:VillageArchitecture.legacyBlueprints()) {
            if(descriptor.type()!=com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType.INN) continue;
            for(var dialect:VillageArchitecture.BiomeDialect.values()) {
                var authored=AuthoredVillageStructures.plan(descriptor.type(),descriptor.templateId(),descriptor.templateRevision(),
                        VillageArchitecture.PALETTE_BALANCED,VillageArchitecture.DRESSING_PROSPEROUS,
                        VillageArchitecture.Character.MERCANTILE,dialect);
                Map<BlockPos,BlockState> cells=new HashMap<>();
                for(var stage:List.of(authored.base(),authored.stageOne(),authored.stageTwo()))
                    for(var cell:stage) cells.put(new BlockPos(cell.x(),cell.y(),cell.z()),cell.state());
                authored.metadata().reservedAir().forEach(p->cells.putIfAbsent(p,Blocks.AIR.defaultBlockState()));
                int hitches=HitchingRailFinishing.extensions(cells).size();catalogHitches+=hitches;
                if(hitches>0) covered.add(dialect);
            }
        }
        require(catalogHitches>0&&covered.size()==5,"finisher did not reach the real historical Inn catalog");
        System.out.println("PASS real historical Inn catalog hitch detection: "+catalogHitches+" rails across five biome palettes; authored air remains reserved");
        BlockPos origin=new BlockPos(4120,level.getMaxY()-30,4120);
        Map<BlockPos,BlockState> before=new LinkedHashMap<>();
        UUID village=UUID.randomUUID();long project=1;
        var ledger=level.getDataStorage().computeIfAbsent(HitchingRailFinishing.Receipts.TYPE);
        int count=0;
        ServerPlayer occupant=null;
        try {
            for(int x=-9;x<=9;x++) for(int z=-9;z<=9;z++) for(int y=0;y<=4;y++) {
                BlockPos p=origin.offset(x,y,z);level.getChunk(p);before.put(p,level.getBlockState(p));
            }
            var method=AuthoredVillageStructures.class.getDeclaredMethod("addHitchingRailZ",
                    AuthoredVillageStructures.Builder.class,AuthoredVillageStructures.Materials.class,int.class,int.class,int.class);
            method.setAccessible(true);
            for(var dialect:VillageArchitecture.BiomeDialect.values()) for(int side:new int[]{-4,4})
                    for(var mirror:List.of(Mirror.NONE,Mirror.FRONT_BACK)) for(var rotation:Rotation.values()) {
                before.keySet().forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),18));
                var b=new AuthoredVillageStructures.Builder(Set.of());
                var materials=AuthoredVillageStructures.walkwayMaterials(VillageArchitecture.Character.MERCANTILE,dialect);
                method.invoke(null,b,materials,side,1,3);
                var original=b.values();Map<BlockPos,BlockState> plan=new HashMap<>();
                for(var cell:original) {
                    BlockPos local=new BlockPos(cell.x(),cell.y(),cell.z());
                    if(mirror==Mirror.FRONT_BACK) local=new BlockPos(-local.getX(),local.getY(),local.getZ());
                    plan.put(origin.offset(local.rotate(rotation)),cell.state().mirror(mirror).rotate(rotation));
                }
                Map<BlockPos,BlockState> frozen=Map.copyOf(plan);
                var additions=HitchingRailFinishing.extensions(plan);
                require(additions.size()==1&&additions.getFirst().caps().size()==2,"paired hitch not recognized "+dialect+side+mirror+rotation);
                require(frozen.equals(plan)&&original.equals(b.values()),"saved plan changed");
                var caps=additions.getFirst().caps();
                Map<BlockPos,BlockState> reservedAir=new HashMap<>(plan);
                reservedAir.put(caps.keySet().iterator().next(),Blocks.AIR.defaultBlockState());
                require(HitchingRailFinishing.extensions(reservedAir).isEmpty(),"cap occupied authored clearance");
                Map<BlockPos,BlockState> broken=new HashMap<>(plan);
                broken.remove(additions.getFirst().witnesses().keySet().stream().filter(p->plan.get(p).is(Blocks.IRON_BARS)).findFirst().orElseThrow());
                require(HitchingRailFinishing.extensions(broken).isEmpty(),"arbitrary unpaired metalwork accepted");
                plan.entrySet().stream().sorted(Comparator.comparingInt(e->e.getKey().getY()))
                        .forEach(e->level.setBlock(e.getKey(),e.getValue(),18));
                throughProduction(level,village,project,origin,plan);
                caps.forEach((p,s)->require(level.getBlockState(p).equals(s),"missing cap extension "+p));
                Map<BlockPos,BlockState> finished=new HashMap<>(plan);finished.putAll(caps);
                Map<BlockPos,BlockState> localFinished=new HashMap<>();
                finished.forEach((p,s)->localFinished.put(p.subtract(origin),s));
                Set<BlockPos> floating=NativeStructureSupport.floating(localFinished);
                for(BlockPos cap:caps.keySet()) {
                    require(!floating.contains(cap.subtract(origin))&&!floating.contains(cap.below().subtract(origin))
                            &&!floating.contains(cap.below(2).subtract(origin)),"timber/hanger lacks native grounding");
                }
                caps.keySet().forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),18));
                HitchingRailFinishing.finish(level,village,project,origin.asLong(),plan);
                caps.keySet().forEach(p->require(level.getBlockState(p).isAir(),"harvested cap regenerated"));
                project++;count++;
                if(count==1) {
                    BlockPos protectedCap=caps.keySet().iterator().next();
                    occupant=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"HitchFixture"),ClientInformation.createDefault());
                    occupant.setPos(protectedCap.getX()+.5,protectedCap.getY()-.9,protectedCap.getZ()+.5);
                    level.players().add(occupant);
                    long occupiedJob=project++;
                    int receiptCount=ledger.done.size();
                    HitchingRailFinishing.finish(level,village,occupiedJob,origin.asLong(),plan);
                    require(ledger.done.size()==receiptCount,"occupied pair consumed its retry receipt");
                    caps.keySet().forEach(p->require(level.getBlockState(p).isAir(),"cap placed through occupant"));
                    occupant.setPos(origin.getX()+8,origin.getY(),origin.getZ()+8);
                    HitchingRailFinishing.finish(level,village,occupiedJob,origin.asLong(),plan);
                    caps.forEach((p,s)->require(level.getBlockState(p).equals(s),"occupied pair failed safe retry"));
                    caps.keySet().forEach(p->level.setBlock(p,Blocks.AIR.defaultBlockState(),18));
                    Map<BlockPos,BlockState> distant=new HashMap<>();
                    BlockPos farOffset=new BlockPos(160000,0,160000);
                    plan.forEach((p,s)->distant.put(p.offset(farOffset),s));
                    require(distant.keySet().stream().noneMatch(p->level.hasChunk(p.getX()>>4,p.getZ()>>4)),"distant test unexpectedly loaded");
                    receiptCount=ledger.done.size();
                    HitchingRailFinishing.finish(level,village,project++,origin.asLong(),distant);
                    require(ledger.done.size()==receiptCount&&distant.keySet().stream().noneMatch(
                            p->level.hasChunk(p.getX()>>4,p.getZ()>>4)),"hitch finishing force-loaded land or consumed unloaded receipt");
                    try(var guard=VillageDevelopmentProtection.register(c->!c.position().equals(protectedCap))) {
                        HitchingRailFinishing.finish(level,village,project++,origin.asLong(),plan);
                        caps.keySet().forEach(p->require(level.getBlockState(p).isAir(),"protected pair partly written"));
                    }
                    level.setBlock(protectedCap,Blocks.DIAMOND_BLOCK.defaultBlockState(),18);
                    HitchingRailFinishing.finish(level,village,project++,origin.asLong(),plan);
                    require(level.getBlockState(protectedCap).is(Blocks.DIAMOND_BLOCK),"player obstruction overwritten");
                    level.setBlock(protectedCap,Blocks.AIR.defaultBlockState(),18);
                    BlockPos post=additions.getFirst().witnesses().keySet().stream().filter(p->plan.get(p).getBlock() instanceof FenceBlock).findFirst().orElseThrow();
                    level.setBlock(post,Blocks.AIR.defaultBlockState(),18);
                    HitchingRailFinishing.finish(level,village,project++,origin.asLong(),plan);
                    caps.keySet().forEach(p->require(level.getBlockState(p).isAir(),"cap added to damaged hitch"));
                }
            }
            var json=HitchingRailFinishing.Receipts.CODEC.encodeStart(JsonOps.INSTANCE,ledger).getOrThrow();
            var restored=HitchingRailFinishing.Receipts.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow();
            require(restored.done.equals(ledger.done),"one-shot receipts did not survive reload");
            System.out.println("PASS hitching rail timber extensions: "+count+" exact historical palettes/sides/transforms; frozen plans, native grounding, protection, player edits and persistent no-regeneration");
        } catch(Exception e) {throw new IllegalStateException(e);}
        finally {
            if(occupant!=null) {level.players().remove(occupant);occupant.discard();}
            before.forEach((p,s)->level.setBlock(p,s,18));
            ledger.done.removeIf(k->k.startsWith(village+"/"));ledger.setDirty();
        }
    }
    private static void throughProduction(ServerLevel level,UUID id,long projectId,BlockPos origin,Map<BlockPos,BlockState> plan)
            throws ReflectiveOperationException {
        var village=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageRecord();village.villageId=id;
        var project=new com.chedidandrew.emeraldstandard.core.EconomyState.VillageProject();
        project.projectId=projectId;project.type=com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.ProjectType.INN;
        project.designSchema=VillageArchitecture.BLUEPRINT_SCHEMA;project.designTemplateRevision=11;
        var placement=Class.forName(VillageProsperityManager.class.getName()+"$Placement");
        var constructor=placement.getDeclaredConstructor(int.class,int.class,int.class,BlockState.class);constructor.setAccessible(true);
        List<Object> cells=new ArrayList<>();
        for(var e:plan.entrySet()) {
            BlockPos p=e.getKey().subtract(origin);cells.add(constructor.newInstance(p.getX(),p.getY(),p.getZ(),e.getValue()));
        }
        var finish=VillageProsperityManager.class.getDeclaredMethod("finishHistoricalHitchingRail",ServerLevel.class,
                BlockPos.class,village.getClass(),project.getClass(),List.class);finish.setAccessible(true);
        finish.invoke(null,level,origin,village,project,cells);
    }
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
