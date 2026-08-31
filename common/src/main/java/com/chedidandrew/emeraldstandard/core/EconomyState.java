package com.chedidandrew.emeraldstandard.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small line-based persistence layer. Values are stored in micro-emeralds where practical. */
public final class EconomyState {
    public static final long MICRO = 1_000_000L;
    public long seed, economicDay, lastWallClockMs, lastGameTicks;
    public EconomyEngine.Regime regime;
    public final Map<String, Double> prices = new LinkedHashMap<>();
    public final Map<UUID, Account> accounts = new HashMap<>();

    public static final class Account {
        public long cashMicro, savingsMicro, cdMicro, loanMicro;
        public long cdMaturityDay, loanMaturityDay;
        public final Map<String, Double> shares = new HashMap<>();
    }

    public static EconomyState fresh(long seed, long now, long gameTicks) {
        EconomyState s = new EconomyState(); s.seed=seed; s.economicDay=0; s.lastWallClockMs=now; s.lastGameTicks=gameTicks;
        s.regime=EconomyEngine.initialRegime(seed); for (var a: EconomyEngine.ASSETS) s.prices.put(a.ticker(), 100.0); return s;
    }

    public void advanceOneDay() {
        economicDay++; regime = EconomyEngine.nextRegime(regime, seed, economicDay);
        double market = EconomyEngine.marketReturn(regime, seed, economicDay);
        for (var a: EconomyEngine.ASSETS) prices.compute(a.ticker(), (k,v) -> Math.max(0.01, (v==null?100:v) * (1.0 + EconomyEngine.assetReturn(a, market, seed, economicDay))));
        for (Account a: accounts.values()) {
            a.savingsMicro += Math.round(EconomyEngine.compoundDaily(a.savingsMicro, EconomyEngine.savingsAnnualRate(regime)));
            if (a.cdMicro > 0) a.cdMicro += Math.round(EconomyEngine.compoundDaily(a.cdMicro, EconomyEngine.cdAnnualRate(regime)));
            if (a.loanMicro > 0) a.loanMicro += Math.round(EconomyEngine.compoundDaily(a.loanMicro, EconomyEngine.villagerLoanAnnualYield(regime)));
        }
    }

    public Account account(UUID id) { return accounts.computeIfAbsent(id, k -> new Account()); }
    public double netWorth(UUID id) { Account a=account(id); double n=(a.cashMicro+a.savingsMicro+a.cdMicro+a.loanMicro)/(double)MICRO; for(var e:a.shares.entrySet()) n+=e.getValue()*prices.getOrDefault(e.getKey(),0.0); return n; }

    public static EconomyState load(Path p, long fallbackSeed, long now, long ticks) throws IOException {
        if (!Files.exists(p)) return fresh(fallbackSeed, now, ticks);
        Properties q=new Properties(); try(var r=Files.newBufferedReader(p)){q.load(r);} EconomyState s=new EconomyState();
        s.seed=Long.parseLong(q.getProperty("seed")); s.economicDay=Long.parseLong(q.getProperty("day","0")); s.lastWallClockMs=Long.parseLong(q.getProperty("wall",Long.toString(now))); s.lastGameTicks=Long.parseLong(q.getProperty("ticks",Long.toString(ticks))); s.regime=EconomyEngine.Regime.valueOf(q.getProperty("regime","EXPANSION"));
        for(var a:EconomyEngine.ASSETS) s.prices.put(a.ticker(),Double.parseDouble(q.getProperty("price."+a.ticker(),"100")));
        for(String k:q.stringPropertyNames()) if(k.startsWith("acct.")) { String[] x=k.split("\\."); if(x.length<3) continue; UUID id=UUID.fromString(x[1]); Account a=s.account(id); String f=x[2], v=q.getProperty(k); if(f.equals("cash"))a.cashMicro=Long.parseLong(v); else if(f.equals("savings"))a.savingsMicro=Long.parseLong(v); else if(f.equals("cd"))a.cdMicro=Long.parseLong(v); else if(f.equals("cdmat"))a.cdMaturityDay=Long.parseLong(v); else if(f.equals("loan"))a.loanMicro=Long.parseLong(v); else if(f.equals("loanmat"))a.loanMaturityDay=Long.parseLong(v); else if(f.equals("share")&&x.length==4)a.shares.put(x[3],Double.parseDouble(v)); }
        return s;
    }

    public void save(Path p) throws IOException { Files.createDirectories(p.getParent()); Properties q=new Properties(); q.setProperty("seed",Long.toString(seed)); q.setProperty("day",Long.toString(economicDay)); q.setProperty("wall",Long.toString(lastWallClockMs)); q.setProperty("ticks",Long.toString(lastGameTicks)); q.setProperty("regime",regime.name()); for(var e:prices.entrySet())q.setProperty("price."+e.getKey(),Double.toString(e.getValue())); for(var e:accounts.entrySet()){String b="acct."+e.getKey()+"."; Account a=e.getValue(); q.setProperty(b+"cash",Long.toString(a.cashMicro)); q.setProperty(b+"savings",Long.toString(a.savingsMicro)); q.setProperty(b+"cd",Long.toString(a.cdMicro)); q.setProperty(b+"cdmat",Long.toString(a.cdMaturityDay)); q.setProperty(b+"loan",Long.toString(a.loanMicro)); q.setProperty(b+"loanmat",Long.toString(a.loanMaturityDay)); for(var h:a.shares.entrySet())q.setProperty(b+"share."+h.getKey(),Double.toString(h.getValue()));} Path tmp=p.resolveSibling(p.getFileName()+".tmp"); try(var w=Files.newBufferedWriter(tmp)){q.store(w,"The Emerald Standard 1.0.0");} try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);} }
}
