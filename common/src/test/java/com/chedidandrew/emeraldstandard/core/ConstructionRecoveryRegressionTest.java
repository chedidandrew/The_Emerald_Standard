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
        System.out.println("PASS per-build construction recovery: timing, isolation, rewinds, progress, expiry, inactivity and hard waits");
    }
    private static void require(boolean pass,String message) { if(!pass) throw new AssertionError(message); }
}
