package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldConfig;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Real settings tooltip extraction; no world, profile, setting or network writes. */
final class TooltipClientChecks extends Screen {
    private static final String KEY="village_prosperity.max_monthly_treasury_spending";
    private final String mode;
    private EmeraldSettingsScreen settings;
    private int hoverX,hoverY,observations;
    TooltipClientChecks(String mode) {super(Component.literal("Wrapped settings tooltip"));this.mode=mode;}
    @Override protected void init() {
        verify(minecraft);
        settings=EmeraldSettingsScreen.preview(EmeraldConfig.defaults());
        settings.init(width,height);
        int index=new ArrayList<>(EmeraldConfig.defaults().values().keySet()).indexOf(KEY);
        set(settings,"page",index/(int)get(settings,"rows"));settings.init(width,height);
        int row=index%(int)get(settings,"rows"), rowY=(int)get(settings,"y")+93+row*27;
        AbstractWidget input=settings.children().stream().filter(c->c instanceof EditBox)
                .map(c->(AbstractWidget)c).filter(c->c.getY()==rowY).findFirst().orElseThrow();
        hoverX=mode.equals("label")?(int)get(settings,"x")+14:input.getX()+input.getWidth()-3;
        hoverY=input.getY()+7;
        if(mode.equals("focus")) {
            minecraft.setLastInputType(net.minecraft.client.InputType.KEYBOARD_TAB);
            settings.setFocused(input);hoverX=4;hoverY=4;
        } else minecraft.setLastInputType(net.minecraft.client.InputType.MOUSE);
        observations=0;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        settings.extractBackground(g,hoverX,hoverY,delta);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        // Fabric widens this field; NeoForge keeps vanilla's private access. Reflection is
        // confined to the opt-in test fixture so production needs no new accessor/mixin.
        var state=(net.minecraft.client.renderer.state.gui.GuiRenderState)get(g,"guiRenderState");
        var probe=new GuiGraphicsExtractor(minecraft,state,hoverX,hoverY) {
            private void record(Font font,List<? extends FormattedCharSequence> lines) {
                check(lines.size()>1,"Actual spending description is still a single line");
                check(lines.stream().allMatch(line->font.width(line)<=GuiTooltips.maxWidth(width)),
                        "Actual settings tooltip exceeds screen-adaptive width");
                observations++;
            }
            @Override public void setTooltipForNextFrame(Font font,List<? extends FormattedCharSequence> lines,int x,int y) {
                record(font,lines);super.setTooltipForNextFrame(font,lines,x,y);
            }
            @Override public void setTooltipForNextFrame(Font font,List<FormattedCharSequence> lines,
                    Optional<TooltipComponent> extra,ClientTooltipPositioner positioner,int x,int y,boolean focus,Identifier style) {
                record(font,lines);super.setTooltipForNextFrame(font,lines,extra,positioner,x,y,focus,style);
            }
        };
        settings.extractRenderState(probe,hoverX,hoverY,delta);
        probe.extractDeferredElements(hoverX,hoverY,delta);
    }
    void verifyObserved() {check(observations>0,"No actual "+mode+" tooltip was rendered");}
    static void verify(Minecraft game) {
        Font font=game.font;
        int count=0;
        for(int width:new int[]{120,180,320,640,1280}) {
            for(String key:EmeraldConfig.defaults().values().keySet()) {
                Component help=Component.literal(SettingsHelp.description(key));
                check(GuiTooltips.lines(font,help,width).stream().allMatch(l->font.width(l)<=GuiTooltips.maxWidth(width)),
                        "Label wrap: "+key);
                Component wrapped=GuiTooltips.widgetText(font,help,width);
                for(String line:wrapped.getString().split("\\n",-1))
                    check(font.width(line)<=Math.min(170,GuiTooltips.maxWidth(width)),"Widget wrap: "+key);
                check(compact(wrapped.getString()).equals(compact(help.getString())),"Lost help text: "+key);
                if(font.width(help)>Math.min(170,GuiTooltips.maxWidth(width)))
                    check(wrapped.getString().contains("\n"),"Missing hard wrap: "+key);
                count++;
            }
        }
        var rich=Component.literal("Fee").withStyle(Style.EMPTY.withBold(true).withColor(0xFFAA00))
                .append(Component.literal("\n\n")).append(Component.literal("9,999.50 E ".repeat(20)).withStyle(Style.EMPTY));
        Component wrapped=GuiTooltips.widgetText(font,rich,180);
        check(wrapped.getString().contains("\n\n"),"Explicit paragraph break lost");
        boolean[] bold={false};
        wrapped.visit((style,text)-> {if(text.contains("Fee"))bold[0]=style.isBold()
                &&style.getColor()!=null&&style.getColor().getValue()==0xFFAA00;return Optional.empty();},Style.EMPTY);
        check(bold[0],"Financial text styling lost during wrapping");
        check(GuiTooltips.widgetText(font,Component.literal("Next"),320).getString().equals("Next"),
                "Short labels should not gain artificial line breaks");
        var word=Component.literal("X".repeat(160));
        check(GuiTooltips.widgetText(font,word,180).getString().contains("\n"),"Long unbroken token not wrapped");
        check(GuiTooltips.widget(Component.literal(SettingsHelp.description(KEY))).toCharSequence(game).size()>1,
                "Native widget did not receive wrapped help");
        BankerClientChecks.verifyTooltipWrapping(game);
        System.out.println("PASS tooltip wrapping: "+count+" setting/width combinations; paragraphs, styles, long tokens and financial values");
    }
    private static String compact(String text) {return text.replaceAll("\\s+","");}
    private static Object get(Object instance,String name) {
        try {
            Class<?> type=instance instanceof GuiGraphicsExtractor?GuiGraphicsExtractor.class:instance.getClass();
            var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(instance);
        }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void set(Object instance,String name,Object value) {
        try {var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);f.set(instance,value);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static void check(boolean pass,String message) {if(!pass)throw new IllegalStateException(message);}
}
