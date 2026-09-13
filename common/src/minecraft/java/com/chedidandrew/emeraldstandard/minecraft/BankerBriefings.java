package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Read-only, bounded reports. Observations are not promises of construction or future returns. */
public final class BankerBriefings {
    private BankerBriefings() {}
    static Component line(String s) { return Component.literal(s); }
    static String amount(long micro) { return String.format(Locale.ROOT, "%.2f E", micro / (double)EconomyState.MICRO); }
    static long cashUsed(SpendingFunds.Payment p) {
        return p.amount()*EconomyState.MICRO - p.inventoryEmeralds()*EconomyState.MICRO
            + (p.inventoryEmeralds()>0 ? p.bankCashAfterMicro() : 0);
    }
    static String number(double v) { return String.format(Locale.ROOT, "%.1f", v); }
    static String name(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_',' ');
    }
    public static List<Component> town(EconomyService economy, UUID id, ServerPlayer player) {
        var snapshot = id == null ? null : economy.villageSnapshot(id);
        if (snapshot == null) return List.of(line("No local village"), line("Visit a village Bank for local advice."));
        var v = snapshot.village();
        var c = EmeraldConfig.current();
        List<Component> out = new ArrayList<>();
        BlockPos center = BlockPos.of(v.centerPos);
        out.add(line("TOWN: WHAT IS HAPPENING?"));
        out.add(line("District center: X "+center.getX()+", Z "+center.getZ()+" | Day "+v.lastSimulatedDay));
        out.add(line("Village condition: "+name(v.lifecycle)+"."));
        if (c.forcedVillageDevelopment())
            out.add(line("DEBUG OVERRIDE: forced instant development is active."));
        else if (!snapshot.simulationEnabled()) out.add(line("Village affairs are on hold."));
        else if (v.expansionMode == VillageExpansion.Mode.PAUSED) out.add(line("City expansion is paused."));
        if (v.lifecycle == VillageProsperityEngine.Lifecycle.EXTINCT
                || v.lifecycle == VillageProsperityEngine.Lifecycle.ABANDONED) {
            out.add(line("RESTORATION"));
            out.add(restoration(v, c));
        }
        int population = VillageProsperityEngine.economicPopulation(v);
        int housing = VillageProsperityEngine.effectiveHousingCapacity(v);
        out.add(line("HOMES AND LIVELIHOODS"));
        out.add(line("Residents: "+population+" | Housing: "+housing+". "
                +(housing > population ? "There is room for newcomers." : "More usable beds are needed.")));
        double food = population * VillageProsperityEngine.GROWTH_FOOD_PER_RESIDENT;
        out.add(line("Food reserves: "+number(v.foodSupply)+". "
                +(population == 0 ? "Maintain farms for returning residents." : v.foodSupply >= food ? "Enough is stored to welcome newcomers."
                    : "Store at least "+number(food)+" before welcoming newcomers. Maintain farms or use a Food grant.")));
        double safety = VillageGuardSecurity.effectiveSafety(v);
        out.add(line("Safety: "+number(safety)+". "
                +(safety >= VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD ? "Keep homes and paths secure."
                    : "Newcomers need Safety "+number(VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD)
                        +" or more. Protect residents and light the paths.")));
        if (v.expansionUpkeepShortfalls > 0)
            out.add(line("Administration has gone unpaid for "+v.expansionUpkeepShortfalls
                    +" days. General or Trade grants can help the treasury."));
        if (v.pendingSettlers > 0) out.add(line(v.pendingSettlers+" settlers are awaiting a safe place to settle."));
        out.add(line("Keep spare beds accessible and the approaches free of monsters. Stay near the village while newcomers arrive."));
        out.add(line("CURRENT CONSTRUCTION"));
        var unfinished = v.projects.stream().filter(VillageConstructionPolicy::needsAttention)
                .sorted(Comparator.comparingInt(VillageConstructionPolicy::attentionPriority)
                        .thenComparingLong(p -> p.projectId)).toList();
        int count = 0;
        for (var p : unfinished) {
            if (count++ >= 8) break;
            var observation = ConstructionDiagnostics.recent(v.villageId+"/"+p.projectId, player.level().getGameTime());
            String stage = !snapshot.visualProgressionEnabled() ? "Building work is on hold."
                    : ConstructionGuidance.project(p, observation == null ? "" : observation.phase());
            out.add(line("#"+p.projectId+" "+(p.vanillaPlan == null ? name(p.type) : p.vanillaPlan.kind().title())+" — "+stage));
            if (!p.economicComplete)
                out.add(line("Planning and paid labor: "+number(100*p.economicProgress)+"%."));
            if (p.originPos != 0 && !p.abstractOnly) {
                BlockPos origin = BlockPos.of(p.originPos);
                out.add(line("Site: X "+origin.getX()+", Z "+origin.getZ()+". Stay nearby and keep the work area clear."));
            }
            // Translate only actual recent observations. Never expose raw diagnostic reason strings.
            // The dominant advice is already included beside this project, without a conflicting generic warning.
        }
        if (count == 0) out.add(line("No unfinished village project."));
        if (unfinished.size() > 8) out.add(line((unfinished.size()-8)+" more projects appear on the district map."));
        var bank = economy.pendingBankForVillage(id);
        if (bank != null) {
            BlockPos origin = BlockPos.of(bank.getValue().origin());
            out.add(line("BANK CONSTRUCTION: X "+origin.getX()+", Z "+origin.getZ()+"."));
            appendWorkAdvice(out, "bank:"+bank.getKey(), player.level().getGameTime());
        }
        var need = economy.nextVillageProjectPlan(id);
        if (need != null) {
            out.add(line("PROPOSED NEXT PROJECT: "+name(need.type())));
            out.add(line("Materials "+number(v.materialSupply)+" / "+number(need.type().materialCost())
                    +"; treasury "+number(v.treasury)+" / "+number(need.type().treasuryCost())+" E."));
            out.add(line("General grants or gifts for this purpose can help prepare the work. Current projects may need to finish first."));
        }
        var expansion = economy.expansionStatus(id);
        if (expansion != null) {
            out.add(line("CITY EXPANSION"));
            out.add(Component.translatable("gui.the_emerald_standard.expansion.reason."
                    +expansion.reason().name().toLowerCase(Locale.ROOT)));
            out.add(line("Administration: "+number(expansion.dailyCityOverhead())+" E/day."));
        }
        out.add(line("WHAT NEXT? Visit a waiting site on the map, or open Fund to review how a contribution would help."));
        return List.copyOf(out);
    }

    static Component restoration(EconomyState.VillageRecord v, EmeraldConfig c) {
        var state = VillageRecoveryGuidance.state(v.lifecycle, v.restorationFund,
                c.villageAutomaticRecoveryEnabled(), c.prosperityFundEnabled());
        return Component.translatable("gui.the_emerald_standard.news.local.restoration."
                +VillageRecoveryGuidance.key(state)+".article",
                number(v.restorationFund), number(VillageProsperityEngine.RESTORATION_EMERALD_TARGET),
                number(VillageRecoveryGuidance.remaining(v.restorationFund)));
    }

    private static void appendWorkAdvice(List<Component> out, String job, long now) {
        var observation = ConstructionDiagnostics.recent(job, now);
        if (observation == null) return;
        String advice = ConstructionGuidance.advice(observation.phase());
        if (!advice.isEmpty()) out.add(line("Latest site report: "+advice));
    }

    public static List<Component> fund(EconomyService economy, UUID id, long whole,
            EconomyState.ProsperityFundType type, EconomyState.DonationPurpose purpose,
            SpendingFunds.Payment payment) {
        var snapshot = id == null ? null : economy.villageSnapshot(id);
        if (snapshot == null) return List.of(line("No associated village."));
        var v = snapshot.village();
        var c = EmeraldConfig.current();
        var eligibility = FundContributionChecks.assess(c, snapshot, type, purpose);
        if (!eligibility.allowed()) return List.of(line("CONTRIBUTION PREVIEW — UNAVAILABLE"),
                line(eligibility.reason()), line("No payment or inventory top-up will be made while this restriction applies."));
        purpose = eligibility.purpose();
        var project = v.projects.stream().filter(p -> p.projectId == eligibility.projectId()).findFirst().orElse(null);
        long total = Math.max(0,Math.min(EconomyService.MAX_WHOLE_EMERALD_TRANSACTION,whole))*EconomyState.MICRO;
        long reserve = FundAllocation.reserve(type,purpose,total,c.prosperityFundEmergencyReserveFraction());
        List<Component> out = new ArrayList<>();
        out.add(line("CONTRIBUTION PREVIEW — NOT A PAYMENT"));
        out.add(line(name(type)+" / "+name(purpose)+": "+amount(total)));
        out.add(line("Gifts are irreversible, village-owned and earn no player repayment or interest."));
        if (!snapshot.simulationEnabled() || !c.prosperityFundEnabled())
            out.add(line("UNAVAILABLE: the village is not accepting contributions."));
        if (type == EconomyState.ProsperityFundType.ENDOWMENT && !c.prosperityFundEndowmentsEnabled())
            out.add(line("UNAVAILABLE: endowments are not being accepted."));
        if (type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP && !c.prosperityFundProjectSponsorshipEnabled())
            out.add(line("UNAVAILABLE: sponsorships are not being accepted."));
        out.add(line("Review this estimate before confirming your gift."));
        if (payment == null || total == 0) out.add(line("Choose an affordable whole-emerald amount to review the payment."));
        else out.add(line("Payment: "+amount(cashUsed(payment))+" cash + "+payment.inventoryEmeralds()
                +" inventory emeralds. Bank Cash after: "+amount(payment.bankCashAfterMicro())+"."));
        switch(type) {
            case DIRECT_GRANT -> out.add(line("On acceptance: "+amount(total-reserve)+" to "+name(purpose)+" spendable capital; "+amount(reserve)+" to emergency reserve."));
            case ENDOWMENT -> out.add(line("On acceptance: "+amount(total)+" protected principal. Annual payout rate: "
                +number(c.prosperityFundEndowmentAnnualPayoutRate()*100)+"%. Only payouts may be spent; principal is not a project budget."));
            case PROJECT_SPONSORSHIP -> out.add(line(project == null ? "UNAVAILABLE: no project needs further paid labor."
                : "On acceptance: "+amount(total)+" reserved for project #"+project.projectId+" "+(project.vanillaPlan == null ? name(project.type) : project.vanillaPlan.kind().title())+" labor. Unused funding later becomes "+name(purpose)+" capital."));
        }
        out.add(line("HOW THIS PURPOSE HELPS"));
        out.add(line(switch(purpose) {
            case FOOD -> "Helps replenish the village's food reserves. Food shortages take priority over new building work.";
            case HOUSING -> "Helps prepare new homes. Newcomers will still need finished homes with usable beds.";
            case SECURITY -> "Helps equip defense projects. Protecting residents and lighting paths still matter.";
            case INFRASTRUCTURE -> "Helps supply materials and planning for civic works. Crews still need a safe place to build.";
            case TRADE -> "Supports the village treasury and trade projects. This is a gift, not an investment.";
            case RESTORATION -> "Helps prepare an empty settlement for returning residents.";
            case GENERAL -> "Flexible support for village expenses, planning and projects. Gifts for other purposes remain reserved for those purposes.";
        }));
        if (purpose == EconomyState.DonationPurpose.RESTORATION) out.add(restoration(v, c));
        out.add(line("WHAT HAPPENS AFTER PAYMENT"));
        out.add(line("Your gift reaches the fund upon acceptance and is spent as village work proceeds."));
        out.add(line(c.prosperityFundFastTrackCapitalEnabled()
            ? "A large enough gift can cover a project's missing supplies and remaining labor in full. Smaller gifts support ordinary progress."
            : "The village spends gifts gradually within its usual budget."));
        out.add(line("Reserve and endowment money keep their restrictions. Crews need clear land and permission to build; stay nearby while work proceeds."));
        var f = economy.villageFundSnapshot(id);
        out.add(line("VILLAGE-WIDE FUND HISTORY"));
        out.add(line("Received "+amount(f.lifetimeReceivedMicro())+"; spent "+amount(f.lifetimeSpentMicro())
            +"; liquid/project capital "+amount(f.spendableTotalMicro())+"; reserve "+amount(f.emergencyReserveMicro())+"."));
        out.add(line("These village-wide totals include all donors."));
        return List.copyOf(out);
    }

    public static List<Component> receipt(EconomyService.VillageFundContributionResult r,
            long reserveAdded, SpendingFunds.Payment payment, long day) {
        return List.of(line("ACCEPTED CONTRIBUTION RECEIPT — Day "+day),
            line(name(r.type())+" / "+name(r.purpose())+": "+amount(r.amountMicro())),
            line("Bank Cash used "+amount(cashUsed(payment))+"; inventory emeralds "+payment.inventoryEmeralds()
                +"; remaining Bank Cash "+amount(payment.bankCashAfterMicro())+"."),
            line(r.type()==EconomyState.ProsperityFundType.ENDOWMENT ? amount(r.amountMicro())+" credited to protected principal."
                : r.type()==EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP ? amount(r.amountMicro())+" reserved for project #"+r.projectId()+" labor."
                : amount(r.amountMicro()-reserveAdded)+" credited to purpose capital; "+amount(reserveAdded)+" credited to emergency reserve."),
            line("Your gift supports the village; building work still takes time."),
            line("Your gift also appears in Activity."),
            line("Open the Fund preview for village-wide spending totals or Town for current needs."));
    }
}
