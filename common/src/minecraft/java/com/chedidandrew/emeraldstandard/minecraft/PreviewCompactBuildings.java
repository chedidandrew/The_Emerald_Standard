package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Twenty-two compact, purpose-built designs per region, not shrunken landmark interiors. */
final class PreviewCompactBuildings {
    record Spec(String id,String role,int width,int depth,int beds,boolean upper,String name) { }
    private static final List<Spec> SPECS=List.of(
        new Spec("cotters_hut","COTTAGE",5,7,1,false,"Cotter's hut"),
        new Spec("garden_cottage","COTTAGE",7,9,2,false,"Garden cottage"),
        new Spec("paired_home","HOUSE",5,9,2,false,"Paired home"),
        new Spec("loft_home","HOUSE",7,9,4,true,"Loft home"),
        new Spec("trail_lodge","INN",7,7,4,true,"Trail lodge"),
        new Spec("wayside_inn","INN",9,11,8,true,"Wayside inn"),
        new Spec("parcel_store","WAREHOUSE",5,7,0,false,"Parcel store"),
        new Spec("loading_store","WAREHOUSE",7,9,0,true,"Loading store"),
        new Spec("seed_house","GRANARY",5,7,0,false,"Seed house"),
        new Spec("harvest_store","GRANARY",7,9,0,true,"Harvest store"),
        new Spec("repair_shed","SMITHY",5,7,0,false,"Repair shed"),
        new Spec("village_forge","SMITHY",7,9,0,false,"Village forge"),
        new Spec("prospector_hut","MINE_ENTRANCE",5,7,0,false,"Prospector's hut"),
        new Spec("survey_office","MINE_ENTRANCE",7,9,0,true,"Survey office"),
        new Spec("produce_stall","MARKET_SQUARE",5,7,0,false,"Produce stall"),
        new Spec("covered_market","MARKET_SQUARE",7,9,0,false,"Covered market"),
        new Spec("watch_hut","GUARD_POST",5,7,0,false,"Watch hut"),
        new Spec("watch_house","GUARD_POST",7,9,0,true,"Watch house"),
        new Spec("trade_kiosk","EXCHANGE_HALL",5,7,0,false,"Trade kiosk"),
        new Spec("merchant_office","EXCHANGE_HALL",7,9,0,true,"Merchant office"),
        new Spec("bank_kiosk","BANK",5,7,0,false,"Bank kiosk"),
        new Spec("savings_branch","BANK",7,9,0,true,"Savings branch"));
    private static final Map<String,Plan> CACHE=new HashMap<>();
    static boolean isCompact(Sample s) { return s.id().startsWith("compact_"); }
    static Spec spec(Sample s) { return SPECS.stream().filter(d->s.id().equals("compact_"+s.style().id()+"_"+d.id())).findFirst().orElseThrow(); }
    static List<Sample> samples(BiomeDialect style) {
        return SPECS.stream().map(d->new Sample(style,d.role(),"compact_"+style.id()+"_"+d.id(),d.width()+4,d.depth()+4)).toList();
    }
    static Plan plan(Sample s) { return CACHE.computeIfAbsent(s.id(),ignored->build(s)); }
    private static Plan build(Sample s) {
        Spec d=spec(s); Builder b=new Builder(s);
        int l=2,r=l+d.width()-1,f=2,back=f+d.depth()-1,c=(l+r)/2,h=d.upper()?8:4;
        Set<BlockPos> floors=new HashSet<>(),targets=new HashSet<>();
        b.room(l,r,f,back,h);
        b.floor(c-1,c+1,0,1);
        // Different regional envelopes; Taiga is the requested bark-on log gable.
        roof(b,d,l,r,f,back,h);
        // A stone hearth wall, window sill and timber jambs, not a featureless box.
        for(int x=l;x<=r;x++) b.put(x,0,f,b.p.trim());
        for(int z=f;z<=back;z++) for(int x:new int[]{l,r}) b.put(x,1,z,b.p.trim());
        if(s.style()==BiomeDialect.TAIGA) for(int y=1;y<=3;y++) for(int x=l+1;x<r;x++) {
            b.put(x,y,f,Blocks.COBBLESTONE); b.put(x,y,back,Blocks.COBBLESTONE);
        }
        for(int side:new int[]{l,r}) {
            b.put(side,2,f+2,Blocks.GLASS_PANE); b.put(side,3,f+2,Blocks.GLASS_PANE);
            if(d.depth()>=9) b.put(side,2,back-2,Blocks.GLASS_PANE);
            if(d.upper()) b.put(side,6,back-2,Blocks.GLASS_PANE);
        }
        b.door(c,f);
        // Small door canopy and grounded planted porch ends, with an untouched center route.
        for(int x=c-1;x<=c+1;x++) b.put(x,4,1,b.p.roof());
        b.put(c,3,1,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        b.put(c-1,1,0,b.p.trim());
        b.put(c-1,2,0,s.style()==BiomeDialect.DESERT?Blocks.POTTED_CACTUS
                :s.style()==BiomeDialect.TAIGA?Blocks.POTTED_SPRUCE_SAPLING:Blocks.POTTED_POPPY);
        exterior(b,d,l,r,f,back,c,h);
        PreviewTaigaCraft.apply(b);
        if(d.upper()) {
            for(int x=l;x<=r;x++) for(int z=f;z<=back;z++) {
                BlockPos at=new BlockPos(x,4,z); b.cells.put(at,b.p.floor().defaultBlockState()); floors.add(at);
            }
            int ladderX=c,z=back-1;
            for(int y=1;y<=4;y++) b.put(ladderX,y,z,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
            floors.remove(new BlockPos(ladderX,4,z));
            targets.add(new BlockPos(c,5,back-2)); b.access.addAll(targets);
            // Upper ceilings carry pendants and leave two clear blocks at the hatch.
            for(int x=l;x<=r;x++) b.put(x,8,f+2,b.p.log());
            b.put(c,7,f+2,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
        for(int x=l;x<=r;x++) b.put(x,4,f+1,b.p.log());
        b.put(c,3,f+1,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        int partitions=0;
        boolean lodging=d.beds()>0;
        if(lodging) {
            int ground=d.upper()?d.beds()/2:d.beds();
            beds(b,l,r,back,1,ground);
            if(d.upper()) beds(b,l,r,back,5,d.beds()-ground);
            // Sleeping quarters behind a framed internal door; sitting/kitchen space in front.
            if(d.depth()>=9) {
                int row=f+3;
                partitions+=partition(b,l,r,c,row,1);
                if(d.upper()) partitions+=partition(b,l,r,c,row,5);
            }
            b.put(l+1,1,f+1,Blocks.CRAFTING_TABLE);
            if(d.width()>=7) {
                b.put(r-1,1,f+1,Blocks.SMOKER);
                b.access.add(new BlockPos(r-1,1,f+2));
            }
        } else {
            // Every work building has a readable work station and a separate store/office.
            Block station=switch(d.role()) {
                case "SMITHY" -> Blocks.SMITHING_TABLE;
                case "MINE_ENTRANCE" -> Blocks.CARTOGRAPHY_TABLE;
                case "GUARD_POST" -> Blocks.FLETCHING_TABLE;
                case "BANK","EXCHANGE_HALL" -> BankerProfessionSupport.exchangeDeskOrLectern();
                case "GRANARY" -> Blocks.COMPOSTER;
                default -> Blocks.BARREL;
            };
            b.put(l+1,1,f+2,station); b.access.add(new BlockPos(c,1,f+2));
            b.put(r-1,1,back-1,d.role().equals("GRANARY")?Blocks.HAY_BLOCK:Blocks.BARREL);
            b.put(r-1,2,back-1,d.role().equals("BANK")?Blocks.BOOKSHELF:Blocks.LANTERN);
            if(d.depth()>=9&&!d.role().equals("MARKET_SQUARE")) partitions+=partition(b,l,r,c,f+4,1);
            if(d.upper()) {
                b.put(l+1,5,f+1,Blocks.BOOKSHELF); b.put(r-1,5,f+1,Blocks.BARREL);
                partitions+=partition(b,l,r,c,f+3,5);
            }
            if(d.role().equals("SMITHY")) {
                b.put(l+1,1,back-1,Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlockStateProperties.LIT,true));
                for(int y=2;y<=h+3;y++) b.put(l+1,y,back-1,Blocks.COBBLESTONE);
            }
            if(d.role().equals("MARKET_SQUARE")) {
                // Open-sided stalls have their own silhouette, not another residential facade.
                for(int x=l+1;x<r;x++) for(int y=1;y<=2;y++) b.remove(x,y,f);
                b.door(c,f); // Clear central entrance between the produce counters.
                for(int side:new int[]{l,r}) for(int z=f+1;z<back;z++) for(int y=2;y<=3;y++) b.remove(side,y,z);
                for(int x=l+1;x<r;x++) if(x!=c) b.put(x,1,f,Blocks.BARREL);
            }
        }
        // Light the sleeping/store end without unsupported floor ornaments.
        b.put(l+1,1,back-1, b.cells.containsKey(new BlockPos(l+1,1,back-1))
                ?b.cells.get(new BlockPos(l+1,1,back-1)):Blocks.BARREL.defaultBlockState());
        // The fixture is attached to a wall carried beam, above—not on top of—a bed.
        for(int x=l;x<=r;x++) b.put(x,4,back-2,b.p.log());
        b.put(c,3,back-2,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        if(d.upper()) {
            for(int x=l;x<=r;x++) b.put(x,8,back-2,b.p.log());
            b.put(c,7,back-2,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        }
        Block rug=s.style()==BiomeDialect.DESERT?Blocks.CARPET.brown():s.style()==BiomeDialect.SAVANNA
                ?Blocks.CARPET.orange():s.style()==BiomeDialect.TAIGA?Blocks.CARPET.green():Blocks.CARPET.red();
        for(int z=f+1;z<back-1;z++) if(!b.cells.containsKey(new BlockPos(c,1,z))) b.put(c,1,z,rug);
        Plan p=PreviewDoorwayAudit.correct(b.finish());
        PreviewRoomLayout.register(p,partitions,d.upper()?1:0,floors,targets);
        p=PreviewFacadePrograms.apply(p);
        BiomeArchitecturePreview.validate(p); return p;
    }
    private static void roof(Builder b,Spec d,int l,int r,int f,int back,int h) {
        if(b.s.style()==BiomeDialect.DESERT) { b.terrace(l,r,f,back,h+1); return; }
        if(d.role().equals("GRANARY")) {
            // Turned end-grain ridge and a tall storage gable, like a little grain barn.
            int mid=(f+back)/2;
            for(int z=f;z<=back;z++) {
                int y=h+1+Math.min(z-f,back-z);
                PreviewTaigaCraft.course(b,Direction.Axis.X,z,l,r,y,f,back);
                for(int x=l;x<=r;x++) {
                    BlockState top=b.s.style()==BiomeDialect.TAIGA?Blocks.SPRUCE_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS,Direction.Axis.X)
                            :b.s.style()==BiomeDialect.SNOWY?b.p.roof().defaultBlockState()
                            :z==mid?b.p.roofSlab().defaultBlockState():b.p.roofStairs().defaultBlockState()
                                    .setValue(StairBlock.FACING,z<mid?Direction.SOUTH:Direction.NORTH);
                    b.put(x,y,z,top);
                    if(b.s.style()==BiomeDialect.SNOWY) b.put(x,y+1,z,Blocks.SNOW);
                }
                for(int x:new int[]{l,r}) for(int yy=h+1;yy<y;yy++) b.put(x,yy,z,b.p.wall());
            }
        } else if(d.role().equals("WAREHOUSE")||d.role().equals("MINE_ENTRANCE")) {
            // Opposed lean-to work roofs: store loading range versus prospector's shelter.
            boolean reverse=d.role().equals("MINE_ENTRANCE");
            for(int x=l;x<=r;x++) {
                int rise=(reverse?r-x:x-l)/2,top=h+1+rise;
                PreviewTaigaCraft.course(b,Direction.Axis.Z,x,f,back,top,l,r);
                for(int z=f;z<=back;z++) {
                    b.put(x,top,z,b.s.style()==BiomeDialect.TAIGA?Blocks.SPRUCE_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS,Direction.Axis.Z):b.p.roof().defaultBlockState());
                    if(b.s.style()==BiomeDialect.SNOWY) b.put(x,top+1,z,Blocks.SNOW);
                    if(z==f||z==back||x==l||x==r) for(int y=h+1;y<top;y++) b.put(x,y,z,b.p.wall());
                }
            }
        } else if(d.role().equals("EXCHANGE_HALL")) {
            // A low, broad merchants' canopy, not the steep residential roof profile.
            for(int x=l;x<=r;x++) {
                int top=h+1+Math.min(x-l,r-x)/2;
                PreviewTaigaCraft.course(b,Direction.Axis.Z,x,f,back,top,l,r);
                for(int z=f;z<=back;z++) {
                    b.put(x,top,z,b.s.style()==BiomeDialect.TAIGA?Blocks.SPRUCE_LOG.defaultBlockState()
                            .setValue(BlockStateProperties.AXIS,Direction.Axis.Z):b.p.roof().defaultBlockState());
                    if(b.s.style()==BiomeDialect.SNOWY) b.put(x,top+1,z,Blocks.SNOW);
                    if(z==f||z==back) for(int y=h+1;y<top;y++) b.put(x,y,z,b.p.wall());
                }
            }
        } else if(b.s.style()==BiomeDialect.SAVANNA) b.hip(l,r,f,back,h+1);
        else b.gable(l,r,f,back,h+1,b.s.style()==BiomeDialect.SNOWY);
    }
    private static void exterior(Builder b,Spec d,int l,int r,int f,int back,int c,int h) {
        Block stone=b.s.style()==BiomeDialect.DESERT?Blocks.CUT_SANDSTONE:Blocks.COBBLESTONE;
        Block rail=b.s.style()==BiomeDialect.DESERT?Blocks.SANDSTONE_WALL
                :b.s.style()==BiomeDialect.SAVANNA?Blocks.ACACIA_FENCE
                :b.s.style()==BiomeDialect.TAIGA||b.s.style()==BiomeDialect.SNOWY?Blocks.SPRUCE_FENCE:Blocks.OAK_FENCE;
        switch(d.role()) {
            case "COTTAGE","HOUSE" -> {
                // Side garden, attached sill and a little hedge; no planter in the doorway.
                for(int z=f+1;z<=f+3;z++) {
                    b.put(r+1,0,z,Blocks.GRASS_BLOCK);
                    b.put(r+2,0,z,stone); // The garden boundary has an authored ground bearing.
                    b.put(r+2,1,z,rail);
                }
                b.put(r+1,1,f+1,b.s.style()==BiomeDialect.DESERT?Blocks.DEAD_BUSH:Blocks.POPPY);
                b.put(r+1,1,f+3,b.s.style()==BiomeDialect.TAIGA?Blocks.FERN:Blocks.DANDELION);
                if(d.id().equals("garden_cottage")) for(int x=c-1;x<=c+1;x++) b.put(x,5,0,b.p.roof());
            }
            case "INN" -> {
                // A wider arrival veranda identifies lodging and carries its lamps.
                for(int x=l;x<=r;x++) for(int z=0;z<=1;z++) { b.put(x,0,z,b.p.floor()); b.put(x,4,z,b.p.roof()); }
                for(int x:new int[]{l,r}) for(int y=1;y<=3;y++) b.put(x,y,0,b.p.log());
                for(int x:new int[]{l+1,r-1}) b.put(x,3,0,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
            }
            case "WAREHOUSE" -> {
                // Loading porch extends only to one side; the entrance is still clear.
                for(int z=f;z<=f+3;z++) { b.put(r+1,0,z,stone); b.put(r+1,4,z,b.p.roof()); }
                for(int y=1;y<=3;y++) b.put(r+1,y,f,b.p.log());
                b.put(r+1,1,f+2,Blocks.BARREL);
            }
            case "GRANARY" -> {
                for(int z=f+1;z<=f+3;z++) { b.put(l-1,0,z,stone); b.put(l-1,1,z,Blocks.HAY_BLOCK); }
                b.put(l-1,2,f+2,Blocks.HAY_BLOCK);
            }
            case "SMITHY" -> {
                for(int z=f;z<=f+3;z++) { b.put(r+1,0,z,stone); b.put(r+1,4,z,stone); }
                for(int y=1;y<=3;y++) b.put(r+1,y,f,stone);
                b.put(r+1,1,f+2,Blocks.ANVIL);
            }
            case "MINE_ENTRANCE" -> {
                // Narrow stone hood flanks the survey window, supported to the foundation.
                for(int y=0;y<=h+2;y++) b.put(l,y,back,stone);
                b.put(l, h+3,back,stone); b.put(l+1,h+3,back,stone);
            }
            case "MARKET_SQUARE" -> {
                for(int x=l;x<=r;x++) for(int z=0;z<=1;z++) { b.put(x,0,z,b.p.floor()); b.put(x,4,z,b.p.roof()); }
                for(int x:new int[]{l,r}) for(int y=1;y<=3;y++) b.put(x,y,0,b.p.log());
                for(int x:new int[]{l+1,r-1}) { b.put(x,1,0,Blocks.BARREL); b.put(x,2,0,Blocks.HAY_BLOCK); }
            }
            case "GUARD_POST" -> {
                // A roof-borne bell turret identifies the watch house without a giant tower.
                int y=h+1+(b.s.style()==BiomeDialect.SAVANNA?3:(r-l)/2);
                for(int yy=h;yy<=y+1;yy++) b.put(c,yy,back-1,b.p.log());
                for(int x=c-1;x<=c+1;x++) b.put(x,y+2,back-1,b.p.roof());
                for(int yy=h+1;yy<=y;yy++) b.put(c,yy,back-2,stone);
                b.put(c,y+1,back-2,Blocks.LANTERN);
            }
            case "EXCHANGE_HALL" -> {
                for(int z=f;z<=f+3;z++) { b.put(l-1,0,z,b.p.floor()); b.put(l-1,4,z,b.p.roof()); }
                for(int y=1;y<=3;y++) b.put(l-1,y,f,b.p.log());
                b.put(l-1,1,f+2,Blocks.LECTERN);
            }
            case "BANK" -> {
                for(int x:new int[]{l,r}) for(int y=0;y<=4;y++) b.put(x,y,f-1,stone);
                for(int x=l;x<=r;x++) b.put(x,4,f-1,b.p.log());
                b.put(c,4,f,Blocks.GLAZED_TERRACOTTA.green());
            }
            default -> throw new IllegalArgumentException(d.role());
        }
    }
    private static void beds(Builder b,int l,int r,int back,int y,int count) {
        for(int i=0;i<count;i++) {
            int x=i%2==0?l+1:r-1,z=back-2-(i/2)*3;
            var bed=Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
            b.put(x,y,z,bed.setValue(BedBlock.PART,BedPart.FOOT)); b.put(x,y,z+1,bed.setValue(BedBlock.PART,BedPart.HEAD));
            b.access.add(new BlockPos(x+(i%2==0?1:-1),y,z));
        }
    }
    private static int partition(Builder b,int l,int r,int c,int z,int y) {
        for(int x=l+1;x<r;x++) for(int yy=y;yy<=y+2;yy++) b.put(x,yy,z,b.p.wall());
        var door=b.p.door().defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH);
        b.put(c,y,z,door); b.put(c,y+1,z,door.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER));
        return 1;
    }
}
