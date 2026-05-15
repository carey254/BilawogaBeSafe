package com.example.bilawoga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

public class SmsDeliveredReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int code = getResultCode();
        // STEALTH MODE: No visible indication on sender's phone
        // Only receiver will see the SMS message
        if (code == android.app.Activity.RESULT_OK) {
            android.util.Log.d("SmsDeliveredReceiver", "STEALTH: SMS delivered (no UI indication)");
        } else {
            android.util.Log.e("SmsDeliveredReceiver", "STEALTH: SMS not delivered (no UI indication)");
        }
        // No toast - completely silent
    }
}
