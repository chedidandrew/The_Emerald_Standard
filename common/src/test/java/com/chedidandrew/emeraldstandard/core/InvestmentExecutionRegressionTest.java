package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.UUID;

/** Financial boundary fixtures use only disposable economies, never a player world. */
public final class InvestmentExecutionRegressionTest {
    public static void main(String[] args) throws Exception {
        var service = new EconomyService();
        var root = Files.createTempDirectory("tes-trade-precision-");
        service.startWithSeed(root, 8181L, 0L, 0L);
        UUID id = UUID.randomUUID();
        require(service.deposit(id, 100), "cash fixture");
        var field = EconomyService.class.getDeclaredField("state");
        field.setAccessible(true);
        var state = (EconomyState) field.get(service);
        var account = state.account(id);
        state.prices.put("VILX", 100.0);
        account.shares.put("VILX", 0x1.0p60);
        account.shareCostBasisMicro.put("VILX", 1L);
        long cash = account.cashMicro;
        double held = account.shares.get("VILX");
        boolean bought = service.buy(id, "VILX", 1);
        boolean sold = service.sell(id, "VILX", 1.0);
        System.out.println("precision probe: buy=" + bought + ", sell=" + sold
                + ", shares before=" + held + ", after=" + service.snapshot().account(id).shares.get("VILX")
                + ", cash delta=" + (service.snapshot().account(id).cashMicro-cash));
        require(!bought && !sold, "unrepresentable trades must fail without charging cash or paying free proceeds");
        require(service.snapshot().account(id).cashMicro == cash
                && service.snapshot().account(id).shares.get("VILX") == held, "rejected trade changes nothing");
        account = ((EconomyState)field.get(service)).account(id);
        account.shares.put("VILX", 0.0000001);
        account.shareCostBasisMicro.put("VILX", 1L);
        require(service.sell(id,"VILX",0.0000001), "tiny but payable full holding is sellable");
        require(!service.snapshot().account(id).shares.containsKey("VILX"), "full sale clears exactly");
        account = ((EconomyState)field.get(service)).account(id);
        account.shares.put("VILX", 0.00000000001);
        long before = account.cashMicro;
        require(!service.sell(id,"VILX",0.00000000001), "sub-money-unit sale is rejected");
        require(service.snapshot().account(id).cashMicro == before
                && service.snapshot().account(id).shares.get("VILX")==0.00000000001, "unsellable dust retained");
        account = ((EconomyState)field.get(service)).account(id);
        account.shares.remove("VILX");
        for (int i=0;i<10;i++) {
            require(service.buy(id,"VILX",1), "ordinary purchase");
            double shares = service.snapshot().account(id).shares.get("VILX");
            require(service.sell(id,"VILX",shares*0.25), "ordinary quarter sale");
        }
        var snapshot=service.snapshot().account(id);
        require(snapshot.cashMicro < before, "spread prevents round-trip profit");
        var reloaded=new EconomyService(); reloaded.startWithSeed(root,8181L,0L,0L);
        require(reloaded.snapshot().account(id).cashMicro==snapshot.cashMicro
                && reloaded.snapshot().account(id).shares.equals(snapshot.shares), "precision decisions survive reload");
        System.out.println("PASS investment representability, tiny holdings, no-value rejection and restart");
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
