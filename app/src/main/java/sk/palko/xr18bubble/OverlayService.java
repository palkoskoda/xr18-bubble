package sk.palko.xr18bubble;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.net.*;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class OverlayService extends Service implements MixerClient.Listener {
    static OverlayService live;
    static final String SHOW="show",PANEL="panel",CONNECT="connect",WIFI="wifi",DISCOVER="discover",RELOAD="reload",PROFILE="profile",STOP="stop",LEARN_WIFI="learn_wifi";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Profile profile;
    private MixerClient mixer;
    private WindowManager wm;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback wifiRequest,watcher,identityWatcher;
    private final Map<Network,WifiIdentity> wifiIdentities=new HashMap<>();
    private final Map<String,float[]> meters=new HashMap<>();
    private final Map<String,Long> meterStamps=new HashMap<>();
    private Network currentNetwork;
    private boolean autoConnect,expanded,destroyed,requestingWifi,hasSession,wifiBorrowed,offerReturnOnClose;
    private AlertDialog returnDialog;
    private Boolean drawnReady;
    private long wifiGeneration;
    private View window,statusDot,dismissTarget;
    private TextView dismissCircle;
    private boolean dismissArmed;
    private float dismissCenterX,dismissCenterY;
    private WindowManager.LayoutParams params;
    private TextView statusView;
    private BubbleView bubble;
    String status;
    boolean connected;
    final Map<String,String> found=new LinkedHashMap<>();
    private final Map<String,Object> values=new HashMap<>();
    private final Map<String,Long> stamps=new HashMap<>();
    private final Map<String,Long> pendingMute=new HashMap<>();
    private final List<Strip> strips=new ArrayList<>();
    private Set<String> required=Collections.emptySet();
    private String s(String key,Object...args){return L.s(this,key,args);}

    public void onCreate(){
        super.onCreate();live=this;profile=new Profile(this);wm=getSystemService(WindowManager.class);cm=getSystemService(ConnectivityManager.class);newClient();status=s("disconnected");
        Notification notification=notification();if(Build.VERSION.SDK_INT>=34)startForeground(18,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(18,notification);
        watcher=new ConnectivityManager.NetworkCallback(){
            public void onLost(Network n){handler.post(()->{wifiIdentities.remove(n);if(!destroyed&&n.equals(currentNetwork)){currentNetwork=null;hasSession=false;invalidateConnection(s("wifi_lost"));}});}
            public void onAvailable(Network n){handler.post(()->{if(!destroyed&&autoConnect&&wifiRequest==null&&!n.equals(currentNetwork)){if(profile.get("ssid").isEmpty())connectCurrent(false);else if(expanded)requestWifi();}});}
        };
        cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),watcher);observeWifiIdentity();handler.post(refreshLoop);
    }
    private Notification notification(){
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("controls",s("notification_title"),NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,OverlayService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"controls").setSmallIcon(R.drawable.notification_icon).setContentTitle(s("app_name")).setContentText(s("notification_body")).setContentIntent(open).setOngoing(true).addAction(new Notification.Action.Builder(null,s("stop"),stop).build()).build();
    }
    private void newClient(){required=profile.requiredPaths();mixer=new MixerClient(this,this);mixer.setRequiredPaths(required);mixer.setMetersEnabled(expanded);}
    private void observeWifiIdentity(){
        if(identityWatcher!=null)try{cm.unregisterNetworkCallback(identityWatcher);}catch(Exception ignored){}
        if(Build.VERSION.SDK_INT>=31)identityWatcher=new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO){public void onCapabilitiesChanged(Network n,NetworkCapabilities c){WifiIdentity identity=WifiIdentity.from(c);handler.post(()->receivedIdentity(n,identity));}};
        else identityWatcher=new ConnectivityManager.NetworkCallback(){public void onCapabilitiesChanged(Network n,NetworkCapabilities c){WifiIdentity identity=WifiIdentity.from(c);handler.post(()->receivedIdentity(n,identity));}};
        cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),identityWatcher);
    }
    private void receivedIdentity(Network n,WifiIdentity identity){if(destroyed||identity==null)return;wifiIdentities.put(n,identity);if(connected&&n.equals(currentNetwork))rememberSessionWifi();}
    private WifiIdentity sessionWifi(){WifiIdentity identity=wifiIdentities.get(currentNetwork);return identity==null?WifiIdentity.read(this,currentNetwork):identity;}
    private void rememberSessionWifi(){if(!connected||currentNetwork==null)return;WifiIdentity identity=sessionWifi();if(identity!=null)profile.rememberWifi(identity);}
    public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&intent.hasExtra("wifi_network")){Network n=intent.getParcelableExtra("wifi_network");String name=intent.getStringExtra("wifi_ssid"),security=intent.getStringExtra("wifi_security");if(n!=null&&name!=null&&!name.isEmpty())wifiIdentities.put(n,new WifiIdentity(name,security==null?"UNKNOWN":security));}
        String action=intent==null?SHOW:intent.getAction();if(STOP.equals(action)){stopSelf();return START_NOT_STICKY;}
        if(PANEL.equals(action)){if(Settings.canDrawOverlays(this))show(true);}
        else if(RELOAD.equals(action)){required=profile.requiredPaths();mixer.setRequiredPaths(required);if(!connected)status=s(hasSession?"connecting_short":"disconnected");getSystemService(NotificationManager.class).notify(18,notification());if(window!=null)show(expanded);}
        else if(PROFILE.equals(action)){autoConnect=false;releaseWifi();wifiBorrowed=false;currentNetwork=null;hasSession=false;invalidateConnection(s("disconnected"));if(window!=null)show(expanded);}
        else if(CONNECT.equals(action))connectCurrent(false);
        else if(DISCOVER.equals(action))connectCurrent(true);
        else if(WIFI.equals(action))requestWifi();
        else if(LEARN_WIFI.equals(action)){observeWifiIdentity();rememberSessionWifi();}
        if(window==null&&Settings.canDrawOverlays(this))show(false);return START_NOT_STICKY;
    }
    boolean ready(){if(mixer==null||!mixer.isReady())return false;for(String path:required)if(fresh(path)==null)return false;return true;}
    String displayStatus(){return ready()?s("connected"):connected?s("syncing"):status;}
    private void invalidateConnection(String reason){mixer.close();newClient();values.clear();stamps.clear();pendingMute.clear();meters.clear();meterStamps.clear();connected=false;status=reason;refresh();}
    private Network currentWifi(){
        if(currentNetwork!=null&&cm.getNetworkCapabilities(currentNetwork)!=null)return currentNetwork;
        Network active=cm.getActiveNetwork();if(active!=null){NetworkCapabilities cap=cm.getNetworkCapabilities(active);if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return active;}
        for(Network n:cm.getAllNetworks()){NetworkCapabilities cap=cm.getNetworkCapabilities(n);if(cap!=null&&cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}return null;
    }
    private void connectCurrent(boolean discover){
        Network n=currentWifi();autoConnect=true;if(n==null){hasSession=false;invalidateConnection(s("no_wifi"));return;}startMixer(n,discover);
    }
    private void startMixer(Network n,boolean discover){
        invalidateConnection(s("connecting_mixer"));currentNetwork=n;hasSession=true;
        if(discover||profile.get("host").isEmpty()){
            hasSession=false;found.clear();mixer.discover(n);status=s("searching_mixers");final MixerClient search=mixer;
            handler.postDelayed(()->{if(!destroyed&&mixer==search&&!connected){status=s(found.isEmpty()?"no_mixers":"choose_mixer");refresh();}},3000);
        }else mixer.connect(profile.get("host"),n);
    }
    private void releaseWifi(){wifiGeneration++;requestingWifi=false;offerReturnOnClose=false;if(returnDialog!=null){AlertDialog dialog=returnDialog;returnDialog=null;dialog.dismiss();}if(wifiRequest!=null){try{cm.unregisterNetworkCallback(wifiRequest);}catch(Exception ignored){}wifiRequest=null;}}
    private boolean wifiPermission(){String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;return checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private void requestWifi(){
        if(profile.get("ssid").isEmpty()){connectCurrent(false);return;}
        if(!wifiPermission()){launchMain(true,0);return;}
        if(requestingWifi)return;
        if(wifiRequest!=null&&currentNetwork!=null&&cm.getNetworkCapabilities(currentNetwork)!=null){if(!hasSession)startMixer(currentNetwork,false);return;}
        Network existing=currentWifi();WifiIdentity identity=wifiIdentities.get(existing);if(identity==null)identity=WifiIdentity.read(this,existing);
        if(identity!=null&&identity.ssid.equals(profile.get("ssid"))){if(!hasSession||!existing.equals(currentNetwork))startMixer(existing,false);return;}
        try{if(!"OPEN".equals(profile.get("security"))&&profile.password().isEmpty()){status=s("wifi_password_needed");launchWifiSetup();return;}}catch(Exception e){status=s("password_error");launchWifiSetup();return;}
        final boolean previousWifi=existing!=null;
        profile.put("return_ssid",identity==null?"":identity.ssid);wifiBorrowed=false;
        autoConnect=false;releaseWifi();currentNetwork=null;hasSession=false;invalidateConnection(s("connecting_wifi",profile.get("ssid")));requestingWifi=true;
        try{
            WifiNetworkSpecifier.Builder builder=new WifiNetworkSpecifier.Builder().setSsid(profile.get("ssid"));String bssid=profile.get("bssid");if(!bssid.isEmpty())builder.setBssid(MacAddress.fromString(bssid));String password=profile.password();
            if(!password.isEmpty()){if("WPA3".equals(profile.get("security")))builder.setWpa3Passphrase(password);else builder.setWpa2Passphrase(password);}
            final long generation=wifiGeneration;
            wifiRequest=new ConnectivityManager.NetworkCallback(){
                private boolean valid(){return !destroyed&&generation==wifiGeneration&&wifiRequest==this;}
                public void onAvailable(Network n){handler.post(()->{if(valid()){requestingWifi=false;wifiBorrowed=previousWifi;offerReturnOnClose=previousWifi&&expanded;startMixer(n,false);if(window!=null&&expanded)show(true);}});}
                public void onUnavailable(){handler.post(()->{if(valid()){releaseWifi();currentNetwork=null;hasSession=false;invalidateConnection(s("wifi_failed"));}});}
                public void onLost(Network n){handler.post(()->{if(valid()&&n.equals(currentNetwork)){currentNetwork=null;requestingWifi=false;hasSession=false;invalidateConnection(s("wifi_lost"));}});}
            };
            cm.requestNetwork(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(builder.build()).build(),wifiRequest,30000);
        }catch(Exception e){releaseWifi();status=s("wifi_failed");refresh();}
    }
    private void ensureConnection(){
        if(ready()||requestingWifi)return;
        if(profile.get("host").isEmpty()){launchMain(false,0);return;}
        if(profile.get("ssid").isEmpty()&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&!"yes".equals(profile.get("wifi_identity_asked"))){launchMain(true,0);return;}
        if(hasSession&&currentNetwork!=null&&cm.getNetworkCapabilities(currentNetwork)!=null){
            if(profile.get("ssid").isEmpty()||wifiRequest!=null)return;
            WifiIdentity identity=sessionWifi();if(identity!=null&&identity.ssid.equals(profile.get("ssid")))return;
        }
        if(profile.get("ssid").isEmpty())connectCurrent(false);else requestWifi();
    }
    private void openControls(){offerReturnOnClose=false;show(true);if(!"off".equals(profile.get("auto_connect")))ensureConnection();}
    public void onStatus(String text,boolean ok){status=text;connected=ok;if(!ok){values.clear();stamps.clear();pendingMute.clear();meters.clear();meterStamps.clear();}else rememberSessionWifi();refresh();}
    public void onMeters(String bank,float[] db){if(!connected||!expanded)return;meters.put(bank,db);meterStamps.put(bank,SystemClock.elapsedRealtime());for(Strip strip:strips)strip.updateMeter();}
    public void onMetersCleared(String bank){meters.remove(bank);meterStamps.remove(bank);for(Strip strip:strips)strip.updateMeter();}
    public void onValue(String path,Object value){Object old=values.put(path,value);stamps.put(path,SystemClock.elapsedRealtime());boolean pending=pendingMute.remove(path)!=null;if(!Objects.equals(old,value)||pending)refresh();}
    public void onDiscovered(String ip,String name,String model){found.put(ip,name+" · "+model);}
    private Object fresh(String path){Long stamp=stamps.get(path);return connected&&stamp!=null&&SystemClock.elapsedRealtime()-stamp<3500?values.get(path):null;}
    private final Runnable refreshLoop=new Runnable(){public void run(){refresh();handler.postDelayed(this,400);}};
    private void refresh(){
        boolean ok=ready();if(statusView!=null){Ui.updateText(statusView,s(ok?"connected_short":connected?"syncing_short":hasSession||requestingWifi?"connecting_short":"disconnected"));statusView.setTextColor(ok?Ui.GREEN:Ui.GOLD);statusView.setContentDescription(displayStatus());}
        if(statusDot!=null&&(drawnReady==null||drawnReady!=ok)){drawnReady=ok;statusDot.setBackground(Ui.bg(ok?Ui.GREEN:Ui.GOLD,Ui.dp(this,5)));}
        if(bubble!=null){bubble.invalidate();bubble.setContentDescription(s("app_name")+", "+displayStatus());}
        for(Strip strip:strips)strip.update(ok);
    }
    private void launchMain(boolean connect,int tab){
        if(window!=null)show(false);Intent intent=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("tab",tab);if(connect)intent.putExtra("connect",true);startActivity(intent);
    }
    private void launchWifiSetup(){if(window!=null)show(false);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("wifi_setup",true));}
    private String returnWifiLabel(){String name=profile.get("return_ssid");return name.isEmpty()?s("return_wifi_generic"):s("return_wifi",name);}
    private void returnPreviousWifi(){
        autoConnect=false;wifiBorrowed=false;releaseWifi();currentNetwork=null;hasSession=false;invalidateConnection(s("returning_wifi"));show(false);
    }
    private void closePanel(){
        if(!wifiBorrowed||wifiRequest==null||!offerReturnOnClose){show(false);return;}
        if(returnDialog!=null)return;
        final long generation=wifiGeneration;final ConnectivityManager.NetworkCallback request=wifiRequest;
        returnDialog=new AlertDialog.Builder(this,Ui.isDark(this)?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert).setTitle(s("return_wifi_question")).setMessage(returnWifiLabel()+"\n\n"+s("return_wifi_explanation")).setPositiveButton(s("return_wifi_yes"),(d,n)->{if(generation==wifiGeneration&&request==wifiRequest&&wifiBorrowed)returnPreviousWifi();}).setNegativeButton(s("stay_on_mixer_wifi"),(d,n)->{if(generation==wifiGeneration&&request==wifiRequest){offerReturnOnClose=false;show(false);}}).setNeutralButton(s("cancel"),null).create();
        if(window!=null)window.setVisibility(View.INVISIBLE);
        returnDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);returnDialog.setOnDismissListener(d->{returnDialog=null;if(window!=null)window.setVisibility(View.VISIBLE);});returnDialog.show();
    }
    private Button tool(String text,String description,Runnable click){Button button=Ui.button(this,text,Ui.BG);button.setTextSize(17);button.setContentDescription(description);button.setOnClickListener(v->click.run());return button;}
    /** Fixed labels/actions surround a scrollable body, so large controls remain reachable. */
    private int textHeight(float sp,int lines,int paddingDp){Paint p=new Paint();p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,sp,getResources().getDisplayMetrics()));Paint.FontMetrics fm=p.getFontMetrics();return (int)Math.ceil((fm.bottom-fm.top)*lines/getResources().getDisplayMetrics().density)+paddingDp;}
    private float fontScale(){return PanelSizing.fontScale(profile.fontSize());}
    private int nameHeight(float scale){return Math.max(Math.round(18*scale),textHeight(10*scale*fontScale(),scale*fontScale()>1.35f?2:1,2));}
    private int valueHeight(float scale){return Math.max(Math.round(20*scale),textHeight(14*scale*fontScale(),1,2));}
    private int muteHeight(float scale){return Math.max(PanelSizing.muteHeightDp(profile.controlSize()),textHeight(11*scale*fontScale(),2,8));}
    private void fitText(TextView view,float sp){view.setTextSize(sp);view.setAutoSizeTextTypeUniformWithConfiguration(8,Math.max(9,(int)Math.ceil(sp)),1,android.util.TypedValue.COMPLEX_UNIT_SP);}
    private int textWidth(float sp,String text,boolean mono){Paint p=new Paint();p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,sp,getResources().getDisplayMetrics()));if(mono)p.setTypeface(Typeface.MONOSPACE);return (int)Math.ceil(p.measureText(text)/getResources().getDisplayMetrics().density);}
    private void hideDismissTarget(){if(dismissTarget!=null){try{wm.removeView(dismissTarget);}catch(IllegalArgumentException ignored){}dismissTarget=null;}dismissCircle=null;dismissArmed=false;if(bubble!=null)bubble.setAlpha(1f);}
    private void showDismissTarget(int screenW,int screenH){
        if(dismissTarget!=null)return;
        LinearLayout target=Ui.column(this);target.setGravity(Gravity.CENTER_HORIZONTAL);
        dismissCircle=Ui.text(this,"×",42,Ui.RED);dismissCircle.setGravity(Gravity.CENTER);dismissCircle.setBackground(Ui.bg(Ui.CARD,Ui.dp(this,40)));dismissCircle.setContentDescription(s("stop_bubble"));target.addView(dismissCircle,new LinearLayout.LayoutParams(Ui.dp(this,80),Ui.dp(this,80)));
        TextView caption=Ui.text(this,s("stop_bubble"),12,Ui.TEXT);caption.setGravity(Gravity.CENTER);caption.setMaxLines(2);caption.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),0);caption.setBackground(Ui.bg(Ui.BG,Ui.dp(this,8)));LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-2,-2);label.topMargin=Ui.dp(this,6);target.addView(caption,label);
        WindowManager.LayoutParams drop=new WindowManager.LayoutParams(Ui.dp(this,200),Ui.dp(this,126),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);drop.gravity=Gravity.TOP|Gravity.LEFT;drop.x=(screenW-drop.width)/2;drop.y=Math.max(0,screenH-drop.height-Ui.dp(this,48));dismissCenterX=drop.x+drop.width/2f;dismissCenterY=drop.y+Ui.dp(this,40);
        try{wm.addView(target,drop);dismissTarget=target;}catch(RuntimeException ignored){dismissCircle=null;}
    }
    private void updateDismissTarget(){
        if(dismissTarget==null)return;float dx=params.x+params.width/2f-dismissCenterX,dy=params.y+params.height/2f-dismissCenterY;boolean armed=dx*dx+dy*dy<=Math.pow(Ui.dp(this,64),2);
        if(armed!=dismissArmed){dismissArmed=armed;dismissCircle.setBackground(Ui.bg(armed?Ui.RED:Ui.CARD,Ui.dp(this,40)));dismissCircle.setTextColor(armed?Ui.TEXT:Ui.RED);bubble.setAlpha(armed?.5f:1f);if(armed)bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);}
    }
    private void show(boolean open){
        hideDismissTarget();if(window!=null){wm.removeView(window);window=null;}strips.clear();statusView=null;statusDot=null;drawnReady=null;bubble=null;expanded=open;
        float density=getResources().getDisplayMetrics().density;int screenW=getResources().getDisplayMetrics().widthPixels,screenH=getResources().getDisplayMetrics().heightPixels;
        List<XAirProtocol.Control> faders=new ArrayList<>(),buttons=new ArrayList<>();for(XAirProtocol.Control c:profile.selected()){if(profile.volume(c))faders.add(c);else if(profile.mute(c))buttons.add(c);}
        mixer.setMetersEnabled(open&&!faders.isEmpty());if(!open){meters.clear();meterStamps.clear();}
        String size=profile.controlSize();float scale=PanelSizing.controlScale(size),headerScale=Math.min(2.2f,scale),textFactor=fontScale(),chromeScale=Math.min(3f,headerScale*textFactor);
        int availableWidth=Math.max(96,(int)(screenW/density)-16),availableHeight=Math.max(96,(int)(screenH/density)-64);
        int headerHeight=Math.max(PanelSizing.headerHeightDp(size),Math.max(textHeight(17*Math.min(2,scale),1,0),textHeight(10*chromeScale,1,0))),statusHeight=Math.max(PanelSizing.statusHeightDp(size),textHeight(9*chromeScale,1,0));
        int muteHeight=muteHeight(scale);boolean stripMute=false;for(XAirProtocol.Control c:faders)stripMute|=profile.mute(c);
        int faderHeight=Math.max(PanelSizing.faderHeightDp(size),nameHeight(scale)+valueHeight(scale)+(stripMute?muteHeight+3:0)+Math.round(80*scale));
        int returnHeight=wifiBorrowed&&wifiRequest!=null?Math.max(PanelSizing.returnHeightDp(size),textHeight(10*chromeScale,1,8)+6):0;
        int bodyHeight=(faders.isEmpty()?0:faderHeight)+(buttons.isEmpty()?0:muteHeight+6);if(bodyHeight==0)bodyHeight=80;
        int desiredStrip=PanelSizing.stripWidth(size);if(textFactor>1)desiredStrip=Math.max(desiredStrip,textWidth(14*scale*textFactor,"+10.0",true)+10);
        int columns=Math.max(faders.size(),buttons.size());int desiredWidth=columns==0?180:columns*desiredStrip+Math.max(0,columns-1)*6+16;
        if(textFactor>1)desiredWidth=Math.max(desiredWidth,16+12+Math.round(headerHeight*52f/24)+textWidth(10*chromeScale,"XR18",false)+8);
        int widthDp=Math.min(availableWidth,Math.max(96,desiredWidth)),heightDp=Math.min(availableHeight,16+headerHeight+statusHeight+bodyHeight+returnHeight),stripWidth=Math.min(desiredStrip,widthDp-16);
        int diameter=PanelSizing.bubbleDiameter(profile.bubbleSize()),width=open?Ui.dp(this,widthDp):Ui.dp(this,diameter);
        params=new WindowManager.LayoutParams(width,Ui.dp(this,open?heightDp:diameter),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            open?WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH:WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.LEFT;int bx=profile.position("x",screenW-Ui.dp(this,diameter+4)),by=profile.position("y",Ui.dp(this,120));
        params.x=open?(bx+Ui.dp(this,diameter)/2<screenW/2?Ui.dp(this,8):screenW-width-Ui.dp(this,8)):Math.max(0,Math.min(bx,screenW-width));
        params.y=Math.max(0,Math.min(by,screenH-params.height-Ui.dp(this,48)));
        if(!open){
            bubble=new BubbleView();bubble.setElevation(Ui.dp(this,5));window=bubble;
            bubble.setOnTouchListener(new View.OnTouchListener(){float x,y;int ox,oy;boolean moved;
                public boolean onTouch(View v,MotionEvent event){
                    switch(event.getActionMasked()){
                        case MotionEvent.ACTION_DOWN:x=event.getRawX();y=event.getRawY();ox=params.x;oy=params.y;moved=false;return true;
                        case MotionEvent.ACTION_MOVE:
                            if(Math.abs(event.getRawX()-x)+Math.abs(event.getRawY()-y)>Ui.dp(OverlayService.this,6))moved=true;
                            if(moved){showDismissTarget(screenW,screenH);params.x=Math.max(0,Math.min(screenW-width,ox+(int)(event.getRawX()-x)));params.y=Math.max(0,Math.min(screenH-params.height-Ui.dp(OverlayService.this,48),oy+(int)(event.getRawY()-y)));wm.updateViewLayout(window,params);updateDismissTarget();}return true;
                        case MotionEvent.ACTION_UP:
                            boolean stop=moved&&dismissArmed;hideDismissTarget();if(stop){stopSelf();return true;}if(!moved){openControls();return true;}params.x=params.x+width/2<screenW/2?0:screenW-width;profile.setPosition("x",params.x);profile.setPosition("y",params.y);wm.updateViewLayout(window,params);return true;
                        case MotionEvent.ACTION_CANCEL:hideDismissTarget();params.x=ox;params.y=oy;wm.updateViewLayout(window,params);return true;
                    }return true;
                }
            });
        }else{
            LinearLayout panel=Ui.column(this);panel.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));panel.setBackground(Ui.bg(Ui.BG,Ui.dp(this,10)));panel.setElevation(Ui.dp(this,10));panel.setFocusableInTouchMode(true);
            panel.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){closePanel();return true;}return false;});panel.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_OUTSIDE){closePanel();return true;}return false;});
            LinearLayout header=Ui.row(this);statusDot=new View(this);LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(Ui.dp(this,7*Math.min(2,scale)),Ui.dp(this,7*Math.min(2,scale)));dotParams.rightMargin=Ui.dp(this,5);header.addView(statusDot,dotParams);
            TextView title=Ui.title(this,widthDp>=128?(profile.get("name").isEmpty()?"XR18":profile.get("name")):"",10);title.setTextSize(10*chromeScale);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            Button options=tool("⋯",s("mixers"),()->launchMain(false,0)),close=tool("×",s("bubble"),()->closePanel());options.setTextSize(17*Math.min(2,scale));close.setTextSize(17*Math.min(2,scale));
            header.addView(options,new LinearLayout.LayoutParams(Ui.dp(this,Math.round(headerHeight*28f/24)),Ui.dp(this,headerHeight)));
            header.addView(close,new LinearLayout.LayoutParams(Ui.dp(this,headerHeight),Ui.dp(this,headerHeight)));panel.addView(header,new LinearLayout.LayoutParams(-1,Ui.dp(this,headerHeight)));
            statusView=Ui.text(this,"",9,Ui.GOLD);statusView.setTextSize(9*chromeScale);statusView.setSingleLine();statusView.setEllipsize(TextUtils.TruncateAt.END);statusView.setOnClickListener(v->ensureConnection());panel.addView(statusView,new LinearLayout.LayoutParams(-1,Ui.dp(this,statusHeight)));
            ScrollView vertical=new ScrollView(this);vertical.setFillViewport(false);vertical.setVerticalScrollBarEnabled(bodyHeight>heightDp-headerHeight-statusHeight-returnHeight-16);LinearLayout body=Ui.column(this);
            if(!faders.isEmpty()){
                HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(faders.size()*(stripWidth+6)>widthDp-16);LinearLayout row=Ui.row(this);row.setGravity(Gravity.TOP);
                for(XAirProtocol.Control c:faders){Strip strip=new Strip(c,true);strips.add(strip);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,stripWidth),-1);lp.rightMargin=Ui.dp(this,6);row.addView(strip.view,lp);}scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));body.addView(scroll,new LinearLayout.LayoutParams(-1,Ui.dp(this,faderHeight)));
            }
            if(!buttons.isEmpty()){
                HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(buttons.size()*(stripWidth+6)>widthDp-16);LinearLayout row=Ui.row(this);
                for(XAirProtocol.Control c:buttons){Strip strip=new Strip(c,false);strips.add(strip);int buttonWidth=buttons.size()==1?widthDp-16:stripWidth;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Ui.dp(this,buttonWidth),Ui.dp(this,muteHeight));lp.rightMargin=Ui.dp(this,6);row.addView(strip.view,lp);}scroll.addView(row,new HorizontalScrollView.LayoutParams(-2,-1));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,muteHeight));lp.topMargin=Ui.dp(this,6);body.addView(scroll,lp);
            }
            if(faders.isEmpty()&&buttons.isEmpty()){Button choose=Ui.button(this,s("edit_controls"),Ui.CARD);choose.setTextSize(11);choose.setOnClickListener(v->launchMain(false,1));body.addView(choose,new LinearLayout.LayoutParams(-1,Ui.dp(this,80)));}
            vertical.addView(body,new ScrollView.LayoutParams(-1,-2));panel.addView(vertical,new LinearLayout.LayoutParams(-1,0,1));
            if(returnHeight>0){Button back=Ui.button(this,"↩ "+returnWifiLabel(),Ui.LINE);back.setTextSize(10*chromeScale);back.setSingleLine();back.setEllipsize(TextUtils.TruncateAt.END);back.setContentDescription(returnWifiLabel());back.setOnClickListener(v->returnPreviousWifi());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(this,returnHeight-6));lp.topMargin=Ui.dp(this,6);panel.addView(back,lp);}
            window=panel;
        }
        try{if(returnDialog!=null)window.setVisibility(View.INVISIBLE);wm.addView(window,params);if(open&&returnDialog==null)window.requestFocus();}catch(Exception e){window=null;stopSelf();}refresh();
    }
    private final class Strip {
        final XAirProtocol.Control control;final LinearLayout view;final TextView value;final Button mute;final FaderView fader;final boolean withFader;Boolean lastMuted;
        Strip(XAirProtocol.Control c,boolean volume){
            float scale=PanelSizing.controlScale(profile.controlSize()),textScale=scale*fontScale();control=c;withFader=volume;view=Ui.column(OverlayService.this);view.setBackground(Ui.bg(Ui.CARD,Ui.dp(OverlayService.this,3)));
            if(volume){
                TextView name=Ui.title(OverlayService.this,profile.label(OverlayService.this,c),10);fitText(name,10*textScale);name.setTextColor(Ui.BG);name.setBackgroundColor(Ui.accent(c));name.setGravity(Gravity.CENTER);name.setMaxLines(textScale>1.35f?2:1);name.setEllipsize(TextUtils.TruncateAt.END);view.addView(name,new LinearLayout.LayoutParams(-1,Ui.dp(OverlayService.this,nameHeight(scale))));
                value=Ui.title(OverlayService.this,"—",14);fitText(value,14*textScale);value.setSingleLine();value.setHorizontallyScrolling(false);value.setTypeface(android.graphics.Typeface.MONOSPACE);value.setGravity(Gravity.CENTER);view.addView(value,new LinearLayout.LayoutParams(-1,Ui.dp(OverlayService.this,valueHeight(scale))));
                fader=new FaderView(OverlayService.this,v->{if(ready()&&fresh(c.faderPath)!=null)mixer.set(c.faderPath,Float.valueOf(v));});fader.setControlScale(scale);fader.setTextScale(fontScale());LinearLayout.LayoutParams rail=new LinearLayout.LayoutParams(-1,0,1);rail.leftMargin=Ui.dp(OverlayService.this,5);rail.rightMargin=Ui.dp(OverlayService.this,5);view.addView(fader,rail);
            }else{value=null;fader=null;}
            if(profile.mute(c)){
                mute=Ui.button(OverlayService.this,"—",Ui.LINE);fitText(mute,(volume?10:11)*textScale);mute.setMaxLines(2);mute.setEllipsize(TextUtils.TruncateAt.END);mute.setContentDescription(profile.label(OverlayService.this,c)+" "+s("mute"));mute.setOnClickListener(v->{Object raw=fresh(c.mutePath);Long pending=pendingMute.get(c.mutePath);if(ready()&&raw instanceof Number&&(pending==null||SystemClock.elapsedRealtime()-pending>800)){pendingMute.put(c.mutePath,SystemClock.elapsedRealtime());mixer.set(c.mutePath,c.muteValue(!c.isMuted(((Number)raw).intValue())));update(ready());}});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Ui.dp(OverlayService.this,muteHeight(scale)));if(volume){lp.leftMargin=Ui.dp(OverlayService.this,3);lp.rightMargin=Ui.dp(OverlayService.this,3);lp.bottomMargin=Ui.dp(OverlayService.this,3);}view.addView(mute,lp);
            }else mute=null;
        }
        private android.text.TextPaint muteLabelPaint(){android.text.TextPaint p=new android.text.TextPaint(mute.getPaint());p.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,(withFader?10:11)*PanelSizing.controlScale(profile.controlSize())*fontScale(),getResources().getDisplayMetrics()));return p;}
        void update(boolean ready){
            Object raw=fresh(control.faderPath);Float v=raw instanceof Number?((Number)raw).floatValue():null;if(value!=null)Ui.updateText(value,FaderView.format(v));if(fader!=null)fader.update(v,ready);
            updateMeter();
            if(mute!=null){Object m=fresh(control.mutePath);boolean known=m instanceof Number,muted=known&&control.isMuted(((Number)m).intValue());Long wait=pendingMute.get(control.mutePath);boolean pending=wait!=null&&SystemClock.elapsedRealtime()-wait<800;
                mute.setEnabled(ready&&known&&!pending);mute.setAlpha(ready&&known?1f:.4f);String state=pending?"…":known?s(muted?"muted":"mute"):"—";String label=profile.label(OverlayService.this,control);if(!withFader&&mute.getWidth()>0)label=TextUtils.ellipsize(label,muteLabelPaint(),Math.max(1,mute.getWidth()-mute.getPaddingLeft()-mute.getPaddingRight()),TextUtils.TruncateAt.END).toString();Ui.updateText(mute,withFader?state:label+"\n"+state);mute.setTextColor(Ui.TEXT);
                if(lastMuted==null||lastMuted!=muted){lastMuted=muted;mute.setBackground(Ui.bg(muted?Ui.RED:Ui.LINE,Ui.dp(OverlayService.this,3)));}
            }
        }
        void updateMeter(){
            if(fader==null)return;String id=control.id,bank=id.startsWith("dca")?"/meters/8":"/meters/1";float[] data=meters.get(bank);Long stamp=meterStamps.get(bank);
            if(!connected||!expanded||data==null||stamp==null||SystemClock.elapsedRealtime()-stamp>1000){fader.meter(null);return;}
            int index=id.equals("main")?36:id.startsWith("ch")?Integer.parseInt(id.substring(2))-1:id.startsWith("bus")?26+Integer.parseInt(id.substring(3))-1:id.startsWith("dca")?Integer.parseInt(id.substring(3))-1:-1;
            if(index<0||index>=data.length){fader.meter(null);return;}fader.meter(id.equals("main")&&index+1<data.length?new float[]{data[index],data[index+1]}:new float[]{data[index]});
        }
    }
    private final class BubbleView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        BubbleView(){super(OverlayService.this);setFocusable(true);}
        protected void onDraw(Canvas canvas){
            float size=getWidth(),center=size/2;paint.setColor(Ui.BG);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(center,center,center-1,paint);paint.setColor(ready()?Ui.GREEN:Ui.GOLD);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Ui.dp(OverlayService.this,2));canvas.drawCircle(center,center,center-Ui.dp(OverlayService.this,2),paint);paint.setStyle(Paint.Style.FILL);
            List<XAirProtocol.Control> controls=new ArrayList<>();for(XAirProtocol.Control c:profile.selected())if(profile.volume(c)&&controls.size()<3)controls.add(c);
            if(controls.isEmpty()){boolean muted=false;for(XAirProtocol.Control c:profile.selected()){Object v=fresh(c.mutePath);if(v instanceof Number){muted=c.isMuted(((Number)v).intValue());break;}}paint.setColor(muted?Ui.RED:Ui.TEXT);paint.setTextSize(size*.36f);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("M",center,center+size*.13f,paint);}
            else{for(int i=0;i<3;i++){float x=center+(i-1)*size*.18f,top=size*.27f,bottom=size*.73f;paint.setColor(Ui.LINE);paint.setStrokeWidth(Ui.dp(OverlayService.this,1));canvas.drawLine(x,top,x,bottom,paint);float v=.5f;if(i<controls.size()){Object raw=fresh(controls.get(i).faderPath);if(raw instanceof Number)v=((Number)raw).floatValue();}float y=bottom-v*(bottom-top);paint.setColor(Ui.TEXT);canvas.drawRect(x-size*.065f,y-size*.035f,x+size*.065f,y+size*.035f,paint);}}
        }
    }
    public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);if(window!=null)show(expanded);}
    public IBinder onBind(Intent intent){return null;}
    public void onDestroy(){hideDismissTarget();destroyed=true;live=null;handler.removeCallbacksAndMessages(null);if(returnDialog!=null)returnDialog.dismiss();releaseWifi();try{cm.unregisterNetworkCallback(watcher);}catch(Exception ignored){}try{if(identityWatcher!=null)cm.unregisterNetworkCallback(identityWatcher);}catch(Exception ignored){}mixer.close();if(window!=null)wm.removeView(window);stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
}
