package sk.palko.xr18bubble;

import android.content.Context;
import android.net.*;
import android.net.wifi.*;
import android.os.Build;

/** Read identity only from the Network actually carrying this mixer session. */
final class WifiIdentity {
    final String ssid,security;
    WifiIdentity(String name,String security){this.ssid=name;this.security=security;}
    static WifiIdentity from(NetworkCapabilities caps){
        if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||!(caps.getTransportInfo() instanceof WifiInfo))return null;
        return from((WifiInfo)caps.getTransportInfo());
    }
    static WifiIdentity from(WifiInfo info){
        if(info==null)return null;String name=info.getSSID();
        if(name==null||name.equals(WifiManager.UNKNOWN_SSID)||name.isEmpty())return null;
        if(name.startsWith("\"")&&name.endsWith("\""))name=name.substring(1,name.length()-1);
        if(name.isEmpty())return null;
        String mode="UNKNOWN";
        if(Build.VERSION.SDK_INT>=31){int type=info.getCurrentSecurityType();
            if(type==WifiInfo.SECURITY_TYPE_OPEN)mode="OPEN";
            else if(type==WifiInfo.SECURITY_TYPE_PSK)mode="WPA2";
            else if(type==WifiInfo.SECURITY_TYPE_SAE)mode="WPA3";
        }
        return new WifiIdentity(name,mode);
    }
    static WifiIdentity read(Context context,Network network){
        if(network==null)return null;
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        try{
            WifiIdentity identity=from(cm.getNetworkCapabilities(network));if(identity!=null)return identity;
            // Legacy WifiManager describes the primary Wi-Fi only, never a secondary request.
            if(network.equals(cm.getActiveNetwork()))return from(context.getSystemService(WifiManager.class).getConnectionInfo());
        }catch(SecurityException ignored){}
        return null;
    }
}
