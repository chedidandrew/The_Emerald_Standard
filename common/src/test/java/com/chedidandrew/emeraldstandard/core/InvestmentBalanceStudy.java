package com.chedidandrew.emeraldstandard.core;

import java.util.*;

/** Research runner, not a pass/fail balance assertion. Calls the full 80-slot production path. */
public final class InvestmentBalanceStudy {
    public static void main(String[] args) {
        int seeds=Integer.parseInt(args[0]), years=Integer.parseInt(args[1]);
        System.out.println("seed,years,ticker,cagr,max_drawdown,largest_index_weight");
        for(int seed=0;seed<seeds;seed++) {
            var state=EconomyState.fresh(seed,0,0);
            var account=state.account(new UUID(0,1));
            var peaks=new HashMap<String,Double>();var drawdowns=new HashMap<String,Double>();
            for(var asset:EconomyEngine.ASSETS) {
                account.shares.put(asset.ticker(),1000/(state.prices.get(asset.ticker())*1.0025));
                peaks.put(asset.ticker(),1000.0);drawdowns.put(asset.ticker(),0.0);
            }
            for(int day=1;day<=years*365;day++) {
                state.advanceOneDay();
                for(var asset:EconomyEngine.ASSETS) {
                    String t=asset.ticker();double value=account.shares.get(t)*state.prices.get(t)*.9975;
                    peaks.put(t,Math.max(peaks.get(t),value));
                    drawdowns.put(t,Math.max(drawdowns.get(t),1-value/peaks.get(t)));
                }
                if(day%365==0&&(day/365==years||Set.of(1,3,5,10,30,100).contains(day/365))) {
                    double largest=StockIndex.weights(state).values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                    for(var asset:EconomyEngine.ASSETS) {
                        String t=asset.ticker();double value=account.shares.get(t)*state.prices.get(t)*.9975;
                        System.out.printf(Locale.ROOT,"%d,%d,%s,%.9f,%.9f,%.9f%n",seed,day/365,t,
                                Math.pow(value/1000,365.0/day)-1,drawdowns.get(t),largest);
                    }
                }
            }
        }
    }
}
