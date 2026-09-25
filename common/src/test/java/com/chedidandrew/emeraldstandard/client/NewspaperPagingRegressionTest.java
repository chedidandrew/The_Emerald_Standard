package com.chedidandrew.emeraldstandard.client;
import java.util.*;
public final class NewspaperPagingRegressionTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args){
        for(int stories:new int[]{0,1,19,256})for(int perPage:new int[]{1,8,16,24})for(int step:new int[]{3,36,Integer.MAX_VALUE}){
            int contents=Math.max(1,(stories+perPage-1)/perPage);
            var p=new NewspaperPaging.Position(-2,0);
            Set<Integer> visited=new HashSet<>();Set<Integer> contentsSeen=new HashSet<>();
            int moves=0;
            while(true){
                if(p.story()>=0)visited.add(p.story());
                if(p.story()==-1)contentsSeen.add(p.offset());
                int max=p.story()>=0?p.story()%4*13:0;
                var next=NewspaperPaging.move(p,1,step,stories,contents,max);
                if(next.equals(p))break;
                p=next;check(++moves<15000,"unbounded forward path");
            }
            check(visited.size()==stories&&contentsSeen.size()==contents,"skipped a story or contents sheet");
            check(p.story()==(stories==0?-1:stories-1),"wrong edition end");
            check(NewspaperPaging.move(p,0,step,stories,contents,0).equals(p),"horizontal scroll changed page");
            while(p.story()!=-2){
                int max=p.story()>=0?p.story()%4*13:0;
                p=NewspaperPaging.move(p,-1,step,stories,contents,max);
                if(p.offset()==Integer.MAX_VALUE)p=new NewspaperPaging.Position(p.story(),p.story()%4*13);
                check(++moves<30000,"unbounded reverse path");
            }
            check(NewspaperPaging.move(p,-1,step,stories,contents,0).equals(p),"cover wraps");
        }
        for(int w:new int[]{80,300,624,740})for(int h:new int[]{100,234,484,1000}){
            var marks=NewspaperPaper.marks(w,h);
            check(marks.equals(NewspaperPaper.marks(w,h))&&marks.size()<=265,"unstable/unbounded wear");
            for(var m:marks)check(m.x()>=0&&m.y()>=0&&m.width()>0&&m.height()>0
                    &&m.x()+m.width()<=w&&m.y()+m.height()<=h,"wear escapes paper");
        }
        check(NewspaperPaper.marks(10,10).isEmpty(),"tiny surface");
        System.out.println("PASS newspaper continuous forward/reverse path, all contents/stories, empty/end bounds and stable subtle paper wear");
    }
}
