package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** Native-state gate: every current Bank keeps a concealed, supported signal fire at every fit. */
final class BankChimneySelfTest {
    static void verify() {
        verifyRepeatedAuthoring();
        verifySlimChimneys();
        int banks=0,unchanged=0;
        for(var e:ApprovedArchitectureCatalog.entries()) for(int fit=0;fit<=3;fit++) {
            var before=ApprovedVillageStructures.data(e.id(),14,fit);
            var after=ApprovedVillageStructures.data(e.id(),15,fit);
            if(!e.role().equals("BANK")) {
                require(before.identity().sha256().equals(after.identity().sha256()),"Non-Bank changed: "+e.id());
                unchanged++;continue;
            }
            var fires=after.cells().entrySet().stream().filter(c->c.getValue().is(Blocks.CAMPFIRE)
                    &&c.getValue().getValue(CampfireBlock.SIGNAL_FIRE)).toList();
            require(fires.size()==1,"Bank must have exactly one smoke marker: "+e.id());
            BlockPos fire=fires.getFirst().getKey();BlockState state=fires.getFirst().getValue();
            // Standalone Bootstrap has no loaded datapack tags; live fixtures also use the native tag predicate.
            require(state.getValue(CampfireBlock.LIT)&&state.getValue(CampfireBlock.SIGNAL_FIRE)
                    &&!state.getValue(CampfireBlock.WATERLOGGED),"Unlit/non-signal Bank chimney "+e.id());
            require(after.cells().get(fire.below()).is(Blocks.HAY_BLOCK),"Missing concealed signal bearing");
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(dx!=0||dz!=0)
                for(int y=-1;y<=1;y++) {
                    BlockPos at=fire.offset(dx,y,dz);BlockState side=after.cells().get(at);
                    require(side!=null&&Block.isShapeFullBlock(side.getCollisionShape(EmptyBlockGetter.INSTANCE,at)),
                            "Exposed fire/hay: "+e.id()+" "+at);
                }
            for(int y=fire.getY()+1;y<after.identity().height();y++)
                require(!after.cells().containsKey(new BlockPos(fire.getX(),y,fire.getZ())),"Capped smoke flue");
            BlockPos shift=after.entrance().subtract(before.entrance());
            for(var c:before.cells().entrySet()) {
                BlockPos at=c.getKey().offset(shift);BlockState old=c.getValue(),now=after.cells().get(at);
                if(old.equals(now))continue;
                require(Math.abs(at.getX()-fire.getX())<=1&&Math.abs(at.getZ()-fire.getZ())<=1
                        &&at.getY()<fire.getY()-1&&(old.is(Blocks.SNOW)
                        ||old.getBlock() instanceof SlabBlock&&old.getValue(SlabBlock.TYPE)==SlabType.BOTTOM
                        &&now.equals(old.setValue(SlabBlock.TYPE,SlabType.DOUBLE).setValue(SlabBlock.WATERLOGGED,false))),
                        "Chimney changed unrelated old geometry "+e.id()+" "+at);
            }
            require(before.access().stream().map(at->at.offset(shift)).collect(java.util.stream.Collectors.toSet()).equals(after.access()),"Chimney moved access");
            require(before.identity().beds()==after.identity().beds(),"Chimney changed capacity");
            require(after.base().stream().filter(c->c.state().is(Blocks.CAMPFIRE)&&c.state().getValue(CampfireBlock.SIGNAL_FIRE)).count()==1,"Smoke marker deferred to decoration stage");
            require(after.cells().values().stream().filter(s->s.is(Blocks.CAMPFIRE)).count()
                    ==before.cells().values().stream().filter(s->s.is(Blocks.CAMPFIRE)).count()+1,"Original campfires changed");
            NativeStructureSupport.validate(after.cells(),e.id()+" chimney fit "+fit);
            banks++;
        }
        require(banks==60&&unchanged==1440,"Incomplete chimney coverage");
        var allocated=new HashSet<Integer>();
        for(int revision=12;revision<=ApprovedArchitectureCatalog.REVISION;revision++) for(int variant=0;variant<3;variant++)
            for(int fit=0;fit<=(revision==12?0:3);fit++)
                require(allocated.add(ApprovedBankStructures.versionForRevision(revision,variant,fit)),"Bank version allocation collision");
        for(var style:VillageArchitecture.BiomeDialect.values()) for(int revision=12;revision<=ApprovedArchitectureCatalog.REVISION;revision++)
            for(int variant=0;variant<3;variant++) for(int fit=0;fit<=(revision==12?0:3);fit++) {
                int version=ApprovedBankStructures.versionForRevision(revision,variant,fit);
                require(ApprovedBankStructures.approvedVersion(version),"Bank version rejected");
                var plan=ApprovedBankStructures.plan(style,version);
                require(plan.cells().values().stream().filter(s->s.is(Blocks.CAMPFIRE)&&s.getValue(CampfireBlock.SIGNAL_FIRE)).count()==(revision>=15?1:0),"Historical Bank version reinterpreted: "+revision+"/"+variant+"/"+fit+" -> "+version);
            }
        for(int invalid:List.of(12,31,35,79,83,127,131,143,147,159,163,175,179,191,195,207,211,223,227,239,243,255,259,263,271,275,287,291,303,307,Integer.MAX_VALUE))
            require(!ApprovedBankStructures.approvedVersion(invalid),"Unallocated Bank version accepted");
        System.out.println("PASS 60 concealed Bank signal chimneys; 1440 non-Bank assets byte-identical; all historical Bank/fit routes retained");
    }
    private static void verifySlimChimneys() {
        int banks=0,unchanged=0;
        for(var e:ApprovedArchitectureCatalog.entries()) for(int fit=0;fit<=3;fit++) {
            var old=ApprovedVillageStructures.data(e.id(),17,fit);
            var slim=ApprovedVillageStructures.data(e.id(),18,fit);
            if(!e.role().equals("BANK")) {
                require(old.identity().sha256().equals(slim.identity().sha256()),"Slim chimney changed non-Bank "+e.id());
                unchanged++;continue;
            }
            var fires=slim.cells().entrySet().stream().filter(c->c.getValue().is(Blocks.CAMPFIRE)
                    &&c.getValue().getValue(CampfireBlock.SIGNAL_FIRE)).toList();
            require(fires.size()==1,"Slim Bank smoke marker count "+e.id());
            var fire=fires.getFirst().getKey();var state=fires.getFirst().getValue();
            require(state.getValue(CampfireBlock.LIT)&&!state.getValue(CampfireBlock.WATERLOGGED),"Slim fire unlit/wet");
            require(slim.cells().get(fire.below()).is(Blocks.HAY_BLOCK),"Slim signal bearing missing");
            for(int dy=-4;dy<=-2;dy++) {
                var shaft=fire.above(dy);var bearing=slim.cells().get(shaft);
                require(bearing!=null&&Block.isShapeFullBlock(bearing.getCollisionShape(EmptyBlockGetter.INSTANCE,shaft)),"Slim shaft not solid");
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(dx!=0||dz!=0)
                    require(!slim.cells().containsKey(shaft.offset(dx,0,dz))
                            ||dy==-4&&slim.cells().get(shaft.offset(dx,0,dz)).is(Blocks.SNOW),
                            "Wide chimney stem remains "+e.id()+" "+shaft.offset(dx,0,dz));
            }
            for(int dx:List.of(-1,1)) for(int dz:List.of(-1,1)) for(int dy=-1;dy<=0;dy++)
                require(!slim.cells().containsKey(fire.offset(dx,dy,dz)),"Wide shroud corner remains "+e.id());
            for(var side:Direction.Plane.HORIZONTAL) for(int dy=-1;dy<=0;dy++) {
                var at=fire.above(dy).relative(side);var panel=slim.cells().get(at);
                require(panel!=null&&panel.is(Blocks.SPRUCE_TRAPDOOR)&&panel.getValue(TrapDoorBlock.OPEN)
                        &&!panel.getValue(TrapDoorBlock.POWERED)&&!panel.getValue(TrapDoorBlock.WATERLOGGED),"Open solid shroud missing "+e.id());
                var box=panel.getShape(EmptyBlockGetter.INSTANCE,at).bounds().move(at.getX(),at.getY(),at.getZ());
                double margin=3.0/16+1e-7;
                require(box.minX>=fire.getX()-margin&&box.maxX<=fire.getX()+1+margin
                        &&box.minZ>=fire.getZ()-margin&&box.maxZ<=fire.getZ()+1+margin,
                        "Shroud faces outer edge, not skinny shaft "+e.id()+" "+side);
                require(!slim.cells().containsKey(at.below(2)),"Wide masonry remains beneath shroud "+e.id());
            }
            for(int y=fire.getY()+1;y<slim.identity().height();y++)
                require(!slim.cells().containsKey(new BlockPos(fire.getX(),y,fire.getZ())),"Slim flue capped");
            var shift=slim.entrance().subtract(old.entrance());
            var before=new HashMap<BlockPos,BlockState>();old.cells().forEach((at,s)->before.put(at.offset(shift),s));
            var all=new HashSet<>(before.keySet());all.addAll(slim.cells().keySet());
            for(var at:all) if(!Objects.equals(before.get(at),slim.cells().get(at)))
                require(Math.abs(at.getX()-fire.getX())<=1&&Math.abs(at.getZ()-fire.getZ())<=1
                        &&at.getY()>=fire.getY()-7,"Unrelated Bank geometry changed "+e.id()+" "+at);
            require(old.access().stream().map(at->at.offset(shift)).collect(java.util.stream.Collectors.toSet()).equals(slim.access()),"Slim chimney changed access");
            require(old.identity().beds()==slim.identity().beds(),"Slim chimney changed capacity");
            require(slim.base().stream().filter(c->c.state().is(Blocks.CAMPFIRE)&&c.state().getValue(CampfireBlock.SIGNAL_FIRE)).count()==1,"Slim chimney became optional");
            NativeStructureSupport.validate(slim.cells(),e.id()+" slim fit "+fit);banks++;
        }
        require(banks==60&&unchanged==1440,"Incomplete skinny chimney coverage");
        System.out.println("PASS 60 skinny Bank chimneys: one-block shaft, 1.375-block solid upright shroud, open native signal fire; 1440 unrelated assets unchanged");
    }
    private static void verifyRepeatedAuthoring() {
        String prior=System.getProperty(StructureGallery.ENABLE_PROPERTY);
        try {
            System.setProperty(StructureGallery.ENABLE_PROPERTY,"true");
            for(var sample:BiomeArchitecturePreview.reviewSamples()) if(sample.role().equals("BANK")) {
                var first=BiomeArchitecturePreview.plan(sample);
                var again=BiomeArchitecturePreview.plan(sample);
                require(first.cells().equals(again.cells())&&first.height()==again.height(),"Repeated Bank authoring lost chimney "+sample.id());
                require(PreviewBankChimney.apply(first).cells().equals(first.cells()),"Chimney pass is not idempotent");
            }
        } finally {
            if(prior==null) System.clearProperty(StructureGallery.ENABLE_PROPERTY);
            else System.setProperty(StructureGallery.ENABLE_PROPERTY,prior);
        }
    }
    private static void require(boolean yes,String why) {if(!yes)throw new IllegalStateException(why);}
}
