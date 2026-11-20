package com.soda.helper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SodaService service = SodaService.get();
        if (service != null) service.stopEngine();
        KeepAliveService.stop(context);
    }
}
