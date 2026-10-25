package com.example.payamresan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.JSONObject;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SharedPreferences d = context.getSharedPreferences("smslab_diag", Context.MODE_PRIVATE);
        d.edit()
            .putInt("recv_count", d.getInt("recv_count", 0) + 1)
            .putLong("recv_last", System.currentTimeMillis())
            .commit();
        try {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null || msgs.length == 0 || msgs[0] == null) return;
            StringBuilder body = new StringBuilder();
            for (SmsMessage m : msgs) {
                if (m != null && m.getMessageBody() != null) body.append(m.getMessageBody());
            }
            JSONObject o = new JSONObject();
            o.put("phone", msgs[0].getOriginatingAddress());
            o.put("text", body.toString());
            o.put("timestamp", msgs[0].getTimestampMillis());
            SmsStore.push(context, SmsStore.Q_RECEIVED, o);
            SmsLabPlugin.pingReceived();
        } catch (Exception e) {
            d.edit().putInt("recv_err", d.getInt("recv_err", 0) + 1).commit();
        }
    }
}
