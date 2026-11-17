package com.chedidandrew.emeraldstandard.minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/** Complements native saved-plan and integrated-world fixtures with wiring boundaries. */
public final class FloraAndChatShutdownWiringRegressionTest {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        String manager=Files.readString(root.resolve("common/src/minecraft/java/com/chedidandrew/emeraldstandard/minecraft/VillageProsperityManager.java"));
        require(manager.contains("!requiredSafetyFixture && role == PlacementRole.STRUCTURE")
                && manager.contains("VegetationCompatibility.forbidden(state)")
                && manager.contains("state.getLightEmission() > 0")
                && manager.contains("BlockTags.FLOWERS"),"Flora waiver escaped nonproductive, unlit structure decoration");
        require(manager.contains("placement.isTrail() || placement.isDecorativeFlora() || (placement.isCosmetic()")
                && manager.contains("placement.isTrail() || placement.isBestEffort()"),"Flora remains mandatory during handover/audit");
        String mixin=Files.readString(root.resolve("common/src/client/java/com/chedidandrew/emeraldstandard/minecraft/mixin/ClientChatRefreshMixin.java"));
        require(mixin.contains("@Mixin(LocalPlayer.class)") && mixin.contains("method=\"refreshChatAbilities\"")
                && mixin.contains("if (client.isSameThread()) return;")
                && mixin.contains("client.execute(() ->") && mixin.contains("client.player == player")
                && mixin.contains("info.cancel();") && !mixin.contains(".join(") && !mixin.contains(".get("),
                "Chat refresh must hand off asynchronously and ignore disconnected players");
        String config=Files.readString(root.resolve("common/src/main/resources/emerald-standard.mixins.json"));
        require(config.indexOf("ClientChatRefreshMixin")>config.indexOf("\"client\""),"Client-only mixin registered on dedicated server");
        String smoke=Files.readString(root.resolve("common/src/client/java/com/chedidandrew/emeraldstandard/client/ClientSmokeSupport.java"));
        require(smoke.contains("clientShutdownOnly")&&smoke.contains("ChatRefreshClientChecks.verify"),"Native copied-world fixture not routed");
        System.out.println("PASS flora authority and asynchronous client shutdown wiring");
    }
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
