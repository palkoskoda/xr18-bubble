package sk.palko.xr18bubble.largecontrolstest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Real overlay layout and gesture regression tests, using reflection instead of production hooks.
 * Run only on the disposable dedicated emulator: no mixer is connected and no SET is sent.
 */
public final class LargeControlsInstrumentation extends Instrumentation {
    private static final String TARGET="sk.palko.xr18bubble";
    private static final String[] SIZES={"small","medium","large","xlarge","xxlarge","huge","maximum"};
    private static final float[] SCALES={1f,1.15f,1.35f,1.8f,2.4f,3.3f,4.5f};
    private static final String[] FONT_SIZES={"75","100","150","200","300"};
    private Class<?> serviceClass;
    private Object service;
    private int checks;
    private long downTime;
    private boolean drawingOnly;

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);drawingOnly=arguments!=null&&"true".equals(arguments.getString("drawing_only"));start();}
    @Override public void onStart(){
        Bundle result=new Bundle();SharedPreferences prefs=getTargetContext().getSharedPreferences("profile",Context.MODE_PRIVATE);
        Map<String,?> saved=prefs.getAll();int resultCode=Activity.RESULT_CANCELED;
        try{
            serviceClass=getTargetContext().getClassLoader().loadClass(TARGET+".OverlayService");
            prefs.edit().clear().putString("host","10.0.2.2").putString("name","Large controls fixture")
                .putString("auto_connect","off").putString("language","en").putString("wifi_identity_asked","yes")
                .putString("control_size","small").putString("bubble_size","medium")
                .putStringSet("selected",new HashSet<>(Arrays.asList("main","dca1","mute1"))).commit();
            startActivitySync(new Intent().setComponent(new ComponentName(TARGET,TARGET+".MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(()->getTargetContext().startForegroundService(new Intent().setComponent(new ComponentName(TARGET,TARGET+".OverlayService")).setAction("show")));
            for(int i=0;i<50;i++){main(()->service=field(serviceClass,"live").get(null));if(service!=null)break;Thread.sleep(100);}
            check(service!=null,"overlay service started");
            main(()->{set("autoConnect",false);set("hasSession",false);});
            if(drawingOnly)drawing(prefs);
            else{
                sizes(prefs);muteOnly(prefs);relativeFader(prefs);drag();
                if(checks!=172)throw new AssertionError("Existing regression baseline changed: "+checks+" instead of 172 checks");
                startService();fontSizes(prefs);
            }
            result.putString("stream","LargeControlsInstrumentation passed: "+checks+" checks; display="+
                getTargetContext().getResources().getDisplayMetrics().widthPixels+"x"+getTargetContext().getResources().getDisplayMetrics().heightPixels+
                ", fontScale="+getTargetContext().getResources().getConfiguration().fontScale+
                (drawingOnly?"; focused drawing combinations=2":"; existing baseline=172; font combinations=35")+"; no mixer session or SET\n");
            resultCode=Activity.RESULT_OK;
        }catch(Throwable failure){result.putString("stream","LargeControlsInstrumentation FAILED after "+checks+" checks: "+android.util.Log.getStackTraceString(failure));}
        finally{
            try{getTargetContext().stopService(new Intent().setComponent(new ComponentName(TARGET,TARGET+".OverlayService")));}catch(Throwable ignored){}
            SharedPreferences.Editor restore=prefs.edit().clear();
            for(Map.Entry<String,?> entry:saved.entrySet()){
                String key=entry.getKey();Object value=entry.getValue();
                if(value instanceof String)restore.putString(key,(String)value);else if(value instanceof Integer)restore.putInt(key,(Integer)value);
                else if(value instanceof Long)restore.putLong(key,(Long)value);else if(value instanceof Boolean)restore.putBoolean(key,(Boolean)value);
                else if(value instanceof Float)restore.putFloat(key,(Float)value);else if(value instanceof java.util.Set)restore.putStringSet(key,new HashSet<>((java.util.Set<String>)value));
            }restore.commit();
        }
        finish(resultCode,result);
    }

    /** Focused drawing check for a canvas-only fix, preserving the full regression mode. */
    private void drawing(SharedPreferences prefs)throws Exception{
        prefs.edit().putStringSet("selected",new HashSet<>(Arrays.asList("main"))).putString("font_size","300").commit();
        for(String size:new String[]{"small","maximum"}){
            prefs.edit().putString("control_size",size).commit();main(()->invoke("show",new Class<?>[]{boolean.class},true));settle();
            main(()->{populateValues(1f);invoke("refresh",new Class<?>[0]);});settle();
            main(()->{
                checkNumeric("+10.0",size+"/300 focused drawing");
                View window=(View)get("window");ScrollView vertical=(ScrollView)views(window,ScrollView.class.getName()).get(0);
                Object strip=((List<?>)get("strips")).get(0);View rail=(View)field(strip.getClass(),"fader").get(strip);View value=(View)field(strip.getClass(),"value").get(strip);
                int top=rail.getTop();View parent=(View)rail.getParent();
                while(parent!=vertical.getChildAt(0)){top+=parent.getTop();parent=(View)parent.getParent();}
                vertical.scrollTo(0,Math.max(0,top-Math.min(value.getHeight(),vertical.getHeight()/3)));
            });settle();
            main(()->{
                View rail=views((View)get("window"),TARGET+".FaderView").get(0);
                // A zero marker proves the observed positive text size comes from a subsequent real draw.
                ((Paint)field(rail.getClass(),"p").get(rail)).setTextSize(0);rail.invalidate();
            });
            final boolean[] drawn={false};
            for(int i=0;i<30;i++){main(()->{View rail=views((View)get("window"),TARGET+".FaderView").get(0);drawn[0]=((Paint)field(rail.getClass(),"p").get(rail)).getTextSize()>0;});if(drawn[0])break;Thread.sleep(100);}
            main(()->{
                View rail=views((View)get("window"),TARGET+".FaderView").get(0);Class<?> type=rail.getClass();Paint paint=(Paint)field(type,"p").get(rail);
                check(drawn[0],"fader canvas actually drew after scrolling "+size);
                check((Boolean)field(type,"known").get(rail)&&Math.abs((Float)field(type,"value").get(rail)-1f)<.0001,"top cap has a known fixture value "+size);
                float start=(Float)call(type,rail,"dp",new Class<?>[]{float.class},1f),capHalf=(Float)call(type,rail,"dp",new Class<?>[]{float.class},12f);
                float capLeft=rail.getWidth()*.56f-capHalf;
                check(paint.measureText("+10")+start<=capLeft-1f+.01f,"positive tick clears cap with one pixel margin "+size);
                check(paint.measureText("-60")+start<=capLeft-1f+.01f,"negative tick clears cap with one pixel margin "+size);
                Rect visible=new Rect();rail.getLocalVisibleRect(visible);
                float capY=(Float)call(type,rail,"dp",new Class<?>[]{float.class},10f),capHalfHeight=(Float)call(type,rail,"dp",new Class<?>[]{float.class},7f);
                check(visible.height()>0&&capY-capHalfHeight>=visible.top&&capY+capHalfHeight<=visible.bottom,"cap is visible in native viewport "+size);
                check(!bool("hasSession")&&!(Boolean)call(serviceClass,service,"ready",new Class<?>[0]),"drawing fixture cannot control a mixer "+size);
                checkNumeric("+10.0",size+"/300 after draw");
            });
            screenshot(size+"-font300-cap");
        }
        main(()->{set("connected",false);((Map<?,?>)get("values")).clear();((Map<?,?>)get("stamps")).clear();});
    }

    private void startService()throws Exception{
        service=null;
        main(()->getTargetContext().startForegroundService(new Intent().setComponent(new ComponentName(TARGET,TARGET+".OverlayService")).setAction("show")));
        for(int i=0;i<50;i++){main(()->service=field(serviceClass,"live").get(null));if(service!=null)break;Thread.sleep(100);}
        check(service!=null,"font regression service restarted after drag-to-stop");
        main(()->{set("autoConnect",false);set("hasSession",false);});
    }

    private void fontSizes(SharedPreferences prefs)throws Exception{
        prefs.edit().remove("font_size").putString("control_size","small")
            .putStringSet("selected",new HashSet<>(Arrays.asList("main","dca1","mute1"))).commit();
        main(()->invoke("show",new Class<?>[]{boolean.class},true));settle();
        final float[][] defaultGeometry={null};
        main(()->{
            Object profile=get("profile");check("100".equals(call(profile.getClass(),profile,"fontSize",new Class<?>[0])),"missing font preference defaults to100percent");
            defaultGeometry[0]=geometry();
        });
        prefs.edit().putString("font_size","100").commit();
        main(()->invoke("onStartCommand",new Class<?>[]{Intent.class,int.class,int.class},new Intent().setAction("reload"),0,201));settle();
        main(()->check(Arrays.equals(defaultGeometry[0],geometry()),"explicit100percent preserves default panel geometry and typography"));

        Class<?> sizing=getTargetContext().getClassLoader().loadClass(TARGET+".PanelSizing");
        for(String value:new String[]{null,"","bad","74","301"})
            check(Math.abs((Float)call(sizing,null,"fontScale",new Class<?>[]{String.class},value)-1f)<.0001,"invalid font factor defaults safely: "+value);
        for(String value:new String[]{"75","100","125","150","175","200","250","300"})
            check(Math.abs((Float)call(sizing,null,"fontScale",new Class<?>[]{String.class},value)-Integer.parseInt(value)/100f)<.0001,"available font factor "+value);

        // A simulated borrowed-request reference makes the fixed return action visible.
        // It is never registered with ConnectivityManager and never changes any network.
        main(()->{set("wifiBorrowed",true);set("wifiRequest",new ConnectivityManager.NetworkCallback(){});set("offerReturnOnClose",false);});
        for(int i=0;i<SIZES.length;i++){
            final int sizeIndex=i;float priorName=0;float[] controlGeometry=null;
            for(String factor:FONT_SIZES){
                final float fontFactor=Integer.parseInt(factor)/100f;
                prefs.edit().putString("control_size",SIZES[i]).putString("font_size",factor).commit();
                main(()->invoke("onStartCommand",new Class<?>[]{Intent.class,int.class,int.class},new Intent().setAction("reload"),0,202));settle();
                final float[][] dimensions={null};final float[] nameSize={0};
                main(()->{
                    Object profile=get("profile");String context=SIZES[sizeIndex]+"/"+factor;
                    check(factor.equals(call(profile.getClass(),profile,"fontSize",new Class<?>[0])),"font factor persists after reload "+context);
                    check(SIZES[sizeIndex].equals(call(profile.getClass(),profile,"controlSize",new Class<?>[0])),"font change preserves chosen control size "+context);
                    View window=(View)get("window");WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");
                    check(window!=null&&window.isAttachedToWindow(),"font panel attached "+context);
                    check(params.width>0&&params.width<=window.getResources().getDisplayMetrics().widthPixels,"font panel width fits "+context);
                    check(params.height>0&&params.height<=window.getResources().getDisplayMetrics().heightPixels,"font panel height fits "+context);
                    List<?> strips=(List<?>)get("strips");check(strips.size()==3,"font panel retains every selected control "+context);
                    Object first=strips.get(0);ViewGroup strip=(ViewGroup)field(first.getClass(),"view").get(first);
                    TextView name=(TextView)strip.getChildAt(0);nameSize[0]=name.getTextSize();
                    View rail=(View)field(first.getClass(),"fader").get(first);Class<?> railType=rail.getClass();
                    check(Math.abs((Float)field(railType,"controlScale").get(rail)-SCALES[sizeIndex])<.0001,"font factor leaves control scale unchanged "+context);
                    check(Math.abs((Float)field(railType,"textScale").get(rail)-fontFactor)<.0001,"fader labels receive independent font factor "+context);
                    check(rail.getWidth()>0&&rail.getHeight()>0,"font scaling leaves a positive fader touch area "+context);
                    check(strip.getWidth()<=window.getWidth()-window.getPaddingLeft()-window.getPaddingRight(),"adapted strip fits viewport "+context);
                    dimensions[0]=new float[]{(Float)call(railType,rail,"dp",new Class<?>[]{float.class},12f),
                        (Float)call(railType,rail,"dp",new Class<?>[]{float.class},7f),(Float)call(railType,rail,"dp",new Class<?>[]{float.class},2f)};
                    ViewGroup header=(ViewGroup)((ViewGroup)window).getChildAt(0);
                    fullyVisible(header.getChildAt(header.getChildCount()-1),"close action reachable "+context);
                    fullyVisible(header.getChildAt(header.getChildCount()-2),"options action reachable "+context);
                    Button back=buttonStarting(window,"↩ ");check(back!=null,"return action retained "+context);fullyVisible(back,"return action reachable "+context);
                    ScrollView vertical=(ScrollView)views(window,ScrollView.class.getName()).get(0);
                    check(vertical.getHeight()>0,"font panel retains usable scrolling viewport "+context);
                    populateValues(1f);invoke("refresh",new Class<?>[0]);
                    check(!bool("hasSession")&&!(Boolean)call(serviceClass,service,"ready",new Class<?>[0]),"fixture has no ready mixer session "+context);
                });settle();
                main(()->checkNumeric("+10.0",SIZES[sizeIndex]+"/"+factor));
                if(factor.equals("300")&&(i==0||i==SIZES.length-1))screenshot((i==0?"small":"maximum")+"-font300-top");
                main(()->{Class<?> protocol=getTargetContext().getClassLoader().loadClass(TARGET+".XAirProtocol");populateValues((Float)call(protocol,null,"dbToNormalized",new Class<?>[]{float.class},-30f));invoke("refresh",new Class<?>[0]);});settle();
                main(()->{
                    checkNumeric("-30.0",SIZES[sizeIndex]+"/"+factor);
                    View window=(View)get("window");ScrollView vertical=(ScrollView)views(window,ScrollView.class.getName()).get(0);vertical.fullScroll(View.FOCUS_DOWN);
                    for(View row:views(window,HorizontalScrollView.class.getName()))((HorizontalScrollView)row).fullScroll(View.FOCUS_RIGHT);
                });settle();
                main(()->{
                    String context=SIZES[sizeIndex]+"/"+factor;View window=(View)get("window");ScrollView vertical=(ScrollView)views(window,ScrollView.class.getName()).get(0);
                    check(vertical.getChildAt(0).getHeight()<=vertical.getHeight()||vertical.getScrollY()>0,"bottom controls reachable after font scaling "+context);
                    HorizontalScrollView horizontal=(HorizontalScrollView)views(window,HorizontalScrollView.class.getName()).get(0);
                    check(horizontal.getChildAt(0).getWidth()<=horizontal.getWidth()||horizontal.getScrollX()>0,"last fader reachable after font scaling "+context);
                    for(Object strip:(List<?>)get("strips")){Button button=(Button)field(strip.getClass(),"mute").get(strip);check(button!=null&&button.getWidth()>0&&button.getHeight()>0,"mute remains laid out "+context);check(!button.isEnabled(),"fixture mute cannot send commands "+context);}
                });
                check(priorName==0||nameSize[0]>=priorName,"channel label grows or stays fitted to the viewport "+SIZES[i]+"/"+factor);priorName=nameSize[0];
                check(controlGeometry==null||Arrays.equals(controlGeometry,dimensions[0]),"cap and meter geometry independent of font factor "+SIZES[i]+"/"+factor);controlGeometry=dimensions[0];
                if(factor.equals("300")&&(i==0||i==SIZES.length-1))screenshot((i==0?"small":"maximum")+"-font300-bottom");
            }
        }
        main(()->{set("connected",false);((Map<?,?>)get("values")).clear();((Map<?,?>)get("stamps")).clear();set("wifiBorrowed",false);set("wifiRequest",null);});
    }
    private float[] geometry()throws Exception{
        View window=(View)get("window");WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");Object strip=((List<?>)get("strips")).get(0);
        ViewGroup column=(ViewGroup)field(strip.getClass(),"view").get(strip);View rail=(View)field(strip.getClass(),"fader").get(strip);Button mute=(Button)field(strip.getClass(),"mute").get(strip);
        return new float[]{params.width,params.height,column.getWidth(),rail.getHeight(),mute.getHeight(),((TextView)column.getChildAt(0)).getTextSize(),((TextView)field(strip.getClass(),"value").get(strip)).getTextSize()};
    }
    @SuppressWarnings("unchecked") private void populateValues(float faderValue)throws Exception{
        set("connected",true);Map<String,Object> values=(Map<String,Object>)get("values");Map<String,Long> stamps=(Map<String,Long>)get("stamps");long now=SystemClock.elapsedRealtime();
        for(Object strip:(List<?>)get("strips")){Object control=field(strip.getClass(),"control").get(strip);String fader=(String)field(control.getClass(),"faderPath").get(control),mute=(String)field(control.getClass(),"mutePath").get(control);
            if(fader!=null){values.put(fader,faderValue);stamps.put(fader,now);}if(mute!=null){values.put(mute,1);stamps.put(mute,now);}}
    }
    private void checkNumeric(String text,String context)throws Exception{
        for(Object strip:(List<?>)get("strips")){TextView value=(TextView)field(strip.getClass(),"value").get(strip);if(value==null)continue;
            check(text.equals(value.getText().toString()),"complete numeric value retained "+context+" "+text);
            check(value.getPaint().measureText(text)<=value.getWidth()-value.getPaddingLeft()-value.getPaddingRight()+1,"numeric text fits available width "+context+" "+text);
            check(value.getLayout()!=null&&value.getLayout().getLineCount()==1&&value.getLayout().getEllipsisCount(0)==0,"numeric value is not ellipsized "+context+" "+text);
            check(!((View)field(strip.getClass(),"fader").get(strip)).isEnabled(),"fixture fader cannot send commands "+context);
        }
    }
    private Button buttonStarting(View root,String prefix){if(root instanceof Button&&((Button)root).getText().toString().startsWith(prefix))return (Button)root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){Button found=buttonStarting(group.getChildAt(i),prefix);if(found!=null)return found;}}return null;}
    private void fullyVisible(View view,String message){Rect visible=new Rect();check(view.getWidth()>0&&view.getHeight()>0&&view.getGlobalVisibleRect(visible)&&visible.width()>=view.getWidth()&&visible.height()>=view.getHeight(),message);}

    private void sizes(SharedPreferences prefs)throws Exception{
        Class<?> sizing=getTargetContext().getClassLoader().loadClass(TARGET+".PanelSizing");
        String[] available=(String[])call(sizing,null,"controlSizes",new Class<?>[0]);
        check(Arrays.equals(available,SIZES),"all seven sizes selectable in model");
        int previousRail=0,previousMute=0;float previousName=0;
        for(int i=0;i<SIZES.length;i++){
            final int index=i;prefs.edit().putString("control_size",SIZES[i]).commit();main(()->invoke("show",new Class<?>[]{boolean.class},true));settle();
            if(i==SIZES.length-1)screenshot("maximum-mixed-top");
            final int[] dimensions=new int[2];final float[] nameSize=new float[1];
            main(()->{
                Object profile=get("profile");check(SIZES[index].equals(call(profile.getClass(),profile,"controlSize",new Class<?>[0])),"size persists "+SIZES[index]);
                View window=(View)get("window");check(window!=null&&window.isAttachedToWindow(),"panel attached "+SIZES[index]);
                WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");
                check(params.width<=window.getResources().getDisplayMetrics().widthPixels,"panel fits screen width "+SIZES[index]);
                check(params.height<=window.getResources().getDisplayMetrics().heightPixels,"panel fits screen height "+SIZES[index]);
                List<View> faders=views(window,TARGET+".FaderView");check(faders.size()==2,"both faders available "+SIZES[index]);
                List<?> strips=(List<?>)get("strips");check(strips.size()==3,"all selected controls retained "+SIZES[index]);
                Object first=strips.get(0);ViewGroup strip=(ViewGroup)field(first.getClass(),"view").get(first);
                TextView name=(TextView)strip.getChildAt(0);nameSize[0]=name.getTextSize();
                View rail=faders.get(0);check(Math.abs((Float)field(rail.getClass(),"controlScale").get(rail)-SCALES[index])<.001,"fader scale "+SIZES[index]);
                check(rail.getWidth()>0&&rail.getHeight()>0,"fader has touch area "+SIZES[index]);
                check(strip.getWidth()<=window.getWidth()-window.getPaddingLeft()-window.getPaddingRight(),"one strip fits viewport "+SIZES[index]);
                check(!rail.isEnabled(),"disconnected fader disabled "+SIZES[index]);
                Button mute=(Button)field(first.getClass(),"mute").get(first);check(!mute.isEnabled(),"disconnected mute disabled "+SIZES[index]);
                check(mute.getHeight()>=48*window.getResources().getDisplayMetrics().density,"mute touch target at least 48dp "+SIZES[index]);
                dimensions[0]=rail.getHeight();dimensions[1]=mute.getHeight();
                ScrollView scroll=(ScrollView)views(window,ScrollView.class.getName()).get(0);
                check(scroll.getHeight()>0,"vertical viewport usable "+SIZES[index]);
                scroll.fullScroll(View.FOCUS_DOWN);
                for(View child:views(window,HorizontalScrollView.class.getName()))((HorizontalScrollView)child).fullScroll(View.FOCUS_RIGHT);
            });settle();
            main(()->{
                View window=(View)get("window");ScrollView scroll=(ScrollView)views(window,ScrollView.class.getName()).get(0);
                int range=scroll.getChildAt(0).getHeight()-scroll.getHeight()+scroll.getPaddingTop()+scroll.getPaddingBottom();
                check(range<=0||scroll.getScrollY()>0,"vertical bottom reachable "+SIZES[index]);
                HorizontalScrollView horizontal=(HorizontalScrollView)views(window,HorizontalScrollView.class.getName()).get(0);
                check(horizontal.getChildAt(0).getWidth()<=horizontal.getWidth()||horizontal.getScrollX()>0,"last fader horizontally reachable "+SIZES[index]);
                check(!bool("hasSession")&&!bool("connected"),"layout does not connect to mixer "+SIZES[index]);
            });
            check(i==0||dimensions[0]>previousRail,"fader rail grows at "+SIZES[i]);
            check(i==0||dimensions[1]>=previousMute,"mute area does not shrink at "+SIZES[i]);
            check(i==0||nameSize[0]>previousName,"label grows at "+SIZES[i]);
            previousRail=dimensions[0];previousMute=dimensions[1];previousName=nameSize[0];
            if(i==SIZES.length-1)screenshot("maximum-mixed-bottom");
        }
    }
    private void muteOnly(SharedPreferences prefs)throws Exception{
        prefs.edit().putString("control_size","maximum").putStringSet("selected",new HashSet<>(Arrays.asList("mute1"))).commit();
        main(()->invoke("show",new Class<?>[]{boolean.class},true));settle();
        main(()->{
            View window=(View)get("window");check(views(window,TARGET+".FaderView").isEmpty(),"mute-only panel contains no fader");
            List<?> strips=(List<?>)get("strips");check(strips.size()==1,"mute-only panel contains one selected control");
            Object strip=strips.get(0);Button button=(Button)field(strip.getClass(),"mute").get(strip);
            check(button.getHeight()>=208*window.getResources().getDisplayMetrics().density,"maximum standalone mute is at least 208dp");
            check(button.getWidth()>0&&button.getWidth()<=window.getWidth(),"standalone mute fits screen");
            check(button.getText().toString().contains("\n"),"standalone title and mute state use separate lines");
        });
        screenshot("maximum-mute-only");
    }
    private void relativeFader(SharedPreferences prefs)throws Exception{
        prefs.edit().putString("control_size","maximum").putStringSet("selected",new HashSet<>(Arrays.asList("main"))).commit();
        main(()->invoke("show",new Class<?>[]{boolean.class},true));settle();
        main(()->{
            View rail=views((View)get("window"),TARGET+".FaderView").get(0);Class<?> type=rail.getClass();
            Field listener=field(type,"listener");Object previous=listener.get(rail);Class<?> callback=listener.getType();
            List<Float> changed=new ArrayList<>();Object fake=Proxy.newProxyInstance(callback.getClassLoader(),new Class<?>[]{callback},(proxy,method,args)->{if(method.getName().equals("changed"))changed.add((Float)args[0]);return null;});
            listener.set(rail,fake);
            try{
                call(type,rail,"update",new Class<?>[]{Float.class,boolean.class},Float.valueOf(.6f),true);
                float y=rail.getHeight()*.8f;
                touch(rail,MotionEvent.ACTION_DOWN,rail.getWidth()/2f,y);check(changed.isEmpty(),"touch far from cap does not jump volume");
                touch(rail,MotionEvent.ACTION_MOVE,rail.getWidth()/2f,y-20);
                float span=(Float)call(type,rail,"span",new Class<?>[0]);check(changed.size()==1&&Math.abs(changed.get(0)-(.6f+20/span))<.0001,"large fader movement remains relative");
                touch(rail,MotionEvent.ACTION_CANCEL,rail.getWidth()/2f,y-20);check(changed.size()==1,"cancel does not send another fader value");
                check(!(Boolean)field(type,"dragging").get(rail),"fader cancel clears dragging");
            }finally{listener.set(rail,previous);call(type,rail,"update",new Class<?>[]{Float.class,boolean.class},null,false);}
        });
    }
    private void drag()throws Exception{
        main(()->invoke("show",new Class<?>[]{boolean.class},false));settle();
        main(()->{
            WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");View bubble=(View)get("bubble");int ox=params.x,oy=params.y;
            float x=ox+params.width/2f,y=oy+params.height/2f;
            touch(bubble,MotionEvent.ACTION_DOWN,x,y);check(get("dismissTarget")==null,"press alone does not show dismiss target");
            touch(bubble,MotionEvent.ACTION_MOVE,x+1,y+1);check(get("dismissTarget")==null,"movement below slop does not show target");
            touch(bubble,MotionEvent.ACTION_MOVE,x-40,y+30);check(get("dismissTarget")!=null,"drag shows dismiss target");
            check(!bool("dismissArmed"),"ordinary drag does not arm dismissal");
            touch(bubble,MotionEvent.ACTION_CANCEL,x-40,y+30);
            check(get("dismissTarget")==null&&!bool("dismissArmed"),"cancel removes and resets target");
            check(params.x==ox&&params.y==oy,"cancel restores original bubble position");check(bubble.getAlpha()==1f,"cancel restores bubble opacity");
            touch(bubble,MotionEvent.ACTION_DOWN,x,y);touch(bubble,MotionEvent.ACTION_MOVE,x-50,y+35);touch(bubble,MotionEvent.ACTION_UP,x-50,y+35);
            check(get("dismissTarget")==null,"off-target release removes target");
            check(params.x==0||params.x==bubble.getResources().getDisplayMetrics().widthPixels-params.width,"off-target release snaps to an edge");
            SharedPreferences prefs=getTargetContext().getSharedPreferences("profile",Context.MODE_PRIVATE);
            check(prefs.getInt("x",-1)==params.x&&prefs.getInt("y",-1)==params.y,"off-target drag persists bubble position");
            check(!bool("destroyed"),"off-target drag keeps service alive");
        });
        main(()->{
            View bubble=(View)get("bubble");WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");float x=params.x+params.width/2f,y=params.y+params.height/2f;
            touch(bubble,MotionEvent.ACTION_DOWN,x,y);touch(bubble,MotionEvent.ACTION_MOVE,x+35,y+35);check(get("dismissTarget")!=null,"second drag shows target");
            View old=(View)get("dismissTarget");invoke("show",new Class<?>[]{boolean.class},true);
            check(get("dismissTarget")==null&&!old.isAttachedToWindow(),"rebuilding expanded panel removes drag target");
            invoke("show",new Class<?>[]{boolean.class},false);
            bubble=(View)get("bubble");params=(WindowManager.LayoutParams)get("params");x=params.x+params.width/2f;y=params.y+params.height/2f;
            touch(bubble,MotionEvent.ACTION_DOWN,x,y);touch(bubble,MotionEvent.ACTION_MOVE,x+35,y+35);old=(View)get("dismissTarget");
            invoke("onConfigurationChanged",new Class<?>[]{Configuration.class},new Configuration(getTargetContext().getResources().getConfiguration()));
            check(get("dismissTarget")==null&&!old.isAttachedToWindow(),"configuration rebuild removes drag target");
        });settle();
        final View[] target=new View[1];
        main(()->{
            View bubble=(View)get("bubble");WindowManager.LayoutParams params=(WindowManager.LayoutParams)get("params");float x=params.x+params.width/2f,y=params.y+params.height/2f;
            touch(bubble,MotionEvent.ACTION_DOWN,x,y);touch(bubble,MotionEvent.ACTION_MOVE,x+35,y+35);target[0]=(View)get("dismissTarget");
            float dx=(Float)get("dismissCenterX"),dy=(Float)get("dismissCenterY");touch(bubble,MotionEvent.ACTION_MOVE,dx,dy);
            check(bool("dismissArmed"),"bubble over cross arms dismissal");check(bubble.getAlpha()<1f,"armed bubble provides visual feedback");
        });
        settle();screenshot("dismiss-target-armed");
        main(()->{touch((View)get("bubble"),MotionEvent.ACTION_UP,(Float)get("dismissCenterX"),(Float)get("dismissCenterY"));check(get("dismissTarget")==null,"drop removes target immediately");});
        for(int i=0;i<50;i++){final boolean[] stopped={false};main(()->stopped[0]=field(serviceClass,"live").get(null)==null);if(stopped[0])break;Thread.sleep(100);}
        main(()->{check(field(serviceClass,"live").get(null)==null&&bool("destroyed"),"drop on cross stops overlay service");check(!target[0].isAttachedToWindow(),"stopped service leaves no dismiss overlay");check(!((View)get("window")).isAttachedToWindow(),"stopped service removes bubble window");});
    }
    private void touch(View view,int action,float x,float y){long now=SystemClock.uptimeMillis();if(action==MotionEvent.ACTION_DOWN)downTime=now;MotionEvent event=MotionEvent.obtain(downTime,now,action,x,y,0);try{view.dispatchTouchEvent(event);}finally{event.recycle();}}
    private void screenshot(String name)throws Exception{Bitmap image=getUiAutomation().takeScreenshot();check(image!=null,"screenshot available "+name);try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getCacheDir(),"large-controls-"+name+".png"))){check(image.compress(Bitmap.CompressFormat.PNG,100,output),"screenshot saved "+name);}finally{image.recycle();}}
    private List<View> views(View root,String className){List<View> out=new ArrayList<>();if(root.getClass().getName().equals(className))out.add(root);if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)out.addAll(views(group.getChildAt(i),className));}return out;}
    private Field field(Class<?> owner,String name)throws Exception{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field;}
    private Object get(String name)throws Exception{return field(serviceClass,name).get(service);}
    private boolean bool(String name)throws Exception{return (Boolean)get(name);}
    private void set(String name,Object value)throws Exception{field(serviceClass,name).set(service,value);}
    private Object call(Class<?> owner,Object target,String name,Class<?>[] types,Object...args)throws Exception{Method method=owner.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args);}
    private void invoke(String name,Class<?>[] types,Object...args)throws Exception{call(serviceClass,service,name,types,args);}
    private void check(boolean pass,String message){checks++;if(!pass)throw new AssertionError(message);}
    private void settle()throws Exception{waitForIdleSync();Thread.sleep(160);waitForIdleSync();}
    private interface Action{void run()throws Exception;}
    private void main(Action action)throws Exception{Throwable[] failure=new Throwable[1];runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new Exception("Main-thread assertion failed",failure[0]);}
}
