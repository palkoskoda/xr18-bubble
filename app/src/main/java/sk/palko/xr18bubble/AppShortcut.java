package sk.palko.xr18bubble;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

/** User-selected launcher shortcut. Reads only launchable apps; no package inventory leaves the device. */
final class AppShortcut {
    private static final String DEFAULT_PACKAGE="org.devcore.mixingstation";
    private final Activity activity;
    private final Profile profile;
    private ImageButton button;
    AppShortcut(Activity activity,Profile profile){this.activity=activity;this.profile=profile;}
    private String s(String key,Object...args){return L.s(activity,key,args);}
    private String selectedPackage(){String selected=profile.get("shortcut_package");return selected.isEmpty()?DEFAULT_PACKAGE:selected;}
    ImageButton button(){
        button=new ImageButton(activity);button.setBackground(Ui.bg(Ui.surface(activity),Ui.dp(activity,12)));button.setPadding(Ui.dp(activity,10),Ui.dp(activity,10),Ui.dp(activity,10),Ui.dp(activity,10));button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setOnClickListener(v->open());button.setOnLongClickListener(v->{choose();return true;});refresh();return button;
    }
    void refresh(){
        if(button==null)return;
        try{
            PackageManager pm=activity.getPackageManager();String selected=selectedPackage();
            if(pm.getLaunchIntentForPackage(selected)==null)throw new PackageManager.NameNotFoundException();
            ApplicationInfo info=pm.getApplicationInfo(selected,0);button.setImageDrawable(pm.getApplicationIcon(info));button.setContentDescription(s("open_app",pm.getApplicationLabel(info)));button.setTooltipText(s("open_app",pm.getApplicationLabel(info))+"\n"+s("shortcut_hint"));
        }catch(PackageManager.NameNotFoundException|SecurityException e){button.setImageResource(R.drawable.shortcut_placeholder);button.setContentDescription(s("add_app_shortcut"));button.setTooltipText(s("shortcut_hint"));}
    }
    private void open(){
        try{Intent launch=activity.getPackageManager().getLaunchIntentForPackage(selectedPackage());if(launch!=null){activity.startActivity(launch);return;}}catch(ActivityNotFoundException|SecurityException ignored){}
        if(!profile.get("shortcut_package").isEmpty())Toast.makeText(activity,s("app_unavailable"),Toast.LENGTH_SHORT).show();choose();
    }
    private static final class App {
        final String name,packageName;final ApplicationInfo info;
        App(String name,ApplicationInfo info){this.name=name;this.info=info;this.packageName=info.packageName;}
    }
    private void choose(){
        LinearLayout body=Ui.column(activity);body.setPadding(Ui.dp(activity,20),0,Ui.dp(activity,20),0);
        EditText search=new EditText(activity);search.setSingleLine();search.setTextSize(15);search.setTextColor(Ui.ink(activity));search.setHintTextColor(Ui.secondary(activity));search.setHint(s("search_apps"));search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);body.addView(search,new LinearLayout.LayoutParams(-1,Ui.dp(activity,48)));
        FrameLayout results=new FrameLayout(activity);ListView list=new ListView(activity);list.setDividerHeight(0);list.setFocusable(false);list.setFocusableInTouchMode(false);list.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);TextView empty=Ui.text(activity,s("no_apps_found"),14,Ui.secondary(activity));empty.setFocusable(false);empty.setGravity(Gravity.CENTER);results.addView(list,new FrameLayout.LayoutParams(-1,-1));results.addView(empty,new FrameLayout.LayoutParams(-1,-1));list.setEmptyView(empty);body.addView(results,new LinearLayout.LayoutParams(-1,Ui.dp(activity,320)));
        final List<App> all=new ArrayList<>(),visible=new ArrayList<>();final PackageManager pm=activity.getPackageManager();
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return visible.size();}public Object getItem(int position){return visible.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                LinearLayout row;ImageView icon;TextView name;
                if(recycled instanceof LinearLayout){row=(LinearLayout)recycled;icon=(ImageView)row.getChildAt(0);name=(TextView)row.getChildAt(1);}
                else{row=Ui.row(activity);row.setPadding(0,Ui.dp(activity,8),0,Ui.dp(activity,8));icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(Ui.dp(activity,32),Ui.dp(activity,32));image.rightMargin=Ui.dp(activity,12);row.addView(icon,image);name=Ui.text(activity,"",15,Ui.ink(activity));name.setSingleLine();name.setEllipsize(TextUtils.TruncateAt.END);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));}
                App app=visible.get(position);name.setText(app.name);try{icon.setImageDrawable(pm.getApplicationIcon(app.info));}catch(Exception e){icon.setImageResource(R.drawable.shortcut_placeholder);}return row;
            }
        };
        list.setAdapter(adapter);
        Runnable filter=()->{boolean editing=search.hasFocus();int cursor=search.getSelectionStart();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);visible.clear();for(App app:all)if(app.name.toLowerCase(Locale.ROOT).contains(query))visible.add(app);adapter.notifyDataSetChanged();if(editing&&!search.hasFocus()){search.requestFocus();search.setSelection(Math.max(0,Math.min(cursor,search.length())));}};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){filter.run();}public void afterTextChanged(Editable text){}});
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(s("choose_shortcut_app")).setView(body).setNegativeButton(s("cancel"),null).create();
        list.setOnItemClickListener((parent,view,position,id)->{profile.put("shortcut_package",visible.get(position).packageName);refresh();dialog.dismiss();});dialog.show();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        new Thread(()->{
            try{
            Map<String,App> apps=new LinkedHashMap<>();Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            for(ResolveInfo resolved:pm.queryIntentActivities(intent,0)){ActivityInfo info=resolved.activityInfo;if(info==null||!info.enabled||!info.exported||!info.applicationInfo.enabled||activity.getPackageName().equals(info.packageName))continue;
                if(pm.getLaunchIntentForPackage(info.packageName)==null)continue;apps.put(info.packageName,new App(pm.getApplicationLabel(info.applicationInfo).toString(),info.applicationInfo));}
            List<App> sorted=new ArrayList<>(apps.values());Collator collator=Collator.getInstance(L.context(activity).getResources().getConfiguration().getLocales().get(0));sorted.sort((a,b)->collator.compare(a.name,b.name));
            activity.runOnUiThread(()->{if(activity.isDestroyed()||!dialog.isShowing())return;all.addAll(sorted);filter.run();});
            }catch(RuntimeException e){activity.runOnUiThread(()->{if(!activity.isDestroyed()&&dialog.isShowing())Toast.makeText(activity,s("app_unavailable"),Toast.LENGTH_SHORT).show();});}
        },"AppShortcutPicker").start();
    }
}
