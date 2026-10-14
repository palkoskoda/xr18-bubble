package sk.palko.xr18bubble.largecontrolstest;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
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
    private Class<?> serviceClass;
    private Object service;
    private int checks;
    private long downTime;

    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
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
            sizes(prefs);muteOnly(prefs);relativeFader(prefs);drag();
            result.putString("stream","LargeControlsInstrumentation passed: "+checks+" checks; display="+
                getTargetContext().getResources().getDisplayMetrics().widthPixels+"x"+getTargetContext().getResources().getDisplayMetrics().heightPixels+
                ", fontScale="+getTargetContext().getResources().getConfiguration().fontScale+"; no mixer session or SET\n");
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
