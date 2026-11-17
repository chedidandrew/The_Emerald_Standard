package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import com.chedidandrew.emeraldstandard.core.*;

/** Release gate over the frozen production assets, not only mutable authoring plans. */
final class NaturalBuildingFinishSelfTest {
    static void verify() {
        String previous=System.getProperty(StructureGallery.ENABLE_PROPERTY);
        try {
            System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
            verifyEnabled();
        } finally {
            if(previous==null)System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            else System.setProperty(StructureGallery.ENABLE_PROPERTY,previous);
        }
    }
    private static void verifyEnabled() {
        int gates=0,snowy=0,variedSnow=0,roofSnow=0;
        var authored=new HashMap<String,BiomeArchitecturePreview.Plan>();
        for(var sample:BiomeArchitectureCatalogPreview.samples()) {
            var plan=PreviewOutdoorLampOrientation.apply(PreviewBuildingUsability.apply(BiomeArchitecturePreview.plan(sample)));
            String id="biome_"+sample.style().id()+"_"+(sample.id().startsWith("compact_")
                    ?sample.id().substring(("compact_"+sample.style().id()+"_").length())
                    :BiomeArchitectureCatalogPreview.masterId(sample));
            authored.put(id,plan);
        }
        for(var entry:ApprovedArchitectureCatalog.entries()) {
            var core=ApprovedVillageStructures.data(entry.id(),20,3);
            var snowLayers=new HashSet<Integer>();
            roofSnow=0;
            var building=authored.get(entry.id());var offset=core.entrance().subtract(building.entrance());
            require(NativeDoorwayClearance.setBackRugs(new HashMap<>(core.cells()))==0,"Gate/door rug catches villagers: "+entry.id());
            var apron=core.cells().entrySet().stream().filter(e->!building.cells().containsKey(e.getKey().subtract(offset))).toList();
            require(apron.size()==1&&apron.getFirst().getKey().subtract(offset).equals(new BlockPos(building.entrance().getX(),-1,-1))
                    &&(apron.getFirst().getValue().is(Blocks.GRASS_BLOCK)||apron.getFirst().getValue().is(Blocks.SAND)),
                    "Standalone retains yard/front clutter: "+entry.id());
            for(var e:core.cells().entrySet()) {
                var at=e.getKey();var state=e.getValue();
                if(state.getBlock() instanceof FenceGateBlock) {
                    require(state.getValue(FenceGateBlock.OPEN),"Generated gate blocks villagers: "+entry.id());
                    require(!core.cells().containsKey(at.above()),"Gate retained upper door: "+entry.id());gates++;
                    for(var rotation:Rotation.values())for(var mirror:List.of(Mirror.NONE,Mirror.FRONT_BACK)) {
                        var transformed=state.mirror(mirror).rotate(rotation);
                        require(transformed.getBlock()==state.getBlock()&&transformed.getValue(FenceGateBlock.OPEN),"Gate transform changed wood/open state");
                    }
                }
                if(state.is(Blocks.SNOW)&&at.getY()>=5) {
                    var bearing=core.cells().get(at.below());
                    require(bearing!=null&&bearing.isFaceSturdy(EmptyBlockGetter.INSTANCE,at.below(),Direction.UP),"Floating roof snow: "+entry.id());
                    snowLayers.add(state.getValue(SnowLayerBlock.LAYERS));
                    roofSnow++;
                }
                if(state.is(Blocks.CAMPFIRE)&&state.getValue(CampfireBlock.SIGNAL_FIRE))
                    require(!core.cells().containsKey(at.above()),"Roof drift capped bank smoke");
            }
            if(entry.dialect().equals("snowy")) {
                // One design has only a single supported roof tile. An accessible guard
                // platform must remain clear instead of burying its declared standing cells.
                require(roofSnow<2||snowLayers.size()>=2,"Snowy roof lacks varied layered snow: "+entry.id());
                require(roofSnow>0||building.access().stream().anyMatch(at->at.getY()>=5),"Missing roof snow without usable roof access: "+entry.id());
                if(snowLayers.size()>=2)variedSnow++;
                snowy++;
            }
            require(core.identity().beds()==ApprovedVillageStructures.data(entry.id(),19,3).identity().beds(),"Capacity changed");
            require(core.cells().equals(ApprovedVillageStructures.data(entry.id(),20,3).cells()),"Nondeterministic frozen geometry");
        }
        require(gates>0&&snowy==75&&variedSnow>=73,"Missing open-building gate/snow coverage: "+gates+"/"+snowy+" varied="+variedSnow);
        fixtures();
        System.out.println("PASS natural building finish: 375 standalone entrances, "+gates+" open matched gates, "+variedSnow+" varied snow roofs, smoke/beds/native support preserved");
    }
    private static void fixtures() {
        for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","bamboo","crimson","warped","pale_oak")) {
            var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.PLAINS,"HOUSE","gate_fixture_"+wood,9,9);
            var b=new BiomeArchitecturePreview.Builder(sample);b.room(1,7,1,7,4);b.gable(1,7,1,7,5,false);b.door(4,1);
            var door=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:"+wood+"_door"));
            b.cells.put(new BlockPos(4,1,1),door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH));
            b.cells.put(new BlockPos(4,2,1),door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
            var sealed=b.finish();var kept=PreviewNaturalBuildingFinish.apply(sealed);
            require(kept.cells().get(new BlockPos(4,1,1)).is(door),"Sealed house converted to gate: "+wood);
            for(int z=3;z<=5;z++)b.cells.remove(new BlockPos(1,2,z)); // Open counter-side headroom.
            var opened=PreviewNaturalBuildingFinish.apply(b.finish());
            var gate=opened.cells().get(new BlockPos(4,1,1));
            require(BuiltInRegistries.BLOCK.getKey(gate.getBlock()).getPath().equals(wood+"_fence_gate"),"Wrong gate wood: "+wood);
            require(!opened.cells().containsKey(new BlockPos(4,2,1)),"Upper door not removed");
            require(opened.equals(PreviewNaturalBuildingFinish.apply(opened)),"Finish not idempotent");
        }
    }
    private static void require(boolean value,String reason){if(!value)throw new IllegalStateException(reason);}
}
