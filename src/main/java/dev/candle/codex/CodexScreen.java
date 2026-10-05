package dev.candle.codex;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class CodexScreen extends Screen {
    private static final int BG=0xFF0A0D12,PANEL=0xFF111720,PANEL2=0xFF171E29,TEXT=0xFFE8EEF7,MUTED=0xFF8995A7,ACCENT=0xFF7CFFB2,BLUE=0xFF67B7FF;
    private int page;
    private final String[] tabs={"Overview","Cosmetics","HUD","Performance","Settings"};
    public CodexScreen(){super(Component.literal("Candle Codex"));}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,BG); for(int x=0;x<width;x+=32)g.fill(x,0,x+1,height,0x101E2935); for(int y=0;y<height;y+=32)g.fill(0,y,width,y+1,0x101E2935);
        g.fill(22,18,width-22,78,PANEL); g.drawString(font,Component.literal("CANDLE CODEX"),42,34,TEXT,false); g.drawString(font,Component.literal("AURORA EDITION  •  0.1"),42,53,MUTED,false); g.fill(width-190,31,width-174,47,ACCENT); g.drawString(font,Component.literal("PERFORMANCE READY"),width-164,34,TEXT,false);
        g.fill(22,92,188,height-22,PANEL); for(int i=0;i<tabs.length;i++){int y=110+i*44;boolean a=i==page;g.fill(34,y-6,176,y+28,a?0xFF1D2A34:PANEL);if(a)g.fill(34,y-6,38,y+28,ACCENT);g.drawString(font,Component.literal(tabs[i]),50,y+5,a?TEXT:MUTED,false);} 
        int x=210,y=92,w=width-232; if(page==0)overview(g,x,y,w); else if(page==1)cosmetics(g,x,y,w); else if(page==2)hud(g,x,y,w); else if(page==3)performance(g,x,y,w); else settings(g,x,y,w); super.render(g,mx,my,delta);
    }
    private void card(GuiGraphics g,int x,int y,int w,int h,String t){g.fill(x,y,x+w,y+h,PANEL);g.fill(x,y,x+w,y+2,0xFF263340);g.drawString(font,Component.literal(t),x+18,y+18,TEXT,false);}
    private void stat(GuiGraphics g,int x,int y,String n,String v){g.drawString(font,Component.literal(n),x,y,MUTED,false);g.drawString(font,Component.literal(v),x,y+16,ACCENT,false);}
    private void overview(GuiGraphics g,int x,int y,int w){card(g,x,y,w,118,"A CLIENT BUILT AROUND CLARITY");g.drawString(font,Component.literal("Fast menus. Quiet animations. Useful information."),x+18,y+46,TEXT,false);g.drawString(font,Component.literal("Codex keeps Candle's identity while rebuilding the experience around"),x+18,y+65,MUTED,false);g.drawString(font,Component.literal("compact panels, strong spacing and performance-first rendering."),x+18,y+81,MUTED,false);int hw=(w-14)/2;card(g,x,y+132,hw,108,"LIVE STATUS");stat(g,x+18,y+176,"FPS","144");stat(g,x+105,y+176,"FRAME","6.9 ms");stat(g,x+192,y+176,"MEM","2.1 GB");card(g,x+hw+14,y+132,hw,108,"CODEX IDEA");g.drawString(font,Component.literal("Aurora Grid"),x+hw+32,y+178,BLUE,false);g.drawString(font,Component.literal("one visual language across every page"),x+hw+32,y+198,MUTED,false);}
    private void cosmetics(GuiGraphics g,int x,int y,int w){card(g,x,y,w,74,"COSMETICS LAB");g.drawString(font,Component.literal("3D-first previews • lightweight geometry • no carousel-only UI"),x+18,y+45,MUTED,false);String[] a={"AURORA CAPE","NEON HALO","CODEX WINGS","VOID PET"};int cw=(w-42)/4;for(int i=0;i<4;i++){int cx=x+i*(cw+10),cy=y+92;card(g,cx,cy,cw,138,a[i]);g.fill(cx+18,cy+52,cx+cw-18,cy+92,i%2==0?0xFF193126:0xFF172A3B);g.drawString(font,Component.literal(i==0?"ACTIVE":"PREVIEW"),cx+18,cy+111,i==0?ACCENT:MUTED,false);}}
    private void hud(GuiGraphics g,int x,int y,int w){card(g,x,y,w,82,"HUD COMPOSER");g.drawString(font,Component.literal("Minimal modules with per-module visibility and colour controls."),x+18,y+46,MUTED,false);String[] a={"FPS","PING","MEMORY","SPEED","DIRECTION","CLOCK"};for(int i=0;i<a.length;i++){int yy=y+100+i*42;g.fill(x+14,yy,x+w-14,yy+32,PANEL2);g.drawString(font,Component.literal(a[i]),x+28,yy+10,TEXT,false);g.drawString(font,Component.literal(i<4?"ON":"OFF"),x+w-55,yy+10,i<4?ACCENT:MUTED,false);}}
    private void performance(GuiGraphics g,int x,int y,int w){card(g,x,y,w,150,"FRAME-TIME RADAR");int gx=x+18,gy=y+55,gw=w-36,gh=72;g.fill(gx,gy,gx+gw,gy+gh,0xFF0D1219);for(int i=0;i<48;i++){int h=10+(int)(Math.abs(Math.sin(i*.58))*45);g.fill(gx+i*(gw/48),gy+gh-h,gx+i*(gw/48)+3,gy+gh,i%7==0?ACCENT:0xFF2A4650);}g.drawString(font,Component.literal("AVG 144 FPS    LOW 121    BEST 165"),x+18,y+131,TEXT,false);card(g,x,y+164,w,104,"PERFORMANCE PROFILE");g.drawString(font,Component.literal("Render: balanced"),x+18,y+205,TEXT,false);g.drawString(font,Component.literal("Cosmetic animations: adaptive"),x+18,y+225,MUTED,false);g.drawString(font,Component.literal("Heavy effects pause automatically under load"),x+18,y+245,MUTED,false);}
    private void settings(GuiGraphics g,int x,int y,int w){card(g,x,y,w,82,"SETTINGS");setting(g,x,y+104,w,"Animation quality","Adaptive");setting(g,x,y+148,w,"Interface scale","100%");setting(g,x,y+192,w,"Theme","Aurora Grid");setting(g,x,y+236,w,"Reduce effects","Off");}
    private void setting(GuiGraphics g,int x,int y,int w,String a,String b){g.fill(x+14,y,x+w-14,y+34,PANEL2);g.drawString(font,Component.literal(a),x+28,y+10,TEXT,false);g.drawString(font,Component.literal(b),x+w-120,y+10,BLUE,false);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&mx>=34&&mx<=176)for(int i=0;i<tabs.length;i++){int y=110+i*44;if(my>=y-6&&my<=y+28){page=i;return true;}}return super.mouseClicked(mx,my,button);}
    @Override public boolean isPauseScreen(){return false;}
}
