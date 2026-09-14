package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Guards the bounded watchdog allowance and exact process/world scope of integration smoke tests. */
public final class SmokeServerHarnessRegressionTest {
    private SmokeServerHarnessRegressionTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Repository root argument is required");
        }
        Path root = Path.of(args[0]);
        String script = Files.readString(root.resolve("scripts/smoke-server.sh"));
        String launcher = Files.readString(root.resolve("scripts/smoke-server.init.gradle"));
        require(script.contains("RUN_DIR=\"$(mktemp -d \"$LOG_DIR/$LOADER-run.XXXXXX\")\"")
                        && script.contains("-PtesSmokeGameDir=\"$RUN_DIR\"")
                        && script.contains("printf 'online-mode=false\\nserver-port=0\\nmax-tick-time=180000\\n'"
                                + " > \"$RUN_DIR/server.properties\""),
                "Smoke watchdog allowance must remain bounded and confined to a fresh ephemeral-port test world");
        require(script.indexOf("max-tick-time=") == script.lastIndexOf("max-tick-time=")
                        && !script.contains("max-tick-time=-1")
                        && launcher.contains("gradleProperty('tesSmokeGameDir')")
                        && launcher.contains("runDirectory = project.file(targetPath)")
                        && launcher.contains("run.gameDirectory.set(project.file(targetPath))"),
                "Both loaders must use only the generated smoke world, without disabling the watchdog");
        require(script.contains("for _ in $(seq 1 360)")
                        && script.contains("server did not finish startup within 360 seconds")
                        && script.contains("server logged a fatal startup error")
                        && script.contains("Exception in thread|A fatal error has been detected|Failed to start the minecraft server")
                        && script.contains("The Emerald Standard Banker integration self-test passed")
                        && script.contains("The Emerald Standard economy started"),
                "The longer smoke-only tick allowance must preserve outer timeout, fatal rejection and integration admission");
        require(script.contains("-Dthe_emerald_standard.integrationSmoke=true")
                        && script.contains("-Dthe_emerald_standard.smokeId=$SMOKE_ID")
                        && script.contains("trap cleanup EXIT")
                        && script.contains("jps -lv | grep -F -- \"-Dthe_emerald_standard.smokeId=$SMOKE_ID\"")
                        && script.contains("taskkill.exe //PID \"$java_pid\" //T //F"),
                "Smoke cleanup must retain exact unique-marker process ownership");
        String fixtures=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/BankerIntegrationSelfTest.java"));
        require(fixtures.contains("sequence.checks.removeFirst().run()") && fixtures.contains("sequence.running")
                        && fixtures.contains("net.minecraft.util.Util.getNanos() - server.getNextTickTime() > 250_000_000L")
                        && fixtures.contains("if (sequence.checks.isEmpty())") && fixtures.contains("SEQUENCES.remove(server)"),
                "Native suites must yield for real clock catch-up, resist nested ticks and clear their lifecycle state");
        String catalog = Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/AuthoredVillageStructures.java"));
        String projects = Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        require(fixtures.contains("checks.addAll(VillageProsperityManager.projectTemplateValidationSteps(level))")
                        && catalog.contains("steps.add(() -> results.add(validateCatalogDescriptor(descriptor)))")
                        && catalog.contains("steps.add(() -> validateCatalogResults(results))")
                        && catalog.contains("activeStructuralSnapshots.size() != 52")
                        && catalog.contains(".requireDistinct()")
                        && projects.contains("steps.addAll(AuthoredVillageStructures.catalogValidationSteps())")
                        && projects.contains("steps.add(VillageProsperityManager::validateModularProjectTemplates)")
                        && projects.contains("steps.add(VillageProsperityManager::validateModularEntranceApproachRecipes)"),
                "Live catalog admission must yield between all 52 masters and retain aggregate and legacy checks");
        String client = Files.readString(root.resolve("common/src/client/java/com/chedidandrew/emeraldstandard/client/ClientSmokeSupport.java"));
        require(client.contains("Math.max(1L, deadline - System.nanoTime())")
                        && client.contains("TimeUnit.SECONDS.toNanos(90)")
                        && client.contains("return onClient(minecraft, work, TimeUnit.SECONDS.toNanos(15))")
                        && client.contains("return result.get(timeoutNanos, TimeUnit.NANOSECONDS)"),
                "Client startup uses one bounded readiness deadline, without extending ordinary UI action deadlines");
        System.out.println("PASS bounded isolated dedicated-server smoke harness regression");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
