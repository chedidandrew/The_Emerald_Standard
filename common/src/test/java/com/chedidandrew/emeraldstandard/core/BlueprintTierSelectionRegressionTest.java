package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Actual release identities, whole-parcel reservations, tier gates and frozen-save coverage. */
public final class BlueprintTierSelectionRegressionTest {
    public static void main(String[] args) {
        require(ApprovedArchitectureCatalog.entries().size()==375,"Missing approved release sites");
        require(VillageArchitecture.activeBlueprints().size()==360,"Missing ordinary release buildings");
        require(VillageArchitecture.legacyBlueprints().size()==52,"Legacy catalog changed");
        for(var legacy:VillageArchitecture.legacyBlueprints()) {
            require(VillageArchitecture.requireBlueprint(legacy.templateId(),11).equals(legacy),"Legacy revision lost");
            require(VillageArchitecture.requireBlueprint(legacy.templateId(),2).type()==legacy.type(),"Older save lost");
            require(!VillageArchitecture.activeBlueprints().contains(legacy),"Legacy selected as new release");
        }
        Set<String> reached=new HashSet<>();
        for(var style:VillageArchitecture.BiomeDialect.values()) {
            for(var type:VillageProsperityEngine.ProjectType.values()) {
                for(int tier=0;tier<=5;tier++) {
                    List<VillageArchitecture.ExistingBlueprint> history=new ArrayList<>();
                    String previous="";
                    for(int sample=0;sample<1024;sample++) {
                        UUID village=new UUID(type.ordinal()+123,VillageArchitecture.mix64(sample+789));
                        var selection=VillageArchitecture.chooseBlueprint(village,sample+1,type,
                                VillageArchitecture.Character.RUSTIC,tier,history,style);
                        var entry=ApprovedArchitectureCatalog.entry(selection.templateId());
                        require(entry!=null&&entry.dialect().equals(style.id()),"Cross-biome selection");
                        require(entry.minimumTier()<=Math.max(1,tier),"Tier gate bypassed");
                        require(selection.templateRevision()==12,"New project selected old revision");
                        require(selection.equals(VillageArchitecture.chooseBlueprint(village,sample+1,type,
                                VillageArchitecture.Character.RUSTIC,tier,history,style)),"Selection rerolled");
                        require(selection.descriptor().width()==entry.width()
                                &&selection.descriptor().depth()==entry.depth(),"Yard not reserved");
                        long choices=VillageArchitecture.blueprints(type,tier).stream()
                                .filter(d->ApprovedArchitectureCatalog.matchesDialect(d.templateId(),style)).count();
                        require(choices<=1||!selection.templateId().equals(previous),"Immediate repeated design");
                        previous=selection.templateId();reached.add(previous);
                        history.add(new VillageArchitecture.ExistingBlueprint(sample+1,type,selection.templateId(),
                                selection.templateRevision(),selection.paletteId(),selection.dressingId(),
                                selection.mirrored(),selection.signature()));
                        if(history.size()>8) history.removeFirst();
                        var project=new EconomyState.VillageProject();project.type=type;
                        project.designTemplateId=entry.id();project.designTemplateRevision=12;
                        require(project.actualHousingBeds()==entry.beds(),"Physical bed capacity invented");
                        require(project.housingGain()==type.housingGain(),"Economic progression changed");
                    }
                }
            }
        }
        require(reached.size()==360,"Unreachable release buildings: "+(360-reached.size()));
        String oldId="exchange_countinghouse_02";
        long signature=VillageArchitecture.blueprintSignature(VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                oldId,2,VillageArchitecture.PALETTE_BALANCED,VillageArchitecture.DRESSING_PROSPEROUS,true);
        require(VillageArchitecture.isKnownBlueprintSelection(VillageProsperityEngine.ProjectType.EXCHANGE_HALL,
                oldId,2,VillageArchitecture.PALETTE_BALANCED,VillageArchitecture.DRESSING_PROSPEROUS,true,signature),
                "Persisted old identity invalidated by current tier");
        System.out.println("PASS 360 approved buildings reachable: five dialects, tiers 1-5, deterministic variety, full parcels and legacy saves");
    }
    private static void require(boolean value,String message) {if(!value) throw new AssertionError(message);}
}
