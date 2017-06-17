/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid;

import android.accounts.Account;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.support.annotation.NonNull;

import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Services;
import at.bitfire.davdroid.resource.LocalTaskList;
import at.bitfire.ical4android.TaskProvider;
import lombok.Cleanup;

public class PackageChangedReceiver extends BroadcastReceiver {

    @Override
    @SuppressLint("MissingPermission")
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction()) ||
            Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction()))
            updateTaskSync(context);
    }

    static void updateTaskSync(@NonNull Context context) {
        boolean tasksInstalled = LocalTaskList.tasksProviderAvailable(context);
        App.log.info("Package (un)installed; OpenTasks provider now available = " + tasksInstalled);

        // check all accounts and (de)activate OpenTasks if a CalDAV service is defined
        @Cleanup ServiceDB.OpenHelper dbHelper = new ServiceDB.OpenHelper(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        @Cleanup Cursor cursor = db.query(Services._TABLE, new String[] { Services.ACCOUNT_NAME },
                Services.SERVICE + "=?", new String[] { Services.SERVICE_CALDAV }, null, null, null);
        while (cursor.moveToNext()) {
            Account account = new Account(cursor.getString(0), context.getString(R.string.account_type));

            if (tasksInstalled) {
                if (ContentResolver.getIsSyncable(account, TaskProvider.ProviderName.OpenTasks.getAuthority()) <= 0) {
                    ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.getAuthority(), 1);
                    ContentResolver.setSyncAutomatically(account, TaskProvider.ProviderName.OpenTasks.getAuthority(), true);
                    ContentResolver.addPeriodicSync(account, TaskProvider.ProviderName.OpenTasks.getAuthority(), new Bundle(), Constants.DEFAULT_SYNC_INTERVAL);
                }
            } else
                ContentResolver.setIsSyncable(account, TaskProvider.ProviderName.OpenTasks.getAuthority(), 0);

        }
    }

}
