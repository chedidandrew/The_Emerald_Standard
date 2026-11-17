package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Independent archived/current geometry proof, including every snowy outdoor fit and Bank. */
final class SnowyRoofOverhangSelfTest {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var registration=AuthoredVillageStructuresSelfTest.class.getDeclaredMethod("registerExactDeskFixture");
        registration.setAccessible(true);registration.invoke(null);verify();
    }
    static void verify() {
        int changed=0,roofs=0,snow=0;
        for(var e:ApprovedArchitectureCatalog.entries())for(int fit=0;fit<=3;fit++) {
            var old=ApprovedVillageStructures.data(e.id(),18,fit);
            var now=ApprovedVillageStructures.data(e.id(),19,fit);
            if(!e.dialect().equals("snowy")) {
                require(old.identity().sha256().equals(now.identity().sha256()),"Other biome changed: "+e.id()+" fit="+fit);
                continue;
            }
            require(old.identity().height()==now.identity().height()&&old.identity().beds()==now.identity().beds(),
                    "Ridge/bed count changed: "+e.id());
            require(old.identity().minimumTier()==now.identity().minimumTier(),"Tier gate changed: "+e.id());
            // The main shell and all furnished rooms remain exact relative to the entrance.
            var oldCore=ApprovedVillageStructures.data(e.id(),18,3);
            var core=ApprovedVillageStructures.data(e.id(),19,3);
            BlockPos offset=core.entrance().subtract(oldCore.entrance());
            var previous=new HashMap<BlockPos,BlockState>();
            oldCore.cells().forEach((at,s)->previous.put(at.offset(offset),s));
            // The exporter retains a small site apron even in fit3. Its ground columns
            // follow the resized reservation, unlike the immutable furnished building.
            previous.forEach((at,s)-> {
                if(apron(at,s))return;
                require(s.equals(core.cells().get(at)),"Original core cell changed: "+e.id()+" "+at+" "+s);
            });
            int local=0;
            for(var cell:core.cells().entrySet())if(!previous.containsKey(cell.getKey())) {
                var at=cell.getKey();var s=cell.getValue();
                if(apron(at,s))continue;
                boolean roof=s.is(Blocks.SPRUCE_PLANKS)||s.is(Blocks.SPRUCE_STAIRS)||s.is(Blocks.SPRUCE_SLAB);
                require(roof||s.is(Blocks.SNOW)||s.is(Blocks.LANTERN)||s.is(Blocks.IRON_CHAIN),"Unexpected addition: "+e.id()+" "+at+" "+s);
                if(roof) {
                    require(at.getY()>=5&&previous.entrySet().stream().anyMatch(c->c.getKey().getY()==at.getY()
                        &&Math.abs(c.getKey().getX()-at.getX())<=1&&Math.abs(c.getKey().getZ()-at.getZ())<=1
                        &&s.equals(c.getValue())),"More than one block of roof projection: "+e.id()+" "+at);
                    roofs++;
                } else if(s.is(Blocks.SNOW)) {
                    var bearing=core.cells().get(at.below());
                    require(bearing!=null&&bearing.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,at.below(),Direction.UP),
                            "Floating snow: "+e.id()+" "+at);snow++;
                }
                local++;
            }
            require(oldCore.access().stream().map(at->at.offset(offset)).collect(java.util.stream.Collectors.toSet()).equals(core.access()),"Access changed: "+e.id());
            if(fit==0&&local>0) {changed++;System.out.println("SNOWY EAVES "+e.id()+": "+local+" added core cells");}
        }
        long expected=ApprovedArchitectureCatalog.entries().stream().filter(e->e.dialect().equals("snowy")).count();
        require(changed==expected&&roofs>100&&snow>100,"Missing snowy roof coverage: "+changed+" of "+expected+" / "+roofs+" / "+snow);
        fixture();
        System.out.println("PASS snowy one-block eaves: "+changed+" designs, all four fits, "+roofs+" roof and "+snow
            +" snow cells; original furnished cores retained; 1200 other-biome assets byte-identical");
    }
    private static void fixture() {
        var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.SNOWY,"HOUSE","snowy_eave_fixture",13,13);
        var b=new BiomeArchitecturePreview.Builder(sample);b.room(3,9,3,9,4);b.gable(3,9,3,9,5,true);b.door(6,3);
        b.put(6,3,6,Blocks.LANTERN);b.put(6,2,6,Blocks.SPRUCE_PLANKS);
        var original=b.finish();var p=PreviewSnowyOverhang.apply(original);
        for(var at:List.of(new BlockPos(3,5,2),new BlockPos(3,5,10),new BlockPos(2,5,5),new BlockPos(10,5,5))) {
            require(p.cells().get(at)!=null&&p.cells().get(at).is(Blocks.SPRUCE_PLANKS),"Missing eave: "+at);
            require(p.cells().get(at.above())!=null&&p.cells().get(at.above()).is(Blocks.SNOW),"Missing cap: "+at);
        }
        require(!p.cells().containsKey(new BlockPos(3,5,1))&&!p.cells().containsKey(new BlockPos(1,5,5)),"Double projection");
        require(p.equals(PreviewSnowyOverhang.apply(p)),"Not idempotent");
        var fresh=new BiomeArchitecturePreview.Plan(new BiomeArchitecturePreview.Sample(
                VillageArchitecture.BiomeDialect.SNOWY,"HOUSE","snowy_existing_eaves_fixture",13,13),
                p.cells(),p.access(),p.entrance(),p.height());
        require(fresh.cells().equals(PreviewSnowyOverhang.afterUsability(fresh).cells()),"Existing eaves grew a second ring");
        var occupied=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(
                VillageArchitecture.BiomeDialect.SNOWY,"HOUSE","snowy_occupied_eave_fixture",13,13));
        occupied.cells.putAll(original.cells());occupied.entrance=original.entrance();occupied.put(2,5,5,Blocks.STONE_BRICKS);
        require(PreviewSnowyOverhang.afterUsability(occupied.finish()).cells().get(new BlockPos(2,5,5)).is(Blocks.STONE_BRICKS),
                "Occupied edge overwritten");
        var balcony=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(
            VillageArchitecture.BiomeDialect.SNOWY,"HOUSE","snowy_balcony_fixture",13,13));
        balcony.cells.putAll(original.cells());balcony.entrance=original.entrance();balcony.put(3,2,2,Blocks.SPRUCE_PLANKS);
        require(!PreviewSnowyOverhang.apply(balcony.finish()).cells().containsKey(new BlockPos(3,5,2)),"Balcony clearance lost");
    }
    private static void require(boolean yes,String why) {if(!yes)throw new IllegalStateException(why);}
    private static boolean apron(BlockPos at,BlockState state) {
        return at.getY()==-1&&(state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.COBBLESTONE));
    }
}
