package com.chedidandrew.emeraldstandard.minecraft;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Builder;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;

/** Explicit architectural programs, not random ornaments on one resized shell. Review only. */
final class PreviewExteriorPrograms {
    enum Form { GABLE, LONG, CROSS, HIP, SPLIT, MULTI, MONO, OFFSET, COURT, KEEP }
    enum Wing { NONE, BAY, VERANDA, LOGGIA, GLASSHOUSE, LOADING, TURRET, GATE, YARD, TWIN_BAYS, ARCADE, APSE }
    record Program(Form roof,Wing wing,int level,int position) { }
    static Program program(String id) {
        return switch(id) {
            case "cottage_hearth_01" -> p(Form.GABLE,Wing.BAY,0,0);
            case "cottage_garden_02" -> p(Form.MONO,Wing.GLASSHOUSE,0,1);
            case "cottage_courtyard_03" -> p(Form.COURT,Wing.LOGGIA,0,0);
            case "cottage_bay_04" -> p(Form.HIP,Wing.BAY,0,1);
            case "cottage_longhouse_05" -> p(Form.LONG,Wing.VERANDA,0,0);
            case "cottage_orchardstead_06" -> p(Form.SPLIT,Wing.GLASSHOUSE,0,0);
            case "house_cross_01" -> p(Form.CROSS,Wing.TWIN_BAYS,0,0);
            case "house_dormer_02" -> p(Form.GABLE,Wing.LOGGIA,1,0);
            case "house_arcade_03" -> p(Form.MULTI,Wing.ARCADE,0,1);
            case "house_hall_04" -> p(Form.HIP,Wing.LOGGIA,0,1);
            case "house_splitwing_05" -> p(Form.OFFSET,Wing.BAY,1,1);
            case "house_towercourt_06" -> p(Form.COURT,Wing.TURRET,1,0);
            case "inn_gallery_01" -> p(Form.LONG,Wing.ARCADE,1,0);
            case "inn_coachhouse_02" -> p(Form.OFFSET,Wing.GATE,0,0);
            case "inn_wayfarer_03" -> p(Form.MONO,Wing.LOGGIA,1,1);
            case "inn_tavern_04" -> p(Form.CROSS,Wing.BAY,0,1);
            case "inn_courtyard_05" -> p(Form.COURT,Wing.TWIN_BAYS,1,0);
            case "warehouse_bay_01" -> p(Form.MONO,Wing.LOADING,0,0);
            case "warehouse_crane_02" -> p(Form.SPLIT,Wing.LOADING,1,1);
            case "warehouse_gabled_03" -> p(Form.GABLE,Wing.LOADING,0,1);
            case "warehouse_wharf_04" -> p(Form.LONG,Wing.VERANDA,1,1);
            case "warehouse_basilica_05" -> p(Form.MULTI,Wing.APSE,1,0);
            case "granary_loft_01" -> p(Form.GABLE,Wing.YARD,1,0);
            case "granary_windmill_02" -> p(Form.HIP,Wing.LOADING,1,0);
            case "granary_cruck_03" -> p(Form.LONG,Wing.BAY,0,0);
            case "granary_stilt_04" -> p(Form.MONO,Wing.VERANDA,0,1);
            case "granary_silocomplex_05" -> p(Form.MULTI,Wing.TURRET,0,1);
            case "smithy_courtyard_01" -> p(Form.COURT,Wing.ARCADE,0,0);
            case "smithy_hammerhall_02" -> p(Form.MULTI,Wing.LOADING,0,0);
            case "smithy_lane_03" -> p(Form.MONO,Wing.BAY,0,0);
            case "smithy_corner_04" -> p(Form.CROSS,Wing.YARD,0,1);
            case "smithy_foundry_05" -> p(Form.SPLIT,Wing.LOADING,0,0);
            case "mine_headframe_01" -> p(Form.OFFSET,Wing.YARD,0,0);
            case "mine_winding_house_02" -> p(Form.HIP,Wing.TURRET,0,0);
            case "mine_adit_03" -> p(Form.MONO,Wing.GATE,0,1);
            case "mine_drift_04" -> p(Form.LONG,Wing.LOADING,0,1);
            case "mine_quarry_05" -> p(Form.COURT,Wing.LOADING,1,1);
            case "market_cloister_01" -> p(Form.COURT,Wing.ARCADE,1,1);
            case "market_guildcourt_02" -> p(Form.SPLIT,Wing.GATE,1,0);
            case "market_crossroads_03" -> p(Form.CROSS,Wing.LOGGIA,0,0);
            case "market_lane_04" -> p(Form.MONO,Wing.ARCADE,1,0);
            case "market_bazaar_05" -> p(Form.MULTI,Wing.GATE,1,1);
            case "guard_watch_01" -> p(Form.KEEP,Wing.TURRET,1,0);
            case "guard_bastion_02" -> p(Form.KEEP,Wing.TWIN_BAYS,0,0);
            case "guard_blockhouse_03" -> p(Form.HIP,Wing.YARD,1,1);
            case "guard_gatehouse_04" -> p(Form.SPLIT,Wing.GATE,0,1);
            case "guard_citadel_05" -> p(Form.KEEP,Wing.GATE,1,1);
            case "exchange_hall_01" -> p(Form.HIP,Wing.ARCADE,1,0);
            case "exchange_countinghouse_02" -> p(Form.OFFSET,Wing.TWIN_BAYS,0,0);
            case "exchange_branch_03" -> p(Form.LONG,Wing.LOGGIA,0,0);
            case "exchange_loggia_04" -> p(Form.CROSS,Wing.LOGGIA,1,1);
            case "exchange_bourse_05" -> p(Form.MULTI,Wing.APSE,0,1);
            default -> throw new IllegalArgumentException("No exterior program: "+id);
        };
    }
    private static Program p(Form form,Wing wing,int level,int position) { return new Program(form,wing,level,position); }
    static void roof(Builder b,String id,int front,int h,int endX) {
        var p=program(id);
        shell(b,p.roof(),0,endX,front,b.s.depth()-1,h+1+p.level(),p.position(),h);
        if(id.contains("dormer")) {
            int w=b.s.width();
            for(int x:new int[]{w/3,w-1-w/3}) dormer(b,x,front,h+2);
        }
    }
    static void shell(Builder b,Form form,int x0,int x1,int z0,int z1,int y,int side,int wallTop) {
        PreviewRoofEnvelope.carry(b,x0,x1,z0,z1,wallTop,y);
        if(x1-x0<3||z1-z0<3) { regional(b,x0,x1,z0,z1,y,wallTop); return; }
        boolean desert=b.s.style()==BiomeDialect.DESERT;
        int mid=(x0+x1)/2,zm=(z0+z1)/2;
        switch(form) {
            case GABLE -> {
                regional(b,x0,x1,z0,z1,y,wallTop);
                if(desert) raised(b,mid-1,mid+1,z0+2,z1-2,y+1,2);
            }
            case LONG -> {
                if(desert) { b.terrace(x0,x1,z0,z1,y); raised(b,x0+1,mid,z0+1,z1-1,y+1,2); }
                else ridgeZ(b,x0,x1,z0,z1,y,wallTop);
            }
            case CROSS -> {
                regional(b,x0,x1,z0,z1,y,wallTop);
                if(desert) raised(b,x0+1,x1-1,zm-1,zm+1,y+1,2);
                else ridgeZ(b,mid-2,mid+2,z0,z1,y+1,wallTop);
            }
            case HIP -> {
                if(desert) {
                    b.terrace(x0,x1,z0,z1,y);
                    raised(b,x0+2,x1-2,z0+2,z1-2,y+1,1+side);
                } else b.hip(x0,x1,z0,z1,y);
            }
            case SPLIT -> {
                regional(b,x0,mid,z0,z1,y+side,wallTop);
                regional(b,mid+1,x1,z0,z1,y+1-side,wallTop);
                for(int yy=y-1;yy<=y+1;yy++) for(int z:new int[]{z0,z1}) b.put(mid+1,yy,z,b.p.log());
            }
            case MULTI -> {
                int third=Math.max(3,(x1-x0+1)/3);
                regional(b,x0,x0+third-1,z0,z1,y,wallTop);
                regional(b,x0+third,x1-third,z0,z1,y+1+side,wallTop);
                regional(b,x1-third+1,x1,z0,z1,y,wallTop);
            }
            case MONO -> lean(b,x0,x1,z0,z1,y,side==1,wallTop);
            case OFFSET -> {
                int split=x1-Math.max(3,(x1-x0+1)/3);
                regional(b,x0,split,z0,z1,y+1,wallTop);
                lean(b,split+1,x1,z0,z1,y,side==1,wallTop);
            }
            case COURT -> {
                regional(b,x0,x0+2,z0,z1,y+1,wallTop);
                regional(b,x1-2,x1,z0,z1,y+1,wallTop);
                if(desert) {
                    regional(b,x0+3,x1-3,z0,z0+2,y+1,wallTop);
                    regional(b,x0+3,x1-3,z1-2,z1,y+1,wallTop);
                }
                else {
                    ridgeZ(b,x0+3,x1-3,z0,z0+2,y+1,wallTop);
                    ridgeZ(b,x0+3,x1-3,z1-2,z1,y+1,wallTop);
                }
                // A real recessed, glazed light court, rather than a second giant gable.
                for(int x=x0+3;x<=x1-3;x++) for(int z=z0+3;z<=z1-3;z++) b.put(x,y,z,Blocks.GLASS);
            }
            case KEEP -> {
                b.terrace(x0,x1,z0,z1,y);
                for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) if(x==x0||x==x1||z==z0||z==z1) {
                    b.put(x,y+1,z,b.p.trim());
                    if((x+z)%2==0) b.put(x,y+2,z,b.p.trim());
                }
                raised(b,side==0?x0+1:x1-3,side==0?x0+3:x1-1,z1-3,z1-1,y+1,2);
            }
        }
    }
    private static void regional(Builder b,int x0,int x1,int z0,int z1,int y,int wallTop) {
        PreviewRoofEnvelope.carry(b,x0,x1,z0,z1,wallTop,y);
        switch(b.s.style()) {
            case DESERT -> b.terrace(x0,x1,z0,z1,y);
            case SAVANNA -> b.hip(x0,x1,z0,z1,y);
            default -> b.gable(x0,x1,z0,z1,y,b.s.style()==BiomeDialect.SNOWY);
        }
    }
    private static void raised(Builder b,int x0,int x1,int z0,int z1,int y,int h) {
        for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
            for(int yy=y;yy<y+h;yy++) b.put(x,yy,z,b.p.wall());
            b.put(x,y+h,z,b.p.roof());
        }
    }
    private static void ridgeZ(Builder b,int x0,int x1,int z0,int z1,int y,int wallTop) {
        PreviewRoofEnvelope.carry(b,x0,x1,z0,z1,wallTop,y);
        if(b.s.style()==BiomeDialect.SAVANNA) { b.hip(x0,x1,z0,z1,y); return; }
        boolean snow=b.s.style()==BiomeDialect.SNOWY;
        int zm=(z0+z1)/2;
        for(int z=z0;z<=z1;z++) {
            int rise=Math.min(z-z0,z1-z)/(snow?2:1);
            PreviewTaigaCraft.course(b,Direction.Axis.X,z,x0,x1,y+rise,z0,z1);
            for(int x=x0;x<=x1;x++) {
                b.put(x,y+rise,z,snow?b.p.roof().defaultBlockState():z==zm?b.p.roofSlab().defaultBlockState()
                        :b.p.roofStairs().defaultBlockState().setValue(StairBlock.FACING,z<zm?Direction.SOUTH:Direction.NORTH));
                if(snow) b.put(x,y+rise+1,z,Blocks.SNOW);
                if(x==x0||x==x1) for(int yy=y;yy<y+rise;yy++) b.put(x,yy,z,b.p.wall());
            }
        }
    }
    private static void lean(Builder b,int x0,int x1,int z0,int z1,int y,boolean reverse,int wallTop) {
        PreviewRoofEnvelope.carry(b,x0,x1,z0,z1,wallTop,y);
        int max=b.s.style()==BiomeDialect.TAIGA?3:2;
        for(int x=x0;x<=x1;x++) {
            int step=(reverse?x1-x:x-x0)*max/Math.max(1,x1-x0);
            PreviewTaigaCraft.course(b,Direction.Axis.Z,x,z0,z1,y+step,x0,x1);
            for(int z=z0;z<=z1;z++) {
                b.put(x,y+step,z,b.p.roof());
                if(b.s.style()==BiomeDialect.SNOWY) b.put(x,y+step+1,z,Blocks.SNOW);
                if(z==z0||z==z1||x==x0||x==x1)
                    for(int yy=y;yy<y+step;yy++) b.put(x,yy,z,b.p.wall());
            }
        }
    }
    private static void dormer(Builder b,int x,int z,int y) {
        for(int dx=-1;dx<=1;dx++) for(int yy=y;yy<=y+2;yy++) b.put(x+dx,yy,z,b.p.wall());
        b.put(x,y+1,z,Blocks.GLASS);
        regional(b,x-1,x+1,z-1,z+1,y+3,y+2);
    }
    static void appendages(Builder b,String id,int h) {
        Program p=program(id); int w=b.s.width(),d=b.s.depth(),mid=w/2;
        switch(p.wing()) {
            case NONE -> { }
            case BAY -> bay(b,p.position()==0?mid:Math.max(2,mid-3),-3,0,4);
            case TWIN_BAYS -> { bay(b,2,-3,0,3); bay(b,w-3,-3,0,3); }
            case VERANDA,ARCADE -> gallery(b,p.position()==0,0,Math.max(3,d-3),p.wing()==Wing.ARCADE);
            case LOGGIA -> loggia(b,w,p.position());
            case GLASSHOUSE -> glasshouse(b,w,d,p.position());
            case LOADING -> loading(b,w,d,p.position());
            case TURRET -> turret(b,w,d,p.level());
            case GATE -> gate(b,w,p.level());
            case YARD -> yard(b,w,d,p.position());
            case APSE -> bay(b,p.position()==0?mid:Math.max(2,mid-3),d-1,d+2,h);
        }
    }
    private static void door(Builder b,int x,int z) {
        var entry=b.entrance; b.door(x,z); b.entrance=entry;
    }
    private static void hanging(Builder b,int x,int z) {
        b.put(x,4,z,b.p.log());
        b.put(x,3,z,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
    }
    private static void bay(Builder b,int center,int z0,int z1,int h) {
        int x0=center-1,x1=center+1;
        b.room(x0,x1,z0,z1,h); regional(b,x0,x1,z0,z1,h+1,h);
        door(b,center,z0); door(b,center,z1);
        hanging(b,center,(z0+z1)/2);
        for(int x:new int[]{x0,x1}) b.put(x,2,(z0+z1)/2,Blocks.GLASS_PANE);
        if(z0>=b.s.depth()-1) b.floor(center,center,b.s.depth()-1,z1);
    }
    private static void loggia(Builder b,int w,int offset) {
        int start=offset==0?1:Math.max(1,w/3),end=w-2;
        b.floor(start,end,-3,0);
        b.posts(new int[]{start,end},new int[]{-3},3);
        for(int x=start;x<=end;x++) for(int z=-3;z<=-1;z++) b.put(x,4,z,b.p.roofSlab());
        for(int x=start;x<=end;x+=4) if(Math.abs(x-b.entrance.getX())>1) b.pier(x,-3,3,b.p.log());
        hanging(b,(start+end)/2,-2);
    }
    private static void gallery(Builder b,boolean left,int z0,int z1,boolean arcade) {
        int x0=left?-3:b.s.width(),x1=x0+2;
        b.floor(x0,x1,z0,z1); b.posts(new int[]{x0,x1},new int[]{z0,z1},3);
        for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) b.put(x,4,z,b.p.roofSlab());
        if(arcade) for(int z=z0+3;z<z1;z+=3) b.pier(left?x0:x1,z,3,b.p.trim());
        hanging(b,x0+1,z0+1); hanging(b,x0+1,z1-1);
    }
    private static void glasshouse(Builder b,int w,int d,int position) {
        int z0=position==0?3:Math.max(3,d/2-2),z1=Math.min(d-2,z0+5);
        b.floor(w,w+2,0,z1); b.room(w,w+2,z0,z1,3);
        b.floor(0,w+2,-1,-1);
        for(int z=z0+1;z<z1;z++) for(int x:new int[]{w,w+2}) for(int y=2;y<=3;y++) b.put(x,y,z,Blocks.GLASS);
        for(int x=w;x<=w+2;x++) for(int z=z0;z<=z1;z++) b.put(x,4,z,Blocks.GLASS);
        door(b,w+1,z0); hanging(b,w+1,z0+1);
        b.put(w+1,1,z1-1,Blocks.BARREL); b.put(w+1,2,z1-1,Blocks.POTTED_FERN);
        b.access.add(new BlockPos(w+1,1,z1-2));
    }
    private static void loading(Builder b,int w,int d,int position) {
        int z0=position==0?2:Math.max(2,d/2),z1=Math.min(d-1,z0+4);
        b.floor(-3,-1,0,z1);
        b.pier(-3,z0,5,b.p.log());
        for(int x=-3;x<=0;x++) b.put(x,5,z0,b.p.log());
        for(int y=2;y<=4;y++) b.put(-1,y,z0,Blocks.IRON_CHAIN);
        b.put(-1,1,z0,Blocks.BARREL);
        b.put(-3,1,z1,Blocks.BARREL); b.put(-3,2,z1,Blocks.BARREL);
        b.put(-2,2,z1,Blocks.LANTERN); b.put(-2,1,z1,b.p.log());
    }
    private static void turret(Builder b,int w,int d,int level) {
        int z0=d-4,z1=d-2;
        b.floor(w,w+2,0,z1); b.room(w,w+2,z0,z1,6+level);
        door(b,w+1,z0);
        b.put(w+2,4,z0+1,Blocks.GLASS_PANE);
        regional(b,w,w+2,z0,z1,7+level,6+level); hanging(b,w+1,z0+1);
    }
    private static void gate(Builder b,int w,int level) {
        b.floor(0,w-1,-3,0);
        for(int x0:new int[]{0,w-3}) {
            b.room(x0,x0+2,-3,-1,4+level);
            b.put(x0+1,2,-3,Blocks.GLASS_PANE);
            regional(b,x0,x0+2,-3,-1,5+level,4+level);
        }
        for(int x=3;x<w-3;x++) { b.put(x,5,-2,b.p.log()); b.put(x,6,-2,b.p.roofSlab()); }
        b.put(w/2,4,-2,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
    }
    private static void yard(Builder b,int w,int d,int position) {
        int end=position==0?d/2:d-1;
        b.floor(w,w+2,0,end);
        for(int z=2;z<=end;z++) b.put(w+2,1,z,b.fence());
        b.put(w+1,1,end,Blocks.BARREL); b.put(w+1,2,end,Blocks.LANTERN);
        b.put(w,1,end,Blocks.COMPOSTER);
    }
}
