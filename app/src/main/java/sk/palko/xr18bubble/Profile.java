package sk.palko.xr18bubble;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class Profile {
    private final SharedPreferences prefs;
    Profile(Context c) { prefs=c.getSharedPreferences("profile",Context.MODE_PRIVATE); }
    String get(String key) { return prefs.getString(key,""); }
    void put(String key,String value) { prefs.edit().putString(key,value).apply(); }
    /** Called only after a verified mixer reply, using that session's Wi-Fi identity. */
    void rememberWifi(WifiIdentity wifi){
        if(wifi==null)return;
        SharedPreferences.Editor edit=prefs.edit();
        if(!wifi.ssid.equals(get("ssid"))){edit.putString("password","").putString("bssid","");}
        edit.putString("ssid",wifi.ssid);
        if(!"UNKNOWN".equals(wifi.security))edit.putString("security",wifi.security);
        else if(!wifi.ssid.equals(get("ssid")))edit.putString("security","UNKNOWN");
        edit.apply();
    }
    int position(String key,int fallback) { return prefs.getInt(key,fallback); }
    void setPosition(String key,int v) { prefs.edit().putInt(key,v).apply(); }
    List<XAirProtocol.Control> selected() {
        Set<String> ids=prefs.getStringSet("selected",new HashSet<>(Arrays.asList("main","dca1","mute1")));
        List<XAirProtocol.Control> out=new ArrayList<>();
        for(XAirProtocol.Control c:XAirProtocol.CONTROLS) if(ids.contains(c.id)) out.add(c);
        return out;
    }
    void select(Set<String> ids) { prefs.edit().putStringSet("selected",new HashSet<>(ids)).apply(); }
    boolean volume(XAirProtocol.Control c){return c.faderPath!=null&&!"off".equals(get("volume_"+c.id));}
    boolean mute(XAirProtocol.Control c){return c.faderPath==null||!"off".equals(get("mute_"+c.id));}
    void parts(XAirProtocol.Control c,boolean volume,boolean mute){put("volume_"+c.id,volume?"on":"off");put("mute_"+c.id,mute?"on":"off");}
    Set<String> requiredPaths(){Set<String> out=new HashSet<>();for(XAirProtocol.Control c:selected()){if(volume(c))out.add(c.faderPath);if(mute(c))out.add(c.mutePath);}return out;}
    String controlSize(){String s=get("control_size");return s.isEmpty()?"small":s;}
    String fontSize(){String s=get("font_size");return s.isEmpty()?"100":s;}
    String bubbleSize(){String s=get("bubble_size");return s.isEmpty()?"medium":s;}
    String label(Context context,XAirProtocol.Control c) {
        String v=get("label_"+c.id);if(!v.isEmpty())return v;
        if(c.id.equals("main"))return L.s(context,"main_lr");
        String prefix=c.id.replaceAll("[0-9]","");int n=Integer.parseInt(c.id.substring(prefix.length()));
        return L.s(context,prefix.equals("dca")?"dca_n":prefix.equals("mute")?"mute_group_n":prefix.equals("bus")?"bus_n":"channel_n",n);
    }
    String label(XAirProtocol.Control c){String v=get("label_"+c.id);return v.isEmpty()?c.label:v;}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        String alias="xr18_wifi";
        if(!ks.containsAlias(alias)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)ks.getKey(alias,null);
    }
    void password(String password) throws Exception {
        if(password.isEmpty()) { put("password",""); return; }
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        put("password",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP));
    }
    String password() throws Exception {
        String v=get("password"); if(v.isEmpty()) return "";
        String[] parts=v.split(":");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
}
