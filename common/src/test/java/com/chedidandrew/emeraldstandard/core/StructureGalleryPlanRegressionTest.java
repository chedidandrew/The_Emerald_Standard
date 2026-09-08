package com.chedidandrew.emeraldstandard.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Regression coverage for the deterministic loader-neutral Blueprint V2 gallery layout. */
public final class StructureGalleryPlanRegressionTest {
    private static final int DESCRIPTOR_COUNT = VillageArchitecture.activeBlueprints().size();
    private static final int DIALECT_COUNT = 5;
    private static final int GOLD_MASTER_COUNT = DESCRIPTOR_COUNT * DIALECT_COUNT;
    private static final int CONTROLLED_COUNT = 11;
    private static final int BANK_COUNT = 5;
    private static final int TOTAL_TARGET_COUNT =
            GOLD_MASTER_COUNT + CONTROLLED_COUNT + BANK_COUNT;
    private static final double EPSILON = 0.000_001;

    private StructureGalleryPlanRegressionTest() {
    }

    public static void main(String[] args) {
        testCountsIndicesAndImmutability();
        testVisitTargetsFrameEveryStructure();
        testGoldMasterMatrixCoverage();
        testReviewHubFacesGoldMasterMatrix();
        testControlledBlueprintCoverage();
        testEntryDescriptorGuards();
        testBankCoverageAndBoundedLayout();
        testCompletionSignatureCoversEveryReviewAxis();
        System.out.println("PASS Blueprint V2 structure gallery plan regressions");
    }

    private static void testCountsIndicesAndImmutability() {
        List<VillageArchitecture.BlueprintDescriptor> descriptors =
                StructureGalleryPlan.goldMasters();
        List<StructureGalleryPlan.Entry> entries = StructureGalleryPlan.entries();
        List<StructureGalleryPlan.BankEntry> banks = StructureGalleryPlan.bankEntries();
        List<StructureGalleryPlan.VisitTarget> targets = StructureGalleryPlan.visitTargets();

        require(descriptors.size() == DESCRIPTOR_COUNT,
                "Gallery no longer exposes every active Blueprint V2 gold master");
        require(StructureGalleryPlan.goldMasterViewCount() == GOLD_MASTER_COUNT,
                "Gold-master view count changed");
        require(StructureGalleryPlan.controlledLabViewCount() == CONTROLLED_COUNT,
                "Controlled-lab view count changed");
        require(entries.size() == GOLD_MASTER_COUNT + CONTROLLED_COUNT,
                "Gallery Blueprint V2 entry count changed");
        require(banks.size() == BANK_COUNT, "Gallery Bank count changed");
        require(targets.size() == TOTAL_TARGET_COUNT,
                "Gallery visit-target count changed");
        require(StructureGalleryPlan.totalStructureCount() == TOTAL_TARGET_COUNT,
                "Gallery total structure count changed");

        for (int index = 0; index < entries.size(); index++) {
            require(entries.get(index).index() == index,
                    "Gallery entry indices are no longer contiguous");
        }
        for (int index = 0; index < banks.size(); index++) {
            require(banks.get(index).index() == entries.size() + index,
                    "Gallery Bank indices are no longer globally contiguous");
        }

        expectImmutable(descriptors, descriptors.get(0));
        expectImmutable(entries, entries.get(0));
        expectImmutable(banks, banks.get(0));
        expectImmutable(targets, targets.get(0));
        require(descriptors == StructureGalleryPlan.goldMasters()
                        && entries == StructureGalleryPlan.entries()
                        && banks == StructureGalleryPlan.bankEntries()
                        && targets == StructureGalleryPlan.visitTargets(),
                "Gallery plan is being rebuilt or rerolled between reads");
    }

    private static void testVisitTargetsFrameEveryStructure() {
        List<StructureGalleryPlan.Entry> entries = StructureGalleryPlan.entries();
        List<StructureGalleryPlan.BankEntry> banks = StructureGalleryPlan.bankEntries();
        List<StructureGalleryPlan.VisitTarget> targets = StructureGalleryPlan.visitTargets();
        Set<Integer> encounteredRotations = new HashSet<>();
        int minimumMasterWidth = Integer.MAX_VALUE;
        int maximumMasterWidth = Integer.MIN_VALUE;
        int minimumMasterHeight = Integer.MAX_VALUE;
        int maximumMasterHeight = Integer.MIN_VALUE;

        require(targets.size() == 276,
                "The camera regression no longer exercises all 276 gallery structures");
        for (int index = 0; index < targets.size(); index++) {
            StructureGalleryPlan.VisitTarget target = targets.get(index);
            require(target.index() == index,
                    "Gallery visit targets are no longer globally contiguous");

            if (index < entries.size()) {
                StructureGalleryPlan.Entry entry = entries.get(index);
                VillageArchitecture.BlueprintDescriptor descriptor =
                        VillageArchitecture.requireBlueprint(
                                entry.templateId(), entry.templateRevision());
                require(target.originX() == entry.originX()
                                && target.originZ() == entry.originZ(),
                        "A Blueprint visit target escaped its gallery plot");
                require(target.width() == descriptor.width()
                                && target.depth() == descriptor.depth()
                                && target.height() == descriptor.height(),
                        "A Blueprint visit target ignores its descriptor envelope");
                require(target.rotation() == entry.rotation(),
                        "A Blueprint visit target ignores its persisted rotation");
                encounteredRotations.add(target.rotation());
                if (index < GOLD_MASTER_COUNT) {
                    minimumMasterWidth = Math.min(minimumMasterWidth, target.width());
                    maximumMasterWidth = Math.max(maximumMasterWidth, target.width());
                    minimumMasterHeight = Math.min(minimumMasterHeight, target.height());
                    maximumMasterHeight = Math.max(maximumMasterHeight, target.height());
                }
            } else {
                StructureGalleryPlan.BankEntry bank = banks.get(index - entries.size());
                require(target.originX() == bank.originX()
                                && target.originZ() == bank.originZ(),
                        "A Bank visit target escaped its gallery plot");
                require(target.width() == StructureGalleryPlan.STANDALONE_BANK_WIDTH
                                && target.depth() == StructureGalleryPlan.STANDALONE_BANK_DEPTH
                                && target.height() == StructureGalleryPlan.STANDALONE_BANK_HEIGHT
                                && target.rotation() == 0,
                        "Standalone Bank framing no longer matches its production envelope");
            }

            int rotatedWidth = target.rotation() % 2 == 0
                    ? target.width()
                    : target.depth();
            int rotatedDepth = target.rotation() % 2 == 0
                    ? target.depth()
                    : target.width();
            int minimumFrameX = Integer.MAX_VALUE;
            int maximumFrameX = Integer.MIN_VALUE;
            int minimumFrameZ = Integer.MAX_VALUE;
            int maximumFrameZ = Integer.MIN_VALUE;
            for (int x : new int[] {-5, target.width() + 4}) {
                for (int z : new int[] {-7, target.depth() + 4}) {
                    int transformedX = rotateX(target, x, z);
                    int transformedZ = rotateZ(target, x, z);
                    minimumFrameX = Math.min(minimumFrameX, transformedX);
                    maximumFrameX = Math.max(maximumFrameX, transformedX);
                    minimumFrameZ = Math.min(minimumFrameZ, transformedZ);
                    maximumFrameZ = Math.max(maximumFrameZ, transformedZ);
                }
            }
            int framingWidth = target.rotation() % 2 == 0
                    ? maximumFrameX - minimumFrameX + 1
                    : maximumFrameZ - minimumFrameZ + 1;
            int framingDepth = target.rotation() % 2 == 0
                    ? maximumFrameZ - minimumFrameZ + 1
                    : maximumFrameX - minimumFrameX + 1;
            double physicalHeight = target.height() + 1.0;
            double centerX = target.originX()
                    + (minimumFrameX + maximumFrameX + 1) / 2.0;
            double centerZ = target.originZ()
                    + (minimumFrameZ + maximumFrameZ + 1) / 2.0;
            double horizontalDistance = Math.hypot(
                    centerX - target.cameraX(), centerZ - target.cameraZ());
            double facadeStandoff = horizontalDistance - framingDepth / 2.0;
            double framingSpan = Math.max(framingWidth, physicalHeight);

            require(target.rotatedWidth() == rotatedWidth
                            && target.rotatedDepth() == rotatedDepth,
                    "Visit framing did not transform a rotated footprint");
            require(target.minimumFrameX() == minimumFrameX
                            && target.maximumFrameX() == maximumFrameX
                            && target.minimumFrameZ() == minimumFrameZ
                            && target.maximumFrameZ() == maximumFrameZ,
                    "Visit framing does not contain the complete authored exterior envelope");
            require(target.framingWidth() == framingWidth
                            && target.framingDepth() == framingDepth,
                    "Visit framing does not follow the rotated authored front");
            require(facadeStandoff + EPSILON >= 10.0
                            && facadeStandoff + EPSILON >= framingSpan * 0.75,
                    "Visit camera standoff does not scale with structure width or height");
            require(horizontalDistance > 0.0,
                    "Visit camera occupies the structure center");
            require(switch (target.rotation()) {
                case 0 -> close(target.cameraX(), centerX) && target.cameraZ() < centerZ;
                case 1 -> target.cameraX() > centerX && close(target.cameraZ(), centerZ);
                case 2 -> close(target.cameraX(), centerX) && target.cameraZ() > centerZ;
                case 3 -> target.cameraX() < centerX && close(target.cameraZ(), centerZ);
                default -> false;
            }, "Visit camera does not orbit with the structure's authored front");
            require(close(
                            Math.toDegrees(Math.atan2(
                                    target.cameraYOffset() - physicalHeight / 2.0,
                                    horizontalDistance)),
                            target.pitchDegrees()),
                    "Visit camera pitch does not aim at the structure's vertical center");
            require(Math.toDegrees(Math.atan2(
                                    framingSpan / 2.0, horizontalDistance))
                            <= 30.0,
                    "Visit camera cannot fit the structure envelope in a screenshot");
            require(close(target.yawDegrees(), target.rotation() * 90.0)
                            && close(target.pitchDegrees(), 20.0),
                    "Visit camera no longer faces the authored front at a stable review angle");
        }

        require(encounteredRotations.equals(Set.of(0, 1, 2, 3)),
                "Gallery visit regression no longer exercises every supported rotation");
        require(minimumMasterWidth == 9 && maximumMasterWidth == 27
                        && minimumMasterHeight == 10 && maximumMasterHeight == 23,
                "Gallery visit regression no longer spans every active master size extreme");
    }

    private static void testGoldMasterMatrixCoverage() {
        List<VillageArchitecture.BlueprintDescriptor> descriptors =
                StructureGalleryPlan.goldMasters();
        List<StructureGalleryPlan.Entry> matrix =
                entriesIn(StructureGalleryPlan.GOLD_MASTER_MATRIX);
        require(matrix.size() == GOLD_MASTER_COUNT,
                "Gold-master gallery no longer exposes every descriptor/dialect view");

        Set<String> descriptorIdentities = new HashSet<>();
        for (VillageArchitecture.BlueprintDescriptor descriptor : descriptors) {
            require(descriptorIdentities.add(identity(descriptor)),
                    "Gold-master descriptor catalog contains a duplicate revision");
        }

        Map<String, Integer> usesByDescriptor = new HashMap<>();
        Map<String, Integer> descriptorIndices = new HashMap<>();
        for (int index = 0; index < descriptors.size(); index++) {
            descriptorIndices.put(identity(descriptors.get(index)), index);
        }
        Set<String> descriptorDialectViews = new HashSet<>();
        VillageArchitecture.BiomeDialect[] dialects =
                VillageArchitecture.BiomeDialect.values();
        require(dialects.length == DIALECT_COUNT, "Gallery dialect contract changed");
        int columns = StructureGalleryPlan.WIDTH_BLOCKS / StructureGalleryPlan.PITCH;
        require(columns >= 11 && columns <= 12,
                "Gallery paging no longer uses its compact 11-12 column review grid");
        for (int index = 0; index < matrix.size(); index++) {
            StructureGalleryPlan.Entry entry = matrix.get(index);
            String entryIdentity = entry.templateId() + "@" + entry.templateRevision();
            Integer descriptorIndex = descriptorIndices.get(entryIdentity);
            require(descriptorIndex != null,
                    "Gold-master matrix references a descriptor outside the active catalog");
            VillageArchitecture.BlueprintDescriptor descriptor =
                    descriptors.get(descriptorIndex);
            int page = descriptorIndex / columns;
            int column = descriptorIndex % columns;
            int dialectRow = entry.dialect().ordinal();

            require(entry.index() == index,
                    "Gold-master matrix is no longer the stable gallery prefix");
            require(entry.originX() == column * StructureGalleryPlan.PITCH
                            && entry.originZ()
                                    == (page * DIALECT_COUNT + dialectRow)
                                            * StructureGalleryPlan.PITCH,
                    "Gold-master entry escaped its deterministic paged grid");
            require(entry.type() == descriptor.type(),
                    "Gold-master plot no longer represents its immutable descriptor");
            require(entry.character() == VillageArchitecture.Character.MERCANTILE
                            && entry.paletteId().equals(VillageArchitecture.PALETTE_BALANCED)
                            && entry.dressingId().equals(
                                    VillageArchitecture.DRESSING_PROSPEROUS)
                            && !entry.mirrored()
                            && entry.visualStage() == 2
                            && entry.rotation() == 0,
                    "Gold-master matrix no longer presents the full prosperous production view");

            usesByDescriptor.merge(entryIdentity, 1, Integer::sum);
            require(descriptorDialectViews.add(entryIdentity + ":" + entry.dialect().id()),
                    "Gold-master matrix duplicated one descriptor/dialect view");
            require(VillageArchitecture.isKnownBlueprintSelection(
                            entry.type(),
                            entry.templateId(),
                            entry.templateRevision(),
                            entry.paletteId(),
                            entry.dressingId(),
                            entry.mirrored(),
                            entry.blueprintSignature()),
                    "Gold-master entry does not carry a valid persisted Blueprint identity");
        }

        require(descriptorDialectViews.size() == GOLD_MASTER_COUNT,
                "Gold-master matrix lost a descriptor/dialect pair");
        for (String identity : descriptorIdentities) {
            require(usesByDescriptor.getOrDefault(identity, 0) == DIALECT_COUNT,
                    "Gold master " + identity + " is not rendered in every biome dialect");
        }
    }

    private static void testReviewHubFacesGoldMasterMatrix() {
        int hubZ = StructureGalleryPlan.reviewHubZ();
        int matrixMaximumZ = entriesIn(StructureGalleryPlan.GOLD_MASTER_MATRIX).stream()
                .mapToInt(StructureGalleryPlan.Entry::originZ)
                .max()
                .orElseThrow();
        int controlledZ = entriesIn(StructureGalleryPlan.CONTROLLED_BLUEPRINT_LAB)
                .getFirst()
                .originZ();

        require(matrixMaximumZ < hubZ && controlledZ > hubZ,
                "Review hub is no longer isolated between the master matrix and controlled lab");
        require(StructureGalleryPlan.reviewHubYawDegrees() == 180,
                "New gallery reviewers no longer face north toward the gold-master matrix");
    }

    private static void testControlledBlueprintCoverage() {
        List<StructureGalleryPlan.Entry> controlled =
                entriesIn(StructureGalleryPlan.CONTROLLED_BLUEPRINT_LAB);
        require(controlled.size() == CONTROLLED_COUNT,
                "Controlled Blueprint lab no longer has eleven isolated comparisons");

        Set<Integer> stages = new HashSet<>();
        Set<Integer> rotations = new HashSet<>();
        Set<String> palettes = new HashSet<>();
        Set<String> dressings = new HashSet<>();
        Set<Boolean> mirrors = new HashSet<>();
        int controlledZ = controlled.getFirst().originZ();
        int matrixMaximumZ = entriesIn(StructureGalleryPlan.GOLD_MASTER_MATRIX).stream()
                .mapToInt(StructureGalleryPlan.Entry::originZ)
                .max()
                .orElseThrow();
        require(controlledZ == matrixMaximumZ + 2 * StructureGalleryPlan.PITCH,
                "Controlled lab no longer follows an empty gallery navigation row");
        for (int column = 0; column < controlled.size(); column++) {
            StructureGalleryPlan.Entry entry = controlled.get(column);
            require(entry.originX() == column * StructureGalleryPlan.PITCH
                            && entry.originZ() == controlledZ,
                    "Controlled Blueprint lab escaped its dedicated row");
            require(entry.type() == VillageProsperityEngine.ProjectType.HOUSE
                            && entry.dialect() == VillageArchitecture.BiomeDialect.PLAINS
                            && entry.character() == VillageArchitecture.Character.MERCANTILE
                            && entry.templateId().equals("house_cross_01")
                            && entry.templateRevision() == 3,
                    "Controlled lab changed its coherent reference blueprint");
            require(VillageArchitecture.isKnownBlueprintSelection(
                            entry.type(),
                            entry.templateId(),
                            entry.templateRevision(),
                            entry.paletteId(),
                            entry.dressingId(),
                            entry.mirrored(),
                            entry.blueprintSignature()),
                    "Controlled lab contains an invalid Blueprint V2 identity");
            stages.add(entry.visualStage());
            rotations.add(entry.rotation());
            palettes.add(entry.paletteId());
            dressings.add(entry.dressingId());
            mirrors.add(entry.mirrored());
        }

        require(stages.equals(Set.of(0, 1, 2)),
                "Controlled lab no longer covers all visual stages");
        require(rotations.equals(Set.of(0, 1, 2, 3)),
                "Controlled lab no longer covers every cardinal rotation");
        require(palettes.equals(Set.of(
                        VillageArchitecture.PALETTE_BALANCED,
                        VillageArchitecture.PALETTE_TIMBER_FORWARD,
                        VillageArchitecture.PALETTE_MASONRY_FORWARD)),
                "Controlled lab no longer covers all semantic palettes");
        require(dressings.equals(Set.of(
                        VillageArchitecture.DRESSING_RESTRAINED,
                        VillageArchitecture.DRESSING_LIVED_IN,
                        VillageArchitecture.DRESSING_PROSPEROUS)),
                "Controlled lab no longer covers all approved dressing kits");
        require(mirrors.equals(Set.of(false, true)),
                "Controlled lab no longer covers the approved mirror transform");

        StructureGalleryPlan.Entry reference = controlled.get(2);
        for (int index = 3; index < controlled.size(); index++) {
            StructureGalleryPlan.Entry comparison = controlled.get(index);
            require(differenceCount(reference, comparison) == 1,
                    "Controlled lab entry " + comparison.index()
                            + " changes more than one review axis");
        }
    }

    private static void testEntryDescriptorGuards() {
        StructureGalleryPlan.Entry reference = StructureGalleryPlan.entries().get(0);
        expectIllegal(() -> new StructureGalleryPlan.Entry(
                0,
                StructureGalleryPlan.GOLD_MASTER_MATRIX,
                0,
                0,
                reference.type(),
                reference.dialect(),
                reference.character(),
                "missing_template",
                1,
                reference.paletteId(),
                reference.dressingId(),
                false,
                2,
                0));
        expectIllegal(() -> new StructureGalleryPlan.Entry(
                0,
                StructureGalleryPlan.GOLD_MASTER_MATRIX,
                0,
                0,
                VillageProsperityEngine.ProjectType.HOUSE,
                reference.dialect(),
                reference.character(),
                reference.templateId(),
                reference.templateRevision(),
                reference.paletteId(),
                reference.dressingId(),
                false,
                2,
                0));

        VillageArchitecture.BlueprintDescriptor nonMirrorable =
                StructureGalleryPlan.goldMasters().stream()
                        .filter(descriptor -> !descriptor.mirrorable())
                        .findFirst()
                        .orElseThrow();
        expectIllegal(() -> new StructureGalleryPlan.Entry(
                0,
                StructureGalleryPlan.GOLD_MASTER_MATRIX,
                0,
                0,
                nonMirrorable.type(),
                VillageArchitecture.BiomeDialect.PLAINS,
                VillageArchitecture.Character.MERCANTILE,
                nonMirrorable.templateId(),
                nonMirrorable.templateRevision(),
                VillageArchitecture.PALETTE_BALANCED,
                VillageArchitecture.DRESSING_RESTRAINED,
                true,
                2,
                0));
    }

    private static void testBankCoverageAndBoundedLayout() {
        List<StructureGalleryPlan.Entry> entries = StructureGalleryPlan.entries();
        List<StructureGalleryPlan.BankEntry> banks = StructureGalleryPlan.bankEntries();
        Set<VillageArchitecture.BiomeDialect> dialects = new HashSet<>();
        Set<String> origins = new HashSet<>();
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        int controlledZ = entriesIn(StructureGalleryPlan.CONTROLLED_BLUEPRINT_LAB)
                .getFirst()
                .originZ();
        int bankZ = banks.getFirst().originZ();
        require(bankZ == controlledZ + 2 * StructureGalleryPlan.PITCH,
                "Biome Banks no longer follow an empty gallery navigation row");

        for (StructureGalleryPlan.Entry entry : entries) {
            require(entry.originX() % StructureGalleryPlan.PITCH == 0
                            && entry.originZ() % StructureGalleryPlan.PITCH == 0,
                    "A gallery entry is not aligned to the 24-block pitch");
            require(origins.add(entry.originX() + ":" + entry.originZ()),
                    "Two Blueprint gallery structures share one plot origin");
            maximumX = Math.max(maximumX, entry.originX());
            maximumZ = Math.max(maximumZ, entry.originZ());
        }
        for (int index = 0; index < banks.size(); index++) {
            StructureGalleryPlan.BankEntry bank = banks.get(index);
            require(bank.originX() == index * StructureGalleryPlan.PITCH
                            && bank.originZ() == bankZ,
                    "Biome Bank gallery row escaped its reserved plots");
            require(origins.add(bank.originX() + ":" + bank.originZ()),
                    "A Bank overlaps another gallery plot origin");
            dialects.add(bank.dialect());
            maximumX = Math.max(maximumX, bank.originX());
            maximumZ = Math.max(maximumZ, bank.originZ());

            StructureGalleryPlan.VisitTarget target = StructureGalleryPlan.visitTargets().get(
                    bank.index());
            require(target.width() == StructureGalleryPlan.STANDALONE_BANK_WIDTH
                            && target.depth() == StructureGalleryPlan.STANDALONE_BANK_DEPTH
                            && target.height() == StructureGalleryPlan.STANDALONE_BANK_HEIGHT,
                    "Standalone Bank review dimensions drifted from production Bank v4");
            require(target.minimumFrameX() <= StructureGalleryPlan.STANDALONE_BANK_MIN_X
                            && target.maximumFrameX()
                            >= StructureGalleryPlan.STANDALONE_BANK_MAX_X
                            && target.minimumFrameZ()
                            <= StructureGalleryPlan.STANDALONE_BANK_MIN_Z
                            && target.maximumFrameZ()
                            >= StructureGalleryPlan.STANDALONE_BANK_MAX_Z,
                    "Standalone Bank camera frame clips a Bank v4 portico, eave, or buttress");
            int bankSpan = Math.max(
                    StructureGalleryPlan.STANDALONE_BANK_MAX_X
                            - StructureGalleryPlan.STANDALONE_BANK_MIN_X + 1,
                    StructureGalleryPlan.STANDALONE_BANK_MAX_Z
                            - StructureGalleryPlan.STANDALONE_BANK_MIN_Z + 1);
            require(StructureGalleryPlan.PITCH >= bankSpan,
                    "Gallery pitch cannot contain the complete Bank v4 footprint");
        }

        require(dialects.equals(Set.of(VillageArchitecture.BiomeDialect.values())),
                "Gallery does not contain exactly one Bank per biome dialect");
        require(maximumX + StructureGalleryPlan.PITCH == StructureGalleryPlan.WIDTH_BLOCKS,
                "Gallery width contract changed");
        require(maximumZ + StructureGalleryPlan.PITCH == StructureGalleryPlan.DEPTH_BLOCKS,
                "Gallery depth contract changed");
        int largestEnvelope = StructureGalleryPlan.goldMasters().stream()
                .mapToInt(descriptor -> Math.max(descriptor.width(), descriptor.depth()))
                .max()
                .orElseThrow();
        require(StructureGalleryPlan.PLOT_SAFETY_MARGIN == 12,
                "Gallery pitch no longer spans z=-7 through depth+4 with separation");
        require(StructureGalleryPlan.PITCH
                                >= largestEnvelope + StructureGalleryPlan.PLOT_SAFETY_MARGIN
                        && StructureGalleryPlan.PITCH % 8 == 0,
                "Gallery plots no longer reserve safe space for authored exterior details");
        require(StructureGalleryPlan.WIDTH_BLOCKS <= 512
                        && StructureGalleryPlan.DEPTH_BLOCKS <= 1_200,
                "Expanded Blueprint V2 gallery exceeded its bounded 512 by 1200 review area");
    }

    private static void testCompletionSignatureCoversEveryReviewAxis() {
        long canonical = StructureGalleryPlan.layoutSignature();
        require(canonical == StructureGalleryPlan.layoutSignature(
                        StructureGalleryPlan.entries(), StructureGalleryPlan.bankEntries()),
                "Public gallery signature does not represent the frozen review plan");
        require(StructureGalleryPlan.GALLERY_CONTENT_REVISION > 0,
                "Gallery render-schema revision must remain a positive explicit contract");
        require(canonical != StructureGalleryPlan.layoutSignature(
                        StructureGalleryPlan.GALLERY_CONTENT_REVISION + 1,
                        StructureGalleryPlan.entries(),
                        StructureGalleryPlan.bankEntries()),
                "Completion signature ignores gallery render-schema revisions");
        expectIllegal(() -> StructureGalleryPlan.layoutSignature(
                0, StructureGalleryPlan.entries(), StructureGalleryPlan.bankEntries()));

        List<StructureGalleryPlan.Entry> changedEntries =
                new ArrayList<>(StructureGalleryPlan.entries());
        int controlledIndex = StructureGalleryPlan.goldMasterViewCount();
        StructureGalleryPlan.Entry controlled = changedEntries.get(controlledIndex);
        changedEntries.set(controlledIndex, new StructureGalleryPlan.Entry(
                controlled.index(),
                controlled.section(),
                controlled.originX(),
                controlled.originZ(),
                controlled.type(),
                controlled.dialect(),
                controlled.character(),
                controlled.templateId(),
                controlled.templateRevision(),
                controlled.paletteId(),
                controlled.dressingId(),
                controlled.mirrored(),
                controlled.visualStage() == 0 ? 1 : 0,
                controlled.rotation()));
        require(canonical != StructureGalleryPlan.layoutSignature(
                        changedEntries, StructureGalleryPlan.bankEntries()),
                "Completion signature ignores controlled-lab stage changes");

        List<StructureGalleryPlan.BankEntry> changedBanks =
                new ArrayList<>(StructureGalleryPlan.bankEntries());
        StructureGalleryPlan.BankEntry bank = changedBanks.getFirst();
        VillageArchitecture.BiomeDialect replacement = bank.dialect()
                == VillageArchitecture.BiomeDialect.PLAINS
                        ? VillageArchitecture.BiomeDialect.DESERT
                        : VillageArchitecture.BiomeDialect.PLAINS;
        changedBanks.set(0, new StructureGalleryPlan.BankEntry(
                bank.index(), bank.originX(), bank.originZ(), replacement));
        require(canonical != StructureGalleryPlan.layoutSignature(
                        StructureGalleryPlan.entries(), changedBanks),
                "Completion signature ignores standalone Bank review changes");
    }

    private static int differenceCount(
            StructureGalleryPlan.Entry left, StructureGalleryPlan.Entry right) {
        int differences = 0;
        differences += left.visualStage() == right.visualStage() ? 0 : 1;
        differences += left.paletteId().equals(right.paletteId()) ? 0 : 1;
        differences += left.dressingId().equals(right.dressingId()) ? 0 : 1;
        differences += left.mirrored() == right.mirrored() ? 0 : 1;
        differences += left.rotation() == right.rotation() ? 0 : 1;
        return differences;
    }

    private static String identity(VillageArchitecture.BlueprintDescriptor descriptor) {
        return descriptor.templateId() + "@" + descriptor.templateRevision();
    }

    private static List<StructureGalleryPlan.Entry> entriesIn(String section) {
        return StructureGalleryPlan.entries().stream()
                .filter(entry -> entry.section().equals(section))
                .toList();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(List list, Object value) {
        try {
            list.add(value);
            throw new AssertionError("Gallery exposed a mutable plan list");
        } catch (UnsupportedOperationException expected) {
            // Expected: the Minecraft bridge receives a frozen gallery contract.
        }
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Invalid Blueprint gallery entry was accepted");
        } catch (IllegalArgumentException expected) {
            // Expected: every entry must resolve to a compatible immutable descriptor.
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= EPSILON;
    }

    private static int rotateX(
            StructureGalleryPlan.VisitTarget target, int x, int z) {
        return switch (target.rotation()) {
            case 1 -> target.depth() - 1 - z;
            case 2 -> target.width() - 1 - x;
            case 3 -> z;
            default -> x;
        };
    }

    private static int rotateZ(
            StructureGalleryPlan.VisitTarget target, int x, int z) {
        return switch (target.rotation()) {
            case 1 -> x;
            case 2 -> target.depth() - 1 - z;
            case 3 -> target.width() - 1 - x;
            default -> z;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
