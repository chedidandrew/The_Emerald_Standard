package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import static com.chedidandrew.emeraldstandard.minecraft.VillageBridgeLedger.*;

/** Shared, bounded Infrastructure construction. Never loads a chunk or regenerates handed-over work. */
final class VillageBridges {
    record Context(EconomyService economy, UUID village, AuthoredVillageStructures.Materials materials,
            boolean masonry, int maxLength, int maxDepth, int concurrent) {
        Context {
            if(maxLength<2||maxLength>64||maxDepth<2||maxDepth>24||concurrent<1||concurrent>4)
                throw new IllegalArgumentException("Invalid bridge limits");
        }
        boolean mayOwn(BlockPos p) { return economy!=null&&economy.mayBridgeParcel(village,p.asLong()); }
    }
    static boolean loaded(ServerLevel level,BlockPos p) {
        if(p.getY()<level.getMinY()+1||p.getY()>level.getMaxY()-7)return false;
        for(int x=(p.getX()-1)>>4;x<=(p.getX()+1)>>4;x++)
            for(int z=(p.getZ()-1)>>4;z<=(p.getZ()+1)>>4;z++)if(!level.hasChunk(x,z))return false;
        return true;
    }
    static boolean natural(ServerLevel level,BlockPos p,BlockState s) {
        return s.getFluidState().isEmpty()&&VillageProsperityManager.isNaturalProjectGround(s)
                &&s.isFaceSturdy(level,p,Direction.UP)&&level.getBlockEntity(p)==null;
    }
    static boolean clear(BlockState s) {
        return s.isAir()||s.is(Blocks.SHORT_GRASS)||s.is(Blocks.FERN)||s.is(Blocks.DEAD_BUSH)
                ||s.is(Blocks.SNOW)||s.is(net.minecraft.tags.BlockTags.FLOWERS)&&!s.hasBlockEntity();
    }
    static boolean replaceable(ServerLevel level,BlockPos p,BlockState before,BlockState after) {
        if(level.getBlockEntity(p)!=null)return false;
        if(before.equals(after)||clear(before))return true;
        if(VillageBridgeSurvey.water(before))return !after.isAir(); // Only surveyed pier cells displace/retain water.
        return natural(level,p,before)&&!after.isAir()
                &&clear(level.getBlockState(p.above()))&&clear(level.getBlockState(p.above(2)));
    }
    static boolean permitted(ServerLevel level,WalkwayConnections.Request r,BlockPos p,
            BlockState before,BlockState after,String permit) {
        return level.getWorldBorder().isWithinBounds(p)&&!WalkwayLighting.excluded(p,r.lots(),r.banks())
                &&!ConstructionOwnership.reserved(level,p)&&level.getBlockEntity(p)==null
                &&VillageDevelopmentProtection.mayPlaceBridge(level,r.village(),r.project(),p,before,after,permit);
    }
    static boolean reserve(ServerLevel level,Context c,Plan p) {
        return reserveAll(level,c,List.of(p));
    }
    static boolean reserveAll(ServerLevel level,Context c,Collection<Plan> plans) {
        var ledger=get(level);
        Map<String,Plan> fresh=new LinkedHashMap<>();
        for(Plan p:plans) {
            Job previous=ledger.jobs.get(p.id());
            if(previous!=null) {if(previous.altered())return false;continue;}
            if(c==null||!p.village().equals(c.village().toString())||ledger.nearby(p))return false;
            fresh.put(p.id(),p);
        }
        if(ledger.jobs.size()+fresh.size()>MAX_JOBS)return false;
        // Admit the entire connected route atomically. Queued spans wait for a free crew before
        // charging; a one-crew setting can therefore build an island route one crossing at a time.
        List<Plan> staged=new ArrayList<>();
        for(Plan p:fresh.values()) {
            for(Plan other:staged)if(VillageBridgeLedger.parallel(p,other))return false;
            if(ledger.jobs.values().stream().anyMatch(j->overlaps(p,j.plan()))
                    ||staged.stream().anyMatch(other->overlaps(p,other)))return false;
            BlockPos a=BlockPos.of(p.low()),b=BlockPos.of(p.high());
            for(int x=a.getX()>>4;x<=b.getX()>>4;x++)for(int z=a.getZ()>>4;z<=b.getZ()>>4;z++)
                if(!c.mayOwn(new BlockPos(x*16+8,a.getY(),z*16+8)))return false;
            staged.add(p);
        }
        staged.forEach(p->ledger.put(Job.fresh(p)));
        return true;
    }
    private static boolean overlaps(Plan p,Plan other) {
        BlockPos a=BlockPos.of(p.low()),b=BlockPos.of(p.high());
        BlockPos c=BlockPos.of(other.low()),d=BlockPos.of(other.high());
        return a.getX()<=d.getX()&&c.getX()<=b.getX()&&a.getZ()<=d.getZ()&&c.getZ()<=b.getZ()
                &&a.getY()<=d.getY()&&c.getY()<=b.getY();
    }
    static int advance(ServerLevel level,WalkwayConnections.Request r,Context c,String id,int budget) {
        var ledger=get(level);Job j=ledger.jobs.get(id);
        if(j==null||j.done()||j.altered()||budget<=0||c==null)return 0;
        Plan p=j.plan();
        // A shared crossing retains its original claim identity even when another road helps it.
        r=new WalkwayConnections.Request(r.village(),p.project(),r.origin(),r.start(),r.destination(),
                r.streetGoal(),r.oldColumns(),r.lots(),r.banks(),r.desert());
        if(!p.village().equals(r.village().toString()))return 0;
        if(j.cursor()>p.pieces().size()) {ledger.put(j.status("altered","Invalid saved bridge progress"));return 0;}
        long deadline=System.nanoTime()+2_000_000L;
        int audit=j.audit(),count=0;
        // Before spending, validate the entire frozen plan in resumable batches. During building,
        // rotate a short audit of already placed work so removed supports cannot be rebuilt forever.
        int auditLimit=j.funded()?j.cursor():p.pieces().size();
        while(audit<auditLimit&&count++<32) {
            if(count>1&&System.nanoTime()>=deadline)break;
            Piece piece=p.pieces().get(audit);BlockPos pos=BlockPos.of(piece.pos());
            if(!loaded(level,pos)) {ledger.put(j.status("waiting","Waiting for both shores and bridge chunks"));return 0;}
            BlockState current=level.getBlockState(pos);
            BlockState expected=j.funded()?piece.after():piece.before();
            if(!same(current,expected)&&!(current.equals(piece.after())&&!j.funded())) {
                ledger.put(j.status("altered","Bridge site changed at "+pos.toShortString()+"; preserving alterations"));return 0;
            }
            if(!c.mayOwn(pos)||!permitted(level,r,pos,current,piece.after(),id)) {
                ledger.put(j.status("waiting","Bridge land is no longer available"));return 0;
            }
            audit++;
        }
        j=new Job(p,j.cursor(),j.verify(),audit,j.funded(),j.state(),j.reason());ledger.put(j);
        if(!j.funded()) {
            if(audit<p.pieces().size())return 0;
            if(level.noSave())return 0;
            if(!ledger.crewAvailable(c.concurrent())) {
                ledger.put(j.status("queued","Waiting for an Infrastructure crew"));return 0;
            }
            try {
                // Plan first, then an idempotent economy journal receipt. A crash between these
                // checkpoints can delay work but cannot duplicate the charge.
                if(!j.state().equals("funding")) {
                    ledger.put(j.status("funding","Gathering Infrastructure materials and treasury funds"));
                    level.getDataStorage().saveAndJoin();
                }
                if(!c.economy().fundVillageBridge(c.village(),p.id(),p.waterLength(),p.operations(),p.low(),p.high())) {
                    ledger.put(j.status("funding","Gathering Infrastructure materials and treasury funds"));return 0;
                }
            } catch(RuntimeException error) {ledger.put(j.status("waiting","Bridge could not be saved"));return 0;}
            j=new Job(p,0,0,0,true,"building","Foundations and framing");ledger.put(j);
            return 0;
        }
        if(audit>=auditLimit)audit=0;
        int cursor=j.cursor(),writes=0;
        count=0;
        while(cursor<p.pieces().size()&&writes<budget&&count++<32) {
            if(count>1&&System.nanoTime()>=deadline)break;
            Piece piece=p.pieces().get(cursor);BlockPos pos=BlockPos.of(piece.pos());
            if(!loaded(level,pos))break;
            BlockState current=level.getBlockState(pos);
            if(same(current,piece.after())) {cursor++;continue;}
            if(!same(current,piece.before())) {
                ledger.put(new Job(p,cursor,0,audit,true,"altered","Bridge site changed at "+pos.toShortString()));return writes;
            }
            if(!c.mayOwn(pos)||!permitted(level,r,pos,current,piece.after(),id)
                    ||!VillageConstructionOccupancy.mayChange(level,pos,current,piece.after())
                    ||!piece.after().canSurvive(level,pos))break;
            if(!level.setBlock(pos,piece.after(),Block.UPDATE_ALL))break;
            writes++;cursor++;
            ledger.put(new Job(p,cursor,0,audit,true,"building",phase(piece.phase())));
        }
        if(cursor<p.pieces().size()) {
            ledger.put(new Job(p,cursor,0,audit,true,"building",
                    writes==0?"Waiting for clear, loaded bridge work":phase(p.pieces().get(Math.max(0,cursor-1)).phase())));
            return writes;
        }
        int verify=j.verify();
        count=0;
        while(verify<p.pieces().size()&&count++<32) {
            if(count>1&&System.nanoTime()>=deadline)break;
            Piece piece=p.pieces().get(verify);BlockPos pos=BlockPos.of(piece.pos());
            if(!loaded(level,pos))break;
            if(!same(level.getBlockState(pos),piece.after())) {
                ledger.put(new Job(p,cursor,verify,0,true,"altered","Bridge changed before handover at "+pos.toShortString()));
                return writes;
            }
            if(!c.mayOwn(pos)||!permitted(level,r,pos,level.getBlockState(pos),piece.after(),id))break;
            verify++;
        }
        boolean done=verify==p.pieces().size();
        ledger.put(new Job(p,cursor,verify,audit,true,done?"complete":"verifying",
                done?"Crossing open":"Checking deck, approaches, railings and navigation channel"));
        return writes;
    }
    private static String phase(int phase) {
        return switch(phase) {case 0 -> "Clearing bridge approaches";case 1 -> "Foundations and framing";
            case 2 -> "Laying bridge deck";case 3 -> "Railings and lamps";case 4 -> "Hanging lanterns";default -> "Checking clear passage";};
    }
    static boolean same(BlockState actual,BlockState expected) {
        if(!actual.is(expected.getBlock()))return false;
        if(actual.getBlock() instanceof FenceBlock||actual.getBlock() instanceof WallBlock)
            return actual.getFluidState().equals(expected.getFluidState());
        if(actual.getBlock() instanceof StairBlock)
            return actual.setValue(StairBlock.SHAPE,expected.getValue(StairBlock.SHAPE)).equals(expected);
        return actual.equals(expected);
    }
    static boolean walkable(ServerLevel level,UUID village,BlockPos p) {
        var ledger=get(level);String id=ledger.reservation(p);
        if(id==null)return false;
        Job job=ledger.jobs.get(id);
        if(!job.done()||!job.plan().village().equals(village.toString()))return false;
        Piece floor=ledger.piece(id,p.asLong());
        return floor!=null&&floor.phase()==2&&same(level.getBlockState(p),floor.after())
                &&level.getBlockState(p.above()).isAir()&&level.getBlockState(p.above(2)).isAir();
    }
    static Map<String,Object> report(ServerLevel level,String id) {
        Job j=get(level).jobs.get(id);
        if(j==null)return Map.of();
        return Map.of("id",id,"style",j.plan().style(),"waterLength",j.plan().waterLength(),"state",j.state(),
                "placed",j.cursor(),"total",j.plan().pieces().size(),"funded",j.funded(),"reason",j.reason());
    }
}
