package com.example.payamresan;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** نتیجهٔ واقعی ارسال و گزارش تحویل را از سیستم‌عامل می‌گیرد و در صف ثبت می‌کند. */
public class SmsResultReceiver extends BroadcastReceiver {
    public static final String ACTION_SENT = "com.example.payamresan.SMS_SENT";
    public static final String ACTION_DELIVERED = "com.example.payamresan.SMS_DELIVERED";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String id = intent.getStringExtra("id");
        int total = intent.getIntExtra("total", 1);
        if (action == null || id == null) return;
        int code = getResultCode();
        try {
            if (ACTION_SENT.equals(action)) {
                boolean ok = code == Activity.RESULT_OK;
                JSONObject agg = SmsStore.recordPart(context, "sent", id, total, ok, ok ? "" : mapError(code));
                if (agg == null) return;
                int bad = agg.getInt("bad");
                JSONObject o = new JSONObject();
                o.put("type", "sent");
                o.put("id", id);
                o.put("result", bad == 0 ? "ok" : "error");
                o.put("errorCode", bad == 0 ? "" : agg.getString("code"));
                o.put("errorDetail", bad == 0 ? "" : ("failed parts " + bad + "/" + total));
                o.put("parts", total);
                SmsStore.push(context, SmsStore.Q_RESULTS, o);
            } else if (ACTION_DELIVERED.equals(action)) {
                boolean ok = code == Activity.RESULT_OK;
                JSONObject agg = SmsStore.recordPart(context, "delivered", id, total, ok, ok ? "" : "GENERIC_FAILURE");
                if (agg == null) return;
                JSONObject o = new JSONObject();
                o.put("type", "delivered");
                o.put("id", id);
                o.put("result", agg.getInt("bad") == 0 ? "ok" : "error");
                o.put("timestamp", System.currentTimeMillis());
                SmsStore.push(context, SmsStore.Q_RESULTS, o);
            } else {
                return;
            }
            SmsLabPlugin.ping();
        } catch (Exception ignored) {
        }
    }

    /** کدهای استاندارد SmsManager.RESULT_ERROR_* (عدد خام، برای سازگاری با نسخه‌های مختلف اندروید) */
    private static String mapError(int code) {
        switch (code) {
            case 2: return "RADIO_OFF";
            case 3: return "NULL_PDU";
            case 4: return "NO_SERVICE";
            case 5: return "LIMIT_EXCEEDED";
            default: return "GENERIC_FAILURE";
        }
    }
}
