package com.example.payamresan;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** صف پایدار رویدادها (نتیجهٔ ارسال، تحویل، پیام دریافتی) که با بسته‌شدن برنامه از بین نمی‌رود. */
final class SmsStore {
    private static final String PREF = "smslab_store";
    static final String Q_RESULTS = "q_results";
    static final String Q_RECEIVED = "q_received";

    private SmsStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    static synchronized void push(Context c, String queue, JSONObject item) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray arr = new JSONArray(p.getString(queue, "[]"));
            arr.put(item);
            p.edit().putString(queue, arr.toString()).commit();
        } catch (Exception ignored) {
        }
    }

    static synchronized JSONArray drain(Context c, String queue) {
        try {
            SharedPreferences p = prefs(c);
            JSONArray arr = new JSONArray(p.getString(queue, "[]"));
            p.edit().putString(queue, "[]").commit();
            return arr;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * ثبت نتیجهٔ یک بخش از پیامک چندبخشی.
     * وقتی همهٔ بخش‌ها رسیدند، خلاصه را برمی‌گرداند؛ تا آن موقع null.
     */
    static synchronized JSONObject recordPart(Context c, String kind, String id, int total, boolean ok, String errCode) {
        try {
            SharedPreferences p = prefs(c);
            String key = "part_" + kind + "_" + id;
            JSONObject s = new JSONObject(p.getString(key, "{\"done\":0,\"bad\":0,\"code\":\"\"}"));
            s.put("done", s.getInt("done") + 1);
            if (!ok) {
                s.put("bad", s.getInt("bad") + 1);
                if (s.getString("code").isEmpty()) s.put("code", errCode);
            }
            if (s.getInt("done") >= total) {
                p.edit().remove(key).commit();
                return s;
            }
            p.edit().putString(key, s.toString()).commit();
        } catch (Exception ignored) {
        }
        return null;
    }
}
