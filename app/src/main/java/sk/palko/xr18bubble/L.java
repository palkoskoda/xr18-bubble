package sk.palko.xr18bubble;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import java.util.Locale;

/** Small resource-backed localization helper shared by the activity and overlay. */
final class L {
    private static final String PREFS = "profile";
    private static final String LANGUAGE = "language";

    private L() {}

    /** Returns one of the supported language codes. */
    static String language(Context context) {
        return supported(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(LANGUAGE, "system"));
    }

    /** Saves the application language. Recreate visible views after changing it. */
    static void locale(Context context, String code) {
        String selected=supported(code);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(LANGUAGE, selected).apply();
        if(android.os.Build.VERSION.SDK_INT>=33){
            android.app.LocaleManager manager=context.getSystemService(android.app.LocaleManager.class);
            if(manager!=null)manager.setApplicationLocales("system".equals(selected)?LocaleList.getEmptyLocaleList():new LocaleList(Locale.forLanguageTag(selected)));
        }
    }

    /** Does not alter the process-wide locale or mutate the caller's resources. */
    static Context context(Context context) {
        String code = language(context);
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        LocaleList locales;
        if ("system".equals(code)) {
            Context app = context.getApplicationContext();
            locales = (app == null ? context : app).getResources().getConfiguration().getLocales();
        } else {
            locales = new LocaleList(Locale.forLanguageTag(code));
        }
        configuration.setLocales(locales);
        if (!locales.isEmpty()) configuration.setLayoutDirection(locales.get(0));
        return context.createConfigurationContext(configuration);
    }

    static String s(Context context, String key, Object... args) {
        Context localized = context(context);
        int id = localized.getResources().getIdentifier(key, "string", context.getPackageName());
        if (id == 0) return key;
        return args.length == 0 ? localized.getString(id) : localized.getString(id, args);
    }

    private static String supported(String code) {
        if (code == null) return "system";
        switch (code) {
            case "en": case "sk": case "cs": case "de": case "pl": case "hu":
            case "fr": case "es": case "it": case "pt": case "nl": case "uk": return code;
            default: return "system";
        }
    }

    static String[] codes() {
        return new String[]{"system","en","sk","cs","de","pl","hu","fr","es","it","pt","nl","uk"};
    }

    static String[] languageNames(Context context) {
        return new String[]{s(context,"language_system"),"English","Slovenčina","Čeština","Deutsch",
                "Polski","Magyar","Français","Español","Italiano","Português","Nederlands","Українська"};
    }
}
