package com.chedidandrew.emeraldstandard.core;

public final class ConstructionRecoveryRegressionTest {
    public static void main(String[] args) {
        var a=new ConstructionRecoveryWindow(); var b=new ConstructionRecoveryWindow();
        for(int tick=0;tick<=100;tick+=20) a.observe(tick,648,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.SUPPORTS_FIRST,"five eligible seconds first recover supports");
        require(b.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"another site never inherits fallback");
        a.observe(120,512,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NATIVE_ORDER,"rewinding consumed prefix is not meaningful progress");
        a.observe(140,648,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NATIVE_ORDER,"replaying old prefix does not reset recovery");
        a.observe(160,649,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"real progress immediately restores ordinary rules");
        for(int tick=180;tick<=300;tick+=20) a.observe(tick,649,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NATIVE_ORDER,"same site earns a later independent recovery");
        for(int tick=320;tick<=380;tick+=20) a.observe(tick,649,true,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"ineffective override expires instead of lasting forever");
        for(int tick=400;tick<=520;tick+=20) a.observe(tick,649,true,false);
        require(a.mode(10000)==ConstructionRecoveryWindow.Mode.NORMAL,"inactive/unloaded period cannot retain a live override");
        for(int tick=10020;tick<=10160;tick+=20) a.observe(tick,649,true,false);
        a.observe(10180,649,false,false);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"real obstruction cancels temporary override");
        a.observe(10200,649,true,true);
        require(a.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"handover resets episode");
        sweep();
        System.out.println("PASS per-build construction recovery: timing, isolation, rewinds, progress, expiry, inactivity and hard waits");
    }
    private static void sweep() {
        var recovery=new ConstructionRecoveryWindow();
        for(int tick=0;tick<=100;tick+=20) recovery.observe(tick,0,true,false);
        var visited=new boolean[12000];
        for(int pulse=0;pulse<4;pulse++) {
            int next=recovery.resumeAfter(0,visited.length);
            int end=Math.min(next+4095,visited.length);
            for(int i=next;i<end;i++) visited[i]=true;
            recovery.scannedThrough(0,end,visited.length);
            recovery.observe(120+pulse*20L,0,true,false);
        }
        for(int i=1;i<visited.length;i++) require(visited[i],"bounded sweep reaches later required cell "+i);
        require(recovery.resumeAfter(0,visited.length)==4096,"completed sweep wraps and starts the next lap");
        recovery.scannedThrough(0,7000,12000);
        for(int tick=200;tick<=220;tick+=20) recovery.observe(tick,0,true,false);
        require(recovery.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"episode expires without granting persistent authority");
        require(recovery.resumeAfter(0,12000)==7000,"slow sweeps retain scan position across eligible episodes");
        var expired=new ConstructionRecoveryWindow();
        for(int tick=0;tick<=220;tick+=20) expired.observe(tick,0,true,false);
        expired.scannedThrough(0,7000,12000);
        require(expired.mode()==ConstructionRecoveryWindow.Mode.NORMAL,"fixture expired before inactivity");
        expired.mode(10000);
        require(expired.resumeAfter(0,12000)==1,"inactivity also discards hints between active episodes");
        recovery.observe(240,0,false,false);
        require(recovery.resumeAfter(0,12000)==1,"hard wait cancels sweep");
        recovery.scannedThrough(0,7000,12000);
        recovery.observe(260,1,true,false);
        require(recovery.resumeAfter(1,12000)==2,"real prefix progress resets sweep");
        recovery.scannedThrough(1,11000,12000);
        require(recovery.resumeAfter(1,500)==2,"target shrink cannot scan unapproved work");
        require(recovery.resumeAfter(499,500)==500,"no suffix means no skipped mandatory cell");
        recovery.scannedThrough(1,11000,12000);
        for(int tick=280;tick<=400;tick+=20) recovery.observe(tick,1,true,false);
        recovery.mode(10000);
        require(recovery.resumeAfter(1,12000)==2,"inactive recovery discards scan hint");
    }
    private static void require(boolean pass,String message) { if(!pass) throw new AssertionError(message); }
}
