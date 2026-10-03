package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;

/** Review-only site reservations, separate from the immutable occupied building envelope.
 * These are real blueprint attachments, not entities or a random scatter over existing rooms.
 * Production adoption must reserve the complete site and survey its terrain before selection. */
final class PreviewOutdoorPrograms {
    static final int GROUND_Y=-1;
    enum Feature { FLOWERS, HERBS, VEGETABLES, ORCHARD, HEDGE, PATIO, PERGOLA, WASH,
        WELL, POND, FOUNTAIN, CISTERN, FIREWOOD, LOGPILE, STUMPS, CAMP, TENT, CART,
        CRATES, HAY, PEN, HIVE, FORGE, STONECUT, TEXTILES, DRYING, FISHING, NOTICE }
    record Site(PlanIdentity identity, Map<BlockPos,BlockState> cells, Set<BlockPos> routes,
            List<Feature> features, int minX, int maxX, int minZ, int maxZ) { }
    record PlanIdentity(String id, Map<BlockPos,BlockState> building) { }
    private static final Map<String,Site> CACHE=new HashMap<>();
    static Site lowerToGrade(Site draft) {
        Map<BlockPos,BlockState> lowered=new LinkedHashMap<>();
        draft.cells().forEach((at,state)->lowered.put(at.below(),state));
        Set<BlockPos> routes=new HashSet<>();draft.routes().forEach(at->routes.add(at.below()));
        return new Site(draft.identity(),Map.copyOf(lowered),Set.copyOf(routes),draft.features(),
                draft.minX(),draft.maxX(),draft.minZ(),draft.maxZ());
    }
    static Site site(BiomeArchitecturePreview.Plan p) {
        Site old=CACHE.get(p.sample().id());
        if(old!=null&&old.identity().building().equals(p.cells())) return old;
        Site result=new Yard(p).build(); validate(p,result); CACHE.put(p.sample().id(),result); return result;
    }
    static List<StructureGalleryBlock> blocks(BiomeArchitecturePreview.Plan p,BlockPos origin) {
        var cells=new HashMap<>(p.cells()); cells.putAll(site(p).cells());
        return cells.entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator
                // Install every bearing before ladders; a valid final map alone cannot stop
                // neighbor updates popping a rung while its later wall block is still air.
                .comparingInt((BlockPos at)->cells.get(at).getBlock() instanceof LadderBlock?1:0)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX)))
                .map(e->new StructureGalleryBlock(origin.offset(e.getKey()),e.getValue())).toList();
    }
    private static final class Yard {
        final BiomeArchitecturePreview.Plan p; final BiomeDialect style; final int w,d,seed;
        final Map<BlockPos,BlockState> cells=new LinkedHashMap<>(); final Set<BlockPos> routes=new HashSet<>();
        final List<Feature> features=new ArrayList<>(); final Block timber,stone,ground,path,fence,flower,leaf;
        Yard(BiomeArchitecturePreview.Plan p) {
            this.p=p;style=p.sample().style();w=p.sample().width();d=p.sample().depth();
            seed=p.sample().id().hashCode()&Integer.MAX_VALUE;
            timber=switch(style){case PLAINS->Blocks.OAK_LOG;case SAVANNA->Blocks.ACACIA_LOG;
                case DESERT->Blocks.SMOOTH_SANDSTONE;default->Blocks.SPRUCE_LOG;};
            stone=style==BiomeDialect.DESERT?Blocks.CUT_SANDSTONE:Blocks.COBBLESTONE;
            ground=switch(style){case DESERT->Blocks.SAND;case SNOWY->Blocks.SNOW_BLOCK;default->Blocks.GRASS_BLOCK;};
            path=switch(style){case DESERT->Blocks.SMOOTH_SANDSTONE;case SNOWY->Blocks.COBBLESTONE;
                case SAVANNA->Blocks.TERRACOTTA;default->Blocks.DIRT_PATH;};
            fence=switch(style){case DESERT->Blocks.SANDSTONE_WALL;case PLAINS->Blocks.OAK_FENCE;
                case SAVANNA->Blocks.ACACIA_FENCE;default->Blocks.SPRUCE_FENCE;};
            flower=switch(style){case DESERT->Blocks.POTTED_CACTUS;case SNOWY,TAIGA->Blocks.POTTED_FERN;
                case SAVANNA->Blocks.POTTED_ACACIA_SAPLING;case PLAINS->Blocks.POTTED_POPPY;};
            leaf=switch(style){case DESERT->Blocks.AZALEA_LEAVES;case PLAINS->Blocks.OAK_LEAVES;
                case SAVANNA->Blocks.ACACIA_LEAVES;default->Blocks.SPRUCE_LEAVES;};
        }
        Site build() {
            boolean compact=PreviewCompactBuildings.isCompact(p.sample());
            int cols=Math.max(2,Math.min(6,(w+6)/7)),rows=compact?1:2;
            // A rear court and two narrow side verges, with no ground edits inside the building.
            for(int x=-6;x<w+6;x++) for(int z=-4;z<d+4+7*rows;z++)
                if(x<0||x>=w||z<0||z>=d) put(x,0,z,ground);
            for(int z=-2;z<=d+1;z++) { route(-2,z);route(w+1,z); }
            for(int x=-2;x<=w+1;x++) { route(x,-2);route(x,d+1); }
            var choices=new ArrayList<>(choices(p.sample().role(),style));
            Collections.shuffle(choices,new Random(seed));
            for(int row=0;row<rows;row++) for(int col=0;col<cols;col++) {
                int x=-4+col*7,z=d+3+row*7;
                for(int i=0;i<7;i++){route(x+i,z);route(x+i,z+6);route(x,z+i);route(x+6,z+i);}
                // Alternating connecting spurs remain bare and two blocks tall.
                for(int zz=d+1;zz<=z;zz++) route(x,zz);
                int choice=col+row*cols;
                if(choice<choices.size()) {
                    Feature feature=choices.get(choice);
                    features.add(feature); feature(feature,x+1,z+1);
                } // The remaining area is usable lawn, not duplicate gazebos or storage piles.
            }
            // Front/side planting and work props are offset from the circulation lane, not door ornaments.
            for(int z=3;z<d-2;z+=7) {
                if((seed+z)%3==0) { stump(-5,z);crate(w+3,z); }
                else { planter(-5,z);planter(w+3,z+1); }
            }
            // All yard perimeter fences have purposeful gaps onto the surrounding path.
            for(int x=-4;x<w+4;x++) if(Math.floorMod(x-seed,7)>1) put(x,1,d+3+7*rows,fence);
            for(int z=d+4;z<d+2+7*rows;z++) if(z%7>1) {put(-5,1,z,fence);put(w+4,1,z,fence);}
            // Night-time circulation lighting: carried lanterns on low stone plinths.
            for(int z=-1;z<d+3+7*rows;z+=7) { lamp(-6,z);lamp(w+5,z); }
            for(int x=-3;x<w+3;x+=7) {lamp(x,-4);lamp(x,d+3+7*rows);}
            int maxZ=cells.keySet().stream().mapToInt(BlockPos::getZ).max().orElse(d);
            // Move the entire site, including buried pond bases and walking coordinates.
            // The building's immutable cells and floor elevation are not part of this shift.
            return lowerToGrade(new Site(new PlanIdentity(p.sample().id(),p.cells()),Map.copyOf(cells),Set.copyOf(routes),
                    List.copyOf(features),-6,w+5,-4,maxZ));
        }
        void put(int x,int y,int z,Block block){put(x,y,z,block.defaultBlockState());}
        void put(int x,int y,int z,BlockState state) {
            BlockPos at=new BlockPos(x,y,z);
            if(p.cells().containsKey(at)) throw new IllegalStateException("Outdoor overwrite "+p.sample().id()+" "+at);
            if(y>0&&routes.contains(new BlockPos(x,1,z))) throw new IllegalStateException("Outdoor prop in yard route "+at);
            cells.put(at,state);
        }
        void route(int x,int z) { put(x,0,z,path); routes.add(new BlockPos(x,1,z)); }
        void lamp(int x,int z){put(x,1,z,stone);put(x,2,z,Blocks.LANTERN);}
        void planter(int x,int z){put(x,1,z,stone);put(x,2,z,flower);}
        void crate(int x,int z){put(x,1,z,Blocks.BARREL);}
        void stump(int x,int z){put(x,0,z,style==BiomeDialect.DESERT?path:Blocks.DIRT);put(x,1,z,timber);}
        void log(int x,int y,int z,Direction.Axis axis){put(x,y,z,timber.defaultBlockState()
                .trySetValue(BlockStateProperties.AXIS,axis));}
        void rug(int x,int z){put(x,1,z,(style==BiomeDialect.DESERT||style==BiomeDialect.SAVANNA
                ?Blocks.CARPET.orange():style==BiomeDialect.SNOWY?Blocks.CARPET.blue():Blocks.CARPET.green()));}
        void basin(int x,int z,boolean roof) {
            put(x,0,z,stone);put(x,1,z,Blocks.WATER);
            for(Direction face:Direction.Plane.HORIZONTAL) put(x+face.getStepX(),1,z+face.getStepZ(),stone);
            if(roof) {
                for(int dx:new int[]{-1,1}) for(int dz:new int[]{-1,1})
                    for(int y=1;y<=3;y++) put(x+dx,y,z+dz,timber);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)log(x+dx,4,z+dz,Direction.Axis.Z);
                put(x,5,z,Blocks.LANTERN);
            }
        }
        void shade(int x,int z,Block cloth) {
            for(int dx:new int[]{0,4}) for(int dz:new int[]{0,4})for(int y=1;y<=3;y++)put(x+dx,y,z+dz,timber);
            for(int xx=0;xx<5;xx++)for(int zz=0;zz<5;zz++)put(x+xx,4,z+zz,cloth);
        }
        void feature(Feature f,int x,int z) {
            switch(f) {
                case FLOWERS,HERBS,VEGETABLES -> {
                    for(int dx:new int[]{0,4})for(int zz=0;zz<5;zz++)put(x+dx,1,z+zz,fence);
                    for(int xx=1;xx<4;xx++)for(int zz:new int[]{0,2,4}) {
                        if(style==BiomeDialect.DESERT||style==BiomeDialect.SNOWY){put(x+xx,1,z+zz,stone);
                            put(x+xx,2,z+zz,f==Feature.FLOWERS?((xx+zz)%2==0?Blocks.POTTED_RED_TULIP:Blocks.POTTED_DANDELION)
                                :f==Feature.HERBS?Blocks.POTTED_FERN:flower);}
                        else if(f==Feature.VEGETABLES){put(x+xx,0,z+zz,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7));
                            put(x+xx,1,z+zz,(seed%2==0?Blocks.WHEAT:Blocks.CARROTS).defaultBlockState().setValue(BlockStateProperties.AGE_7,7));}
                        else {put(x+xx,0,z+zz,Blocks.GRASS_BLOCK);put(x+xx,1,z+zz,f==Feature.HERBS?Blocks.FERN
                            :style==BiomeDialect.SAVANNA?Blocks.DANDELION:(xx+zz)%2==0?Blocks.POPPY:Blocks.OXEYE_DAISY);}
                    }
                    crate(x+2,z+3);
                }
                case ORCHARD,HEDGE -> {
                    if(style==BiomeDialect.DESERT){shade(x,z,Blocks.WOOL.white());planter(x+2,z+2);break;}
                    for(int xx:new int[]{1,3}) {
                        for(int y=1;y<=3;y++)log(x+xx,y,z+2,Direction.Axis.Y);
                        boolean conifer=style==BiomeDialect.TAIGA||style==BiomeDialect.SNOWY;
                        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
                            if(conifer&&(dx!=0||dz!=0))put(x+xx+dx,3,z+2+dz,leaf.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
                            if(!conifer||Math.abs(dx)+Math.abs(dz)<=1)
                                put(x+xx+dx,4,z+2+dz,leaf.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
                        }
                        put(x+xx,5,z+2,leaf.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
                    }
                    for(int xx=0;xx<5;xx++)put(x+xx,1,z,leaf.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
                }
                case WELL -> basin(x+2,z+2,true);
                case FOUNTAIN -> {basin(x+2,z+2,false);planter(x,z);planter(x+4,z+4);}
                case CISTERN,WASH -> {
                    put(x+1,1,z+2,Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL,3));
                    put(x+3,1,z+2,stone);rug(x+2,z+4);crate(x+4,z);planter(x,z);
                    // Wash courts stay open; repeating a full canopy here overwhelms small homes.
                }
                case POND,FISHING -> {
                    for(int xx=1;xx<4;xx++)for(int zz=1;zz<4;zz++)put(x+xx,0,z+zz,stone);
                    put(x+2,-1,z+2,stone);put(x+2,0,z+2,style==BiomeDialect.SNOWY?Blocks.PACKED_ICE:Blocks.WATER);
                    if(style!=BiomeDialect.SNOWY&&style!=BiomeDialect.DESERT)put(x+2,1,z+2,Blocks.LILY_PAD);
                    planter(x,z);crate(x+4,z+4);log(x+1,1,z+4,Direction.Axis.X);log(x+2,1,z+4,Direction.Axis.X);
                }
                case FIREWOOD,LOGPILE,STUMPS -> {
                    for(int xx=0;xx<4;xx++){log(x+xx,1,z+1,Direction.Axis.X);log(x+xx,1,z+2,Direction.Axis.X);}
                    if(f!=Feature.STUMPS){for(int xx=1;xx<3;xx++)log(x+xx,2,z+1,Direction.Axis.X);}
                    stump(x+4,z+4);crate(x,z+4);
                    if(f==Feature.FIREWOOD){shade(x,z,style==BiomeDialect.DESERT?Blocks.WOOL.white():timber);}
                }
                case CAMP -> {
                    for(Direction face:Direction.Plane.HORIZONTAL)put(x+2+face.getStepX(),1,z+2+face.getStepZ(),stone);
                    put(x+2,1,z+2,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false));
                    log(x,1,z+1,Direction.Axis.Z);log(x,1,z+2,Direction.Axis.Z);crate(x+4,z);rug(x+4,z+4);
                }
                case TENT -> {
                    Block cloth=style==BiomeDialect.SNOWY?Blocks.WOOL.blue():style==BiomeDialect.DESERT?Blocks.WOOL.white():Blocks.WOOL.brown();
                    for(int zz=0;zz<4;zz++) {
                        for(int y=1;y<=2;y++){put(x,y,z+zz,cloth);put(x+4,y,z+zz,cloth);}
                        for(int xx=0;xx<5;xx++)put(x+xx,3,z+zz,cloth);
                        for(int xx=1;xx<4;xx++)put(x+xx,4,z+zz,cloth);
                        put(x+2,5,z+zz,cloth);
                    }
                    // Actual open sleeping shelter, not a fake door or an unregistered extra bed.
                    for(int zz=0;zz<4;zz++)rug(x+2,z+zz);
                    crate(x+4,z+4);planter(x,z+4);
                }
                case PATIO,PERGOLA -> {
                    log(x,1,z+2,Direction.Axis.Z);log(x+4,1,z+2,Direction.Axis.Z);
                    put(x+2,1,z+2,timber);put(x+2,2,z+2,Blocks.LANTERN);
                    rug(x+1,z+3);rug(x+2,z+3);rug(x+3,z+3);planter(x,z);planter(x+4,z+4);
                    if(f==Feature.PERGOLA) {
                        shade(x,z,style==BiomeDialect.DESERT?Blocks.WOOL.white():timber);
                        if(style!=BiomeDialect.DESERT)for(int xx=1;xx<4;xx++)for(int zz=1;zz<4;zz++)
                            if(xx!=2)cells.remove(new BlockPos(x+xx,4,z+zz));
                    }
                }
                case CART -> {
                    for(int zz=1;zz<4;zz++){log(x,1,z+zz,Direction.Axis.X);log(x+4,1,z+zz,Direction.Axis.X);}
                    for(int xx=1;xx<4;xx++)for(int zz=1;zz<4;zz++)put(x+xx,1,z+zz,timber);
                    crate(x+1,z+1);put(x+2,2,z+2,Blocks.BARREL);put(x+3,2,z+3,Blocks.HAY_BLOCK);
                }
                case CRATES,HAY -> {
                    for(int xx=0;xx<4;xx++){log(x+xx,1,z+2,Direction.Axis.X);put(x+xx,2,z+2,f==Feature.HAY?Blocks.HAY_BLOCK:Blocks.BARREL);}
                    put(x+1,3,z+2,f==Feature.HAY?Blocks.HAY_BLOCK:Blocks.BARREL);crate(x+4,z);
                }
                case PEN -> {
                    for(int i=0;i<5;i++){put(x,1,z+i,fence);put(x+4,1,z+i,fence);put(x+i,1,z+4,fence);if(i!=2)put(x+i,1,z,fence);}
                    put(x+1,1,z+3,Blocks.HAY_BLOCK);put(x+3,1,z+3,Blocks.WATER_CAULDRON.defaultBlockState()
                        .setValue(LayeredCauldronBlock.LEVEL,3));
                }
                case HIVE -> {
                    for(int xx:new int[]{1,3}){put(x+xx,1,z+2,timber);put(x+xx,2,z+2,Blocks.BEEHIVE);}
                    planter(x,z);planter(x+4,z+4);
                }
                case FORGE,STONECUT -> {
                    put(x+1,1,z+1,Blocks.FURNACE);put(x+2,1,z+1,Blocks.FURNACE);
                    put(x+3,1,z+2,f==Feature.FORGE?Blocks.ANVIL:Blocks.STONECUTTER);
                    for(int xx=1;xx<4;xx++)put(x+xx,1,z+4,f==Feature.FORGE?Blocks.SMOOTH_STONE:stone);
                    put(x+1,2,z+4,stone);crate(x,z+3);
                }
                case TEXTILES,DRYING -> {
                    shade(x,z,style==BiomeDialect.SNOWY?Blocks.WOOL.blue():Blocks.WOOL.white());
                    put(x+1,1,z+1,f==Feature.TEXTILES?Blocks.LOOM:Blocks.SMOKER);crate(x+3,z+1);
                    for(int xx=1;xx<4;xx++){rug(x+xx,z+3);put(x+xx,3,z+2,Blocks.IRON_CHAIN);}
                }
                case NOTICE -> {
                    put(x+1,1,z+2,timber);put(x+1,2,z+2,Blocks.LECTERN);put(x+3,1,z+2,timber);
                    put(x+3,2,z+2,Blocks.LANTERN);planter(x,z);planter(x+4,z+4);rug(x+2,z+3);
                }
            }
            // A small lamp carried by the tile's corner boundary lights each outdoor room.
            lamp(x+4,z+1);
        }
    }
    private static List<Feature> choices(String role,BiomeDialect style) {
        List<Feature> list=new ArrayList<>(switch(role) {
            case "COTTAGE","HOUSE"->List.of(Feature.FLOWERS,Feature.HERBS,Feature.VEGETABLES,Feature.ORCHARD,
                Feature.HEDGE,Feature.PATIO,Feature.WASH,Feature.WELL,Feature.POND,Feature.FIREWOOD,Feature.HIVE,Feature.PERGOLA);
            case "INN"->List.of(Feature.PERGOLA,Feature.PATIO,Feature.TENT,Feature.CAMP,Feature.WASH,Feature.WELL,
                Feature.PEN,Feature.CART,Feature.FLOWERS,Feature.FIREWOOD);
            case "SMITHY"->List.of(Feature.FORGE,Feature.STONECUT,Feature.CRATES,Feature.LOGPILE,Feature.CART,Feature.CISTERN);
            case "MINE_ENTRANCE"->List.of(Feature.STONECUT,Feature.TENT,Feature.CAMP,Feature.LOGPILE,Feature.STUMPS,Feature.CRATES,Feature.CART,Feature.WELL);
            case "WAREHOUSE"->List.of(Feature.CART,Feature.CRATES,Feature.TEXTILES,Feature.DRYING,Feature.FISHING,Feature.FIREWOOD);
            case "GRANARY"->List.of(Feature.HAY,Feature.VEGETABLES,Feature.HERBS,Feature.PEN,Feature.CART,Feature.CRATES,Feature.WELL);
            case "MARKET_SQUARE"->List.of(Feature.TEXTILES,Feature.DRYING,Feature.FISHING,Feature.CART,Feature.HAY,Feature.PERGOLA,Feature.WASH);
            case "GUARD_POST"->List.of(Feature.TENT,Feature.CAMP,Feature.FIREWOOD,Feature.STONECUT,Feature.NOTICE,Feature.WELL);
            case "EXCHANGE_HALL","BANK"->List.of(Feature.NOTICE,Feature.PATIO,Feature.FOUNTAIN,Feature.FLOWERS,Feature.HEDGE,Feature.PERGOLA,Feature.WELL);
            default->throw new IllegalStateException("No outdoor purpose for "+role);
        });
        if(style==BiomeDialect.DESERT)list.replaceAll(f->switch(f){case HIVE,ORCHARD,HEDGE->Feature.CISTERN;
            case POND,FISHING->Feature.WASH;case FIREWOOD,LOGPILE->Feature.STONECUT;default->f;});
        if(style==BiomeDialect.SNOWY)list.replaceAll(f->switch(f){case VEGETABLES,HIVE,HERBS->Feature.FIREWOOD;
            case FLOWERS,HEDGE->Feature.WASH;default->f;});
        return list.stream().distinct().toList();
    }
    static void validate(BiomeArchitecturePreview.Plan p,Site s) {
        if(!s.identity().id().equals(p.sample().id())||!s.identity().building().equals(p.cells()))
            throw new IllegalStateException("Outdoor plan changed occupied building");
        Map<BlockPos,BlockState> all=new HashMap<>(p.cells());
        for(var e:s.cells().entrySet()) {
            BlockPos at=e.getKey();
            if(all.putIfAbsent(at,e.getValue())!=null)throw new IllegalStateException("Outdoor overlap "+at);
            if(at.getX()<s.minX()||at.getX()>s.maxX()||at.getZ()<s.minZ()||at.getZ()>s.maxZ()||at.getY() < GROUND_Y-1||at.getY()>GROUND_Y+5)
                throw new IllegalStateException("Unreserved outdoor cell "+at);
            if(at.getX()>=0&&at.getX()<p.sample().width()&&at.getZ()>=0&&at.getZ()<p.sample().depth())
                throw new IllegalStateException("Outdoor addition inside protected building bounds "+at);
        }
        for(BlockPos at:s.routes()) {
            if(at.getY()!=GROUND_Y+1)throw new IllegalStateException("Raised yard path "+at);
            if(!PreviewDoorwayAudit.clear(all,at))throw new IllegalStateException("Blocked yard path "+at);
        }
        // Every garden/work tile joins the same continuous ring; no isolated fenced destination.
        if(!yardReachable(all,new BlockPos(-2,GROUND_Y+1,-2)).containsAll(s.routes()))
            throw new IllegalStateException("Disconnected outdoor circulation "+p.sample().id());
        for(var e:s.cells().entrySet()) {
            BlockPos at=e.getKey();BlockState state=e.getValue(),below=all.get(at.below());
            if(state.is(Blocks.WATER)) {
                if(!sealed(below,at.below()))throw new IllegalStateException("Leaking water base "+at);
                for(Direction side:Direction.Plane.HORIZONTAL)if(!sealed(all.get(at.relative(side)),at.relative(side)))
                    throw new IllegalStateException("Uncontained water "+at);
            }
            if(state.is(Blocks.DIRT_PATH)) {
                BlockState top=all.get(at.above());
                if(top!=null&&!top.isAir()&&!top.getCollisionShape(EmptyBlockGetter.INSTANCE,at.above()).isEmpty())
                    throw new IllegalStateException("Dirt path under an outdoor prop "+at);
            }
            if(state.is(Blocks.LANTERN)||state.getBlock() instanceof FlowerPotBlock||state.getBlock() instanceof CarpetBlock
                    ||state.is(Blocks.CAMPFIRE)||state.is(Blocks.BEEHIVE))
                if(!full(below,at.below()))throw new IllegalStateException("Unsupported outdoor fixture "+at);
            if(state.is(Blocks.IRON_CHAIN)&&!full(all.get(at.above()),at.above()))throw new IllegalStateException("Floating drying chain "+at);
            if(state.is(Blocks.CAMPFIRE)&&state.getValue(CampfireBlock.LIT))throw new IllegalStateException("Outdoor fire on walking surface");
            if(p.sample().style()==BiomeDialect.DESERT&&(state.is(Blocks.COARSE_DIRT)||state.is(Blocks.GRAVEL)
                    ||state.is(Blocks.ACACIA_LOG)||state.is(Blocks.ACACIA_PLANKS)))throw new IllegalStateException("Non-desert outdoor palette");
        }
        PreviewDoorwayAudit.validate(new BiomeArchitecturePreview.Plan(p.sample(),Map.copyOf(all),p.access(),p.entrance(),p.height()));
        PreviewSupportAudit.validate(p,all);
    }
    private static boolean full(BlockState state,BlockPos at) {
        return state!=null&&state.isFaceSturdy(EmptyBlockGetter.INSTANCE,at,Direction.UP);
    }
    private static boolean sealed(BlockState state,BlockPos at) {
        return state!=null&&Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE,at));
    }
    private static Set<BlockPos> yardReachable(Map<BlockPos,BlockState> all,BlockPos start) {
        Set<BlockPos> seen=new HashSet<>();ArrayDeque<BlockPos> queue=new ArrayDeque<>();queue.add(start);
        while(!queue.isEmpty()) {
            BlockPos at=queue.removeFirst();BlockState floor=all.get(at.below());
            // Dirt paths have a native 15/16-high walking surface, not a sturdy full-height top.
            if(floor==null||(!full(floor,at.below())&&!floor.is(Blocks.DIRT_PATH))
                    ||!PreviewDoorwayAudit.clear(all,at)||!seen.add(at))continue;
            for(Direction direction:Direction.Plane.HORIZONTAL)queue.add(at.relative(direction));
        }
        return seen;
    }
    static void report(List<BiomeArchitecturePreview.Sample> samples) {
        Set<Feature> used=new HashSet<>();int count=0,cells=0;
        for(var sample:samples){var p=BiomeArchitecturePreview.plan(sample);var s=site(p);validate(p,s);
            if(!s.equals(new Yard(p).build()))throw new IllegalStateException("Nondeterministic outdoor replay "+sample.id());
            if(new HashSet<>(s.features()).size()!=s.features().size())throw new IllegalStateException("Repeated yard feature "+sample.id());
            used.addAll(s.features());cells+=s.cells().size();count+=s.features().size();}
        if(used.size()!=Feature.values().length)throw new IllegalStateException("Missing outdoor feature family "+used);
        System.out.println("OUTDOOR SCOPE "+samples.size()+" sites; "+count+" purposeful yard modules; "+used.size()+" feature families; "+cells+" site cells; building envelopes retained");
    }
}
