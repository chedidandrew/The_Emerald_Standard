package com.chedidandrew.emeraldstandard.client;

/** One bounded reading path: cover, all contents sheets, then ranked stories. */
public final class NewspaperPaging {
    public static final int COVER=-2,CONTENTS=-1;
    public record Position(int story,int offset) {}
    public static Position move(Position p,int direction,int amount,int stories,int contentsPages,int maximumScroll) {
        int d=Integer.signum(direction);
        if(d==0)return p;
        int lastContents=Math.max(1,contentsPages)-1;
        if(p.story==COVER)return d>0?new Position(CONTENTS,0):p;
        if(p.story==CONTENTS){
            if(d<0&&p.offset==0)return new Position(COVER,0);
            if(d>0&&p.offset>=lastContents)return stories>0?new Position(0,0):new Position(CONTENTS,lastContents);
            return new Position(CONTENTS,Math.max(0,Math.min(lastContents,p.offset+d)));
        }
        int max=Math.max(0,maximumScroll),offset=Math.max(0,Math.min(max,p.offset));
        if(d<0&&offset>0)return new Position(p.story,(int)Math.max(0L,(long)offset-Math.max(1,amount)));
        if(d>0&&offset<max)return new Position(p.story,(int)Math.min(max,(long)offset+Math.max(1,amount)));
        if(d<0)return p.story==0?new Position(CONTENTS,lastContents):new Position(p.story-1,Integer.MAX_VALUE);
        return p.story+1<stories?new Position(p.story+1,0):new Position(p.story,offset);
    }
    private NewspaperPaging(){}
}
