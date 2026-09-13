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
        if (snapshot == null) return List.of(line("No associated village"),line("Find a managed village or use its assigned Bank desk."));
        var v = snapshot.village();
        var c = EmeraldConfig.current();
        List<Component> out = new ArrayList<>();
        BlockPos center = BlockPos.of(v.centerPos);
        out.add(line("TOWN: WHAT IS HAPPENING?"));
        out.add(line("District center: X "+center.getX()+", Z "+center.getZ()+" | Day "+v.lastSimulatedDay));
        out.add(line("Status: "+name(v.lifecycle)+". These facts concern the village, not the Bank building's condition."));
        if (c.forcedVillageDevelopment()) out.add(line("DEBUG OVERRIDE: ordinary economy and pause gates are bypassed. Protection and loading still apply."));
        else if (!snapshot.simulationEnabled()) out.add(line("WAIT: village simulation is disabled. Ask the world owner to review Settings."));
        else if (v.expansionMode == VillageExpansion.Mode.PAUSED) out.add(line("WAIT: this city's development is paused. The owner/operator can resume it in City expansion."));
        else if (!VillageConstructionPolicy.villageEligible(v)) out.add(line("WAIT: restoration or living residents are needed. Check the recovery section before sponsoring construction."));
        out.add(line("RESIDENT GROWTH CHECKLIST (not a promise of an arrival)"));
        int population = VillageProsperityEngine.economicPopulation(v);
        int housing = VillageProsperityEngine.effectiveHousingCapacity(v);
        out.add(line((housing > population ? "PASS: " : "WAIT: ")+"housing "+population+" / "+housing+". Completed, usable housing is needed; Housing gifts do not create beds."));
        double food = population * VillageProsperityEngine.GROWTH_FOOD_PER_RESIDENT;
        out.add(line((v.foodSupply >= food ? "PASS: " : "WAIT: ")+"food "+number(v.foodSupply)+" / "+number(food)+" growth threshold. Maintain farms or use a Food grant."));
        double safety = VillageGuardSecurity.effectiveSafety(v);
        out.add(line((safety >= VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD ? "PASS: " : "WAIT: ")+"Safety "+number(safety)+" / "+number(VillageProsperityEngine.GROWTH_SAFETY_THRESHOLD)+". Protect residents and light routes; a Security gift is not instant Safety."));
        out.add(line((v.expansionUpkeepShortfalls < 3 ? "PASS: " : "WAIT: ")+"unpaid administration days: "+v.expansionUpkeepShortfalls+". General/Trade funds support treasury; expanding cities need recurring income."));
        long unverified=v.residents.values().stream().filter(r ->
                r.status==VillageProsperityEngine.ResidentStatus.UNVERIFIED
                || r.status==VillageProsperityEngine.ResidentStatus.AWAY).count();
        out.add(line("Loaded census: "+v.observedPopulation+" | Unverified: "+unverified
                +" | Queued arrivals: "+v.pendingSettlers+". Unloaded residents are not replaced."));
        out.add(line("Immigration progress: "+number(v.immigrationProgress*100)+"% toward the next arrival. "
                +"Daily eligibility approves at most 4; at most 8 arrivals wait in the queue."));
        out.add(line("District economic population cap: 64. Larger physical populations remain in the census."));
        out.add(line("Housing survey: "+v.housingChunks.values().stream().mapToInt(List::size).sum()
                +" bed heads across "+v.housingChunks.size()+" loaded/cached chunks. "+VillagePopulationEnvironment.status(id)));
        long cityResidents=economy.villageSnapshots().stream().map(EconomyService.VillageSnapshot::village)
                .filter(other -> VillageExpansion.rootId(other).equals(VillageExpansion.rootId(v)))
                .mapToLong(other -> Math.max(other.population,other.residents.values().stream().filter(r ->
                        r.status==VillageProsperityEngine.ResidentStatus.ACTIVE
                        || r.status==VillageProsperityEngine.ResidentStatus.UNVERIFIED
                        || r.status==VillageProsperityEngine.ResidentStatus.AWAY).count())).sum();
        out.add(line("City residents (all associated districts, including unverified): "+cityResidents));
        out.add(line("Arrivals need an intact free bed, a reachable nearby landing and no visible nearby monster. "
                +"Walls/terrain can shelter the landing; no chunks are forced to load."));
        out.add(line("CURRENT CONSTRUCTION"));
        var unfinished = v.projects.stream().filter(VillageConstructionPolicy::needsAttention)
                .sorted(Comparator.comparingInt(VillageConstructionPolicy::attentionPriority)
                        .thenComparingLong(p -> p.projectId)).toList();
        int count = 0;
        for (var p : unfinished) {
            if (count++ >= 8) break;
            String stage = p.manualRepairRequired ? "MANUAL REPAIR: inspect damage; the mod will not overwrite your changes."
                    : p.relocationPending ? "RELOCATION PENDING: waiting for a replacement site; the previous building is retained."
                    : p.abstractOnly ? "SIMULATED ONLY: this project has no physical build."
                    : !snapshot.visualProgressionEnabled() ? "PHYSICAL WORK DISABLED in Settings."
                    : p.originPos == 0 ? "SITE SEARCH: no approved location yet. Open-looking land may still fail footprint, access or protection checks."
                    : !p.sitePreparationComplete ? "TERRAIN PREPARATION before fences and structural work."
                    : p.blocked ? "SAVED OBSTRUCTION: inspect the site, protected blocks and access. Specific obstruction is not inferred."
                    : "BUILDING: "+p.materializedBlocks+" / "+p.totalBlocks+" planned block operations.";
            out.add(line("#"+p.projectId+" "+name(p.type)+" — "+stage));
            out.add(line("Economic labor "+number(100*p.economicProgress)+"%; physical completion "+(p.materializedComplete?"yes":"no")+". Paid labor does not bypass physical work."));
            appendObservation(out,v.villageId+"/"+p.projectId,player.level().getGameTime());
            if (ProjectSiteRetry.searching(p)) {
                long now = player.level().getGameTime();
                long wait = ProjectSiteRetry.remaining(now,p.retryAfterGameTick);
                int candidates = VillageNeighborhoodPlan.offsets(p.materializationFailures,v.villageId).size();
                out.add(line("Search: "+p.siteSearchCursor+" / "+candidates+" candidate positions this sweep; "
                        +p.materializationFailures+" prior failures. "
                        +(wait > 0 ? "Retry in about "+(wait/20+(wait%20==0?0:1))+" seconds at 20 TPS."
                            : "Retry due; requires active development and a nearby player.")));
                var trial = SiteSearchDiagnostics.last(v.villageId+"/"+p.projectId);
                if (trial == null) out.add(line("No candidate reason observed this session yet. /emerald debug records the search automatically."));
                else out.add(line("Last observed check: "+trial.reason()+" at X "+trial.x()+", Y "+trial.y()+", Z "+trial.z()
                        +" (game tick "+trial.tick()+", sweep "+trial.sweep()+"). "+trial.detail()));
                out.add(line("Cooldown is at most 30 seconds; a full candidate sweep can take longer. Cached map terrain is not proof of loaded chunks."));
            }
            if (p.originPos != 0 && !p.abstractOnly && !p.manualRepairRequired && !p.relocationPending) {
                BlockPos origin = BlockPos.of(p.originPos);
                var level = player.level();
                boolean same = v.dimensionKey.equals(level.dimension().identifier().toString());
                boolean loaded = same && level.hasChunk(origin.getX()>>4,origin.getZ()>>4);
                boolean nearby = same && level.players().stream().anyMatch(w ->
                    !w.isSpectator() && Math.pow(w.getX()-center.getX(),2)+Math.pow(w.getZ()-center.getZ(),2)
                        <= (double)c.villageDevelopmentRadius()*c.villageDevelopmentRadius());
                out.add(line("Site X "+origin.getX()+", Z "+origin.getZ()+": origin chunk "+(loaded?"loaded":"unloaded")
                    +"; player activation "+(nearby?"nearby":"out of range")+". Other footprint chunks must also be loaded."));
                if (loaded && !c.forcedVillageDevelopment() && !ConstructionSitePresentation.fenceReady(
                        (net.minecraft.server.level.ServerLevel)level, VillageConstructionActivity.projectTag(v.villageId,p.projectId,p.originPos)))
                    out.add(line("Fence preparation is not yet recorded complete. Normal structural work waits; intentional access gaps remain."));
                out.add(line("Players/animals in a work cell can pause placement. No occupancy cause is claimed without a live placement observation."));
            }
        }
        if (count == 0) out.add(line("No unfinished village project."));
        if (unfinished.size() > 8) out.add(line((unfinished.size()-8)+" additional unfinished projects are not shown. Repairs and active sites appear first; inspect the map for other sites."));
        var bank = economy.pendingBankForVillage(id);
        if (bank != null) {
            BlockPos origin=BlockPos.of(bank.getValue().origin());
            out.add(line("BANK CONSTRUCTION: reserved at X "+origin.getX()+", Z "+origin.getZ()
                +". The saved plan is not a completion percentage; Minecraft's saved blocks are authoritative."));
            appendObservation(out,"bank:"+bank.getKey(),player.level().getGameTime());
        } else out.add(line("No pending Bank plan for this district. An existing Bank or ongoing site discovery is separate from local projects."));
        var need = economy.nextVillageProjectPlan(id);
        out.add(line("NEXT PROJECT INPUTS"));
        if (need == null) out.add(line("No new project selected now: current work, district limits or needs may take priority."));
        else {
            out.add(line("Current needs-based candidate: "+name(need.type())+" (not an approval)."));
            out.add(line("Materials "+number(v.materialSupply)+" / "+number(need.type().materialCost())
                +"; treasury "+number(v.treasury)+" / "+number(need.type().treasuryCost())+" E; development "
                +number(v.developmentPoints)+" / "+number(need.requiredDevelopment())+"."));
            out.add(line("Matching-purpose or General capital may close these gaps on an economic update; backlog and village eligibility still apply."));
        }
        var expansion = economy.expansionStatus(id);
        if (expansion != null) {
            out.add(line("CITY EXPANSION — "+expansion.districts()+" districts"));
            out.add(Component.translatable("gui.the_emerald_standard.expansion.reason."+expansion.reason().name().toLowerCase(Locale.ROOT)));
            out.add(line("Administration: "+number(expansion.dailyCityOverhead())+" E/day. This is separate from the next local building."));
        }
        out.add(line("WHAT NEXT? Read the checklist, inspect a waiting site on the map, or open Fund for a purpose-specific preview. No exact finish time is promised."));
        return List.copyOf(out);
    }

    private static void appendObservation(List<Component> out,String job,long now) {
        var observation=ConstructionDiagnostics.recent(job,now);
        if(observation==null)return;
        out.add(line("Latest work check ("+(now-observation.tick())+" ticks ago): "
            +observation.phase().replace('_',' ')+" — "+observation.reason()
            +". Observed "+observation.done()+" / "+observation.total()+" operations; conditions may have changed."));
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
            out.add(line("UNAVAILABLE: world settings disable village simulation or donations."));
        if (type == EconomyState.ProsperityFundType.ENDOWMENT && !c.prosperityFundEndowmentsEnabled())
            out.add(line("UNAVAILABLE: world settings disable endowments."));
        if (type == EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP && !c.prosperityFundProjectSponsorshipEnabled())
            out.add(line("UNAVAILABLE: world settings disable sponsorship."));
        out.add(line("Current estimate only: eligibility, balances, purpose restrictions and confirmation are checked again on payment."));
        if (payment == null || total == 0) out.add(line("Apply an affordable whole-emerald amount first. This preview does not authorize a transaction."));
        else out.add(line("Payment: "+amount(cashUsed(payment))+" cash + "+payment.inventoryEmeralds()
                +" inventory emeralds. Bank Cash after: "+amount(payment.bankCashAfterMicro())+"."));
        switch(type) {
            case DIRECT_GRANT -> out.add(line("On acceptance: "+amount(total-reserve)+" to "+name(purpose)+" spendable capital; "+amount(reserve)+" to emergency reserve."));
            case ENDOWMENT -> out.add(line("On acceptance: "+amount(total)+" protected principal. Annual payout setting: "
                +number(c.prosperityFundEndowmentAnnualPayoutRate()*100)+"%. Only payouts may be spent; principal is not a project budget."));
            case PROJECT_SPONSORSHIP -> out.add(line(project == null ? "UNAVAILABLE: no economically unfinished project."
                : "On acceptance: "+amount(total)+" reserved for project #"+project.projectId+" "+name(project.type)+" labor. Unused funding later becomes "+name(purpose)+" capital."));
        }
        out.add(line("HOW THIS PURPOSE HELPS"));
        out.add(line(switch(purpose) {
            case FOOD -> "Funds convert to simulated food reserves, not bread in chests. Acute food relief takes priority over capital projects.";
            case HOUSING -> "Supports housing-project inputs/development. Housing capacity still needs completed, usable homes.";
            case SECURITY -> "Supports defense-project inputs/development. It does not directly add Safety points or create guards.";
            case INFRASTRUCTURE -> "Supports materials and development for civic infrastructure. It does not bypass safe-site checks.";
            case TRADE -> "Supports municipal treasury and trade/development inputs, not a guaranteed investment return.";
            case RESTORATION -> "Funds the restoration requirement. Recovery cooldowns, physical housing and safe settlers still apply.";
            case GENERAL -> "Flexible municipal support: routine treasury/development needs and eligible project gaps. It does not override dedicated-purpose restrictions.";
        }));
        out.add(line("WHAT HAPPENS AFTER PAYMENT"));
        out.add(line("Acceptance credits the fund now. Spending occurs through economic updates, not this preview."));
        out.add(line(c.prosperityFundFastTrackCapitalEnabled()
            ? "Fast-track capital is enabled: eligible gifts may fund one project's exact input deficits and all remaining labor. Partial labor funding waits; ordinary progress can continue."
            : "Fast-track capital is disabled: ordinary spending caps apply."));
        out.add(line("Reserve and endowment payouts remain restricted. Construction still needs loaded land, protection approval and clear work cells."));
        var f = economy.villageFundSnapshot(id);
        out.add(line("VILLAGE-WIDE FUND HISTORY"));
        out.add(line("Received "+amount(f.lifetimeReceivedMicro())+"; released into economy "+amount(f.lifetimeSpentMicro())
            +"; liquid/project capital "+amount(f.spendableTotalMicro())+"; reserve "+amount(f.emergencyReserveMicro())+"."));
        out.add(line("These totals include all donors. Later releases cannot honestly be attributed to this individual gift."));
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
            line("No blocks were placed by this payment. Economic spending and protected physical construction happen separately."),
            line("This receipt lasts for this Desk visit. Activity retains the gift in your bounded transaction history."),
            line("Open the live preview for village-wide spending totals or Town's progress report to see current waiting reasons."));
    }
}
