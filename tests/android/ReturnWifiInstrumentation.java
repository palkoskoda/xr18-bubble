package sk.palko.xr18bubble.returnwifitest;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;

/** Tests existing overlay UI handlers in the target process, with no production hooks. */
public final class ReturnWifiInstrumentation extends Instrumentation {
    private static final String TARGET = "sk.palko.xr18bubble";
    private Class<?> serviceClass;
    private Object service;
    private int checks;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            serviceClass = target.getClassLoader().loadClass(TARGET + ".OverlayService");
            target.getSharedPreferences("profile", Context.MODE_PRIVATE).edit().clear()
                .putString("host", "10.0.2.2").putString("name", "Return Wi-Fi fixture")
                .putString("language", "en").putString("auto_connect", "off")
                .putString("control_size", "small").putString("bubble_size", "medium")
                .putString("wifi_identity_asked", "yes").putString("return_ssid", "PreviousNet")
                .putStringSet("selected", new HashSet<>(Arrays.asList("main"))).commit();
            startActivitySync(new Intent().setComponent(new ComponentName(TARGET, TARGET + ".MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            main(() -> target.startForegroundService(new Intent().setComponent(new ComponentName(TARGET, TARGET + ".OverlayService")).setAction("show")));
            for (int i = 0; i < 50; i++) {
                main(() -> service = field(serviceClass, "live").get(null));
                if (service != null) break;
                Thread.sleep(100);
            }
            check(service != null, "overlay service started");
            exercise();
            result.putString("stream", "ReturnWifiInstrumentation passed: " + checks + " checks; simulated request lifecycle only, no radio association\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "ReturnWifiInstrumentation FAILED: " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            try { getTargetContext().stopService(new Intent().setComponent(new ComponentName(TARGET, TARGET + ".OverlayService"))); }
            catch (Throwable ignored) {}
        }
    }

    private void exercise() throws Exception {
        ConnectivityManager.NetworkCallback borrowedRequest = new ConnectivityManager.NetworkCallback() {};
        int[] baseHeight = new int[1];
        main(() -> {
        set("wifiRequest", borrowedRequest); set("wifiBorrowed", false);
        invoke("show", new Class<?>[]{boolean.class}, true);
        baseHeight[0] = ((WindowManager.LayoutParams)get("params")).height;
        check(returnButton((View)get("window")) == null, "normal panel has no return button");
        invoke("closePanel");
        check(!bool("expanded"), "normal close collapses");
        check(get("returnDialog") == null, "normal close has no confirmation");
        check(get("wifiRequest") == borrowedRequest, "normal close does not release unrelated request");

        set("wifiBorrowed", true); set("hasSession", true); set("offerReturnOnClose", true);
        invoke("show", new Class<?>[]{boolean.class}, true);
        check(returnButton((View)get("window")) != null, "borrowed panel has return button");
        int extraHeight = ((WindowManager.LayoutParams)get("params")).height - baseHeight[0];
        int expectedExtra = Math.round(34 * getTargetContext().getResources().getDisplayMetrics().density);
        check(extraHeight == expectedExtra, "return button adds exactly 34 dp, got " + extraHeight);
        invoke("closePanel");
        AlertDialog dialog = (AlertDialog)get("returnDialog");
        check(dialog != null && dialog.isShowing(), "borrowed close asks for return");
        check(((View)get("window")).getVisibility() == View.INVISIBLE, "confirmation hides underlying panel");
        View previousWindow = (View)get("window");
        invoke("onStartCommand", new Class<?>[]{Intent.class, int.class, int.class}, new Intent().setAction("reload"), 0, 99);
        check(get("window") != previousWindow, "RELOAD rebuilds panel while confirmation is open");
        check(get("returnDialog") == dialog && dialog.isShowing(), "RELOAD retains visible confirmation");
        check(((View)get("window")).getVisibility() == View.INVISIBLE, "RELOAD replacement panel stays hidden behind confirmation");
        check(!((View)get("window")).hasFocus(), "hidden replacement panel does not take focus from confirmation");
        });
        clickDialog(AlertDialog.BUTTON_NEUTRAL);
        main(() -> {
        check(bool("expanded"), "Cancel leaves panel expanded");
        check(bool("wifiBorrowed") && get("wifiRequest") == borrowedRequest, "Cancel retains borrowed request");
        check(bool("offerReturnOnClose"), "Cancel preserves pending return offer");
        check(get("returnDialog") == null, "Cancel dismisses dialog");
        check(((View)get("window")).getVisibility() == View.VISIBLE, "Cancel restores expanded panel visibility");

        invoke("closePanel");
        });
        clickDialog(AlertDialog.BUTTON_NEGATIVE);
        main(() -> {
        check(!bool("expanded"), "Stay collapses panel");
        check(bool("wifiBorrowed") && get("wifiRequest") == borrowedRequest, "Stay retains borrowed request");
        check(!bool("offerReturnOnClose"), "Stay consumes pending return offer");
        check(get("returnDialog") == null, "Stay dismisses dialog");
        check(((View)get("window")).getVisibility() == View.VISIBLE, "Stay leaves collapsed bubble visible");

        invoke("openControls");
        check(bool("expanded"), "reopening retained mixer session expands controls");
        check(!bool("offerReturnOnClose"), "reopening without a network change does not rearm return offer");
        check(returnButton((View)get("window")) != null, "manual return button remains after Stay and reopen");
        invoke("closePanel");
        check(get("returnDialog") == null && !bool("expanded"), "closing reopened unchanged Wi-Fi session does not prompt again");
        check(bool("wifiBorrowed") && get("wifiRequest") == borrowedRequest && bool("hasSession"), "silent repeated close retains borrowed mixer session");
        // A subsequent real loan would rearm the offer in the Wi-Fi callback.
        set("offerReturnOnClose", true);
        invoke("show", new Class<?>[]{boolean.class}, true);
        invoke("closePanel");
        check(get("returnDialog") != null, "a newly armed Wi-Fi loan offers return again");
        });
        clickDialog(AlertDialog.BUTTON_POSITIVE);
        main(() -> {
        check(!bool("expanded"), "Return collapses panel");
        check(!bool("wifiBorrowed"), "Return clears borrowed state");
        check(get("wifiRequest") == null, "Return releases request");
        check(!bool("hasSession") && get("currentNetwork") == null, "Return clears mixer network session");
        check(!bool("autoConnect"), "Return prevents automatic reconnect");
        check(!bool("offerReturnOnClose"), "Return clears pending return offer");
        check(get("returnDialog") == null, "Return dismisses dialog");
        check(((View)get("window")).getVisibility() == View.VISIBLE, "Return leaves collapsed bubble visible");

        invoke("show", new Class<?>[]{boolean.class}, true);
        check(returnButton((View)get("window")) == null, "returned panel removes return button");
        check(((WindowManager.LayoutParams)get("params")).height == baseHeight[0], "returned panel restores compact height");
        set("wifiRequest", borrowedRequest); set("wifiBorrowed", true); set("hasSession", true); set("offerReturnOnClose", true);
        invoke("show", new Class<?>[]{boolean.class}, true);
        Button direct = returnButton((View)get("window"));
        check(direct != null && direct.performClick(), "direct return button is clickable");
        check(!bool("expanded") && !bool("wifiBorrowed") && get("wifiRequest") == null && !bool("hasSession"), "direct return button cleans request/session and collapses");
        check(!bool("offerReturnOnClose"), "direct return clears pending return offer");
        });

        AlertDialog[] staleDialog = new AlertDialog[1];
        ConnectivityManager.NetworkCallback replacement = new ConnectivityManager.NetworkCallback() {};
        main(() -> {
            set("wifiRequest", borrowedRequest); set("wifiBorrowed", true); set("hasSession", true); set("offerReturnOnClose", true);
            invoke("show", new Class<?>[]{boolean.class}, true);
            invoke("closePanel");
            staleDialog[0] = (AlertDialog)get("returnDialog");
            invoke("closePanel");
            check(get("returnDialog") == staleDialog[0], "repeated close reuses one pending confirmation");
            invoke("onStartCommand", new Class<?>[]{Intent.class, int.class, int.class}, new Intent().setAction("profile"), 0, 100);
            check(get("returnDialog") == null && !staleDialog[0].isShowing(), "PROFILE dismisses stale confirmation");
            check(get("wifiRequest") == null && !bool("wifiBorrowed") && !bool("hasSession"), "PROFILE clears previous borrowed session");
            check(!bool("offerReturnOnClose"), "PROFILE clears pending return offer");
            set("wifiRequest", replacement); set("wifiBorrowed", true); set("hasSession", true); set("offerReturnOnClose", true);
            invoke("show", new Class<?>[]{boolean.class}, true);
            // Simulates a previously queued click on the dismissed dialog after a new request.
            staleDialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        waitForIdleSync();
        main(() -> {
            check(get("wifiRequest") == replacement && bool("wifiBorrowed") && bool("hasSession"), "stale confirmation cannot release replacement request/session");
            check(bool("expanded"), "stale confirmation cannot collapse replacement panel");
            check(bool("offerReturnOnClose"), "stale confirmation cannot consume replacement loan offer");
            invoke("returnPreviousWifi");
        });
    }

    private void clickDialog(int button) throws Exception {
        main(() -> ((AlertDialog)get("returnDialog")).getButton(button).performClick());
        waitForIdleSync();
    }

    private Button returnButton(View view) {
        if (view instanceof Button && ((Button)view).getText().toString().startsWith("↩ ")) return (Button)view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) {
            Button found = returnButton(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private Field field(Class<?> owner, String name) throws Exception { Field f = owner.getDeclaredField(name); f.setAccessible(true); return f; }
    private Object get(String name) throws Exception { return field(serviceClass, name).get(service); }
    private boolean bool(String name) throws Exception { return (Boolean)get(name); }
    private void set(String name, Object value) throws Exception { field(serviceClass, name).set(service, value); }
    private void invoke(String name) throws Exception { invoke(name, new Class<?>[0]); }
    private void invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = serviceClass.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(service, args);
    }
    private void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private interface Action { void run() throws Exception; }
    private void main(Action action) throws Exception {
        Throwable[] failure = new Throwable[1];
        runOnMainSync(() -> { try { action.run(); } catch (Throwable e) { failure[0] = e; } });
        if (failure[0] != null) throw new Exception("Main-thread assertion failed", failure[0]);
    }
}
