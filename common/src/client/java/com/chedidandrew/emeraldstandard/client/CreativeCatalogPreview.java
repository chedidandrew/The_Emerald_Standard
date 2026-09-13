package com.chedidandrew.emeraldstandard.client;

import com.chedidandrew.emeraldstandard.minecraft.EmeraldCreativeContent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Opt-in render fixture for the same catalog as the native creative tab; never opens a world. */
final class CreativeCatalogPreview extends Screen {
    CreativeCatalogPreview() { super(Component.translatable("itemGroup.the_emerald_standard")); }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xff192820);
        g.text(font,title,12,12,0xfff4ce56,false);
        int y=36;
        for(var item:EmeraldCreativeContent.items()) {
            var stack=HandbookRecipes.previewStack(item,1);
            if(stack.getHoverName().getString().equals(item.getDescriptionId()))
                throw new IllegalStateException("Missing catalog item name");
            g.fill(12,y,34,y+22,0xffbec4b5);
            g.fakeItem(stack,15,y+3);
            var name=stack.getHoverName();
            if(font.width(name)>width-52) throw new IllegalStateException("Catalog fixture label overflows");
            g.text(font,name,42,y+7,0xffeeeecc,false);
            y+=31;
        }
        g.text(font,"Eggs: Creative only",12,y+5,0xffe2c872,false);
    }
}
