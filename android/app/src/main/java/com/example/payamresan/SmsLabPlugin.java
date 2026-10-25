package com.example.payamresan;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.activity.result.ActivityResult;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(
    name = "SmsLab",
    permissions = {
        @Permission(alias = "send", strings = {Manifest.permission.SEND_SMS}),
        @Permission(alias = "receive", strings = {Manifest.permission.RECEIVE_SMS}),
        @Permission(alias = "phoneState", strings = {Manifest.permission.READ_PHONE_STATE})
    }
)
public class SmsLabPlugin extends Plugin {

    private static SmsLabPlugin instance;

    @Override
    public void load() {
        instance = this;
    }

    @Override
    protected void handleOnResume() {
        flushResults();
        flushReceived();
    }

    @Override
    protected void handleOnDestroy() {
        if (instance == this) instance = null;
    }

    /** از گیرنده‌ها صدا زده می‌شود تا اگر رابط کاربری زنده است، رویداد را فوراً بفرستد. */
    static void ping() {
        SmsLabPlugin p = instance;
        if (p != null) p.flushResults();
    }

    static void pingReceived() {
        SmsLabPlugin p = instance;
        if (p != null) p.flushReceived();
    }

    private void flushResults() {
        JSONArray items = SmsStore.drain(getContext(), SmsStore.Q_RESULTS);
        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject o = items.getJSONObject(i);
                String type = o.optString("type");
                o.remove("type");
                notifyListeners("sent".equals(type) ? "smsSent" : "smsDelivered", JSObject.fromJSONObject(o), true);
            } catch (Exception ignored) {
            }
        }
    }

    private void flushReceived() {
        JSONArray items = SmsStore.drain(getContext(), SmsStore.Q_RECEIVED);
        for (int i = 0; i < items.length(); i++) {
            try {
                notifyListeners("smsReceived", JSObject.fromJSONObject(items.getJSONObject(i)), true);
            } catch (Exception ignored) {
            }
        }
    }

    /* ---------------- مجوزها ---------------- */

    private boolean hasPerm(String p) {
        return ContextCompat.checkSelfPermission(getContext(), p) == PackageManager.PERMISSION_GRANTED;
    }

    private String stateOf(String alias, String perm) {
        if (hasPerm(perm)) return "granted";
        PermissionState s = getPermissionState(alias);
        return s == PermissionState.DENIED ? "denied" : "prompt";
    }

    private JSObject buildPerms() {
        JSObject r = new JSObject();
        r.put("send", stateOf("send", Manifest.permission.SEND_SMS));
        r.put("receive", stateOf("receive", Manifest.permission.RECEIVE_SMS));
        r.put("phoneState", stateOf("phoneState", Manifest.permission.READ_PHONE_STATE));
        // انتخاب مخاطب با انتخابگر سیستم انجام می‌شود و مجوز خواندن مخاطبین لازم ندارد.
        r.put("contacts", "granted");
        return r;
    }

    @Override
    @PluginMethod
    public void checkPermissions(PluginCall call) {
        call.resolve(buildPerms());
    }

    @Override
    @PluginMethod
    public void requestPermissions(PluginCall call) {
        List<String> aliases = new ArrayList<>();
        JSArray which = call.getArray("which");
        if (which != null) {
            for (int i = 0; i < which.length(); i++) {
                String a = which.optString(i);
                boolean known = "send".equals(a) || "receive".equals(a) || "phoneState".equals(a);
                if (known && !aliases.contains(a)) aliases.add(a);
            }
        }
        if (aliases.isEmpty()) {
            call.resolve(buildPerms());
            return;
        }
        requestPermissionForAliases(aliases.toArray(new String[0]), call, "permsCallback");
    }

    @PermissionCallback
    private void permsCallback(PluginCall call) {
        call.resolve(buildPerms());
    }

    /* ---------------- سیم‌کارت‌ها ---------------- */

    private List<SubscriptionInfo> activeSubs() {
        if (!hasPerm(Manifest.permission.READ_PHONE_STATE)) return null;
        SubscriptionManager sm = (SubscriptionManager) getContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        if (sm == null) return null;
        try {
            return sm.getActiveSubscriptionInfoList();
        } catch (SecurityException e) {
            return null;
        }
    }

    @PluginMethod
    public void getSims(PluginCall call) {
        JSObject ret = new JSObject();
        JSArray arr = new JSArray();
        List<SubscriptionInfo> list = activeSubs();
        if (list != null) {
            for (SubscriptionInfo info : list) {
                JSObject s = new JSObject();
                int slot = info.getSimSlotIndex();
                CharSequence carrier = info.getCarrierName();
                String label = "SIM " + (slot + 1);
                if (carrier != null && carrier.length() > 0) label += " - " + carrier;
                s.put("slot", slot);
                s.put("subscriptionId", info.getSubscriptionId());
                s.put("label", label);
                arr.put(s);
            }
        }
        ret.put("sims", arr);
        call.resolve(ret);
    }

    private int findSubscription(int slot) {
        List<SubscriptionInfo> list = activeSubs();
        if (list == null) return -1;
        for (SubscriptionInfo info : list) {
            if (info.getSimSlotIndex() == slot) return info.getSubscriptionId();
        }
        return -1;
    }
    /* ---------------- ارسال پیامک ---------------- */

    @SuppressWarnings("deprecation")
    private SmsManager smsManagerFor(Context ctx, int subId) {
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager base = ctx.getSystemService(SmsManager.class);
            return subId >= 0 ? base.createForSubscriptionId(subId) : base;
        }
        return subId >= 0 ? SmsManager.getSmsManagerForSubscriptionId(subId) : SmsManager.getDefault();
    }

    private PendingIntent makeIntent(Context ctx, String action, String id, int total, int part) {
        Intent intent = new Intent(action);
        intent.setClass(ctx, SmsResultReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("total", total);
        int req = (id + action + part).hashCode();
        return PendingIntent.getBroadcast(ctx, req, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    @PluginMethod
    public void sendSms(PluginCall call) {
        String id = call.getString("id");
        String phone = call.getString("phone");
        String text = call.getString("text");
        int simSlot = call.getInt("simSlot", -1);

        if (id == null || id.isEmpty() || text == null || text.isEmpty()) {
            call.reject("invalid request", "GENERIC_FAILURE");
            return;
        }
        if (phone == null || phone.trim().isEmpty()) {
            call.reject("invalid number", "INVALID_NUMBER");
            return;
        }
        Context ctx = getContext();
        if (!ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            call.reject("no telephony", "UNAVAILABLE");
            return;
        }
        if (!hasPerm(Manifest.permission.SEND_SMS)) {
            call.reject("permission denied", "PERMISSION_DENIED");
            return;
        }
        try {
            int subId = -1;
            if (simSlot >= 0) {
                subId = findSubscription(simSlot);
                if (subId < 0) {
                    call.reject("sim not found", "NO_SIM");
                    return;
                }
            }
            SmsManager sm = smsManagerFor(ctx, subId);
            ArrayList<String> parts = sm.divideMessage(text);
            int total = parts.size();
            ArrayList<PendingIntent> sentList = new ArrayList<>();
            ArrayList<PendingIntent> delList = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                sentList.add(makeIntent(ctx, SmsResultReceiver.ACTION_SENT, id, total, i));
                delList.add(makeIntent(ctx, SmsResultReceiver.ACTION_DELIVERED, id, total, i));
            }
            if (total == 1) {
                sm.sendTextMessage(phone.trim(), null, parts.get(0), sentList.get(0), delList.get(0));
            } else {
                sm.sendMultipartTextMessage(phone.trim(), null, parts, sentList, delList);
            }
            JSObject ret = new JSObject();
            ret.put("accepted", true);
            ret.put("parts", total);
            call.resolve(ret);
        } catch (SecurityException e) {
            call.reject("permission denied", "PERMISSION_DENIED");
        } catch (IllegalArgumentException e) {
            call.reject("invalid number", "INVALID_NUMBER");
        } catch (Exception e) {
            call.reject("send failed", "GENERIC_FAILURE");
        }
    }

    /* ---------------- پیام‌های دریافتی ---------------- */

    @PluginMethod
    public void drainReceived(PluginCall call) {
        flushResults();
        JSArray arr = new JSArray();
        JSONArray items = SmsStore.drain(getContext(), SmsStore.Q_RECEIVED);
        for (int i = 0; i < items.length(); i++) {
            arr.put(items.opt(i));
        }
        JSObject ret = new JSObject();
        ret.put("messages", arr);
        call.resolve(ret);
    }

    /* ---------------- انتخاب مخاطب ---------------- */

    @PluginMethod
    public void pickContact(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(call, intent, "pickContactResult");
    }

    @ActivityCallback
    private void pickContactResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        Intent data = result.getData();
        if (result.getResultCode() == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            String[] cols = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            };
            try (Cursor c = getContext().getContentResolver().query(uri, cols, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    ret.put("phone", c.getString(0));
                    ret.put("name", c.getString(1));
                }
            } catch (Exception ignored) {
            }
        }
        call.resolve(ret);
    }

    /* ---------------- اطلاعات دستگاه ---------------- */

    private String simStateName(int s) {
        switch (s) {
            case TelephonyManager.SIM_STATE_ABSENT: return "absent";
            case TelephonyManager.SIM_STATE_READY: return "ready";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED: return "pin_required";
            case TelephonyManager.SIM_STATE_PUK_REQUIRED: return "puk_required";
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED: return "network_locked";
            default: return "unknown";
        }
    }

    @PluginMethod
    public void getDeviceInfo(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("manufacturer", Build.MANUFACTURER);
        ret.put("model", Build.MODEL);
        ret.put("androidVersion", Build.VERSION.RELEASE);
        ret.put("sdkInt", Build.VERSION.SDK_INT);
        ret.put("hasTelephony", getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        TelephonyManager tm = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (tm != null) ret.put("simState", simStateName(tm.getSimState()));
        call.resolve(ret);
    }
}
