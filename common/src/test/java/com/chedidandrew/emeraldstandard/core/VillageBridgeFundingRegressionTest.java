package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.*;

/** Payment receipts survive full snapshots and the real economy's village journal. */
public final class VillageBridgeFundingRegressionTest {
    public static void main(String[] args) throws Exception {
        var v=new EconomyState.VillageRecord();
        String receipt=UUID.randomUUID().toString();
        check(!VillageBridgeFunding.pay(v,receipt,24,640,false),"unfunded bridge admitted");
        check(v.bridgeFundingReceipts.isEmpty(),"failed payment retained receipt");
        v.materialSupply=100;v.treasury=100;
        var cost=VillageBridgeFunding.cost(24,640);
        check(cost.materials()==40&&cost.treasury()==10,"size-based Infrastructure cost");
        check(VillageBridgeFunding.pay(v,receipt,24,640,false),"funded bridge rejected");
        check(VillageBridgeFunding.pay(v,receipt,24,640,false),"receipt replay rejected");
        check(v.materialSupply==60&&v.treasury==90,"double charge");
        check(v.copy().bridgeFundingReceipts.equals(v.bridgeFundingReceipts),"copy lost receipt");
        String forced=UUID.randomUUID().toString();
        v.materialSupply=v.treasury=0;
        check(VillageBridgeFunding.pay(v,forced,48,1200,true),"debug bridge failed");
        check(VillageBridgeFunding.pay(v,forced,48,1200,false),"switching debug charged funded bridge");
        check(v.treasury==0&&v.materialSupply==0,"debug resources changed");
        check(VillageBridgeFunding.cost(48,1200).materials()>cost.materials(),"larger bridge needs more inputs");
        var directory=Files.createTempDirectory("tes-bridge-cost-");
        var economy=new EconomyService();economy.startWithSeed(directory,73,0,0);
        UUID id=new UUID(73,37);
        long origin=pack(0,64,0);
        economy.observeNaturalVillage(id,new EconomyService.VillageObservation("minecraft:overworld",
                origin,0,0,8,12,0,false,List.of()));
        economy.configureForcedVillageDevelopment(true);
        String journal=UUID.randomUUID().toString();
        check(economy.fundVillageBridge(id,journal,24,640,origin,pack(36,72,4)),"journal bridge approval");
        var restart=new EconomyService();restart.startWithSeed(directory,73,0,0);
        check(restart.developmentVillageSnapshot(id).village().bridgeFundingReceipts.contains(journal),"journal lost payment");
        check(restart.fundVillageBridge(id,journal,24,640,origin,pack(36,72,4)),"restart replay charged twice");
        restart.observeNaturalVillage(new UUID(74,37),new EconomyService.VillageObservation("minecraft:overworld",
                pack(80,64,0),1,0,8,12,0,false,List.of()));
        check(!restart.mayBridgeParcel(id,pack(70,64,0)),"neighbor's watershed claimed");
        check(!restart.fundVillageBridge(id,UUID.randomUUID().toString(),48,700,origin,pack(75,72,4)),
                "bridge crossed neighboring district");
        String language=Files.readString(Path.of("common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        check(language.contains("Village footbridges")&&language.contains("six dry approach")
                &&language.contains("never withdraws from your personal account"),"bridge handbook missing");
        System.out.println("PASS bridge Infrastructure costs, limits, insufficient funds, receipt replay, mode switch, copy, journal restart, territory and handbook");
    }
    private static long pack(int x,int y,int z) { return ((long)x&0x3ffffffL)<<38|((long)z&0x3ffffffL)<<12|(y&0xfff); }
    private static void check(boolean ok,String why) {if(!ok)throw new AssertionError(why);}
}
