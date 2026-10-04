package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.minecraft.BiomeArchitecturePreview.Sample;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture.BiomeDialect;
import com.chedidandrew.emeraldstandard.core.VillageArchitecture;
import java.util.*;

/** Staged review policy; intentionally not wired into the released saved-project selector. */
final class PreviewArchitectureTiers {
    enum Size { TINY(1), SMALL(1), MEDIUM(2), LARGE(3), VERY_LARGE(4), LANDMARK(5);
        final int minimumTier; Size(int tier) { minimumTier=tier; }
    }
    static Size size(Sample s) {
        int area=PreviewCompactBuildings.isCompact(s) ? PreviewCompactBuildings.spec(s).width()*PreviewCompactBuildings.spec(s).depth()
                :s.role().equals("BANK")?s.width()*s.depth()
                :VillageArchitecture.legacyBlueprints().stream()
                    .filter(d->d.templateId().equals(BiomeArchitectureCatalogPreview.masterId(s)))
                    .mapToInt(d->d.width()*d.depth()).findFirst().orElseThrow();
        return area<=49?Size.TINY:area<=121?Size.SMALL:area<=225?Size.MEDIUM
                :area<=399?Size.LARGE:area<=575?Size.VERY_LARGE:Size.LANDMARK;
    }
    static List<Sample> eligible(List<Sample> catalog,BiomeDialect style,String role,int tier) {
        int bounded=Math.max(1,Math.min(5,tier));
        return catalog.stream().filter(s->s.style()==style&&s.role().equals(role)&&size(s).minimumTier<=bounded).toList();
    }
    static Sample select(List<Sample> catalog,BiomeDialect style,String role,int tier,long projectSeed) {
        List<Sample> choices=eligible(catalog,style,role,tier);
        if(choices.isEmpty()) throw new IllegalArgumentException("No staged design for "+style+" "+role+" tier "+tier);
        // Smaller designs remain a real choice at every tier, rather than silently retiring.
        var random=new Random(projectSeed); return choices.get(random.nextInt(choices.size()));
    }
}
