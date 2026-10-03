package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.StructureGalleryPlan;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Complete art-review catalog only. Not a production recipe, persisted design or migration. */
final class BiomeArchitectureCatalogPreview {
    private static final Map<String,Plan> CACHE=new LinkedHashMap<>();
    private static final Map<String,Set<BlockPos>> ROOF_JOINS=new LinkedHashMap<>();
    private static List<Sample> samples;

    static synchronized List<Sample> samples() {
        if(samples!=null) return samples;
        List<Sample> result=new ArrayList<>();
        for(BiomeDialect style:BiomeDialect.values()) {
            for(var descriptor:StructureGalleryPlan.goldMasters()) {
                String role=descriptor.type().name(), id=descriptor.templateId();
                if(style==BiomeDialect.PLAINS) {
                    Plan plan=PlainsLegacyArchitecturePreview.catalog(role,id);
                    CACHE.put(plan.sample().id(),plan); result.add(plan.sample());
                } else result.add(new Sample(style,role,"catalog_"+style.id()+"_"+id,
                        descriptor.width()+6,descriptor.depth()+6));
            }
            var bank=BiomeArchitecturePreview.samples().stream()
                    .filter(s->s.style()==style&&s.role().equals("BANK")).findFirst().orElseThrow();
            Plan source=BiomeArchitecturePreview.plan(bank);
            Sample sample=new Sample(style,"BANK","catalog_"+style.id()+"_standalone_bank",bank.width(),bank.depth());
            CACHE.put(sample.id(),new Plan(sample,source.cells(),source.access(),source.entrance(),source.height()));
            PreviewDoorwayAudit.alias(source,sample.id());
            result.add(sample);
        }
        samples=List.copyOf(result); return samples;
    }
    static synchronized Plan plan(Sample sample) {
        samples();
        return CACHE.computeIfAbsent(sample.id(),key->build(sample));
    }
    static String masterId(Sample s) { return s.id().substring(("catalog_"+s.style().id()+"_").length()); }
    static Set<BlockPos> roofJoins(Sample sample) { plan(sample); return ROOF_JOINS.getOrDefault(sample.id(),Set.of()); }

    private static Plan build(Sample s) {
        Builder b=new Builder(new Sample(s.style(),s.role(),s.id(),s.width()-6,s.depth()-6));
        String id=masterId(s);
        var descriptor=VillageArchitecture.activeBlueprints().stream().filter(d->d.templateId().equals(id))
                .findFirst().orElseThrow();
        if(!descriptor.type().name().equals(s.role())) throw new IllegalArgumentException("Catalog role mismatch");
        int variant=Integer.parseInt(id.substring(id.lastIndexOf('_')+1));
        int w=b.s.width(),d=b.s.depth(),mid=w/2;
        boolean open=s.role().equals("MARKET_SQUARE"),smith=s.role().equals("SMITHY");
        int front=s.style()==BiomeDialect.DESERT?1:2;
        int h=s.role().equals("GUARD_POST")?6:s.role().equals("EXCHANGE_HALL")?5:4;
        b.floor(0,w-1,0,d-1);
        if(open) {
            b.pergola(0,w-1,front,d-1,h);
            PreviewExteriorPrograms.roof(b,id,front,h,w-1);
            b.entrance=new BlockPos(mid,1,front);
        } else if(smith) {
            int split=w/2;
            b.room(0,split,front,d-1,h); PreviewExteriorPrograms.roof(b,id,front,h,split);
            b.pergola(split+1,w-1,front,d-1,h);
            b.door(split/2,front);
            for(int y=1;y<=2;y++) b.remove(split,y,d/2);
        } else {
            b.room(0,w-1,front,d-1,h); PreviewExteriorPrograms.roof(b,id,front,h,w-1);
            windows(b,front,h);
            b.door(mid,front);
            if(s.style()!=BiomeDialect.DESERT) b.porch(2,w-3,0,front,3);
            else b.pergola(1,w-2,0,front,3);
        }
        switch(s.role()) {
            case "COTTAGE","HOUSE" -> dwelling(b,variant);
            case "INN" -> lodging(b,variant);
            case "WAREHOUSE" -> warehouse(b,variant);
            case "GRANARY" -> granary(b,variant,id);
            case "SMITHY" -> smithy(b,variant);
            case "MINE_ENTRANCE" -> mine(b,variant);
            case "MARKET_SQUARE" -> market(b,variant);
            case "GUARD_POST" -> guard(b,variant);
            case "EXCHANGE_HALL" -> exchange(b,variant);
            default -> throw new IllegalArgumentException(s.role());
        }
        regionalFinish(b,front,h,variant,id);
        // Roof-bearing, side-positioned pendants; none hang in the entrance sight line.
        for(int z=front+1;z<d-1;z+=4) {
            b.lamp(2,3,z); b.lamp(w-3,3,z);
            if(w>=19) b.lamp(mid,3,z);
        }
        if(w>=19) {
            b.pier(0,0,3,b.p.log()); b.pier(w-1,0,3,b.p.log()); b.lamp(mid,3,0);
        }
        b.details();
        PreviewExteriorPrograms.appendages(b,id,h);
        PreviewRoofEnvelope.seal(b);
        // Later porch/clerestory/light composition can replace a roof bearing. A snow layer
        // is optional dressing: keep it only where the final roof actually supports it.
        b.cells.entrySet().removeIf(e->e.getValue().is(Blocks.SNOW)
                && (b.cells.get(e.getKey().below())==null
                || !b.cells.get(e.getKey().below()).isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
                        e.getKey().below(),Direction.UP)));
        Map<BlockPos,BlockState> translated=new LinkedHashMap<>();
        b.cells.forEach((pos,state)->translated.put(pos.offset(3,0,3),state));
        Set<BlockPos> access=new HashSet<>(); b.access.forEach(pos->access.add(pos.offset(3,0,3)));
        Set<BlockPos> joins=new HashSet<>(); b.roofJoins.keySet().forEach(pos->joins.add(pos.offset(3,0,3)));
        ROOF_JOINS.put(s.id(),Set.copyOf(joins));
        Plan plan=PreviewDoorwayAudit.correct(new Plan(s,Map.copyOf(translated),Set.copyOf(access),b.entrance.offset(3,0,3),b.finish().height()));
        BiomeArchitecturePreview.validate(plan); return plan;
    }
    private static void roof(Builder b,int x0,int x1,int z0,int z1,int y) {
        switch(b.s.style()) {
            case DESERT -> b.terrace(x0,x1,z0,z1,y);
            case SAVANNA -> b.hip(x0,x1,z0,z1,y);
            case TAIGA,SNOWY -> b.gable(x0,x1,z0,z1,y,b.s.style()==BiomeDialect.SNOWY);
            default -> throw new IllegalStateException("Plains uses original native geometry");
        }
    }
    private static void windows(Builder b,int front,int h) {
        int w=b.s.width(),d=b.s.depth();
        for(int x:new int[]{w/4,w-1-w/4}) {
            b.window(x,front,false);
            for(int dx=-1;dx<=1;dx++) { b.put(x+dx,1,front,b.p.trim()); b.put(x+dx,4,front,b.p.log()); }
        }
        for(int z=front+3;z<d-2;z+=5) { b.window(0,z,true); b.window(w-1,z,true); }
    }
    private static void bed(Builder b,int x,int z) {
        BlockState state=Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        b.put(x,1,z,state.setValue(BedBlock.PART,BedPart.FOOT));
        b.put(x,1,z+1,state.setValue(BedBlock.PART,BedPart.HEAD));
        b.access.add(new BlockPos(x+1,1,z));
        b.put(x,1,z-1,Blocks.BARREL);
        b.put(x,2,z-1,b.s.style()==BiomeDialect.DESERT?Blocks.POTTED_DEAD_BUSH:Blocks.POTTED_POPPY);
    }
    private static void dwelling(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth(),count=b.s.role().equals("COTTAGE")?2:4;
        int[] xs=count==2?new int[]{1,w-3}:new int[]{1,3,w-5,w-3};
        for(int x:xs) bed(b,x,d-3);
        b.kitchen(1,4);
        if(d<=10) {
            b.put(w-4,1,4,b.s.style()==BiomeDialect.DESERT?Blocks.CUT_SANDSTONE:b.fence());
            b.put(w-4,2,4,Blocks.OAK_PRESSURE_PLATE);
            b.bench(w-5,4,1,Direction.EAST);
        } else b.table(w-4,4,2);
        if(d>=13) {
            // Defined bedroom screens with side passages, instead of furniture scattered centrally.
            int screen=d-6;
            for(int x=1;x<w-1;x++) if(Math.abs(x-w/2)>1) b.put(x,1,screen,b.p.log());
        }
        if(variant%2==0&&d>=11) { b.put(1,1,6,Blocks.BOOKSHELF); b.put(1,2,6,Blocks.POTTED_FERN); }
    }
    private static void lodging(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth();
        int[] xs={1,3,w-5,w-3};
        for(int z:new int[]{Math.max(5,d-7),d-3}) for(int x:xs) bed(b,x,z);
        // The central passage joins both guest wings; larger inns have a separate public taproom.
        if(d>=15) {
            b.table(2,4,2); b.table(w-4,4,2);
            b.put(1,1,6,Blocks.SMOKER); b.access.add(new BlockPos(2,1,6));
            b.counter(w-5,w-2,1,6); b.put(w-2,2,6,Blocks.FLOWER_POT);
        }
        int screen=d-5;
        for(int x=1;x<w-1;x++) if(Math.abs(x-w/2)>1) b.put(x,3,screen,b.p.log());
        // A carried lintel connects the sleeping-bay screens to the wall/ceiling, not floating slabs.
        for(int x:new int[]{0,w-1}) b.pier(x,screen,3,b.p.log());
    }
    private static void warehouse(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth();
        for(int z=5;z<d-2;z+=3) for(int x:new int[]{1,w-2}) {
            b.put(x,1,z,Blocks.BARREL); b.put(x,2,z,Blocks.BARREL); b.put(x,3,z,b.p.slab());
            b.access.add(new BlockPos(x==1?2:w-3,1,z));
        }
        b.put(1,1,3,Blocks.CRAFTING_TABLE); b.access.add(new BlockPos(2,1,3));
        b.table(w-4,3,2);
        if(variant==2||variant==4) crane(b,w-3,0);
    }
    private static void crane(Builder b,int x,int z) {
        b.pier(x,z,5,b.p.log());
        for(int dx=0;dx<=3;dx++) b.put(x-dx,5,z,b.p.log());
        for(int y=2;y<=4;y++) b.put(x-3,y,z,Blocks.IRON_CHAIN);
        b.put(x-3,1,z,Blocks.BARREL);
    }
    private static void granary(Builder b,int variant,String id) {
        int w=b.s.width(),d=b.s.depth();
        for(int z=5;z<d-2;z+=2) {
            for(int y=1;y<=2+(variant%2);y++) b.put(1,y,z,Blocks.HAY_BLOCK);
            b.put(w-2,1,z,Blocks.BARREL); b.access.add(new BlockPos(w-3,1,z));
        }
        b.put(1,1,3,Blocks.COMPOSTER); b.access.add(new BlockPos(2,1,3));
        if(id.contains("windmill")) {
            int x=w/2;
            for(int y=3;y<=10;y++) b.put(x,y,2,b.p.log());
            for(int bx=0;bx<w;bx++) b.put(bx,4,2,b.p.log());
            for(int i=-4;i<=4;i++) { b.put(x+i,9,1,b.p.log()); b.put(x,9+i,1,b.p.log()); }
            b.put(x,9,2,b.p.log());
            // A solid bearing ties the four contiguous wooden sail arms to the mill façade.
            b.put(x,8,2,b.p.log());
        } else if(id.contains("silocomplex")) {
            for(int x:new int[]{2,w-3}) {
                for(int y=5;y<=8;y++) for(int dx=-1;dx<=1;dx++) for(int dz=0;dz<=2;dz++)
                    b.put(x+dx,y,d-4+dz,b.p.log());
                roof(b,x-1,x+1,d-4,d-2,9);
            }
        }
    }
    private static void smithy(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth(),split=w/2;
        b.put(1,1,d-3,Blocks.SMITHING_TABLE); b.access.add(new BlockPos(2,1,d-3));
        b.storage(1,4);
        int forgeX=w-3;
        b.put(forgeX,1,d-3,Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT,true));
        b.access.add(new BlockPos(forgeX,1,d-4));
        for(int y=2;y<=7;y++) b.put(forgeX,y,d-3,b.p.trim());
        for(int x:new int[]{forgeX-1,forgeX+1}) b.pier(x,d-3,2,b.p.trim());
        b.put(split+2,1,5,Blocks.ANVIL); b.access.add(new BlockPos(split+2,1,4));
        b.put(w-2,1,3,Blocks.CAULDRON);
        if(variant%2==0) { b.put(2,1,d-5,Blocks.GRINDSTONE); b.access.add(new BlockPos(3,1,d-5)); }
    }
    private static void mine(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth(),mid=w/2;
        b.storage(1,4); b.put(w-2,1,4,Blocks.STONECUTTER); b.access.add(new BlockPos(w-3,1,4));
        if(variant==1||variant==2) {
            for(int x:new int[]{mid-2,mid+2}) b.pier(x,d-3,5,b.p.log());
            for(int x=mid-2;x<=mid+2;x++) b.put(x,6,d-3,b.p.log());
            b.put(mid,1,d-3,Blocks.IRON_TRAPDOOR);
        } else {
            for(int x=mid-2;x<=mid+2;x++) {
                b.put(x,0,d-2,Blocks.STONE_BRICKS);
                if(x!=mid) b.pier(x,d-2,3,b.p.trim());
                b.put(x,4,d-2,b.p.trim());
            }
            b.put(mid,1,d-2,Blocks.IRON_TRAPDOOR);
        }
        // A capped exhibition shaft, never a dug hazard or claim of a connected underground mine.
        b.access.add(new BlockPos(mid,1,d-4));
    }
    private static void market(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth();
        for(int z=4;z<d-2;z+=4) for(int x:new int[]{1,w-3}) {
            b.put(x,1,z,Blocks.BARREL); b.put(x+1,1,z,Blocks.CRAFTING_TABLE);
            b.put(x,2,z,Blocks.FLOWER_POT);
            b.access.add(new BlockPos(x==1?3:w-4,1,z));
            for(int dx=0;dx<=1;dx++) b.put(x+dx,3,z,b.p.log());
            b.pier(x,z+1,3,b.p.log());
        }
        b.table(w/2-1,d-3,3);
        if(variant==2||variant==5) {
            for(int x=1;x<w-1;x++) b.put(x,1,d-1,b.p.trim());
            b.put(w/2,2,d-1,Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT,BellAttachType.FLOOR));
        }
        if(b.s.style()==BiomeDialect.TAIGA) {
            for(int x=0;x<w;x++) { b.put(x,0,0,Blocks.COBBLESTONE); b.put(x,0,d-1,Blocks.COBBLESTONE); }
        }
    }
    private static void guard(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth(),mid=d/2;
        b.put(1,1,4,Blocks.BARREL); b.access.add(new BlockPos(2,1,4));
        b.put(w-2,1,d-3,Blocks.CRAFTING_TABLE); b.access.add(new BlockPos(w-3,1,d-3));
        for(int x=w-4;x<=w-3;x++) for(int z=mid-1;z<=mid+1;z++) b.put(x,5,z,b.p.floor());
        for(int y=1;y<=6;y++) b.put(w-2,y,mid,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.WEST));
        b.access.add(new BlockPos(w-2,1,mid-1));
        b.bench(2,3,2,Direction.SOUTH);
        if(variant==4) {
            for(int x:new int[]{1,w-2}) b.pier(x,0,6,b.p.log());
            for(int x=1;x<w-1;x++) b.put(x,7,0,b.p.roof());
        }
        for(int x:new int[]{2,w-3}) b.put(x,5,2,Blocks.GLASS_PANE);
    }
    private static void exchange(Builder b,int variant) {
        int w=b.s.width(),d=b.s.depth(),mid=w/2;
        int teller=d-5;
        for(int x=2;x<w-2;x++) {
            b.put(x,1,teller,x==mid?Blocks.LECTERN:b.p.trim());
            if(x!=mid) b.put(x,2,teller,b.p.slab());
        }
        b.access.add(new BlockPos(mid,1,teller-1)); b.access.add(new BlockPos(mid,1,teller+1));
        b.recordsAlcove(1,w-2,d-2,b.p.log());
        b.table(2,4,2); b.bench(w-5,4,3,Direction.SOUTH);
        if(variant==4||variant==5) for(int x:new int[]{2,w-3}) b.pier(x,6,4,b.p.log());
    }
    private static void regionalFinish(Builder b,int front,int h,int variant,String id) {
        int w=b.s.width(),d=b.s.depth();
        if(!b.s.role().equals("MARKET_SQUARE")) {
            if(b.s.style()==BiomeDialect.DESERT) {
                for(int x=0;x<w;x++) b.put(x,h,front,Blocks.CHISELED_SANDSTONE);
                for(int x=0;x<w;x+=4) b.put(x,h+2,front,Blocks.CUT_SANDSTONE);
            } else if(b.s.style()==BiomeDialect.SAVANNA) {
                for(int x:new int[]{0,w-1}) for(int z=front+1;z<d-1;z++) b.put(x,h,z,Blocks.TERRACOTTA);
                for(int x=1;x<w-1;x++) if(Math.abs(x-b.entrance.getX())>2) b.put(x,1,0,Blocks.ACACIA_FENCE);
            } else if(b.s.style()==BiomeDialect.TAIGA) {
                for(int x=0;x<w;x++) if(x!=b.entrance.getX()) b.put(x,1,front,Blocks.COBBLESTONE);
                // At most two low, deliberately placed weathered accents, not a mossy wall/floor palette.
                b.put(0,1,d-2,Blocks.MOSSY_COBBLESTONE);
                if(w>=17) b.put(w-1,1,d-3,Blocks.MOSSY_COBBLESTONE);
                for(int x:new int[]{w/4-1,w-w/4}) if(x>0&&x<w-1) for(int y=2;y<=3;y++)
                    b.put(x,y,front-1,Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                            .setValue(TrapDoorBlock.OPEN,true).setValue(TrapDoorBlock.FACING,Direction.NORTH));
            } else {
                for(int x:new int[]{0,w-1}) for(int z=front+1;z<d-1;z++) {
                    BlockState at=b.cells.get(new BlockPos(x,3,z));
                    if(at!=null&&!at.is(Blocks.GLASS_PANE)) b.put(x,3,z,Blocks.WOOL.white());
                }
            }
        }
        // Distinct bays, courtyards and tower masses follow the design's architectural program.
        if(id.contains("splitwing")) {
            // A low front wing, not a renamed copy of the cross house.
            b.room(w-5,w-1,0,front,3); roof(b,w-5,w-1,0,front,4);
            b.window(w-3,0,false);
        }
        if(id.contains("courtyard")||id.contains("garden")||id.contains("orchard")) {
            int x=w-4,z=d-4;
            // A roof light opens onto a planted internal court while keeping sleep/work routes dry.
            int roofHeight=b.finish().height();
            for(int y=h+1;y<roofHeight;y++) for(int dx=0;dx<=1;dx++) for(int dz=0;dz<=1;dz++)
                b.remove(x+dx,y,z+dz);
            for(int dx=0;dx<=1;dx++) for(int dz=0;dz<=1;dz++) b.put(x+dx,h+1,z+dz,Blocks.GLASS);
        }
        if(id.contains("towercourt")||id.contains("citadel")||id.contains("basilica")) {
            // Solid rear clerestory mass, carried by the original perimeter walls/roof.
            for(int x=w-5;x<w;x++) for(int z=d-4;z<d;z++) for(int y=h+1;y<=h+3;y++) b.put(x,y,z,b.p.wall());
            roof(b,w-5,w-1,d-4,d-1,h+4);
        }
        // A useful fixture and grounded planting even in small utilitarian buildings.
        BlockPos at=new BlockPos(w-2,1,d-2);
        if(!b.cells.containsKey(at)&&!b.access.contains(at)) {
            b.put(at.getX(),1,at.getZ(),Blocks.BARREL);
            b.put(at.getX(),2,at.getZ(),b.s.style()==BiomeDialect.DESERT?Blocks.POTTED_CACTUS:Blocks.POTTED_FERN);
        }
    }
}
