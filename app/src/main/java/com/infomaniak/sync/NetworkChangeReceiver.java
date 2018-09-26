package com.infomaniak.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

public class NetworkChangeReceiver extends BroadcastReceiver {

    private final Handler handler;

    public NetworkChangeReceiver(Handler handler) {
        this.handler = handler;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        Message message = handler.obtainMessage();
        Bundle messageBundle = new Bundle();
        messageBundle.putBoolean("isNetworking", (activeNetwork != null));
        message.setData(messageBundle);
        handler.sendMessage(message);
    }
}