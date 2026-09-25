package com.chedidandrew.emeraldstandard.core;

import java.nio.file.Files;
import java.util.*;
import static com.chedidandrew.emeraldstandard.core.RegressionTestSupport.*;

public final class InvestmentBalanceRegressionTest {
    public static void main(String[] args) throws Exception {
        var s=EconomyState.fresh(81,0,0);
        var a=s.account(PLAYER);a.cashMicro=123000000;a.shares.put("VILX",3.0);
        var loan=new EconomyState.LoanPosition();loan.positionId=1;loan.serial=1;
        loan.principalMicro=loan.valueMicro=100000000;loan.annualRate=.15;loan.maturityDay=365;
        a.loanPositions.put(1L,loan);a.loanSerial=1;EconomyState.syncLegacyProductViews(a);
        var dir=Files.createTempDirectory("tes-investment-migration-");
        try {
            var path=dir.resolve("world.properties");s.save(path);
            var props=readProperties(path);props.setProperty("format","41");
            props.keySet().removeIf(k->k.toString().startsWith("company.float.")
                    ||k.toString().equals("index.stock.rebalanced_day")
                    ||k.toString().endsWith(".risk_version")||k.toString().endsWith(".borrower"));
            refreshChecksum(props);writeProperties(path,props);
            var restored=EconomyState.load(path,0,0,0);
            require(restored.prices.equals(s.prices)&&restored.priceHistory.equals(s.priceHistory),"migration changed prices/history");
            require(restored.stockIndexShares.equals(s.stockIndexShares),"migration reweighted saved index");
            require(restored.account(PLAYER).cashMicro==a.cashMicro&&restored.account(PLAYER).shares.equals(a.shares),"migration changed holdings");
            var old=restored.account(PLAYER).loanPositions.get(1L);
            require(old.riskVersion==0&&old.annualRate==.15,"legacy contract changed");
            var cloned=restored.copy();for(int day=0;day<365;day++){restored.advanceOneDay();cloned.advanceOneDay();}
            var expected=EconomyEngine.resolveLoan(81,PLAYER,1,0,365,old.stress);
            require(old.outcome==expected.outcome()&&old.recoveryRate==expected.recoveryRate(),"legacy settlement changed");
            require(restored.prices.equals(cloned.prices),"copied replay diverged");
            restored.save(path);var again=EconomyState.load(path,0,0,0);
            require(again.companySharesOutstanding.equals(restored.companySharesOutstanding),"company float not durable");
            require(again.stockIndexRebalancedDay==365,"rebalance date not durable");
            var modern=new EconomyState.LoanPosition();modern.positionId=2;modern.serial=2;modern.riskVersion=1;modern.borrower=17;
            modern.openDay=365;modern.maturityDay=730;modern.annualRate=.13;
            modern.principalMicro=modern.valueMicro=100000000;again.account(PLAYER).loanPositions.put(2L,modern);
            again.save(path);var modernLoaded=EconomyState.load(path,0,0,0).account(PLAYER).loanPositions.get(2L);
            require(modernLoaded.riskVersion==1&&modernLoaded.borrower==17,"new contract identity not durable");
            props=readProperties(path);props.remove("account."+PLAYER+".loanpos.2.risk_version");
            var broken=dir.resolve("broken.properties");refreshChecksum(props);writeProperties(broken,props);
            try {EconomyState.load(broken,0,0,0);throw new AssertionError("missing contract silently became legacy");}
            catch(java.io.IOException expectedFailure) { }
        } finally {deleteTree(dir);}
        var floats=new LinkedHashMap<>(s.companySharesOutstanding);
        s.prices.put("AURM",1000000.0);StockIndex.reprice(s);
        double before=s.prices.get("VILX");s.economicDay=365;StockIndex.review(s);StockIndex.reprice(s);
        require(Math.abs(s.prices.get("VILX")/before-1)<1e-12,"rebalance manufactured value");
        require(StockIndex.weights(s).values().stream().allMatch(w->w<=.200000001),"constituent cap");
        require(floats.equals(s.companySharesOutstanding),"rebalance changed company size");
        var basket=new LinkedHashMap<>(s.stockIndexShares);StockIndex.review(s);
        require(basket.equals(s.stockIndexShares),"rebalance repeated");
        for(int seed=0;seed<100;seed++)for(int term:new int[]{30,90,180,365}) {
            var legacy=EconomyEngine.resolveLoan(seed,PLAYER,3,45,term,term*.1);
            require(legacy.equals(EconomyEngine.resolveLoanVersioned(seed,PLAYER,3,45,term,term*.1,0,0)),"legacy risk changed");
            double p1=EconomyEngine.estimatedLoanDefaultProbability(EconomyEngine.Regime.STAGNATION,180);
            double p2=EconomyEngine.estimatedLoanDefaultProbability(EconomyEngine.Regime.STAGNATION,365);
            require(p2>p1,"full-year risk exposure capped at 180 days");
        }
        int defaults=0,shared=0,separate=0;
        for(int seed=0;seed<5000;seed++) {
            boolean first=EconomyEngine.resolveLoanVersioned(seed,PLAYER,1,0,365,36.5,1,0).recoveryRate()<1;
            boolean same=EconomyEngine.resolveLoanVersioned(seed,PLAYER,2,0,365,36.5,1,0).recoveryRate()<1;
            boolean other=EconomyEngine.resolveLoanVersioned(seed,PLAYER,2,0,365,36.5,1,24).recoveryRate()<1;
            if(first) {defaults++;if(same)shared++;if(other)separate++;}
        }
        require(defaults>400&&defaults<700,"default marginal calibration");
        require(shared>separate*2,"shared borrower risks are independent");
        System.out.println("PASS investment balance: legacy/new contracts, migration, missing-field rejection, replay, full-term/correlated risk and value-neutral capped index");
    }
}
