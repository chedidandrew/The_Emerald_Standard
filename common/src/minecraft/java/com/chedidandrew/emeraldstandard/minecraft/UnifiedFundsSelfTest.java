package com.chedidandrew.emeraldstandard.minecraft;

import com.chedidandrew.emeraldstandard.core.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.*;

/** Real inventory/menu checks in the opt-in disposable dedicated-server smoke world. */
final class UnifiedFundsSelfTest {
    static void run(ServerLevel level) {
        try {
            verifyCommodityInventory(level);
            verifyDashboardEdges(level);
            verifyConfirmationAndQuoteEdges(level);
            Path root = Files.createTempDirectory("tes-unified-funds-");
            EconomyService economy = new EconomyService();
            economy.start(root, 91L, 0L);
            ServerPlayer player = player(level, UUID.randomUUID());
            UUID id = player.getUUID();
            BankInventory.insertItems(player, Items.EMERALD, 320);
            var village = economy.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld", player.blockPosition().asLong(), 0L, 0L, 18, 16, 0, false, List.of()));
            var villageId = village.village().villageId;
            require(BankingOperations.supportVillage(player, economy, villageId, 100,
                    EconomyState.ProsperityFundType.DIRECT_GRANT, EconomyState.DonationPurpose.GENERAL)
                    == BankingOperations.VILLAGE_FUNDED, "320 inventory / zero cash can fund 100");
            require(count(player) == 220 && cash(economy, id) == 0, "gift conserves items");
            require(economy.villageFundSnapshot(villageId).lifetimeReceivedMicro() == 100 * EconomyState.MICRO,
                    "village receives gift once");
            require(BankingOperations.supportVillage(player, economy, villageId, 100,
                    EconomyState.ProsperityFundType.DIRECT_GRANT, EconomyState.DonationPurpose.GENERAL)
                    == BankingOperations.BUSY, "double-click cooldown");
            BankingOperations.forgetPlayer(id);
            require(BankingOperations.buy(player, economy, "VILX", 10) == BankingOperations.BOUGHT, "inventory investment");
            BankingOperations.forgetPlayer(id);
            require(BankingOperations.moveSavings(player, economy, 10, true) == BankingOperations.SAVED, "inventory savings");
            BankingOperations.forgetPlayer(id);
            require(BankingOperations.openCd(player, economy, 10, 30) == BankingOperations.CD_OPENED, "inventory CD");
            BankingOperations.forgetPlayer(id);
            require(BankingOperations.fundLending(player, economy, 10, 30) == BankingOperations.LENDING_FUNDED, "inventory loan");
            require(count(player) == 180 && cash(economy, id) == 0, "four spending routes consume exactly 40");
            BankingOperations.forgetPlayer(id);
            require(economy.creditMicro(id, EconomyState.MICRO / 4), "fraction fixture");
            require(BankingOperations.moveSavings(player, economy, 1, true) == BankingOperations.SAVED, "fraction spend");
            require(count(player) == 179 && cash(economy, id) == EconomyState.MICRO / 4, "fractional change stays cash");
            ItemStack named = new ItemStack(Items.EMERALD, 4);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep me"));
            player.getInventory().setItem(10, named);
            require(count(player) == 179 && !BankInventory.isOrdinaryItem(named, Items.EMERALD), "custom items protected");
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.EMERALD, 3));
            require(count(player) == 182, "offhand emeralds included");

            // Typed stale amounts cannot be partially spent. Closed menu packets have no effect.
            BankerMenu menu = new BankerMenu(99, player.getInventory(), economy, player);
            player.containerMenu = menu;
            for (int asset=0;asset<EconomyEngine.ASSETS.size();asset++)
                require(menu.assetPrice(asset)>0, "every listing receives its real server quote, including commodities");
            long previewCash = cash(economy,id);
            int previewItems = count(player);
            require(menu.clickMenuButton(player, BankerMenu.BUTTON_TOWN_REPORT), "open read-only report");
            menu.broadcastChanges();
            require(!menu.briefingPages(BankerMenu.BUTTON_TOWN_REPORT).isEmpty(), "Town report delivered");
            ConstructionDiagnostics.record("report-test","building",1,100,50,"fixture");
            ConstructionDiagnostics.record("report-test","building",1,100,90,"fixture");
            require(ConstructionDiagnostics.recent("report-test",90).tick()==90
                    && ConstructionDiagnostics.recent("report-test",49)==null
                    && ConstructionDiagnostics.recent("report-test",1291)==null,"recent diagnostics refresh and expire");
            for (var input : net.minecraft.world.inventory.ContainerInput.values()) {
                menu.clicked(0,0,input,player);menu.clicked(-999,0,input,player);
                require(menu.quickMoveStack(player,0).isEmpty(), "briefing cannot shift-click");
            }
            require(menu.getCarried().isEmpty() && !menu.slots.getFirst().mayPickup(player)
                    && !menu.slots.getFirst().mayPlace(new ItemStack(Items.EMERALD)), "document not obtainable");
            require(previewCash==cash(economy,id)&&previewItems==count(player), "reports and clicks are read-only");
            menu.clickMenuButton(player,BankerMenu.BUTTON_FUND_PREVIEW);
            try {
                var f=BankerMenu.class.getDeclaredField("lastBriefingTick");f.setAccessible(true);f.setLong(menu,Long.MIN_VALUE);
            } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
            require(menu.briefingPages(BankerMenu.BUTTON_TOWN_REPORT).isEmpty(), "new request clears stale report");
            menu.broadcastChanges();
            require(!menu.briefingPages(BankerMenu.BUTTON_FUND_PREVIEW).isEmpty()
                    && menu.briefingPages(BankerMenu.BUTTON_TOWN_REPORT).isEmpty(), "rapid switches eventually publish latest mode");
            menu.clickMenuButton(player,BankerMenu.BUTTON_REPORT_CLOSE);
            menu.clickMenuButton(player, BankerAmountSelection.encodeFundButtonId(100));
            require(menu.donationDraft() == 100 && menu.spendingPower() == 182.25,
                    "Fund Apply uses combined funds");
            menu.clickMenuButton(player, BankerAmountSelection.encodeFundButtonId(500));
            require(menu.donationDraft() == 100 && menu.statusCode() == BankingOperations.INSUFFICIENT,
                    "unaffordable typed gift does not silently shrink");
            BankInventory.removeItems(player, Items.EMERALD, 100);
            menu.clickMenuButton(player, BankerMenu.ACTION_SUPPORT_VILLAGE);
            require(menu.donationDraft() == 100 && menu.statusCode() == BankingOperations.INSUFFICIENT,
                    "stale gift preserved but rejected");
            BankInventory.insertItems(player, Items.EMERALD, 100);
            BankingOperations.forgetPlayer(id);
            menu.clickMenuButton(player, BankerMenu.ACTION_SUPPORT_VILLAGE);
            require(menu.statusCode() == BankingOperations.CONFIRM_REQUIRED, "gift requires confirmation");
            menu.clickMenuButton(player, BankerMenu.ACTION_SUPPORT_VILLAGE);
            require(menu.statusCode() == BankingOperations.VILLAGE_FUNDED && menu.donationDraft() == 0
                            && count(player) == 82 && cash(economy, id) == EconomyState.MICRO / 4,
                    "confirmed GUI gift spends exactly once");
            try {
                var f=BankerMenu.class.getDeclaredField("lastBriefingTick");f.setAccessible(true);f.setLong(menu,Long.MIN_VALUE);
            } catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
            menu.broadcastChanges();
            String accepted = menu.briefingPages(BankerMenu.BUTTON_FUND_RECEIPT).stream()
                    .map(Component::getString).collect(java.util.stream.Collectors.joining(" "));
            require(accepted.contains("100.00 E") && accepted.contains("inventory emeralds 100")
                    && accepted.contains("remaining Bank Cash 0.25 E"), "receipt uses actual accepted payment");
            menu.clickMenuButton(player, BankerMenu.ACTION_SUPPORT_VILLAGE);
            require(count(player) == 82, "replayed gift button cannot repeat a cleared draft");
            menu.clickMenuButton(player, BankerAmountSelection.encodeButtonId(500));
            long before = cash(economy, id); int itemsBefore = count(player);
            BankingOperations.forgetPlayer(id);
            menu.clickMenuButton(player, BankerMenu.ACTION_BUY);
            require(cash(economy, id) == before && count(player) == itemsBefore, "stale exact amount rejected");
            player.containerMenu = player.inventoryMenu;
            require(!menu.clickMenuButton(player, BankerMenu.ACTION_DEPOSIT), "closed menu packet rejected");

            // Committed deposit receipt survives player NBT reload; added inventory is not mistaken for duplication.
            var pending = economy.prepareInventoryCredit(id, EconomyState.InventoryTransactionKind.DEPOSIT,
                    "emerald", 5, count(player), 5 * EconomyState.MICRO, true);
            require(BankInventory.removeItems(player, Items.EMERALD, 5), "remove fixture");
            receipt(player, pending.transactionId, -5);
            require(economy.commitPreparedInventoryCredit(id, pending.transactionId), "commit fixture");
            BankInventory.insertItems(player, Items.EMERALD, 7);
            require(BankTransactionCoordinator.checkpointPlayer(player), "checkpoint inventory and receipt");
            var saved = NbtIo.readCompressed(level.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR)
                    .resolve(id + ".dat"), NbtAccounter.unlimitedHeap());
            ServerPlayer restored = player(level, id);
            restored.load(TagValueInput.create(new ProblemReporter.Collector(), level.registryAccess(), saved));
            int restoredBefore = count(restored);
            EconomyService reloaded = new EconomyService(); reloaded.start(root, 91L, 0L);
            require(BankTransactionCoordinator.reconcile(restored, reloaded).recovered(), "recovery after restart");
            require(count(restored) == restoredBefore, "receipt ignores unrelated later inventory changes");
            require(!BankTransactionCoordinator.reconcile(restored, reloaded).found(), "recovery replay no-op");

            // PREPARED receipt restores once, regardless of current item count.
            pending = reloaded.prepareInventoryCredit(id, EconomyState.InventoryTransactionKind.DEPOSIT,
                    "emerald", 5, count(restored), 5 * EconomyState.MICRO, true);
            BankInventory.removeItems(restored, Items.EMERALD, 5); receipt(restored, pending.transactionId, -5);
            before = cash(reloaded, id); itemsBefore = count(restored);
            require(BankTransactionCoordinator.reconcile(restored, reloaded).rolledBack(), "prepared rollback");
            require(count(restored) == itemsBefore + 5 && cash(reloaded, id) == before, "rollback conserves value");
            require(!BankTransactionCoordinator.reconcile(restored, reloaded).found(), "rollback no duplicate");

            // Creative Inventory.add can claim success while discarding the remainder. Observe actual delivery.
            restored.getInventory().clearContent();
            for (int slot = 0; slot < 36; slot++) restored.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            restored.getAbilities().instabuild = true;
            before = cash(reloaded, id);
            require(BankTransactionCoordinator.withdrawInventory(restored, reloaded, 2) == 0, "full creative inventory");
            require(cash(reloaded, id) == before, "full inventory refunds all");
            restored.getInventory().setItem(0, new ItemStack(Items.EMERALD, 63));
            before = cash(reloaded, id);
            require(BankTransactionCoordinator.withdrawInventory(restored, reloaded, 2) == 1, "partial creative delivery");
            require(count(restored) == 64 && cash(reloaded, id) == before - EconomyState.MICRO,
                    "charge only actually delivered emerald");

            CompoundTag mismatched = saved.copy();
            mismatched.remove("Tags");
            require(!BankTransactionCoordinator.inventoryMatches(saved, mismatched), "receipt required in readback");
            BankingOperations.forgetPlayer(id);
            System.out.println("PASS unified funds live inventory, all spending routes, stale menu, restart, replay and full inventory");
        } catch (Exception exception) {
            throw new IllegalStateException("Unified funds self-test failed", exception);
        }
    }
    private static void verifyConfirmationAndQuoteEdges(ServerLevel level) throws Exception {
        var economy=new EconomyService(); economy.start(Files.createTempDirectory("tes-confirmation-"),92L,0L);
        var player=player(level,UUID.randomUUID()); var id=player.getUUID();
        require(economy.deposit(id,500),"confirmation cash fixture");
        long first=economy.openCdPosition(id,10,30),second=economy.openCdPosition(id,20,30);
        BankerMenu menu=new BankerMenu(97,player.getInventory(),economy,player); player.containerMenu=menu;
        require(menu.selectedCdPositionId()==first,"first CD selected");
        menu.clickMenuButton(player,BankerMenu.ACTION_CLOSE_CD);
        require(menu.confirmationAction()==BankerMenu.ACTION_CLOSE_CD,"CD confirmation opened");
        require(economy.closeCd(id,first).closed(),"original CD removed outside this menu");
        menu.clickMenuButton(player,BankerMenu.ACTION_CLOSE_CD);
        require(economy.portfolioSnapshot(id).account().cdPositions.containsKey(second)
                && menu.statusCode()==BankingOperations.CONFIRM_REQUIRED,"stale confirmation cannot close replacement CD");
        menu.clickMenuButton(player,BankerMenu.ACTION_CLOSE_CD);
        require(!economy.portfolioSnapshot(id).account().cdPositions.containsKey(second),"newly confirmed CD closes");
        require(economy.buy(id,"VILX",10),"sell fixture");
        menu.clickMenuButton(player,BankerMenu.ACTION_SELL_ALL);
        require(economy.buy(id,"VILX",10),"holding changed outside confirmation");
        menu.clickMenuButton(player,BankerMenu.ACTION_SELL_ALL);
        require(economy.portfolioSnapshot(id).account().shares.get("VILX")>0
                && menu.statusCode()==BankingOperations.CONFIRM_REQUIRED,"sell confirmation binds actual shares");
        BankingOperations.forgetPlayer(id); menu.clickMenuButton(player,BankerMenu.ACTION_SELL_ALL);
        require(!economy.portfolioSnapshot(id).account().shares.containsKey("VILX"),"fresh sell confirmation works");
        // The All preset is capped to current spendable funds; changed funds require review again.
        menu.clickMenuButton(player,BankerMenu.BUTTON_AMOUNT_BASE+5);
        menu.clickMenuButton(player,BankerMenu.ACTION_FUND_LENDING);
        require(economy.withdraw(id,1)==1,"available money changed");
        menu.clickMenuButton(player,BankerMenu.ACTION_FUND_LENDING);
        require(economy.portfolioSnapshot(id).account().loanPositions.isEmpty()
                && menu.statusCode()==BankingOperations.CONFIRM_REQUIRED,"All lending amount cannot change under confirmation");
        var stateField=EconomyService.class.getDeclaredField("state"); stateField.setAccessible(true);
        var state=(EconomyState)stateField.get(economy);
        state.prices.put("VILX",0.000001);
        menu.clickMenuButton(player,BankerMenu.BUTTON_HISTORY_RANGE);
        require(menu.selectedAssetPrice()==0.000001 && menu.assetPrice(0)==0.000001,"sub-cent server quotes preserved");
        state.prices.put("VILX",50_000_000.0); menu.clickMenuButton(player,BankerMenu.BUTTON_HISTORY_RANGE);
        require(menu.selectedAssetPrice()==50_000_000.0,"large server quote is not capped");
        state.prices.put("VILX",100.0);
        state.account(id).shares.put("VILX",0.0000001); menu.clickMenuButton(player,BankerMenu.BUTTON_HISTORY_RANGE);
        require(menu.ownsAsset(0) && menu.selectedShares()==0,"native owned dust fixture");
        BankingOperations.forgetPlayer(id);
        require(BankingOperations.sellFraction(player,economy,"VILX",1)==BankingOperations.SOLD,"payable dust sells");
        state=(EconomyState)stateField.get(economy);
        state.account(id).shares.put("VILX",0.00000000001);
        BankingOperations.forgetPlayer(id);
        require(BankingOperations.sellFraction(player,economy,"VILX",1)==BankingOperations.NUMERIC_LIMIT
                && economy.portfolioSnapshot(id).account().shares.get("VILX")>0,"zero-proceeds dust is retained");
        state=(EconomyState)stateField.get(economy);
        state.account(id).shares.put("VILX",0x1.0p60);
        long cashBefore=state.account(id).cashMicro;
        require(economy.withdraw(id,cashBefore/EconomyState.MICRO)>0,"remove whole cash to require top-up");
        BankInventory.insertItems(player,Items.EMERALD,1);
        int items=count(player);long cashBeforeReject=cash(economy,id);
        BankingOperations.forgetPlayer(id);
        require(BankingOperations.buy(player,economy,"VILX",1)==BankingOperations.NUMERIC_LIMIT
                && count(player)==items && cash(economy,id)==cashBeforeReject,"precision failure precedes inventory top-up");
        BankingOperations.forgetPlayer(id);player.containerMenu=player.inventoryMenu;
        System.out.println("PASS exact confirmation targets, quote range, sellable dust and numeric pre-top-up rejection");
    }

    private static void verifyDashboardEdges(ServerLevel level) throws Exception {
        var activeConfig=EmeraldConfig.current();
        var configField=EmeraldConfig.class.getDeclaredField("current");configField.setAccessible(true);
        var properties=new java.util.Properties();properties.putAll(activeConfig.values());
        properties.setProperty("village_prosperity.targeted_donations_enabled","false");
        properties.setProperty("village_prosperity.donations_enabled","true");
        properties.setProperty("village_prosperity.simulation_enabled","true");
        properties.setProperty("transactions.cooldown_ticks","0");
        var config=EmeraldConfig.parse(properties);
        var economy=new EconomyService();economy.start(Files.createTempDirectory("tes-dashboard-edge-"),901,0);
        var player=player(level,UUID.randomUUID());var id=player.getUUID();
        try {
            configField.set(null,config);
            BankInventory.insertItems(player,Items.EMERALD,20);
            var snapshot=economy.observeVillage(new EconomyService.VillageObservation(
                    "minecraft:overworld",player.blockPosition().asLong(),0L,0L,0,4,0,false,List.of()));
            var villageId=snapshot.village().villageId;
            require(snapshot.village().lifecycle==VillageProsperityEngine.Lifecycle.ABANDONED,"abandoned fixture");
            for(var purpose:List.of(EconomyState.DonationPurpose.GENERAL,EconomyState.DonationPurpose.RESTORATION))
                require(FundContributionChecks.assess(config,snapshot,EconomyState.ProsperityFundType.DIRECT_GRANT,purpose).allowed(),
                        "mandatory restoration is allowed with targeted donations off");
            String preview=BankerBriefings.fund(economy,villageId,10,EconomyState.ProsperityFundType.DIRECT_GRANT,
                    EconomyState.DonationPurpose.GENERAL,SpendingFunds.plan(0,20,10)).toString();
            require(preview.contains("restoration")&&!preview.contains("UNAVAILABLE"),"preview agrees with restoration payment");
            require(BankingOperations.supportVillage(player,economy,villageId,10,
                    EconomyState.ProsperityFundType.DIRECT_GRANT,EconomyState.DonationPurpose.RESTORATION)
                    ==BankingOperations.VILLAGE_FUNDED,"restoration actually pays");
            require(count(player)==10 && economy.villageFundSnapshot(villageId).emergencyReserveMicro()==0
                    && economy.villageFundSnapshot(villageId).lifetimeReceivedMicro()==10*EconomyState.MICRO,
                    "restoration conserves inventory and bypasses ordinary reserve");

            var v=snapshot.village();v.lifecycle=VillageProsperityEngine.Lifecycle.ACTIVE;
            require(!FundContributionChecks.assess(config,snapshot,EconomyState.ProsperityFundType.DIRECT_GRANT,
                    EconomyState.DonationPurpose.FOOD).allowed(),"ordinary targeted grant stays disabled");
            require(FundContributionChecks.assess(config,snapshot,EconomyState.ProsperityFundType.DIRECT_GRANT,
                    EconomyState.DonationPurpose.GENERAL).allowed(),"general grant stays enabled");
            int items=count(player);long cash=cash(economy,id);
            require(BankingOperations.supportVillage(player,economy,villageId,5,
                    EconomyState.ProsperityFundType.PROJECT_SPONSORSHIP,EconomyState.DonationPurpose.GENERAL)
                    ==BankingOperations.NOT_READY,"missing sponsorship project rejected before top-up");
            require(count(player)==items&&cash(economy,id)==cash,"rejected sponsor does not move emeralds");

            properties.setProperty("village_prosperity.donations_enabled","false");
            configField.set(null,EmeraldConfig.parse(properties));
            require(BankingOperations.supportVillage(player,economy,villageId,5,
                    EconomyState.ProsperityFundType.DIRECT_GRANT,EconomyState.DonationPurpose.GENERAL)
                    ==BankingOperations.UNSUPPORTED,"disabled donations override restoration");
            require(count(player)==items&&cash(economy,id)==cash,"disabled donation does not move emeralds");
            require(BankerBriefings.fund(economy,villageId,5,EconomyState.ProsperityFundType.DIRECT_GRANT,
                    EconomyState.DonationPurpose.GENERAL,SpendingFunds.plan(cash,items,5)).toString().contains("UNAVAILABLE"),
                    "disabled preview matches payment");
            System.out.println("PASS dashboard restoration settings, shared preview eligibility and pre-top-up rejection");
        } finally {configField.set(null,activeConfig);BankingOperations.forgetPlayer(id);}
    }

    private static void verifyCommodityInventory(ServerLevel level) throws Exception {
        Path root = Files.createTempDirectory("tes-commodity-inventory-");
        EconomyService economy = new EconomyService(); economy.start(root, 91, 0);
        ServerPlayer player = player(level, UUID.randomUUID());
        UUID id = player.getUUID();
        BankInventory.insertItems(player, Items.EMERALD, 80);
        BankInventory.insertItems(player, Items.GOLD_INGOT, 7);
        BankerMenu menu = new BankerMenu(98, player.getInventory(), economy, player);
        player.containerMenu = menu;
        for (var asset : EconomyEngine.ASSETS) if (asset.isCommodity()) {
            require(menu.clickMenuButton(player, BankerMenu.BUTTON_ASSET_BASE + EconomyEngine.ASSETS.indexOf(asset))
                    && menu.selectedAsset().ticker().equals(asset.ticker()), "commodity GUI selection");
            for (int resource = 0; resource < BankerMenu.RESOURCE_NAMES.size(); resource++) {
                require(menu.clickMenuButton(player, BankerMenu.BUTTON_RESOURCE_BASE + resource)
                        && menu.selectedAsset().ticker().equals(asset.ticker()), "Trade selector changed investment");
            }
            int before = count(player);
            long beforeCash = cash(economy, id);
            BankingOperations.forgetPlayer(id);
            require(BankingOperations.buy(player, economy, asset.ticker(), 10) == BankingOperations.BOUGHT,
                    "inventory-funded commodity " + asset.ticker());
            require(before - count(player) == 10 && cash(economy, id) == beforeCash, "commodity emerald conservation");
            int after = count(player);
            require(BankingOperations.buy(player, economy, asset.ticker(), 10) == BankingOperations.BUSY
                    && count(player) == after, "commodity click replay");
            require(BankInventory.countItems(player, Items.GOLD_INGOT) == 7, "commodity touched physical resources");
        }
        require(count(player) == 0, "commodity total inventory debit");
        var restored = new EconomyService(); restored.start(root, 91, 0);
        for (var asset : EconomyEngine.ASSETS) if (asset.isCommodity()) {
            double units = restored.portfolioSnapshot(id).account().shares.get(asset.ticker());
            require(restored.sell(id, asset.ticker(), units), "commodity sale after restart");
            require(!restored.sell(id, asset.ticker(), units), "commodity repeated sale");
        }
        require(cash(restored, id) > 79 * EconomyState.MICRO && cash(restored, id) < 80 * EconomyState.MICRO,
                "commodity round-trip spread/conservation");
        require(count(player) == 0 && BankInventory.countItems(player, Items.GOLD_INGOT) == 7,
                "commodity sale minted physical items");
        BankingOperations.forgetPlayer(id);
        System.out.println("PASS all eight commodity inventory purchases, cooldown, physical-item isolation, restart and sales");
    }

    private static ServerPlayer player(ServerLevel level, UUID id) {
        return new ServerPlayer(level.getServer(), level, new GameProfile(id, "FundsFixture"),
                ClientInformation.createDefault()) {
            @Override public void sendSystemMessage(Component message) { }
        };
    }
    private static void receipt(ServerPlayer player, UUID id, int delta) {
        for (String tag : java.util.Set.copyOf(player.entityTags()))
            if (tag.startsWith(InventoryReceipt.PREFIX)) player.removeTag(tag);
        player.addTag(InventoryReceipt.encode(id, delta));
    }
    private static long cash(EconomyService economy, UUID id) {
        return economy.portfolioSnapshot(id).account().cashMicro;
    }
    private static int count(ServerPlayer player) { return BankInventory.countItems(player, Items.EMERALD); }
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
