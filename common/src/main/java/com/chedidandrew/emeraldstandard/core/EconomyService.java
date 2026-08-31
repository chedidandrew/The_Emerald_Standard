package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/** Loader-neutral application service. One Minecraft day equals one economic day. Offline wall time also advances the economy. */
public final class EconomyService {
    private EconomyState state; private Path path;
    public synchronized void start(Path worldDataDir, long worldSeed, long gameTicks) throws IOException { path=worldDataDir.resolve("the_emerald_standard.properties"); long now=System.currentTimeMillis(); state=EconomyState.load(path, worldSeed ^ 0x544553L, now, gameTicks); catchUp(now,gameTicks); state.save(path); }
    public synchronized void tick(long gameTicks) { if(state==null)return; long gameDays=Math.max(0,(gameTicks-state.lastGameTicks)/24000L); if(gameDays>0){advance(gameDays); state.lastGameTicks += gameDays*24000L; saveQuiet();} }
    public synchronized void catchUp(long now,long gameTicks){ if(state==null)return; long offlineDays=Math.max(0,(now-state.lastWallClockMs)/1_200_000L); long gameDays=Math.max(0,(gameTicks-state.lastGameTicks)/24000L); advance(Math.max(offlineDays,gameDays)); state.lastWallClockMs=now; state.lastGameTicks=gameTicks; }
    private void advance(long days){ for(long i=0;i<Math.min(days,200_000L);i++)state.advanceOneDay(); }
    public synchronized EconomyState state(){return state;}
    public synchronized void saveQuiet(){try{if(state!=null){state.lastWallClockMs=System.currentTimeMillis();state.save(path);}}catch(IOException ignored){}}
    public synchronized boolean deposit(UUID id,long emeralds){if(emeralds<=0)return false;state.account(id).cashMicro+=emeralds*EconomyState.MICRO;saveQuiet();return true;}
    public synchronized long withdraw(UUID id,long emeralds){var a=state.account(id);long m=emeralds*EconomyState.MICRO;if(emeralds<=0||a.cashMicro<m)return 0;a.cashMicro-=m;saveQuiet();return emeralds;}
    public synchronized boolean moveSavings(UUID id,long emeralds,boolean into){var a=state.account(id);long m=emeralds*EconomyState.MICRO;if(emeralds<=0)return false;if(into&&a.cashMicro>=m){a.cashMicro-=m;a.savingsMicro+=m;}else if(!into&&a.savingsMicro>=m){a.savingsMicro-=m;a.cashMicro+=m;}else return false;saveQuiet();return true;}
    public synchronized boolean openCd(UUID id,long emeralds,int term){var a=state.account(id);long m=emeralds*EconomyState.MICRO;if(emeralds<=0||a.cashMicro<m||a.cdMicro>0)return false;a.cashMicro-=m;a.cdMicro=m;a.cdMaturityDay=state.economicDay+term;saveQuiet();return true;}
    public synchronized boolean closeCd(UUID id){var a=state.account(id);if(a.cdMicro<=0)return false;long payout=a.cdMicro;if(state.economicDay<a.cdMaturityDay)payout=Math.round(payout*0.98);a.cashMicro+=payout;a.cdMicro=0;a.cdMaturityDay=0;saveQuiet();return true;}
    public synchronized boolean fundLoan(UUID id,long emeralds,int term){var a=state.account(id);long m=emeralds*EconomyState.MICRO;if(emeralds<=0||a.cashMicro<m||a.loanMicro>0)return false;a.cashMicro-=m;a.loanMicro=m;a.loanMaturityDay=state.economicDay+term;saveQuiet();return true;}
    public synchronized boolean collectLoan(UUID id){var a=state.account(id);if(a.loanMicro<=0||state.economicDay<a.loanMaturityDay)return false;a.cashMicro+=a.loanMicro;a.loanMicro=0;a.loanMaturityDay=0;saveQuiet();return true;}
    public synchronized boolean buy(UUID id,String ticker,long emeralds){var a=state.account(id);ticker=ticker.toUpperCase(Locale.ROOT);Double p=state.prices.get(ticker);long m=emeralds*EconomyState.MICRO;if(p==null||emeralds<=0||a.cashMicro<m)return false;a.cashMicro-=m;a.shares.merge(ticker,emeralds/p,Double::sum);saveQuiet();return true;}
    public synchronized boolean sell(UUID id,String ticker,double shares){var a=state.account(id);ticker=ticker.toUpperCase(Locale.ROOT);double held=a.shares.getOrDefault(ticker,0.0);Double p=state.prices.get(ticker);if(p==null||shares<=0||held+1e-9<shares)return false;a.shares.put(ticker,held-shares);a.cashMicro+=Math.round(shares*p*EconomyState.MICRO);saveQuiet();return true;}
}
