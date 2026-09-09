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
        System.out.println("PASS bounded isolated dedicated-server smoke harness regression");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
