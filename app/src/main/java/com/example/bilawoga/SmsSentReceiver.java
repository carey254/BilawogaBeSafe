package com.example.bilawoga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public class SmsSentReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int code = getResultCode();
        
        // STEALTH MODE: No visible indication on sender's phone
        // Only log silently - no toast messages
        if (code == android.app.Activity.RESULT_OK) {
            android.util.Log.d("SmsSentReceiver", "STEALTH: SMS sent successfully (no UI indication)");
        } else {
            String errorMsg;
            switch (code) {
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    errorMsg = "Failed to send SMS: Generic failure"; break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    errorMsg = "Failed to send SMS: No service"; break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    errorMsg = "Failed to send SMS: Null PDU"; break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    errorMsg = "Failed to send SMS: Ragdio off"; break;
                case SmsManager.RESULT_ERROR_LIMIT_EXCEEDED:
                    errorMsg = "Failed to send SMS: Rate limit exceeded"; break;
                default:
                    errorMsg = "Failed to send SMS: Error code " + code; break;
            }
            android.util.Log.e("SmsSentReceiver", "STEALTH: " + errorMsg + " (no UI indication)");
        }
        
        // No toast - silent operation
        // Only receiver will see the SMS message

        // For failures, attempt retry via SOSHelper using original extras
        if (code != android.app.Activity.RESULT_OK) {
            try {
                // Create instance of SOSHelper to call instance method
                com.example.bilawoga.utils.SOSHelper sosHelper = new com.example.bilawoga.utils.SOSHelper(context.getApplicationContext());
                sosHelper.handleSmsSendFailure(context.getApplicationContext(), intent, code);
            } catch (Throwable ignore) {
                // Silently ignore errors in stealth mode
            }
        }
    }
}
