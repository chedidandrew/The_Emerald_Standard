package com.chedidandrew.emeraldstandard.client;

import java.util.*;

/** Subtle, deterministic GUI-native paper wear; no external texture or animated noise. */
public final class NewspaperPaper {
    public record Mark(int x,int y,int width,int height,int color) {}
    public static List<Mark> marks(int width,int height) {
        if(width<80||height<100)return List.of();
        var marks=new ArrayList<Mark>();
        var random=new Random(0xE4E2A1D);
        int count=Math.min(230,width*height/900);
        for(int i=0;i<count;i++){
            int x=12+random.nextInt(width-27),y=12+random.nextInt(height-27);
            marks.add(new Mark(x,y,1+random.nextInt(3),1,i%3==0?0x08FFFFFF:0x073D352A));
        }
        int middle=width/2,fold=height*3/5;
        marks.add(new Mark(middle-2,12,2,height-24,0x073D352A));
        marks.add(new Mark(middle,12,1,height-24,0x16FFFFFF));
        marks.add(new Mark(middle+1,12,2,height-24,0x043D352A));
        marks.add(new Mark(10,fold-1,width-20,1,0x0B3D352A));
        marks.add(new Mark(10,fold,width-20,1,0x20FFFFFF));
        for(int i=0;i<8;i++) {
            marks.add(new Mark(width-5-i,4+i,1,1,0xFFC6BEAA));
            marks.add(new Mark(width-5-i,5+i,1,1,0xFFF9F5E8));
        }
        // A tiny notch stays inside the outer margin, clear of every reading column.
        int tear=height*2/3;
        for(int i=0;i<7;i++){
            int depth=1+Math.min(i,6-i);
            marks.add(new Mark(3,tear+i,depth,1,0xFFB9B4A5));
            marks.add(new Mark(3+depth,tear+i,1,1,0xFFD4CCB8));
        }
        return List.copyOf(marks);
    }
    private NewspaperPaper(){}
}
