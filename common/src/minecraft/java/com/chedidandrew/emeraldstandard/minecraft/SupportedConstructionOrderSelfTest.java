package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import com.chedidandrew.emeraldstandard.core.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

final class SupportedConstructionOrderSelfTest {
    static void run() {
        var wood=Blocks.OAK_PLANKS.defaultBlockState(); var stone=Blocks.STONE.defaultBlockState();
        var example=List.of(new SupportedConstructionOrder.Cell(new BlockPos(0,3,0),wood,0),
                new SupportedConstructionOrder.Cell(new BlockPos(1,3,0),wood,0),
                new SupportedConstructionOrder.Cell(new BlockPos(0,0,0),stone,0),
                new SupportedConstructionOrder.Cell(new BlockPos(0,1,0),stone,2),
                new SupportedConstructionOrder.Cell(new BlockPos(0,2,0),stone,2),
                new SupportedConstructionOrder.Cell(new BlockPos(1,2,0),Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING,true),6));
        var sequence=SupportedConstructionOrder.sequence(example,List.of(0,example.size()));
        require(sequence.indices().indexOf(4)<sequence.indices().indexOf(0),"upper FOUNDATION floor waits for masonry below");
        require(sequence.indices().indexOf(1)<sequence.indices().indexOf(5),"hanging light waits for upper floor");
        require(sequence.disconnected().isEmpty(),"supported synthetic building has no disconnected cells");
        require(floating(example,sequence.indices())==0,"synthetic sequence stays connected to ground");
        for(int prefix=0;prefix<example.size();prefix++) {
            var migrated=SupportedConstructionOrder.sequence(example,List.of(prefix,example.size()));
            for(int i=0;i<prefix;i++) require(migrated.indices().get(i)==i,"upgrade preserves consumed historical prefix");
        }
        var replacements=new ArrayList<>(example); replacements.add(new SupportedConstructionOrder.Cell(new BlockPos(0,0,0),wood,0));
        var appended=SupportedConstructionOrder.sequence(replacements,List.of(0,example.size(),replacements.size()));
        require(appended.indices().subList(0,example.size()).equals(sequence.indices()),"future upgrades never reorder earlier ranges");
        var disconnectedReplacement=List.of(
                new SupportedConstructionOrder.Cell(new BlockPos(0,2,0),Blocks.DIAMOND_BLOCK.defaultBlockState(),6),
                new SupportedConstructionOrder.Cell(new BlockPos(0,1,0),stone,2),
                new SupportedConstructionOrder.Cell(new BlockPos(0,2,0),Blocks.OAK_LOG.defaultBlockState(),1));
        var preserved=SupportedConstructionOrder.sequence(disconnectedReplacement,List.of(0,3));
        require(preserved.indices().indexOf(0)<preserved.indices().indexOf(2),
                "legacy disconnected fallback cannot reverse replacements at the same coordinate");
        int catalogs=0,oldFloating=0,newFloating=0; var samples=new ArrayList<String>();
        for(var descriptor:VillageArchitecture.activeBlueprints()) for(var dialect:VillageArchitecture.BiomeDialect.values()) {
            var plan=AuthoredVillageStructures.plan(descriptor.type(),descriptor.templateId(),descriptor.templateRevision(),
                    VillageArchitecture.PALETTE_BALANCED,VillageArchitecture.DRESSING_PROSPEROUS,
                    VillageArchitecture.Character.RUSTIC,dialect,42L);
            var cells=plan.base().stream().map(c->new SupportedConstructionOrder.Cell(new BlockPos(c.x(),c.y(),c.z()),c.state(),c.phase().ordinal())).toList();
            var ordered=SupportedConstructionOrder.sequence(cells,List.of(0,cells.size()));
            require(new HashSet<>(ordered.indices()).size()==cells.size() && ordered.indices().size()==cells.size(),"exact permutation: "+descriptor.templateId());
            var again=SupportedConstructionOrder.sequence(cells,List.of(0,cells.size()));
            require(ordered.equals(again),"deterministic reload order: "+descriptor.templateId());
            int oldCount=floating(cells,java.util.stream.IntStream.range(0,cells.size()).boxed().toList());
            int newCount=floating(cells,ordered.indices());
            if(dialect==VillageArchitecture.BiomeDialect.PLAINS && samples.size()<24)
                for(int i:ordered.disconnected()) if(SupportedConstructionOrder.phase(cells.get(i))<4 && samples.size()<24)
                    samples.add(descriptor.templateId()+": "+cells.get(i));
            require(newCount<=oldCount,"ordering adds floating starts: "+descriptor.templateId()+" "+oldCount+" -> "+newCount);
            oldFloating+=oldCount; newFloating+=newCount; catalogs++;
        }
        require(newFloating<oldFloating,"catalog ordering improves unsupported starts");
        System.out.println("PASS support-first construction: "+catalogs+" master/dialect plans; floating structural starts "+oldFloating+" -> "+newFloating+"; exact cells, migration prefix, append-only upgrades and deterministic replay");
        System.out.println("Construction legacy disconnected samples: "+samples);
    }
    private static int floating(List<SupportedConstructionOrder.Cell> cells,List<Integer> order) {
        Set<BlockPos> built=new HashSet<>(); int count=0;
        for(int i:order) {
            var c=cells.get(i); if(c.state().isAir()) continue;
            if(SupportedConstructionOrder.phase(c)>=4) {if(SupportedConstructionOrder.anchor(c)) built.add(c.pos());continue;}
            boolean connected=c.pos().getY()<=0;
            for(BlockPos neighbor:SupportedConstructionOrder.contacts(c)) connected|=built.contains(neighbor);
            if(!connected) count++; built.add(c.pos());
        }
        return count;
    }
    private static void require(boolean value,String message) {if(!value) throw new IllegalStateException(message);}
}
