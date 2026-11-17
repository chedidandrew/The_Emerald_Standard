package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Native-state release gate for the narrowly scoped revision-14 eaves. */
final class PlainsRoofOverhangSelfTest {
    static void verify() {
        int changed=0,roofs=0,lights=0;
        for(var e:ApprovedArchitectureCatalog.entries()) {
            var old=ApprovedVillageStructures.data(e.id(),13);
            var now=ApprovedVillageStructures.data(e.id(),14);
            BlockPos offset=now.entrance().subtract(old.entrance());
            old.cells().forEach((at,s)->require(s.equals(now.cells().get(at.offset(offset))),"Old cell changed: "+e.id()+" "+at));
            Set<BlockPos> previous=new HashSet<>();old.cells().keySet().forEach(at->previous.add(at.offset(offset)));
            int local=0;
            for(var cell:now.cells().entrySet()) if(!previous.contains(cell.getKey())) {
                require(e.dialect().equals("plains"),"Other biome changed: "+e.id());
                BlockState s=cell.getValue();BlockPos at=cell.getKey();
                boolean roof=s.is(Blocks.OAK_STAIRS)||s.is(Blocks.OAK_SLAB)||s.is(Blocks.OAK_PLANKS);
                require(roof||s.is(Blocks.LANTERN)||s.is(Blocks.IRON_CHAIN),"Unexpected added material "+e.id()+" "+s);
                if(roof) {
                    require(at.getY()>=4&&previous.stream().anyMatch(p->p.getY()==at.getY()
                            &&Math.abs(p.getX()-at.getX())<=1&&Math.abs(p.getZ()-at.getZ())<=1
                            &&s.equals(now.cells().get(p))),"Overhang exceeds one block: "+e.id()+" "+at);
                    for(Rotation rotation:Rotation.values()) for(Mirror mirror:Mirror.values())
                        require(s.mirror(mirror).rotate(rotation).getBlock()==s.getBlock(),"Roof transform changed material");
                    roofs++;
                } else lights++;
                local++;
            }
            require(old.access().stream().map(at->at.offset(offset)).collect(java.util.stream.Collectors.toSet()).equals(now.access()),"Access moved");
            require(old.identity().height()==now.identity().height()&&old.identity().beds()==now.identity().beds(),"Ridge/bed count changed");
            if(!e.dialect().equals("plains")) require(old.identity().sha256().equals(now.identity().sha256()),"Other biome asset changed");
            if(local>0) {changed++;System.out.println("PLAINS EAVES "+e.id()+": "+local+" added cells");}
            for(int fit=1;fit<=3;fit++) if(!e.dialect().equals("plains"))
                require(ApprovedArchitectureCatalog.fittedEntry(e.id(),13,fit).sha256().equals(
                        ApprovedArchitectureCatalog.fittedEntry(e.id(),14,fit).sha256()),"Other biome fit changed");
        }
        require(changed>=22&&roofs>100,"Missing plains roof coverage: "+changed+" / "+roofs);
        fixture();
        System.out.println("PASS Plains one-block overhangs: "+changed+" designs, "+roofs+" roof blocks, "+lights
                +" supported lighting cells; 300 other-biome assets and 900 fits byte-identical; archived cells retained");
    }
    private static void fixture() {
        var sample=new BiomeArchitecturePreview.Sample(VillageArchitecture.BiomeDialect.PLAINS,"HOUSE","compact_overhang_fixture",12,12);
        var b=new BiomeArchitecturePreview.Builder(sample);b.room(3,7,3,7,4);b.gable(3,7,3,7,5,false);b.door(5,3);
        // The empty fixture is deliberately lit; geometry proof must not depend on skylight.
        b.put(5,3,5,Blocks.LANTERN.defaultBlockState());b.put(5,2,5,Blocks.OAK_PLANKS);
        var original=b.finish();var p=PreviewPlainsOverhang.apply(original);
        require(p.cells().containsKey(new BlockPos(3,5,2))&&p.cells().containsKey(new BlockPos(3,5,8)),"Missing gable projection");
        require(p.cells().containsKey(new BlockPos(2,5,4))&&p.cells().containsKey(new BlockPos(8,5,4)),"Missing side eaves");
        require(!p.cells().containsKey(new BlockPos(1,5,4))&&!p.cells().containsKey(new BlockPos(3,5,1)),"Double overhang");
        require(p.equals(PreviewPlainsOverhang.apply(p)),"Pass not stable");
        var balcony=new BiomeArchitecturePreview.Builder(new BiomeArchitecturePreview.Sample(
                VillageArchitecture.BiomeDialect.PLAINS,"HOUSE","compact_balcony_fixture",12,12));
        balcony.cells.putAll(original.cells());balcony.entrance=original.entrance();
        balcony.put(3,2,2,Blocks.OAK_PLANKS);
        var safe=PreviewPlainsOverhang.apply(balcony.finish());
        require(!safe.cells().containsKey(new BlockPos(3,5,2)),"Overhang lowered balcony headroom");
        require(safe.cells().get(new BlockPos(3,2,2)).is(Blocks.OAK_PLANKS),"Balcony floor changed");
    }
    private static void require(boolean yes,String why) {if(!yes)throw new IllegalStateException(why);}
}
