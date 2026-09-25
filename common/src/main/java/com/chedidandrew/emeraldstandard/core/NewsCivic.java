package com.chedidandrew.emeraldstandard.core;

import java.io.IOException;
import java.util.*;

/** Bounded editorial comparisons of recorded physical projects. Never scans or alters the world. */
final class NewsCivic {
    static final int LIMIT=2048;
    record Watch(int flags,long article,long day) {}
    static String subject(EconomyState.VillageProject p) {return "project:"+p.projectId+":"+p.originPos;}
    private static int flags(EconomyState.VillageProject p) {
        return (p.economicComplete?1:0)|(p.constructionStarted&&p.materializedBlocks>0?2:0)
            |(p.totalBlocks>0&&2L*p.materializedBlocks>=p.totalBlocks?4:0)
            |(p.materializedComplete&&!p.manualRepairRequired?8:0)|(p.manualRepairRequired?16:0);
    }
    static void day(EconomyState s) {
        if(s.villages.isEmpty())return;
        var villages=new ArrayList<>(s.villages.values());
        int start=(int)Math.floorMod(s.economicDay*16,villages.size()),budget=6;
        for(int n=0;n<Math.min(16,villages.size());n++) {
            var v=villages.get((start+n)%villages.size());
            String root=v.villageId.toString()+":";
            boolean first=!s.editor.civic.containsKey(root);
            var projects=v.projects.subList(Math.max(0,v.projects.size()-128),v.projects.size());
            for(var p:projects) {
                if(p.originPos==0||p.abstractOnly||p.relocationPending)continue;
                String key=root+subject(p);int now=flags(p);
                var old=s.editor.civic.get(key);
                if(first) {put(s,key,new Watch(now,0,s.economicDay));continue;}
                if(old!=null&&old.flags()==now)continue;
                String event=event(old,now);
                if(event==null) {put(s,key,new Watch(now,old==null?0:old.article(),s.economicDay));continue;}
                if(budget==0)continue;
                // No guess about builders, residents moving in, exact repairs or project causes.
                var prior=old==null?null:s.news.stream().filter(a->a.id()==old.article()).findFirst().orElse(null);
                // A verified structural restoration may continue an exact project damage report.
                if(event.equals("RESTORED")&&s.editor.playerReports) {
                    for(int i=s.news.size()-1;i>=0;i--) {
                        var a=s.news.get(i);
                        if(a.kind()==NewsWire.Kind.DAMAGE&&a.village().equals(v.villageId.toString())
                                &&a.subject().equals(subject(p))) {prior=a;break;}
                    }
                }
                String name=name(p),headline=headline(s,event,name),continuity=prior==null?"":
                        "This follows “"+prior.headline()+"”, our report from Day "+prior.day()+".";
                String detail=NewsNarrative.paragraphs(NewsWire.location(v),change(event),
                        consequence(p,event),continuity,NewsColumns.column(s,NewsWire.OUTLETS.get(3),headline),
                        ending(event),NewsNarrative.FACTS+"Project: "+name+".\n"+change(event)
                        +"\nFirst approved: Day "+p.approvedDay+".");
                long source=prior==null?0:prior.id();
                NewsWire.append(s,new NewsWire.Article(0,s.economicDay,NewsWire.Kind.CIVIC,"CIVIC_"+event,
                        NewsWire.OUTLETS.get(3),v.villageId.toString(),"",0,headline,detail,subject(p),source));
                put(s,key,new Watch(now,s.news.getLast().id(),s.economicDay));budget--;
            }
            put(s,root,new Watch(0,0,s.economicDay));
        }
    }
    private static String event(Watch old,int now) {
        if((now&8)!=0&&(old==null||(old.flags()&8)==0))return old!=null&&(old.flags()&16)!=0?"RESTORED":"OPEN";
        if((now&16)!=0&&(old==null||(old.flags()&16)==0))return "REPAIR";
        if(old==null)return (now&2)!=0?"START":"PLANNED";
        if((now&4)!=0&&(old.flags()&4)==0)return "HALFWAY";
        if((now&2)!=0&&(old.flags()&2)==0)return "START";
        if((now&1)!=0&&(old.flags()&1)==0)return "FUNDED";
        return null;
    }
    private static String name(EconomyState.VillageProject p) {
        return switch(p.type) {
            case COTTAGE -> "Cottage";case WAREHOUSE -> "Warehouse";case MINE_ENTRANCE -> "Mine entrance";
            case HOUSE -> "House";case INN -> "Inn";case MARKET_SQUARE -> "Market square";case SMITHY -> "Smithy";
            case GRANARY -> "Granary";case GUARD_POST -> "Guard post";case EXCHANGE_HALL -> "Exchange hall";
        };
    }
    private static String headline(EconomyState s,String event,String name) {
        var tails=switch(event) {
            case "OPEN" -> List.of("opens; the useful part can begin","completed; drawings finally meet their match","ready for village life");
            case "RESTORED" -> List.of("fit for use again","restored; the gap in its story closes","returns to sound condition");
            case "REPAIR" -> List.of("needs repair; attention turns to the building","awaits work before returning to use","inspection finds work still needed");
            case "HALFWAY" -> List.of("takes shape; more than a promising outline","reaches a construction milestone","build reaches the halfway mark");
            case "FUNDED" -> List.of("labor account settled; work continues","work account settled; the site still matters","reaches a milestone in its labor account");
            case "START" -> List.of("construction begins; timber gets its turn","work starts beyond the drawing board","build gets under way");
            default -> List.of("gets a place in the village's plans","planned; a new chapter awaits its foundations","joins the village building program");
        };
        return NewsWire.choose(s,"CIVIC_"+event,tails.stream().map(t->name+" "+t).toList());
    }
    private static String change(String event) {
        return switch(event) {
            case "OPEN" -> "The building's planned construction is complete and it is ready for use.";
            case "RESTORED" -> "A fresh inspection has verified that the building is sound again.";
            case "REPAIR" -> "Inspection has found that the building needs repair before it can return to use.";
            case "HALFWAY" -> "Construction has reached the halfway mark in its planned work, with finishing still ahead.";
            case "FUNDED" -> "The project's labor account is settled. Physical work still has to be finished.";
            case "START" -> "Construction has begun at the reserved site.";
            default -> "The village has a planned project with a selected building site.";
        };
    }
    private static String consequence(EconomyState.VillageProject p,String event) {
        if(event.equals("REPAIR"))return "A usable building is worth more than the outline it leaves on a map. The immediate question is practical: what will put this place back into service? This edition leaves the cause and the repairer's identity open; the inspection does not answer either question.";
        if(event.equals("PLANNED"))return "A site gives the proposal an address, not a roof. There is work between a promising drawing and a useful building. For now, the plan offers the village a destination for its next effort, and offers the drawing no opportunity to blame the weather.";
        if(event.equals("HALFWAY"))return "The halfway point is a satisfying place to stand only if nobody mistakes it for the end. Details still have to meet, finishing work still matters, and a door is most useful when the rest of its building is ready too. Progress deserves a mention; completion will deserve another.";
        if(event.equals("FUNDED"))return "An account can be settled while a roof remains an ambition. The distinction is particularly clear when it rains. With the labor account attended to, the remaining task is the patient business of making the planned place useful.";
        if(event.equals("RESTORED"))return "There is quiet value in getting a familiar place back into service. New projects receive grand drawings; repaired ones often receive the more practical compliment of being useful again. A sound building needs no speech to make the difference felt.";
        return switch(p.type) {
            case COTTAGE,HOUSE,INN -> "Housing is one of those ambitions that becomes more persuasive when it has a roof. A room can offer shelter without promising that somebody has already moved in. The village now has a more tangible prospect than a square on a drawing.";
            case WAREHOUSE,GRANARY -> "Storage sounds modest until something needs somewhere dry to wait. Timber and a sound roof do what even the longest inventory cannot: put a useful space around the goods. Filling that space remains a separate job.";
            case MARKET_SQUARE,EXCHANGE_HALL -> "A place to trade gives village errands a destination. It cannot guarantee a good bargain, but it can give buyers and sellers somewhere to discuss one. That is a reasonable beginning for a public space.";
            case MINE_ENTRANCE,SMITHY -> "A workshop's reputation will eventually be made by useful work, not its frontage. First it needs a sound place for that work. Picks, tools and materials have a habit of asking more practical questions than a drawing can answer.";
            case GUARD_POST -> "A guard post gives the village a place from which to watch its approaches. Stone alone will not keep watch. Staffing and the everyday care of the surrounding paths still matter after the masonry has received its attention.";
        };
    }
    private static String ending(String event) {
        return event.equals("REPAIR")?"The next report will wait for a change at the building, rather than a more cheerful speech."
                :"The village will judge this work by what it makes possible, long after the drawings have been put away.";
    }
    private static void put(EconomyState s,String key,Watch w) {
        s.editor.civic.remove(key);s.editor.civic.put(key,w);
        while(s.editor.civic.size()>LIMIT)s.editor.civic.remove(s.editor.civic.keySet().iterator().next());
    }
    static void write(EconomyState s,Properties p) {
        p.setProperty("news.civic.count",""+s.editor.civic.size());int i=0;
        for(var e:s.editor.civic.entrySet()) {
            String k="news.civic."+(i++)+".";
            p.setProperty(k+"key",e.getKey());p.setProperty(k+"flags",""+e.getValue().flags());
            p.setProperty(k+"article",""+e.getValue().article());p.setProperty(k+"day",""+e.getValue().day());
        }
    }
    static void read(EconomyState s,Properties p)throws IOException {
        int count=Integer.parseInt(p.getProperty("news.civic.count","0"));
        if(count<0||count>LIMIT)throw new IOException("Civic news limit");
        for(int i=0;i<count;i++) {
            String k="news.civic."+i+".",key=Objects.requireNonNull(p.getProperty(k+"key"));
            var w=new Watch(Integer.parseInt(p.getProperty(k+"flags")),Long.parseLong(p.getProperty(k+"article")),Long.parseLong(p.getProperty(k+"day")));
            if(s.editor.civic.put(key,w)!=null)throw new IOException("Duplicate civic watch");
        }
    }
    static void validate(EconomyState s)throws IOException {
        if(s.editor.civic.size()>LIMIT)throw new IOException("Civic news limit");
        for(var e:s.editor.civic.entrySet()) {
            var w=e.getValue();
            if(e.getKey().length()>200||!e.getKey().matches("[A-Za-z0-9:_-]+")||w.flags()<0||w.flags()>31
                    ||w.article()<0||w.article()>=s.editor.nextId||w.day()<0||w.day()>s.economicDay)
                throw new IOException("Invalid civic observation");
        }
    }
    private NewsCivic() {}
}
