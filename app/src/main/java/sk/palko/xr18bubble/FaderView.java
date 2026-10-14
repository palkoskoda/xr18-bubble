package sk.palko.xr18bubble;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.os.SystemClock;
import java.util.Locale;

/** Relative movement prevents a loud jump when the rail is touched away from the cap. */
final class FaderView extends View {
    interface Listener { void changed(float value); }
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float value=.0f,startY,startValue;
    private boolean known,dragging;
    private Listener listener;
    private float[] meterDb;
    private final float[] peaks={-90,-90};
    private final long[] peakUntil={0,0};
    private long meterTime;
    FaderView(Context c,Listener l) { super(c);listener=l;setContentDescription(L.s(c,"volume"));setFocusable(true); }
    void update(Float v,boolean enabled) {
        known=v!=null;
        if((!enabled||!known)&&dragging){dragging=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);}
        if(v!=null&&!dragging)value=v;setEnabled(enabled&&known);setAlpha(isEnabled()?1f:.38f);invalidate();
    }
    void meter(float[] db){
        if(db==null){if(meterDb!=null){meterDb=null;peaks[0]=peaks[1]=-90;invalidate();}return;}
        long now=SystemClock.elapsedRealtime();
        for(int i=0;i<db.length&&i<2;i++){float level=Math.max(-90,Math.min(0,db[i]));if(level>=peaks[i]){peaks[i]=level;peakUntil[i]=now+800;}else if(now>peakUntil[i])peaks[i]=Math.max(level,peaks[i]-(now-meterTime)*.025f);}
        meterDb=db;meterTime=now;invalidate();
    }
    private float top() {return Ui.dp(getContext(),10);}
    private float span() {return Math.max(1,getHeight()-Ui.dp(getContext(),22));}
    protected void onDraw(Canvas c) {
        super.onDraw(c);float x=getWidth()*.56f,t=top(),h=span();
        drawMeter(c,t,h);
        p.setStrokeWidth(Ui.dp(getContext(),4));p.setColor(0xff080808);c.drawLine(x,t,x,t+h,p);
        p.setTextSize(Ui.dp(getContext(),8));p.setTypeface(Typeface.MONOSPACE);
        float lastLabel=-Float.MAX_VALUE;
        for(int db:new int[]{10,0,-10,-20,-30,-40,-60}) {
            float y=t+(1-XAirProtocol.dbToNormalized(db))*h;
            p.setColor(db==0?Ui.TEXT:Ui.DIM);
            if(y-lastLabel>=Ui.dp(getContext(),9)){c.drawText(db>0?"+"+db:""+db,Ui.dp(getContext(),1),y+3,p);lastLabel=y;}
            p.setStrokeWidth(Ui.dp(getContext(),1));c.drawLine(x-Ui.dp(getContext(),10),y,x-Ui.dp(getContext(),4),y,p);
        }
        if(!known)return;
        float y=t+(1-value)*h,w=Ui.dp(getContext(),12),hh=Ui.dp(getContext(),7);
        p.setShader(new LinearGradient(0,y-hh,0,y+hh,0xfffafafa,0xffaaaaaa,Shader.TileMode.CLAMP));
        c.drawRoundRect(x-w,y-hh,x+w,y+hh,Ui.dp(getContext(),2),Ui.dp(getContext(),2),p);p.setShader(null);
        p.setColor(0xff444444);p.setStrokeWidth(Ui.dp(getContext(),1));c.drawLine(x-w+2,y,x+w-2,y,p);
    }
    private void drawMeter(Canvas c,float top,float height){
        int count=meterDb==null?1:Math.min(2,meterDb.length);float w=Ui.dp(getContext(),2),gap=Ui.dp(getContext(),1),left=getWidth()-Ui.dp(getContext(),1)-count*(w+gap);
        for(int i=0;i<count;i++){
            float x=left+i*(w+gap);p.setColor(0xff101410);c.drawRect(x,top,x+w,top+height,p);
            if(meterDb==null)continue;
            float db=Math.max(-60,Math.min(0,meterDb[i]));float filled=(db+60)/60*height;
            float step=Ui.dp(getContext(),4),segment=Ui.dp(getContext(),3);
            for(float y=0;y<height;y+=step){float level=-60+60*y/height;p.setColor(y<filled?(level>=-3?Ui.RED:level>=-18?Ui.GOLD:Ui.GREEN):0xff1e271e);c.drawRect(x,top+height-y-segment,x+w,top+height-y,p);}
            float peakY=top+height-(Math.max(-60,peaks[i])+60)/60*height;p.setColor(peaks[i]>=-3?Ui.RED:Ui.TEXT);c.drawRect(x,peakY,x+w,peakY+Ui.dp(getContext(),1),p);
        }
    }
    public boolean onTouchEvent(MotionEvent e) {
        if(!isEnabled())return false;
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: dragging=true;startY=e.getY();startValue=value;getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                value=Math.max(0,Math.min(1,startValue+(startY-e.getY())/span()));listener.changed(value);invalidate();return true;
            case MotionEvent.ACTION_UP: dragging=false;listener.changed(value);performClick();getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL: dragging=false;getParent().requestDisallowInterceptTouchEvent(false);return true;
        } return true;
    }
    public boolean performClick() {super.performClick();return true;}
    static String format(Float v) { if(v==null)return "—";if(v<=0)return "−∞";float db=XAirProtocol.normalizedToDb(v);return String.format(Locale.ROOT,"%+.1f",db); }
}
