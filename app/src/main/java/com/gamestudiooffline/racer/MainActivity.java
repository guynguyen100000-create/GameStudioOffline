package com.gamestudiooffline.racer;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.content.Context;
import java.util.Random;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(new RacerView(this));
    }

    static class RacerView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Random random = new Random();
        float playerX = 0.5f, roadOffset = 0, speed = 0.55f, distance = 0;
        float leftTouch = 0, rightTouch = 0;
        boolean leftDown, rightDown, crashed = false;
        float enemyX = 0.32f, enemyY = -0.3f, enemySpeed = 0.42f;
        long lastTime = 0;
        int score = 0, best = 0;
        final int grass = Color.rgb(31, 132, 69);
        final int asphalt = Color.rgb(53, 58, 66);
        final int white = Color.WHITE;

        RacerView(Context c) { super(c); setFocusable(true); }

        void rect(Canvas c, int color, float l, float t, float r, float b, float radius) {
            p.setColor(color); c.drawRoundRect(l,t,r,b,radius,radius,p);
        }
        void text(Canvas c, String s, float x, float y, float size, int color) {
            p.setColor(color); p.setTextSize(size); p.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)); c.drawText(s,x,y,p);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight();
            long now=System.nanoTime();
            float dt=lastTime==0?0.016f:Math.min(0.04f,(now-lastTime)/1_000_000_000f); lastTime=now;
            if (!crashed) {
                if (leftDown) playerX -= dt*0.78f;
                if (rightDown) playerX += dt*0.78f;
                playerX=Math.max(0.12f,Math.min(0.88f,playerX));
                roadOffset += dt*(0.7f+speed)*h*0.62f;
                distance += dt*(0.7f+speed)*12;
                enemyY += dt*enemySpeed*h*0.8f;
                if (enemyY > 1.1f) { enemyY=-0.25f; enemyX=0.22f+random.nextFloat()*0.56f; enemySpeed=0.38f+random.nextFloat()*0.35f; score+=10; }
                float py=h*0.76f, ex=enemyX*w, ey=enemyY*h;
                if (Math.abs(playerX*w-ex)<w*0.105f && Math.abs(py-ey)<h*0.105f) crashed=true;
            }

            c.drawColor(grass);
            float roadL=w*0.12f, roadR=w*0.88f;
            rect(c,Color.rgb(225,215,177),roadL-w*0.025f,0,roadR+w*0.025f,h,0);
            rect(c,asphalt,roadL,0,roadR,h,0);
            // roadside markers and dashed center lines
            float dashH=h*0.065f, gap=h*0.055f;
            for (float y=-gap+(roadOffset%(dashH+gap)); y<h; y+=dashH+gap) {
                rect(c,white,roadL+w*0.012f,y,roadL+w*0.027f,y+dashH,4);
                rect(c,white,roadR-w*0.027f,y,roadR-w*0.012f,y+dashH,4);
                rect(c,Color.rgb(245,218,87),w*0.495f,y,w*0.505f,y+dashH*0.72f,3);
            }
            // AI car
            drawCar(c,enemyX*w,enemyY*h, w*0.105f, h*0.095f, Color.rgb(255,176,52));
            // player car
            drawCar(c,playerX*w,h*0.76f,w*0.115f,h*0.105f,Color.rgb(49,155,255));
            rect(c,Color.argb(190,0,0,0),w*0.035f,h*0.025f,w*0.55f,h*0.13f,18);
            text(c,"MINI RACER",w*0.06f,h*0.065f,h*0.027f,white);
            text(c,"Điểm: "+score,w*0.06f,h*0.105f,h*0.025f,white);
            text(c,""+(int)distance+" m",w*0.72f,h*0.065f,h*0.027f,white);
            // touch controls
            rect(c,Color.argb(155,20,25,32),w*0.06f,h*0.86f,w*0.34f,h*0.97f,24);
            rect(c,Color.argb(155,20,25,32),w*0.66f,h*0.86f,w*0.94f,h*0.97f,24);
            text(c,"◀",w*0.17f,h*0.93f,h*0.06f,white);
            text(c,"▶",w*0.76f,h*0.93f,h*0.06f,white);
            if (crashed) {
                rect(c,Color.argb(205,0,0,0),w*0.08f,h*0.34f,w*0.92f,h*0.62f,24);
                text(c,"VA CHẠM!",w*0.25f,h*0.42f,h*0.052f,Color.rgb(255,95,95));
                text(c,"Điểm: "+score,w*0.32f,h*0.48f,h*0.034f,white);
                text(c,"Chạm màn hình để chơi lại",w*0.16f,h*0.55f,h*0.027f,white);
            }
            postInvalidateDelayed(16);
        }
        void drawCar(Canvas c,float cx,float cy,float cw,float ch,int color) {
            // wheels
            rect(c,Color.BLACK,cx-cw*0.55f,cy-ch*0.32f,cx-cw*0.32f,cy-ch*0.05f,5);
            rect(c,Color.BLACK,cx+cw*0.32f,cy-ch*0.32f,cx+cw*0.55f,cy-ch*0.05f,5);
            rect(c,Color.BLACK,cx-cw*0.55f,cy+ch*0.14f,cx-cw*0.32f,cy+ch*0.42f,5);
            rect(c,Color.BLACK,cx+cw*0.32f,cy+ch*0.14f,cx+cw*0.55f,cy+ch*0.42f,5);
            rect(c,color,cx-cw*0.42f,cy-ch*0.5f,cx+cw*0.42f,cy+ch*0.5f,12);
            rect(c,Color.rgb(182,225,242),cx-cw*0.29f,cy-ch*0.28f,cx+cw*0.29f,cy-ch*0.02f,7);
            rect(c,Color.rgb(35,55,73),cx-cw*0.29f,cy+ch*0.13f,cx+cw*0.29f,cy+ch*0.34f,7);
            rect(c,Color.rgb(255,244,185),cx-cw*0.34f,cy-ch*0.48f,cx-cw*0.17f,cy-ch*0.42f,3);
            rect(c,Color.rgb(255,244,185),cx+cw*0.17f,cy-ch*0.48f,cx+cw*0.34f,cy-ch*0.42f,3);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX()/Math.max(1,getWidth()), y=e.getY()/Math.max(1,getHeight());
            if (e.getAction()==MotionEvent.ACTION_DOWN && crashed) {
                crashed=false; score=0; distance=0; playerX=0.5f; enemyY=-0.3f; lastTime=0; invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_DOWN || e.getAction()==MotionEvent.ACTION_MOVE) {
                leftDown=x<0.48f && y>0.82f;
                rightDown=x>=0.52f && y>0.82f;
            } else if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) {
                leftDown=false; rightDown=false;
            }
            return true;
        }
    }
}
