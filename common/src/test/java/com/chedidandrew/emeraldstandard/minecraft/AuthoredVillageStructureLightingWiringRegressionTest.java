package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the Minecraft block-state adapter for loader-neutral interior-light validation. */
public final class AuthoredVillageStructureLightingWiringRegressionTest {
    private static final String AUTHORED_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredVillageStructures.java";
    private static final String BANK_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "VillageBankManager.java";
    private static final String FIXTURE_SUPPORT_SOURCE =
            "common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/"
                    + "AuthoredLightFixtureSupportValidator.java";

    private AuthoredVillageStructureLightingWiringRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path sourceFile = Path.of(args[0]).resolve(AUTHORED_SOURCE);
        require(Files.isRegularFile(sourceFile),
                "Missing authored village structures source: " + sourceFile);
        String source = Files.readString(sourceFile);
        Path bankSourceFile = Path.of(args[0]).resolve(BANK_SOURCE);
        require(Files.isRegularFile(bankSourceFile),
                "Missing Village Bank source: " + bankSourceFile);
        String bankSource = Files.readString(bankSourceFile);
        Path fixtureSupportSourceFile = Path.of(args[0]).resolve(FIXTURE_SUPPORT_SOURCE);
        require(Files.isRegularFile(fixtureSupportSourceFile),
                "Missing authored fixture-support source: " + fixtureSupportSourceFile);
        String fixtureSupportSource = Files.readString(fixtureSupportSourceFile);

        verifyEveryCoveredFloorPlanIsGated(source);
        verifyEveryVisualStageIsGated(source);
        verifyEveryCoveredFloorIsTargeted(source);
        verifyClosedInteriorPocketsAreRejected(source);
        verifyIsolatedVerticalRoutesCannotSeedCirculation(source);
        verifyRealBlockStateLightingIsMapped(source);
        verifyNaturalSupportedFixturePolicy(source);
        verifyCompleteFixtureAttachments(source, bankSource, fixtureSupportSource);
        verifyFullCatalogMatrixExercisesTheGate(source);
        verifyEveryBankDialectExercisesTheExactGate(bankSource);
        verifyProductionBankBuildIsGated(bankSource);
        System.out.println("PASS authored village lighting wiring regression");
    }

    private static void verifyEveryCoveredFloorPlanIsGated(String source) {
        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        require(validation.contains("if (blueprint.metadata.enclosed)"),
                "Enclosed connectivity validation lost its explicit scope");
        int enclosedBranchEnd = validation.indexOf("validateCoveredFloorLighting(");
        require(enclosedBranchEnd >= 0
                        && validation.indexOf("validateInteriorConnectivity(") < enclosedBranchEnd,
                "Covered-floor lighting is not applied after the enclosed-only connectivity gate");

        String lighting = methodBody(source, "private static void validateCoveredFloorLighting(");
        require(lighting.contains("requireComfortablyLit()"),
                "Production admission only checks technical spawn safety, not visual comfort");

        String composition = methodBody(
                source, "private static void ensureComfortableInteriorLighting(");
        require(!composition.contains("if (!metadata.enclosed)"),
                "Open and semi-open masters can still bypass authored light composition");
    }

    private static void verifyEveryVisualStageIsGated(String source) {
        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        String plan = methodBody(source, "static Blueprint plan(");
        String cumulativeComposer = methodBody(
                source, "private static void ensureCumulativeComfortableInteriorLighting(");
        require(validation.contains("Map<BlockPos, BlockState> throughStageOne")
                        && validation.contains("appendDisjoint(throughStageOne, blueprint.stageOne")
                        && validation.contains(
                                "validateCoveredFloorLighting(blueprint, \"base\", base);")
                        && validation.contains(
                                "validateCoveredFloorLighting(blueprint, \"stage-one\", throughStageOne);")
                        && validation.contains(
                                "validateCoveredFloorLighting(blueprint, \"stage-two\", complete);")
                        && validation.contains("if (complete.containsKey(reserved))")
                        && count(validation, "validateCoveredFloorLighting(") == 3,
                "BASE, cumulative Stage 1, and cumulative Stage 2 are not each admitted through "
                        + "the exact lighting gate while preserving reserved circulation");
        require(count(plan, "ensureCumulativeComfortableInteriorLighting(") == 2
                        && cumulativeComposer.contains("Builder cumulative = new Builder(Set.of())")
                        && cumulativeComposer.contains("replayLightingComposition(target, composition)")
                        && cumulativeComposer.contains("composeComfortableInteriorLighting("),
                "Late visual stages are validated but no longer receive append-only natural "
                        + "lighting composition when dressing adds or shades covered floor cells");
    }

    private static void verifyEveryCoveredFloorIsTargeted(String source) {
        String floors = methodBody(source, "private static Set<Voxel> coveredSpawnableFloors(");
        require(floors.contains(
                                "for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet())")
                        && floors.contains("BlockPos support = entry.getKey()")
                        && floors.contains("BlockPos feet = support.above()"),
                "Lighting validation no longer enumerates every actual authored support surface");
        require(floors.contains("isValidSpawn(")
                        && floors.contains("EntityTypes.ZOMBIE"),
                "Non-spawnable furniture is no longer excluded with Minecraft's real hostile "
                        + "spawn-support rule");
        require(floors.contains("authored.containsKey(feet)")
                        && floors.contains("authored.containsKey(feet.above())"),
                "Lighting targets are not restricted to actually clear two-block standing space");
        require(!floors.contains("exteriorAir.contains(feet)")
                        && floors.contains("weatherCovered(authored, feet, height)"),
                "Covered open-air work floors can bypass the all-structure lighting audit");
        require(!floors.contains("structuralFloor(floor.phase)"),
                "Lighting targets regressed to BASE phase labels instead of completed block-state "
                        + "spawn surfaces");

        String bounds = methodBody(source, "private static Bounds authoredLightingBounds(");
        require(bounds.contains("for (BlockPos position : authored.keySet())")
                        && bounds.contains("minX = Math.min(minX, position.getX())")
                        && bounds.contains("maxX = Math.max(maxX, position.getX())")
                        && bounds.contains("minZ = Math.min(minZ, position.getZ())")
                        && bounds.contains("maxZ = Math.max(maxZ, position.getZ())"),
                "Lighting bounds no longer follow the actual cumulative authored X/Z projection");

        String snapshot = methodBody(source, "private static LightingSnapshot lightingSnapshot(");
        require(snapshot.contains("authoredLightingBounds(height, authored)")
                        && snapshot.contains(
                                "coveredSpawnableFloors(height, authored, bounds)"),
                "Production snapshots do not use authored projection bounds and actual supported "
                        + "floor targets");
    }

    private static void verifyClosedInteriorPocketsAreRejected(String source) {
        String validation = methodBody(source, "private static void validate(Blueprint blueprint)");
        require(validation.contains("validateInteriorConnectivity(blueprint, base, complete);"),
                "Enclosed buildings can bypass the closed-room admission gate");

        String connectivity = methodBody(
                source, "private static void validateInteriorConnectivity(");
        require(connectivity.contains("interiorSpawnableFloors(")
                        && connectivity.contains("interiorCirculation(")
                        && connectivity.contains("!circulation.contains(position)"),
                "Closed-room admission does not compare every spawnable floor with circulation");
        require(connectivity.contains("throw new IllegalStateException("),
                "Disconnected spawnable interior is reported but not rejected");
    }

    private static void verifyIsolatedVerticalRoutesCannotSeedCirculation(String source) {
        String circulation = methodBody(source, "private static Set<BlockPos> interiorCirculation(");
        int boardingGate = circulation.indexOf(
                "if (!reachableLowerBoarding(base, visited, access))");
        int verticalSeed = circulation.indexOf(
                "for (int y = access.from.getY(); y <= access.to.getY() + 2; y++)");
        require(circulation.contains("List<VerticalAccess> pending")
                        && circulation.contains("do {")
                        && circulation.contains("iterator.remove()")
                        && boardingGate >= 0
                        && verticalSeed > boardingGate,
                "An isolated declared ladder can seed disconnected upper-floor circulation");

        String boarding = methodBody(source, "private static boolean reachableLowerBoarding(");
        require(boarding.contains("access.from.relative(direction)")
                        && boarding.contains("visited.contains(boarding)")
                        && boarding.contains("walkable(base, boarding)"),
                "Vertical access does not require an entrance-reachable lower boarding tile");
    }

    private static void verifyRealBlockStateLightingIsMapped(String source) {
        String snapshot = methodBody(source, "private static LightingSnapshot lightingSnapshot(");
        require(snapshot.contains("state.getLightEmission()"),
                "Authored emitters no longer use Minecraft block-state light emission");
        require(snapshot.contains("conservativeLightDampening(state)"),
                "Authored geometry no longer contributes light dampening");

        String dampening = methodBody(source, "private static int conservativeLightDampening(");
        require(dampening.contains("state.getLightDampening()")
                        && dampening.contains("state.canOcclude()")
                        && dampening.contains("state.useShapeForLightOcclusion()"),
                "Palette-specific block opacity can leak optimistic light through walls or trim");
    }

    private static void verifyNaturalSupportedFixturePolicy(String source) {
        String plan = methodBody(source, "static Blueprint plan(");
        require(plan.contains("ensureComfortableInteriorLighting(base, metadata, templateId);"),
                "Dark zones are validated but no longer receive authored natural lighting");

        String mount = methodBody(source, "private static LightingMount supportedLightingMount(");
        require(mount.contains("isFaceSturdy(")
                        && mount.contains("Direction.DOWN")
                        && mount.contains("maximumChainLength"),
                "Generated lanterns are not constrained to sturdy, short ceiling mounts");

        require(source.contains("lightingFixtureSpaced(cells, candidate)")
                        && source.contains("lightingAestheticScore(metadata, candidate)")
                        && source.contains("lightingMountPreservesReservedAir(metadata, candidate)"),
                "Coverage selection lost spacing, reserved-circulation, or role composition");
        require(source.contains("interiorCirculation("),
                "Closed-room validation no longer derives actor-reachable circulation");
        require(source.contains("singleMountSnapshot(snapshot, candidate)"),
                "Fixture selection estimates distance but does not prove actual propagated coverage");
        require(source.contains("Blocks.WALL_TORCH.defaultBlockState()")
                        && source.contains("WallTorchBlock.FACING"),
                "Low supported work/domestic wall lighting is no longer available as a fallback");
        require(source.contains("addCivicLightingPartner(builder, metadata, snapshot, mount);"),
                "Large civic interiors lost their balanced lantern-pair treatment");
    }

    private static void verifyCompleteFixtureAttachments(
            String source, String bankSource, String fixtureSupportSource) {
        String authoredGate = methodBody(
                source, "private static void validateAttachedSupports(");
        require(authoredGate.contains(
                        "AuthoredLightFixtureSupportValidator.validate(cells, blueprint.id)"),
                "Authored cumulative stages can bypass complete fixture-attachment admission");

        String validation = methodBody(
                fixtureSupportSource,
                "static void validate(Map<BlockPos, BlockState> authored, String snapshotId)");
        require(validation.contains("state.getBlock() instanceof LanternBlock")
                        && validation.contains("LanternBlock.HANGING")
                        && validation.contains("validateHangingLantern(")
                        && validation.contains("entry.getKey().below()")
                        && validation.contains("Direction.UP")
                        && validation.contains("standing lantern"),
                "Standing and hanging lanterns no longer use the shared attachment gate");
        require(validation.contains("state.getBlock() instanceof WallTorchBlock")
                        && validation.contains("WallTorchBlock.FACING")
                        && validation.contains("facing.getOpposite()")
                        && validation.contains("isFaceSturdy("),
                "Wall-mounted lights no longer require their authored backing face");
        require(validation.contains("state.getBlock() instanceof TorchBlock")
                        && validation.contains("standing torch"),
                "Standing torches can bypass center-bearing floor support admission");

        String hanging = methodBody(
                fixtureSupportSource, "private static void validateHangingLantern(");
        require(hanging.contains("support.is(Blocks.IRON_CHAIN)")
                        && hanging.contains("RotatedPillarBlock.AXIS")
                        && hanging.contains("Direction.Axis.Y")
                        && hanging.contains("supportPosition = supportPosition.above()")
                        && hanging.contains("BlockTags.UNSTABLE_BOTTOM_CENTER")
                        && hanging.contains("Direction.DOWN")
                        && hanging.contains("SupportType.CENTER"),
                "Hanging-lantern admission no longer proves a contiguous vertical chain run "
                        + "ending at a center-bearing ceiling mount");
        require(!fixtureSupportSource.contains("setBlock(")
                        && !fixtureSupportSource.contains("setBlockAndUpdate(")
                        && !fixtureSupportSource.contains("authored.put("),
                "Fixture attachment admission must remain read-only and never repair damage");

        String bankLighting = methodBody(
                bankSource, "private static void validateBankInteriorLighting(");
        require(bankLighting.contains(
                        "AuthoredLightFixtureSupportValidator.validate(authored, snapshotId)"),
                "Standalone Bank plans can bypass complete fixture-attachment admission");
    }

    private static void verifyFullCatalogMatrixExercisesTheGate(String source) {
        String descriptor = methodBody(source, "private static CatalogValidationResult "
                + "validateCatalogDescriptor(");
        for (String dimension : new String[] {
                "descriptor.dressingIds()",
                "VillageArchitecture.BiomeDialect.values()",
                "descriptor.paletteIds()",
                "VillageArchitecture.Character.values()"
        }) {
            require(descriptor.contains(dimension),
                    "Catalog lighting admission omits variant dimension " + dimension);
        }
        require(count(descriptor, "plan(") >= 3,
                "Catalog variants no longer pass through full authored plan validation");
    }

    private static void verifyEveryBankDialectExercisesTheExactGate(String source) {
        String dialects = methodBody(
                source, "static void validateBankDialectPaletteContract()");
        String dialectMatrix = methodBody(
                source, "private static void validateBankDialectPaletteContract(");
        String exact = methodBody(
                source, "private static void validateCurrentBankBlueprint(");
        require(dialects.contains("validateBankDialectPaletteContract(false);")
                        && dialectMatrix.contains("VillageArchitecture.BiomeDialect.values()")
                        && dialectMatrix.contains("validateCurrentBankBlueprint(")
                        && exact.contains("bankPlan(origin, palette)")
                        && exact.contains("validateBankInteriorLighting(authored, snapshotId)")
                        && dialectMatrix.contains("dialect.id()"),
                "One or more standalone Bank dialects can bypass exact no-skylight validation");

        String lighting = methodBody(
                source, "private static void validateBankInteriorLighting(");
        require(lighting.contains("snapshotId")
                        && lighting.contains("bankLightingBounds(authored)")
                        && lighting.contains("bankCoveredSpawnableFloors(authored, bounds)")
                        && lighting.contains("requireComfortablyLit()"),
                "Standalone Bank dialect validation no longer uses the exact comfortable-light "
                        + "admission threshold");

        String floors = methodBody(
                source, "private static Set<Voxel> bankCoveredSpawnableFloors(");
        require(floors.contains("for (Map.Entry<BlockPos, BlockState> entry : authored.entrySet())")
                        && floors.contains("isValidSpawn(")
                        && floors.contains("EntityTypes.ZOMBIE")
                        && floors.contains("isBankWalkableCell(authored.get(feet))")
                        && floors.contains("isBankWalkableCell(authored.get(feet.above()))")
                        && floors.contains("bankWeatherCovered(authored, feet"),
                "Bank lighting can omit a roof-covered authored porch, apron, or landing floor");
        require(!floors.contains("x = 1; x < BANK_WIDTH - 1")
                        && !floors.contains("z = 1; z < BANK_DEPTH - 1"),
                "Bank floor admission regressed to the nominal interior rectangle");

        String bounds = methodBody(source, "private static Bounds bankLightingBounds(");
        require(bounds.contains("for (BlockPos position : authored.keySet())")
                        && bounds.contains("minX = Math.min")
                        && bounds.contains("minZ = Math.min")
                        && bounds.contains("maxX = Math.max")
                        && bounds.contains("maxZ = Math.max"),
                "Bank lighting bounds no longer include the complete authored projection");

        String current = methodBody(source, "private static List<BankPlacement> bankPlan(");
        String versionSix = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV6(");
        String versionFive = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV5(");
        String versionFour = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV4(");
        String inherited = methodBody(
                source, "private static List<BankPlacement> legacyBankPlanV3(");
        require(current.contains("legacyBankPlanV6(origin, palette)")
                        && versionSix.contains("legacyBankPlanV5(origin, palette)")
                        && versionFive.contains("legacyBankPlanV4(origin, palette)")
                        && versionFour.contains("legacyBankPlanV3(origin, palette)")
                        && inherited.contains("origin.offset(centerX, 3, -1)")
                        && inherited.contains("LanternBlock.HANGING, true"),
                "The roof-covered three-wide Bank landing lost its supported portico pendant");
    }

    private static void verifyProductionBankBuildIsGated(String source) {
        String build = methodBody(source, "private static BankBuildResult buildBank(");
        require(build.contains("ensureBankTemplateValidated();"),
                "Production Bank construction can bypass exact dialect lighting admission");
        String gate = methodBody(source, "private static void ensureBankTemplateValidated(");
        require(gate.contains("validateBankDialectPaletteContract();")
                        && gate.contains("bankTemplateValidated = true"),
                "Production Bank lighting gate is not cached after exact validation");
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        require(method >= 0, "Missing source method: " + signature);
        int openingBrace = source.indexOf('{', method);
        require(openingBrace >= 0, "Missing method body: " + signature);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(openingBrace + 1, index);
            }
        }
        throw new AssertionError("Unterminated method body: " + signature);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
