package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.EconomyService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Identifies Banker villagers and opens the graphical banking dashboard. */
public final class BankerAccess {
    public static final String BANKER_TAG = "the_emerald_standard_banker";
    public static final String FIRST_BANKER_VISIT_TAG = "the_emerald_standard_first_banker_visit";
    private static final String BANK_REGION_TAG_PREFIX = "the_emerald_standard_bank_";
    private static final String BANKER_CONVERSION_TAG_PREFIX =
            "the_emerald_standard_banker_converted_from_";
    private static final String BANKER_CONVERSION_SOURCE_TAG_PREFIX =
            "the_emerald_standard_banker_conversion_source_";

    private BankerAccess() {
    }

    public static boolean isBanker(Entity entity) {
        return entity instanceof Villager villager
                && (hasManagedBankerTag(villager)
                        || BankerProfessionSupport.isRegisteredBanker(
                                villager.getVillagerData().profession()));
    }

    public static boolean isBankerForRegion(Entity entity, long regionKey) {
        return hasManagedBankerTag(entity)
                && entity.entityTags().contains(regionTag(regionKey));
    }

    /** Returns the persistent bank key carried by a scoped Banker, or null for legacy access. */
    public static Long bankRegionKey(Entity entity) {
        if (!hasManagedBankerTag(entity)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(BANK_REGION_TAG_PREFIX)) {
                continue;
            }
            try {
                return Long.parseUnsignedLong(
                        tag.substring(BANK_REGION_TAG_PREFIX.length()), 36);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed third-party or manually edited tags.
            }
        }
        return null;
    }

    public static boolean isLegacyUnscopedBanker(Entity entity) {
        if (!hasManagedBankerTag(entity)) {
            return false;
        }
        return entity.entityTags().stream().noneMatch(tag -> tag.startsWith(BANK_REGION_TAG_PREFIX));
    }

    /**
     * Marks the entity created by a vanilla conversion with its exact canonical predecessor.
     * The durable format-17 transaction is authority; this tag corroborates loaded-entity recovery.
     */
    static boolean markBankerConversion(
            Entity converted,
            long regionKey,
            UUID previousBankerId,
            UUID immediateSourceId) {
        for (String tag : List.copyOf(converted.entityTags())) {
            if (tag.equals(BANKER_TAG)
                    || tag.startsWith(BANK_REGION_TAG_PREFIX)
                    || tag.startsWith(BANKER_CONVERSION_TAG_PREFIX)
                    || tag.startsWith(BANKER_CONVERSION_SOURCE_TAG_PREFIX)) {
                converted.removeTag(tag);
            }
        }
        boolean regionAdded = converted.addTag(regionTag(regionKey));
        String lineage = conversionTag(previousBankerId, immediateSourceId);
        boolean lineageAdded = converted.addTag(lineage);
        if ((!regionAdded && !converted.entityTags().contains(regionTag(regionKey)))
                || (!lineageAdded && !converted.entityTags().contains(lineage))) {
            clearBankerLineage(converted);
            return false;
        }
        if (converted instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
        return true;
    }

    static boolean isBankerConversionFrom(
            Entity entity, long regionKey, UUID previousBankerId) {
        return isBankerForRegion(entity, regionKey)
                && previousBankerId.equals(bankerConversionPredecessor(entity));
    }

    static UUID bankerConversionPredecessor(Entity entity) {
        if (!hasManagedBankerTag(entity)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(BANKER_CONVERSION_TAG_PREFIX)) {
                continue;
            }
            try {
                String encoded = tag.substring(BANKER_CONVERSION_TAG_PREFIX.length());
                int separator = encoded.indexOf('_');
                return UUID.fromString(separator < 0
                        ? encoded
                        : encoded.substring(0, separator));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed third-party or manually edited tags.
            }
        }
        return null;
    }

    static UUID bankerConversionSource(Entity entity) {
        if (!hasManagedBankerTag(entity)) {
            return null;
        }
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(BANKER_CONVERSION_TAG_PREFIX)) {
                String encoded = tag.substring(BANKER_CONVERSION_TAG_PREFIX.length());
                int separator = encoded.indexOf('_');
                if (separator >= 0) {
                    try {
                        return UUID.fromString(encoded.substring(separator + 1));
                    } catch (IllegalArgumentException ignored) {
                        // Try the temporary two-tag development format below.
                    }
                }
            } else if (tag.startsWith(BANKER_CONVERSION_SOURCE_TAG_PREFIX)) {
                try {
                    return UUID.fromString(
                            tag.substring(BANKER_CONVERSION_SOURCE_TAG_PREFIX.length()));
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed third-party or manually edited tags.
                }
            }
        }
        return null;
    }

    static boolean isBankerConversionSource(Entity entity, UUID immediateSourceId) {
        return immediateSourceId.equals(bankerConversionSource(entity));
    }

    static void clearBankerConversionMarker(Entity entity, UUID previousBankerId) {
        for (String tag : List.copyOf(entity.entityTags())) {
            if (tag.startsWith(BANKER_CONVERSION_TAG_PREFIX)
                    || tag.startsWith(BANKER_CONVERSION_SOURCE_TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }
        entity.addTag(BANKER_TAG);
    }

    static void clearBankerLineage(Entity entity) {
        entity.removeTag(BANKER_TAG);
        for (String tag : List.copyOf(entity.entityTags())) {
            if (tag.startsWith(BANK_REGION_TAG_PREFIX)
                    || tag.startsWith(BANKER_CONVERSION_TAG_PREFIX)
                    || tag.startsWith(BANKER_CONVERSION_SOURCE_TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }
    }

    /**
     * Removes inherited Banker authority when the economy could not durably prepare a conversion.
     * The outcome may still be inserted by vanilla, so profession data must be neutralized as
     * well as scoreboard tags or a later cure could create a second interactive Banker.
     */
    static void neutralizeFailedBankerConversion(Entity entity) {
        clearBankerLineage(entity);
        var unemployed = BuiltInRegistries.VILLAGER_PROFESSION.get(VillagerProfession.NONE);
        if (unemployed.isEmpty()) {
            return;
        }
        if (entity instanceof Villager villager) {
            releasePreviousJobSite(villager);
            villager.setVillagerData(
                    villager.getVillagerData().withProfession(unemployed.orElseThrow()));
            villager.setVillagerXp(0);
            villager.setVillagerDataFinalized(false);
        } else if (entity instanceof ZombieVillager zombie) {
            zombie.setVillagerData(
                    zombie.getVillagerData().withProfession(unemployed.orElseThrow()));
            zombie.setVillagerXp(0);
        }
    }

    /**
     * Returns true only for an untouched adult villager that can safely become a Banker.
     * Existing professions, offers, XP, names, babies, and dead villagers are never repurposed.
     */
    public static boolean isEligibleUnemployedVillager(Villager villager) {
        return villager.isAlive()
                && !villager.isBaby()
                && !villager.hasCustomName()
                && villager.getVillagerXp() == 0
                && villager.getOffers().isEmpty()
                && villager.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    /**
     * Accepts either an untouched unemployed adult or an untouched villager that naturally claimed
     * an Exchange Desk. Established villagers and custom professions from other mods are excluded.
     */
    public static boolean isEligibleBankerCandidate(Villager villager) {
        return villager.isAlive()
                && !villager.isBaby()
                && !villager.hasCustomName()
                && villager.getVillagerXp() == 0
                && villager.getOffers().isEmpty()
                && (villager.getVillagerData().profession().is(VillagerProfession.NONE)
                        || BankerProfessionSupport.isRegisteredBanker(
                                villager.getVillagerData().profession()));
    }

    /**
     * Marks a newly spawned or untouched unemployed villager as the Banker for one village region.
     * Existing Banker tags are migrated to the supplied region id without resetting their data.
     */
    public static boolean markBanker(Villager villager, long regionKey) {
        return markBanker(villager, regionKey, null);
    }

    /**
     * Marks a managed Banker and, when supplied, binds it to its generated Exchange Desk.
     *
     * <p>Vanilla immediately clears any level-one, zero-XP profession that has no job-site
     * memory. Managed Bankers deliberately have no trade offers, so they would otherwise show
     * the Banker clothing for one frame and then revert to unemployed. One career XP is the
     * vanilla profession-lock mechanism and has no visible level badge or trade side effect.</p>
     */
    public static boolean markBanker(
            Villager villager, long regionKey, BlockPos exchangeDeskPosition) {
        boolean existingBanker = hasManagedBankerTag(villager);
        if (!existingBanker && !isEligibleBankerCandidate(villager)) {
            return false;
        }

        List<String> previousScopeTags = new java.util.ArrayList<>();
        for (String tag : List.copyOf(villager.entityTags())) {
            if (tag.equals(BANKER_TAG) || tag.startsWith(BANK_REGION_TAG_PREFIX)) {
                previousScopeTags.add(tag);
                villager.removeTag(tag);
            }
        }
        String scopedRegionTag = regionTag(regionKey);
        boolean bankerAdded = villager.addTag(BANKER_TAG);
        boolean regionAdded = villager.addTag(scopedRegionTag);
        if ((!bankerAdded && !villager.entityTags().contains(BANKER_TAG))
                || (!regionAdded && !villager.entityTags().contains(scopedRegionTag))) {
            // Scoreboard tags are capped by vanilla. Never install a profession/name after a
            // partial identity write: that would create an interactive, unscoped Banker. Restore
            // the prior TES scope best-effort after removing the two attempted tags.
            villager.removeTag(BANKER_TAG);
            villager.removeTag(scopedRegionTag);
            for (String previousTag : previousScopeTags) {
                villager.addTag(previousTag);
            }
            return false;
        }
        villager.setCustomName(Component.translatable("entity.the_emerald_standard.banker"));
        villager.setCustomNameVisible(true);
        villager.setPersistenceRequired();

        boolean legacyProfession = villager.getVillagerData().profession()
                .is(VillagerProfession.NONE)
                || villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
        boolean professionChanged = false;
        if (!BankerProfessionSupport.isRegisteredBanker(
                        villager.getVillagerData().profession())
                && (!existingBanker || legacyProfession)) {
            var profession = BankerProfessionSupport.registeredBanker()
                    .or(() -> BuiltInRegistries.VILLAGER_PROFESSION.get(
                            VillagerProfession.LIBRARIAN));
            if (profession.isPresent()) {
                releasePreviousJobSite(villager);
                villager.setVillagerData(
                        existingBanker
                                ? villager.getVillagerData().withProfession(profession.get())
                                : villager.getVillagerData()
                                        .withProfession(profession.get())
                                        .withLevel(1));
                professionChanged = true;
            }
        }
        if (BankerProfessionSupport.isRegisteredBanker(
                villager.getVillagerData().profession())) {
            villager.setVillagerXp(Math.max(1, villager.getVillagerXp()));
            villager.setVillagerDataFinalized(true);
            if (villager.level() instanceof ServerLevel level) {
                bindExchangeDesk(level, villager, exchangeDeskPosition);
            }
            if (professionChanged && villager.level() instanceof ServerLevel level) {
                villager.refreshBrain(level);
            }
        }
        return true;
    }

    private static void releasePreviousJobSite(Villager villager) {
        var brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.JOB_SITE).isPresent()) {
            villager.releasePoi(MemoryModuleType.JOB_SITE);
            brain.eraseMemory(MemoryModuleType.JOB_SITE);
        }
        if (brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent()) {
            villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        }
    }

    private static void bindExchangeDesk(
            ServerLevel level, Villager villager, BlockPos exchangeDeskPosition) {
        if (exchangeDeskPosition == null
                || !BankerProfessionSupport.isExchangeDesk(
                        level.getBlockState(exchangeDeskPosition))) {
            return;
        }

        GlobalPos target = GlobalPos.of(level.dimension(), exchangeDeskPosition);
        var brain = villager.getBrain();
        var currentJobSite = brain.getMemory(MemoryModuleType.JOB_SITE);
        if (currentJobSite.filter(target::equals).isPresent()) {
            return;
        }

        var potentialJobSite = brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        if (potentialJobSite.filter(target::equals).isPresent()) {
            brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
            brain.setMemory(MemoryModuleType.JOB_SITE, target);
            return;
        }

        if (currentJobSite.isPresent()) {
            villager.releasePoi(MemoryModuleType.JOB_SITE);
            brain.eraseMemory(MemoryModuleType.JOB_SITE);
        }
        if (potentialJobSite.isPresent()) {
            villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        }

        level.getPoiManager().take(
                        holder -> holder.is(BankerProfessionSupport.BANKER_POI_KEY),
                        (holder, position) -> position.equals(exchangeDeskPosition),
                        exchangeDeskPosition,
                        1)
                .ifPresent(position -> brain.setMemory(
                        MemoryModuleType.JOB_SITE,
                        GlobalPos.of(level.dimension(), position)));
    }

    private static boolean hasManagedBankerTag(Entity entity) {
        // Converted Zombie Villagers must retain lineage without becoming interactive Bankers.
        // isBanker itself remains Villager-gated.
        return entity != null
                && (entity.entityTags().contains(BANKER_TAG)
                        || entity.entityTags().stream()
                                .anyMatch(tag -> tag.startsWith(
                                        BANKER_CONVERSION_TAG_PREFIX)));
    }

    /**
     * Repairs the profession lock for an already-managed Banker immediately before interaction.
     *
     * <p>This is intentionally tag-gated and entity-local: it upgrades old saves even when a test
     * enclosure is not recognized as a vanilla village, without scanning for or modifying normal
     * villagers. Region tags and all non-legacy professions are left untouched.</p>
     */
    static boolean repairManagedBankerForInteraction(Entity entity) {
        if (!(entity instanceof Villager villager) || !hasManagedBankerTag(villager)) {
            return false;
        }

        boolean registeredProfession = BankerProfessionSupport.isRegisteredBanker(
                villager.getVillagerData().profession());
        boolean legacyProfession = villager.getVillagerData().profession()
                .is(VillagerProfession.NONE)
                || villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN);
        boolean professionChanged = false;
        if (!registeredProfession && legacyProfession) {
            var profession = BankerProfessionSupport.registeredBanker();
            if (profession.isEmpty()) {
                return false;
            }
            releasePreviousJobSite(villager);
            villager.setVillagerData(
                    villager.getVillagerData().withProfession(profession.orElseThrow()));
            professionChanged = true;
            registeredProfession = true;
        }
        if (!registeredProfession) {
            return false;
        }

        villager.setVillagerXp(Math.max(1, villager.getVillagerXp()));
        villager.setVillagerDataFinalized(true);
        villager.setPersistenceRequired();
        if (professionChanged && villager.level() instanceof ServerLevel level) {
            villager.refreshBrain(level);
        }
        return true;
    }

    /** Compatibility overload used only when no persistent village-region identity exists. */
    public static boolean markBanker(Villager villager) {
        return markBanker(villager, 0L);
    }

    public static boolean open(ServerPlayer player, EconomyService economy) {
        return openAt(player, economy, null);
    }

    public static boolean open(
            ServerPlayer player, EconomyService economy, Entity banker) {
        if (banker != null
                && economy.pendingGeneratedBankerConversionByTarget(
                        banker.getUUID()) != null) {
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.bank_unsafe"));
            return false;
        }
        // Old managed Bankers may have been reset before a village-gated maintenance scan ran.
        // Direct server-side interaction is a bounded, deterministic migration opportunity.
        repairManagedBankerForInteraction(banker);
        Long regionKey = bankRegionKey(banker);
        if (regionKey != null
                && (!(banker.level() instanceof ServerLevel bankerLevel)
                        || bankerLevel != bankerLevel.getServer().overworld())) {
            // Generated Banks are Overworld-only. This also contains a rolled-back duplicate UUID
            // in another dimension without guessing which copy should own the Bank.
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.bank_unsafe"));
            return false;
        }
        if (regionKey != null) {
            java.util.UUID canonical = economy.generatedBankerId(regionKey);
            if (canonical != null
                    && (banker == null || !canonical.equals(banker.getUUID()))) {
                // Legacy duplicate entities remain untouched, but only the durable canonical
                // Banker may operate the region's account desk.
                player.sendSystemMessage(Component.translatable(
                        "message.the_emerald_standard.bank_unsafe"));
                return false;
            }
        }
        if (regionKey != null
                && player.level() instanceof ServerLevel level
                && !VillageBankManager.isManagedBankOperational(
                        level, economy, regionKey, banker.blockPosition())) {
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.bank_unsafe"));
            return false;
        }
        boolean opened = openAt(
                player,
                economy,
                banker == null ? null : banker.blockPosition(),
                regionKey);
        if (!opened) {
            player.sendSystemMessage(Component.translatable(
                    "message.the_emerald_standard.bank_open_failed"));
        }
        return opened;
    }

    public static boolean openAt(
            ServerPlayer player, EconomyService economy, BlockPos accessPoint) {
        Long regionKey = null;
        if (accessPoint != null) {
            long packedAccessPoint = accessPoint.asLong();
            regionKey = economy.generatedBankAnchorsSnapshot().entrySet().stream()
                    .filter(entry -> entry.getValue() == packedAccessPoint)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }
        return openAt(player, economy, accessPoint, regionKey);
    }

    private static boolean openAt(
            ServerPlayer player,
            EconomyService economy,
            BlockPos accessPoint,
            Long regionKey) {
        BankTransactionCoordinator.reconcile(player, economy);
        boolean opened = player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, ignored) ->
                                new BankerMenu(
                                        containerId,
                                        inventory,
                                        economy,
                                        player,
                                        accessPoint,
                                        regionKey),
                        Component.translatable("gui.the_emerald_standard.banker.title")))
                .isPresent();
        if (!opened) {
            return false;
        }

        boolean firstVisit = !player.entityTags().contains(FIRST_BANKER_VISIT_TAG)
                && player.addTag(FIRST_BANKER_VISIT_TAG);
        if (firstVisit) {
            player.sendSystemMessage(
                    Component.translatable("message.the_emerald_standard.first_banker_visit"));
        }
        awardFirstBankerAdvancement(player);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.VILLAGER_TRADE,
                    SoundSource.NEUTRAL,
                    0.65F,
                    firstVisit ? 1.15F : 1.0F);
            if (firstVisit) {
                level.sendParticles(
                        ParticleTypes.HAPPY_VILLAGER,
                        player.getX(),
                        player.getY() + 1.0,
                        player.getZ(),
                        6,
                        0.35,
                        0.45,
                        0.35,
                        0.02);
            }
        }
        return true;
    }

    private static void awardFirstBankerAdvancement(ServerPlayer player) {
        if (player.level().getServer() == null) {
            return;
        }
        var advancement = player.level().getServer().getAdvancements().get(
                Identifier.fromNamespaceAndPath(
                        "the_emerald_standard", "first_banker"));
        if (advancement != null) {
            player.getAdvancements().award(advancement, "opened_banker");
        }
    }

    private static String regionTag(long regionKey) {
        return BANK_REGION_TAG_PREFIX + Long.toUnsignedString(regionKey, 36);
    }

    private static String conversionTag(UUID previousBankerId, UUID immediateSourceId) {
        return BANKER_CONVERSION_TAG_PREFIX + previousBankerId + "_" + immediateSourceId;
    }
}
