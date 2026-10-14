package sk.palko.xr18bubble;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

final class Ui {
    static final int BG=0xff171717,CARD=0xff292929,LINE=0xff444444,TEXT=0xfff2f2f2,DIM=0xffa4a4a4,GREEN=0xff50c878,RED=0xffe44747,GOLD=0xffe6be56;
    static final int PAGE=0xfff2f2f7,INK=0xff1c1c1e,SECONDARY=0xff73737b,WHITE=Color.WHITE,BLUE=0xff007aff,SEPARATOR=0xffe5e5ea;
    /** Settings palette is resolved per context; the live audio panel keeps its own palette. */
    static boolean isDark(Context c) {
        String theme=c.getSharedPreferences("profile",Context.MODE_PRIVATE).getString("theme","system");
        return "dark".equals(theme)||(!"light".equals(theme)&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);
    }
    static int activityTheme(Context c){return isDark(c)?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar;}
    static int page(Context c){return isDark(c)?0xff111113:PAGE;}
    static int surface(Context c){return isDark(c)?0xff202023:WHITE;}
    static int ink(Context c){return isDark(c)?0xfff3f3f6:INK;}
    static int secondary(Context c){return isDark(c)?0xffa3a3ad:SECONDARY;}
    static int separator(Context c){return isDark(c)?0xff38383e:SEPARATOR;}
    static int blue(Context c){return isDark(c)?0xff5aa8ff:BLUE;}
    static int dp(Context c,float n) { return (int)(c.getResources().getDisplayMetrics().density*n+.5f); }
    static GradientDrawable bg(int color,int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d; }
    static TextView text(Context c,String s,int size,int color) { TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t; }
    static TextView title(Context c,String s,int size) { TextView t=text(c,s,size,TEXT);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t; }
    static void updateText(TextView view,String text){if(!text.contentEquals(view.getText()))view.setText(text);}
    static Button button(Context c,String s,int color) {
        Button b=new Button(c);b.setText(s);b.setTextColor(TEXT);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
        b.setBackground(bg(color,dp(c,8)));b.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));return b;
    }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.VERTICAL);return l; }
    static LinearLayout row(Context c) { LinearLayout l=new LinearLayout(c);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    static void add(LinearLayout l,android.view.View v,int h) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,h<0?h:dp(l.getContext(),h));p.bottomMargin=dp(l.getContext(),10);l.addView(v,p); }
    static int accent(XAirProtocol.Control c){return c.id.startsWith("dca")?0xffd4b746:c.id.startsWith("bus")?0xffa579c2:c.id.startsWith("ch")?0xff53ab78:0xff5fa8da;}
}
