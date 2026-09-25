package com.chedidandrew.emeraldstandard.core;
import java.util.*;

/** Term-level study: production rates, regimes and resolution; book-value marking, no early exit. */
public final class LendingBalanceStudy {
    public static void main(String[] args) {
        System.out.println("version,seed,identity,term,years,cagr,max_drawdown");
        for(int version=0;version<=1;version++)for(int seed=0;seed<128;seed++)for(int identity=0;identity<4;identity++)
            for(int term:new int[]{30,90,180,365}) {
                var account=new UUID(identity,identity+123);long serial=0,opened=0;
                double total=1000,peak=1000,drawdown=0,stress=0,rate=0;
                var regime=EconomyEngine.initialRegime(seed);
                for(int day=1;day<=365*30;day++) {
                    if((day-1)%term==0){opened=day-1;stress=0;rate=EconomyEngine.villagerLoanAnnualYield(regime,term)+(version==0&&term==365?.02:0);}
                    regime=EconomyEngine.nextRegime(regime,seed,day);stress+=EconomyEngine.loanStressIncrement(regime);
                    total*=Math.pow(1+rate,1.0/365);
                    if(day%term==0) {
                        double recovered=0;
                        for(int n=0;n<8;n++) {
                            serial++;
                            int borrower=EconomyEngine.loanBorrower(seed,account,serial,opened);
                            recovered+=EconomyEngine.resolveLoanVersioned(seed,account,serial,opened,term,stress,version,borrower).recoveryRate()/8;
                        }
                        total*=recovered;
                    }
                    peak=Math.max(peak,total);drawdown=Math.max(drawdown,1-total/peak);
                }
                System.out.printf(Locale.ROOT,"%d,%d,%d,%d,30,%.9f,%.9f%n",version,seed,identity,term,Math.pow(total/1000,1.0/30)-1,drawdown);
            }
    }
}
