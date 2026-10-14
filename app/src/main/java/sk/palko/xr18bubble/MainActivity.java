package sk.palko.xr18bubble;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private Profile profile;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout content,discoveryList;
    private TextView liveStatus,liveWifi;
    private Button scanButton;
    private MixerClient scanner;
    private AppShortcut shortcut;
    private final Map<String,String[]> found=new LinkedHashMap<>();
    private int tab;
    private boolean scanning,scanned,awaitingOverlay,quick,destroyed;
    private String pendingPermission;
    private String s(String key,Object...args){return L.s(this,key,args);}

    public void onCreate(Bundle state){setTheme(Ui.activityTheme(this));super.onCreate(state);profile=new Profile(this);if(state!=null){tab=state.getInt("tab");awaitingOverlay=state.getBoolean("overlay");quick=state.getBoolean("quick");pendingPermission=state.getString("permission");}render();}
    private Button action(String text,boolean primary){Button b=Ui.button(this,text,primary?Ui.BLUE:Ui.surface(this));b.setTextColor(primary?Ui.WHITE:Ui.blue(this));b.setTextSize(15);return b;}
    private TextView label(String text,int size,int color){TextView t=Ui.text(this,text,size,color);return t;}
    private void section(String key){TextView t=label(s(key).toUpperCase(L.context(this).getResources().getConfiguration().getLocales().get(0)),11,Ui.secondary(this));t.setPadding(4,Ui.dp(this,12),0,Ui.dp(this,4));Ui.add(content,t,-2);}
    private LinearLayout card(){LinearLayout c=Ui.column(this);c.setPadding(Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12));c.setBackground(Ui.bg(Ui.surface(this),Ui.dp(this,12)));Ui.add(content,c,-2);return c;}
    private void textRow(LinearLayout parent,String title,String detail,Runnable click){
        LinearLayout row=Ui.row(this);row.setMinimumHeight(Ui.dp(this,48));LinearLayout labels=Ui.column(this);
        labels.addView(label(title,15,Ui.ink(this)));if(detail!=null&&!detail.isEmpty()){TextView sub=label(detail,12,Ui.secondary(this));sub.setPadding(0,Ui.dp(this,3),0,0);labels.addView(sub);}
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));if(click!=null){TextView arrow=label("›",24,Ui.secondary(this));row.addView(arrow,new LinearLayout.LayoutParams(Ui.dp(this,20),-2));row.setOnClickListener(v->click.run());row.setBackground(Ui.bg(Ui.surface(this),4));}parent.addView(row);
    }
    private void render(){
        liveStatus=null;liveWifi=null;discoveryList=null;scanButton=null;shortcut=null;
        getWindow().setStatusBarColor(Ui.page(this));getWindow().setNavigationBarColor(Ui.surface(this));
        getWindow().getDecorView().setSystemUiVisibility(Ui.isDark(this)?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.page(this)));
        LinearLayout root=Ui.column(this);root.setBackgroundColor(Ui.page(this));
        root.setOnApplyWindowInsetsListener((v,insets)->{int l,t,r,b;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());l=i.left;t=i.top;r=i.right;b=i.bottom;}else{l=insets.getSystemWindowInsetLeft();t=insets.getSystemWindowInsetTop();r=insets.getSystemWindowInsetRight();b=insets.getSystemWindowInsetBottom();}v.setPadding(l,t,r,b);return insets;});
        LinearLayout header=Ui.row(this);header.setPadding(Ui.dp(this,20),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,10));
        TextView title=label(s(tab==0?"mixers":tab==1?"controls":"settings"),28,Ui.ink(this));title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        if(tab==0){shortcut=new AppShortcut(this,profile);LinearLayout.LayoutParams icon=new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44));icon.rightMargin=Ui.dp(this,8);header.addView(shortcut.button(),icon);Button scan=action("↻",false);scan.setContentDescription(s("scan"));scan.setOnClickListener(v->scan());header.addView(scan,new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,44)));}
        root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);content=Ui.column(this);content.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),Ui.dp(this,20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==0)mixers();else if(tab==1)controls();else settings();
        View line=new View(this);line.setBackgroundColor(Ui.separator(this));root.addView(line,new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));
        LinearLayout nav=Ui.row(this);nav.setBackgroundColor(Ui.surface(this));
        String[] tabs={"mixers","controls","settings"};for(int i=0;i<tabs.length;i++){final int index=i;Button b=action(s(tabs[i]),false);b.setTextColor(i==tab?Ui.blue(this):Ui.secondary(this));b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,i==tab?Typeface.BOLD:Typeface.NORMAL);b.setOnClickListener(v->{tab=index;render();});nav.addView(b,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));}root.addView(nav);setContentView(root);
    }
    private void mixers(){
        if(!profile.get("host").isEmpty()){
            section("saved_mixer");LinearLayout saved=card();
            String name=profile.get("name");if(name.isEmpty())name=profile.get("model").isEmpty()?"XR18":profile.get("model");
            textRow(saved,name,profile.get("host")+(profile.get("ssid").isEmpty()?"":" · "+profile.get("ssid")),()->editProfile(null));
            liveStatus=label(s("disconnected"),12,Ui.secondary(this));liveStatus.setPadding(0,Ui.dp(this,6),0,Ui.dp(this,10));saved.addView(liveStatus);
            Button connect=action(s("connect"),true);connect.setOnClickListener(v->connectPreferred());Ui.add(saved,connect,44);
            Button remember=action(s("remember_wifi"),false);remember.setOnClickListener(v->rememberCurrentWifi());Ui.add(saved,remember,44);
            liveWifi=label(wifiSummary(),12,Ui.secondary(this));Ui.add(saved,liveWifi,-2);
            if(!profile.get("ssid").isEmpty()&&!"OPEN".equals(profile.get("security")))try{if(profile.password().isEmpty())textRow(saved,s("wifi_password_needed"),profile.get("ssid"),()->editProfile(null));}catch(Exception ignored){}
            Button bubble=action(s("show_bubble"),false);bubble.setOnClickListener(v->showBubble());Ui.add(saved,bubble,44);
        }else{
            TextView intro=label(s("welcome_title"),18,Ui.ink(this));intro.setPadding(0,Ui.dp(this,6),0,0);Ui.add(content,intro,-2);
            Ui.add(content,label(s("welcome_subtitle"),14,Ui.secondary(this)),-2);
        }
        section("nearby_mixers");discoveryList=Ui.column(this);Ui.add(content,discoveryList,-2);renderDiscovery();
        scanButton=action(s(scanning?"scanning":"scan"),false);scanButton.setEnabled(!scanning);scanButton.setOnClickListener(v->scan());Ui.add(content,scanButton,44);
        Button manual=action(s("add_manually"),false);manual.setOnClickListener(v->editProfile(new String[]{"","","XR18"}));Ui.add(content,manual,44);
    }
    private void renderDiscovery(){if(discoveryList==null)return;discoveryList.removeAllViews();
        if(found.isEmpty()){TextView empty=label(s(scanning?"scanning":"no_mixers"),14,Ui.secondary(this));empty.setPadding(Ui.dp(this,12),Ui.dp(this,14),Ui.dp(this,12),Ui.dp(this,14));discoveryList.addView(empty);}
        for(Map.Entry<String,String[]> entry:found.entrySet()){
            String ip=entry.getKey();String[] data=entry.getValue();LinearLayout row=Ui.column(this);row.setPadding(Ui.dp(this,14),Ui.dp(this,4),Ui.dp(this,14),Ui.dp(this,4));row.setBackground(Ui.bg(Ui.surface(this),Ui.dp(this,12)));
            textRow(row,data[0],data[1]+" · "+ip,()->editProfile(new String[]{ip,data[0],data[1]}));Ui.add(discoveryList,row,-2);
        }
    }
    private Network wifi(){ConnectivityManager cm=getSystemService(ConnectivityManager.class);Network n=cm.getActiveNetwork();if(n!=null){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}for(Network item:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(item);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return item;}return null;}
    private void scan(){
        if(scanning)return;Network n=wifi();if(n==null){toast(s("no_wifi"));return;}
        if(scanner!=null)scanner.close();found.clear();scanning=true;scanned=true;renderDiscovery();if(scanButton!=null){scanButton.setText(s("scanning"));scanButton.setEnabled(false);}
        scanner=new MixerClient(this,new MixerClient.Listener(){public void onStatus(String t,boolean ok){}public void onValue(String p,Object v){}public void onDiscovered(String ip,String name,String model){found.put(ip,new String[]{name,model});renderDiscovery();}});
        final MixerClient search=scanner;scanner.discover(n);handler.postDelayed(()->{if(!destroyed&&scanner==search){scanning=false;renderDiscovery();if(scanButton!=null){scanButton.setText(s("scan"));scanButton.setEnabled(true);}}},3000);
    }
    private EditText field(LinearLayout parent,String key,String value,int type){
        TextView l=label(s(key),12,Ui.secondary(this));l.setPadding(0,Ui.dp(this,6),0,0);Ui.add(parent,l,-2);EditText e=new EditText(this);e.setTextSize(15);e.setTextColor(Ui.ink(this));e.setSingleLine();e.setInputType(type);e.setText(value);e.setBackground(Ui.bg(Ui.page(this),Ui.dp(this,7)));e.setPadding(Ui.dp(this,10),0,Ui.dp(this,10),0);Ui.add(parent,e,44);return e;
    }
    private void editProfile(String[] selected){
        boolean existing=selected==null;String ip=existing?profile.get("host"):selected[0];String name=existing?profile.get("name"):selected[1];String model=existing?profile.get("model"):selected[2];
        LinearLayout form=Ui.column(this);form.setPadding(Ui.dp(this,20),Ui.dp(this,4),Ui.dp(this,20),Ui.dp(this,8));ScrollView scroll=new ScrollView(this);scroll.addView(form);
        EditText nameField=field(form,"mixer_name",name,InputType.TYPE_CLASS_TEXT);
        EditText ipField=field(form,"mixer_address",ip,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);ipField.setHint(s("manual_address_hint"));
        Ui.add(form,label(s("wifi_auto_hint"),13,Ui.secondary(this)),-2);
        EditText ssid=field(form,"wifi_name",existing?profile.get("ssid"):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String secret="";if(existing)try{secret=profile.password();}catch(Exception e){toast(s("password_error"));}
        EditText password=field(form,"wifi_password",secret,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout advanced=Ui.column(this);advanced.setVisibility(View.GONE);
        Ui.add(advanced,label(s("wifi_security"),12,Ui.secondary(this)),-2);Spinner security=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"WPA2","WPA3",s("open_network")});security.setAdapter(adapter);security.setSelection(existing&&"WPA3".equals(profile.get("security"))?1:existing&&"OPEN".equals(profile.get("security"))?2:0);Ui.add(advanced,security,44);
        EditText bssid=field(advanced,"bssid_optional",existing?profile.get("bssid"):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        Button more=action(s("advanced"),false);more.setOnClickListener(v->advanced.setVisibility(advanced.getVisibility()==View.GONE?View.VISIBLE:View.GONE));Ui.add(form,more,40);form.addView(advanced);
        Ui.add(form,label(s("wifi_confirmation_note"),12,Ui.secondary(this)),-2);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(s(existing?"edit_profile":"new_profile")).setView(scroll).setPositiveButton(s("save"),null).setNegativeButton(s("cancel"),null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String target=ipField.getText().toString().trim(),network=ssid.getText().toString(),pass=password.getText().toString(),mac=bssid.getText().toString().trim();
            if(!validAddress(target)){ipField.setError(s("invalid_address"));return;}
            if(network.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>32){ssid.setError(s("invalid_ssid"));return;}
            if(!mac.isEmpty())try{MacAddress.fromString(mac);}catch(Exception e){bssid.setError(s("invalid_bssid"));return;}
            String mode=security.getSelectedItemPosition()==1?"WPA3":security.getSelectedItemPosition()==2?"OPEN":"WPA2";
            if(!network.isEmpty()&&!mode.equals("OPEN")&&!pass.isEmpty()&&(pass.length()<8||pass.length()>63)){password.setError(s("invalid_password"));advanced.setVisibility(View.VISIBLE);return;}
            try{profile.password(mode.equals("OPEN")?"":pass);}catch(Exception e){toast(s("password_error"));return;}
            profile.put("host",target);profile.put("name",nameField.getText().toString().trim());profile.put("model",model);profile.put("ssid",network);profile.put("bssid",mac);profile.put("security",mode);
            dialog.dismiss();if(OverlayService.live!=null)service(OverlayService.PROFILE);tab=0;render();connectPreferred();
        }));dialog.show();
    }
    private void controls(){
        Ui.add(content,label(s("choose_controls_hint"),14,Ui.secondary(this)),-2);
        Button preview=action(s("show_bubble"),false);preview.setOnClickListener(v->showBubble());Ui.add(content,preview,44);
        section("appearance");LinearLayout size=card();textRow(size,s("control_size"),sizeLabel(profile.controlSize()),()->chooseSize("control_size"));Ui.add(content,label(s("auto_resize_hint"),12,Ui.secondary(this)),-2);if(PanelSizing.controlScale(profile.controlSize())>1.35f)Ui.add(content,label(s("large_controls_hint"),12,Ui.secondary(this)),-2);
        Set<String> selected=new HashSet<>();for(XAirProtocol.Control c:profile.selected())selected.add(c.id);
        Ui.add(content,label(s("rename_hint"),12,Ui.secondary(this)),-2);
        LinearLayout group=null;
        for(XAirProtocol.Control c:XAirProtocol.CONTROLS){
            String heading=c.id.equals("main")?"main_lr":c.id.equals("dca1")?"dca_groups":c.id.equals("mute1")?"mute_groups":c.id.equals("bus1")?"buses":c.id.equals("ch1")?"channels":null;
            if(heading!=null){section(heading);group=card();}
            if(group.getChildCount()>0){View divider=new View(this);divider.setBackgroundColor(Ui.separator(this));group.addView(divider,new LinearLayout.LayoutParams(-1,Ui.dp(this,1)));}
            LinearLayout row=Ui.row(this);CheckBox enabled=new CheckBox(this);enabled.setText(profile.label(this,c));enabled.setTextSize(14);enabled.setTextColor(Ui.ink(this));enabled.setSingleLine();enabled.setEllipsize(android.text.TextUtils.TruncateAt.END);enabled.setChecked(selected.contains(c.id));enabled.setOnLongClickListener(v->{editControl(c);return true;});row.addView(enabled,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
            if(c.faderPath!=null){
                CheckBox volume=new CheckBox(this),mute=new CheckBox(this);volume.setTextColor(Ui.ink(this));mute.setTextColor(Ui.ink(this));volume.setText(s("volume"));mute.setText(s("mute"));volume.setTextSize(10);mute.setTextSize(10);volume.setContentDescription(profile.label(this,c)+" · "+s("volume"));mute.setContentDescription(profile.label(this,c)+" · "+s("mute"));volume.setChecked(profile.volume(c));mute.setChecked(profile.mute(c));volume.setEnabled(enabled.isChecked());mute.setEnabled(enabled.isChecked());row.addView(volume,new LinearLayout.LayoutParams(Ui.dp(this,98),Ui.dp(this,52)));row.addView(mute,new LinearLayout.LayoutParams(Ui.dp(this,84),Ui.dp(this,52)));
                boolean[] changing={false};volume.setOnCheckedChangeListener((button,on)->{if(changing[0])return;changing[0]=true;if(!on&&!mute.isChecked())mute.setChecked(true);profile.parts(c,on,mute.isChecked());changing[0]=false;reload();});mute.setOnCheckedChangeListener((button,on)->{if(changing[0])return;changing[0]=true;if(!on&&!volume.isChecked())volume.setChecked(true);profile.parts(c,volume.isChecked(),on);changing[0]=false;reload();});
                enabled.setOnCheckedChangeListener((button,on)->{if(on)selected.add(c.id);else selected.remove(c.id);profile.select(selected);volume.setEnabled(on);mute.setEnabled(on);reload();});
            }else{
                TextView kind=label(s("mute_only"),11,Ui.secondary(this));kind.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);row.addView(kind,new LinearLayout.LayoutParams(Ui.dp(this,88),Ui.dp(this,52)));enabled.setOnCheckedChangeListener((button,on)->{if(on)selected.add(c.id);else selected.remove(c.id);profile.select(selected);reload();});
            }
            group.addView(row);
        }
    }
    private void editControl(XAirProtocol.Control c){
        LinearLayout form=Ui.column(this);form.setPadding(Ui.dp(this,20),0,Ui.dp(this,20),0);EditText name=field(form,"rename_control",profile.get("label_"+c.id),InputType.TYPE_CLASS_TEXT);name.setHint(profile.label(this,c));
        RadioGroup modes=new RadioGroup(this);String[] keys={"volume_and_mute","volume_only","mute_only"};
        if(c.faderPath!=null){for(int i=0;i<keys.length;i++){RadioButton r=new RadioButton(this);r.setText(s(keys[i]));r.setId(100+i);modes.addView(r);}modes.check(profile.volume(c)?(profile.mute(c)?100:101):102);form.addView(modes);}else Ui.add(form,label(s("mute_only"),14,Ui.secondary(this)),-2);
        new AlertDialog.Builder(this).setTitle(profile.label(this,c)).setView(form).setPositiveButton(s("save"),(d,n)->{String custom=name.getText().toString().trim();profile.put("label_"+c.id,custom.length()>20?custom.substring(0,20):custom);int mode=modes.getCheckedRadioButtonId();profile.parts(c,c.faderPath!=null&&mode!=102,c.faderPath==null||mode!=101);reload();render();}).setNegativeButton(s("cancel"),null).show();
    }
    private String sizeLabel(String code){for(String size:PanelSizing.controlSizes())if(size.equals(code))return s("size_"+size);return s("size_medium");}
    private void chooseSize(String key){String[] values=key.equals("bubble_size")?new String[]{"small","medium","large"}:PanelSizing.controlSizes();String current=key.equals("bubble_size")?profile.bubbleSize():profile.controlSize();String[] labels=new String[values.length];for(int i=0;i<values.length;i++)labels[i]=sizeLabel(values[i]);int selected=Arrays.asList(values).indexOf(current);new AlertDialog.Builder(this).setTitle(s(key)).setSingleChoiceItems(labels,selected<0?1:selected,(d,n)->{profile.put(key,values[n]);d.dismiss();reload();render();}).setNegativeButton(s("cancel"),null).show();}
    private String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException e){return "—";}}
    private void settings(){
        section("bubble");LinearLayout bubble=card();textRow(bubble,s("bubble_size"),sizeLabel(profile.bubbleSize()),()->chooseSize("bubble_size"));
        LinearLayout auto=Ui.row(this);TextView text=label(s("automatic_connection"),15,Ui.ink(this));auto.addView(text,new LinearLayout.LayoutParams(0,-2,1));Switch toggle=new Switch(this);toggle.setChecked(!"off".equals(profile.get("auto_connect")));toggle.setOnCheckedChangeListener((b,on)->profile.put("auto_connect",on?"on":"off"));auto.addView(toggle);bubble.addView(auto,new LinearLayout.LayoutParams(-1,Ui.dp(this,48)));
        Ui.add(content,label(s("automatic_connection_hint"),12,Ui.secondary(this)),-2);
        section("appearance");LinearLayout appearance=card();textRow(appearance,s("theme"),themeLabel(),()->chooseTheme());
        section("language");LinearLayout language=card();int languageIndex=Arrays.asList(L.codes()).indexOf(L.language(this));String display=L.languageNames(this)[Math.max(0,languageIndex)];textRow(language,s("language"),display,()->chooseLanguage());
        section("about");LinearLayout info=card();textRow(info,s("privacy"),null,()->new AlertDialog.Builder(this).setTitle(s("privacy")).setMessage(s("privacy_text")).setPositiveButton(s("done"),null).show());textRow(info,s("app_name"),s("developed_by","Pavol Škoda"),null);textRow(info,s("version"),appVersion(),null);textRow(info,s("support"),"pavolskoda@azet.sk",()->{try{startActivity(new Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:pavolskoda@azet.sk")));}catch(ActivityNotFoundException|SecurityException ignored){toast("pavolskoda@azet.sk");}});
        Button stop=action(s("stop_bubble"),false);stop.setTextColor(Ui.RED);stop.setOnClickListener(v->{stopService(new Intent(this,OverlayService.class));toast(s("disconnected"));});Ui.add(content,stop,44);
    }
    private void chooseLanguage(){String[] codes=L.codes();String[] names=L.languageNames(this);new AlertDialog.Builder(this).setTitle(s("language")).setSingleChoiceItems(names,Arrays.asList(codes).indexOf(L.language(this)),(d,n)->{L.locale(this,codes[n]);d.dismiss();reload();render();}).setNegativeButton(s("cancel"),null).show();}
    private String themeLabel(){String value=profile.get("theme");return s("dark".equals(value)?"theme_dark":"light".equals(value)?"theme_light":"theme_system");}
    private void chooseTheme(){String[] values={"system","light","dark"};String value=profile.get("theme");int selected=Arrays.asList(values).indexOf(value);String[] labels={s("theme_system"),s("theme_light"),s("theme_dark")};new AlertDialog.Builder(this).setTitle(s("theme")).setSingleChoiceItems(labels,Math.max(0,selected),(d,n)->{profile.put("theme",values[n]);d.dismiss();recreate();}).setNegativeButton(s("cancel"),null).show();}
    private void reload(){if(OverlayService.live!=null)service(OverlayService.RELOAD);}
    private void service(String action){Intent intent=new Intent(this,OverlayService.class).setAction(action);Network n=wifi();WifiIdentity identity=WifiIdentity.read(this,n);if(identity!=null)intent.putExtra("wifi_network",n).putExtra("wifi_ssid",identity.ssid).putExtra("wifi_security",identity.security);startForegroundService(intent);}
    private boolean locationPermission(){return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private String wifiSummary(){return profile.get("ssid").isEmpty()?s("wifi_not_remembered"):s("saved_wifi_ready")+": "+profile.get("ssid");}
    private void askRememberPermission(String action){
        new AlertDialog.Builder(this).setTitle(s("remember_wifi")).setMessage(s("wifi_location_explanation")).setPositiveButton(s("continue_button"),(d,n)->{profile.put("wifi_identity_asked","yes");pendingPermission=action;requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},20);}).setNegativeButton(s("skip"),(d,n)->{profile.put("wifi_identity_asked","yes");if(OverlayService.LEARN_WIFI.equals(action))return;connectPreferred();}).show();
    }
    private void rememberCurrentWifi(){
        if(profile.get("host").isEmpty()){toast(s("profile_required"));return;}
        if(!locationPermission()){askRememberPermission(OverlayService.LEARN_WIFI);return;}
        if(!getSystemService(android.location.LocationManager.class).isLocationEnabled()){new AlertDialog.Builder(this).setTitle(s("remember_wifi")).setMessage(s("wifi_location_disabled")).setPositiveButton(s("settings"),(d,n)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton(s("cancel"),null).show();return;}
        service(OverlayService.LEARN_WIFI);if(OverlayService.live==null||!OverlayService.live.connected)service(OverlayService.CONNECT);toast(s("wifi_remember_after_connect"));
    }
    private void connectPreferred(){if(profile.get("host").isEmpty()){toast(s("profile_required"));tab=0;render();return;}
        if(profile.get("ssid").isEmpty()&&!locationPermission()&&!"yes".equals(profile.get("wifi_identity_asked"))){askRememberPermission(OverlayService.CONNECT);return;}
        if(profile.get("ssid").isEmpty()){service(OverlayService.CONNECT);finishQuick();return;}
        String permission=Build.VERSION.SDK_INT>=33?Manifest.permission.NEARBY_WIFI_DEVICES:Manifest.permission.ACCESS_FINE_LOCATION;
        if(checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){pendingPermission=OverlayService.WIFI;if(Build.VERSION.SDK_INT<=32)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},18);else requestPermissions(new String[]{permission},18);return;}service(OverlayService.WIFI);finishQuick();
    }
    private void finishQuick(){if(quick){quick=false;service(OverlayService.PANEL);moveTaskToBack(true);}}
    private void showBubble(){if(profile.get("host").isEmpty()){toast(s("profile_required"));tab=0;render();return;}
        if(!Settings.canDrawOverlays(this)){new AlertDialog.Builder(this).setTitle(s("overlay_title")).setMessage(s("overlay_explanation")).setPositiveButton(s("allow_overlay"),(d,n)->{awaitingOverlay=true;startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}).setNegativeButton(s("cancel"),null).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){pendingPermission=OverlayService.SHOW;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},19);return;}
        service(OverlayService.SHOW);moveTaskToBack(true);
    }
    public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);String action=pendingPermission;pendingPermission=null;if(request==20){if(OverlayService.LEARN_WIFI.equals(action)){if(locationPermission())rememberCurrentWifi();else toast(s("wifi_not_remembered"));}else connectPreferred();}else if(request==19){service(OverlayService.SHOW);moveTaskToBack(true);}else if(request==18&&grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED&&action!=null){service(action);finishQuick();}else{quick=false;toast(s("wifi_permission"));}}
    public void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleIntent();}
    private void handleIntent(){
        if(getIntent().getBooleanExtra("wifi_setup",false)){getIntent().removeExtra("wifi_setup");tab=0;quick=false;render();editProfile(null);}
        if(getIntent().hasExtra("tab")){tab=Math.max(0,Math.min(2,getIntent().getIntExtra("tab",0)));getIntent().removeExtra("tab");render();}
        if(getIntent().getBooleanExtra("connect",false)){getIntent().removeExtra("connect");quick=true;connectPreferred();}
    }
    private boolean validAddress(String address){
        if(address.isEmpty()||address.length()>253)return false;
        if(address.matches("[0-9.]+")){String[] numbers=address.split("\\.",-1);if(numbers.length!=4)return false;for(String n:numbers)try{if(n.isEmpty()||n.length()>3||Integer.parseInt(n)>255)return false;}catch(NumberFormatException e){return false;}return true;}
        return address.matches("(?i)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*");
    }
    public void onResume(){super.onResume();handler.post(tick);if(awaitingOverlay&&Settings.canDrawOverlays(this)){awaitingOverlay=false;showBubble();}handleIntent();if(shortcut!=null)shortcut.refresh();if(!scanned&&profile.get("host").isEmpty())handler.post(this::scan);}
    public void onPause(){handler.removeCallbacks(tick);super.onPause();}
    public void onDestroy(){destroyed=true;handler.removeCallbacksAndMessages(null);if(scanner!=null)scanner.close();super.onDestroy();}
    protected void onSaveInstanceState(Bundle state){state.putInt("tab",tab);state.putBoolean("overlay",awaitingOverlay);state.putBoolean("quick",quick);state.putString("permission",pendingPermission);super.onSaveInstanceState(state);}
    private final Runnable tick=new Runnable(){public void run(){if(liveStatus!=null){OverlayService service=OverlayService.live;Ui.updateText(liveStatus,service==null?s("disconnected"):service.displayStatus());liveStatus.setTextColor(service!=null&&service.ready()?(Ui.isDark(MainActivity.this)?Ui.GREEN:0xff25854a):Ui.secondary(MainActivity.this));}if(liveWifi!=null)Ui.updateText(liveWifi,wifiSummary());handler.postDelayed(this,500);}};
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
