package com.chedidandrew.emeraldstandard.core;

import java.nio.file.*;
import java.util.UUID;
import static com.chedidandrew.emeraldstandard.core.VillageProsperityEngine.Lifecycle.*;
import static com.chedidandrew.emeraldstandard.core.VillageRecoveryGuidance.State.*;

public final class VillageRecoveryGuidanceRegressionTest {
    public static void main(String[] args) throws Exception {
        for (double fund : new double[]{0, 10, 24.99}) {
            require(VillageRecoveryGuidance.state(EXTINCT,fund,true,true)==OPTIONAL,"Extinct aid is optional");
            require(VillageRecoveryGuidance.state(ABANDONED,fund,true,true)==REQUIRED,"Abandoned needs aid");
            require(VillageRecoveryGuidance.state(ABANDONED,fund,true,false)==REQUIRED,"Unavailable gifts do not waive requirement");
        }
        for (var life : new VillageProsperityEngine.Lifecycle[]{EXTINCT,ABANDONED}) {
            require(VillageRecoveryGuidance.state(life,25,true,true)==FUNDED,"Target reached");
            require(VillageRecoveryGuidance.state(life,100,true,false)==FUNDED,"Excess funds do not demand more");
            for (double fund : new double[]{0,25,100})
                require(VillageRecoveryGuidance.state(life,fund,false,true)==PAUSED,"Disabled recovery must never promise arrivals");
        }
        require(VillageRecoveryGuidance.state(EXTINCT,0,true,false)==WAITING,"No unavailable gift suggestion");
        require(VillageRecoveryGuidance.remaining(10)==15 && VillageRecoveryGuidance.remaining(100)==0
                && VillageRecoveryGuidance.remaining(Double.NaN)==25,"Remaining aid bounds");

        // Assert wording matches the unchanged actual recovery engine, including the waiting period.
        for (var life : new VillageProsperityEngine.Lifecycle[]{EXTINCT,ABANDONED})
            for (boolean enabled : new boolean[]{true,false})
                for (double fund : new double[]{0,10,25}) {
                    var v = new EconomyState.VillageRecord();
                    v.villageId=UUID.randomUUID(); v.lifecycle=life; v.population=0;
                    v.restorationFund=fund; v.restorationFunded=fund>=25; v.recoveryEligibleDay=7;
                    VillageProsperityEngine.advanceOneDay(v,87,6,enabled,true);
                    require(v.lifecycle==life && v.pendingSettlers==0,"Payment cannot skip time");
                    VillageProsperityEngine.advanceOneDay(v,87,7,enabled,true);
                    boolean canReturn=enabled && (life==EXTINCT || fund>=25);
                    require((v.lifecycle==RECOVERING)==canReturn,"Guidance matches engine requirements");
                    require(v.population==0 && v.pendingSettlers==(canReturn?2:0),"Safe arrivals still separate");
                }

        for (long due : new long[]{2,20}) {
            var v=new EconomyState.VillageRecord();v.recoveryEligibleDay=due;
            EconomyState.applyFundInputs(v,EconomyState.DonationPurpose.RESTORATION,25*EconomyState.MICRO,0);
            require(v.restorationFunded && v.restorationFund==25
                    &&v.recoveryEligibleDay==Math.min(due,3),"Funded recovery shortens but never extends time");
        }
        Path root=args.length==0?Path.of("."):Path.of(args[0]);
        String language=Files.readString(root.resolve("common/src/main/resources/assets/the_emerald_standard/lang/en_us.json"));
        for(String state:new String[]{"optional","required","funded","paused","waiting"}) {
            require(language.contains("news.local.restoration."+state+".article"),"Localized recovery article "+state);
            require(language.contains("news.tip.prosperity.restore."+state),"Localized recovery advice "+state);
        }
        for(String line:language.split("\\R")) if(line.contains("\"gui.")) {
            String value=line.substring(line.indexOf(':')+1).toLowerCase(java.util.Locale.ROOT);
            for(String forbidden:new String[]{"cooldown","synchronized snapshot","authoritative local","visual queue",
                    "simulated","loaded chunks","origin chunk","recorded toward restoration"})
                require(!value.contains(forbidden),"Player copy leaked "+forbidden);
        }
        require(language.contains("25 E hastens help.") && language.contains("25 E required.")
                &&language.contains("Extinct village with 0/25 emeralds does not owe 25 emeralds")
                &&language.contains("another 15 reaches the target"),"Both handbook formats explain optional versus required aid");
        String briefings=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/BankerBriefings.java"));
        for(String forbidden:new String[]{"observation.reason()","observation.tick()","siteSearchCursor",
                "SiteSearchDiagnostics.last","loaded/cached chunks","at 20 TPS","planned block operations"})
            require(!briefings.contains(forbidden),"Ordinary report leaked "+forbidden);
        String debug=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/ConstructionDiagnostics.java"));
        require(debug.contains("observation.reason()") && debug.contains("\"gameTick\""),"Debug keeps exact worksite data");
        System.out.println("PASS recovery guidance, actual recovery rules, immersive resources and handbook");
    }
    private static void require(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
