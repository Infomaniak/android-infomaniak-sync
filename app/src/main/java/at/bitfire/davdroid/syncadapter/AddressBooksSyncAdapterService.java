/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid.syncadapter;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import at.bitfire.davdroid.AccountSettings;
import at.bitfire.davdroid.App;
import at.bitfire.davdroid.InvalidAccountException;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.model.CollectionInfo;
import at.bitfire.davdroid.model.ServiceDB;
import at.bitfire.davdroid.model.ServiceDB.Collections;
import at.bitfire.davdroid.resource.LocalAddressBook;
import at.bitfire.davdroid.resource.LocalCalendar;
import at.bitfire.vcard4android.ContactsStorageException;
import lombok.Cleanup;

public class AddressBooksSyncAdapterService extends SyncAdapterService {

    @Override
    protected AbstractThreadedSyncAdapter syncAdapter() {
        return new ContactsSyncAdapter(this);
    }


	private static class ContactsSyncAdapter extends SyncAdapter {

        public ContactsSyncAdapter(Context context) {
            super(context);
        }

        @Override
        public void sync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(getContext());
            try {
                AccountSettings settings = new AccountSettings(getContext(), account);
                if (!extras.containsKey(ContentResolver.SYNC_EXTRAS_MANUAL) && !checkSyncConditions(settings))
                    return;

                updateLocalAddressBooks(provider, account);

                AccountManager accountManager = AccountManager.get(getContext());
                for (Account addressBookAccount : accountManager.getAccountsByType(getContext().getString(R.string.account_type_address_book))) {
                    App.log.log(Level.INFO, "Running sync for address book", addressBookAccount);
                    Bundle syncExtras = new Bundle(extras);
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
                    syncExtras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_BACKOFF, true);
                    ContentResolver.requestSync(addressBookAccount, ContactsContract.AUTHORITY, syncExtras);
                }

            } catch(InvalidAccountException e) {
                App.log.log(Level.SEVERE, "Couldn't get account settings", e);
            } catch(ContactsStorageException e) {
                App.log.log(Level.SEVERE, "Couldn't prepare local address books", e);
            } finally {
                dbHelper.close();
            }

            App.log.info("Address book sync complete");
        }

        private void updateLocalAddressBooks(ContentProviderClient provider, Account account) throws ContactsStorageException {
            final Context context = getContext();
            @Cleanup SQLiteOpenHelper dbHelper = new ServiceDB.OpenHelper(context);

            // enumerate remote and local address books
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Long service = getService(db, account);
            Map<String, CollectionInfo> remote = remoteAddressBooks(db, service);

            LocalAddressBook[] local = LocalAddressBook.find(context, provider);

            // delete obsolete local address books
            for (LocalAddressBook addressBook : local) {
                String url = addressBook.getURL();
                if (!remote.containsKey(url)) {
                    App.log.fine("Deleting obsolete local address book " + url);
                    addressBook.delete();
                } else {
                    // we already have a local address book for this remote collection, don't take into consideration anymore
                    addressBook.update(remote.get(url));
                    remote.remove(url);
                }
            }

            // create new local address books
            for (String url : remote.keySet()) {
                CollectionInfo info = remote.get(url);
                App.log.info("Adding local address book " + info);
                LocalAddressBook.create(context, provider, account, info);
            }
        }

        @Nullable
        private Long getService(@NonNull SQLiteDatabase db, @NonNull Account account) {
            @Cleanup Cursor c = db.query(ServiceDB.Services._TABLE, new String[] { ServiceDB.Services.ID },
                    ServiceDB.Services.ACCOUNT_NAME + "=? AND " + ServiceDB.Services.SERVICE + "=?", new String[] { account.name, ServiceDB.Services.SERVICE_CARDDAV }, null, null, null);
            if (c.moveToNext())
                return c.getLong(0);
            else
                return null;
        }

        @NonNull
        private Map<String, CollectionInfo> remoteAddressBooks(@NonNull SQLiteDatabase db, Long service) {
            Map<String, CollectionInfo> collections = new LinkedHashMap<>();
            if (service != null) {
                @Cleanup Cursor c = db.query(Collections._TABLE, null,
                        Collections.SERVICE_ID + "=? AND " + Collections.SYNC, new String[] { String.valueOf(service) }, null, null, null);
                while (c.moveToNext()) {
                    ContentValues values = new ContentValues();
                    DatabaseUtils.cursorRowToContentValues(c, values);
                    CollectionInfo info = CollectionInfo.fromDB(values);
                    collections.put(info.url, info);
                }
            }
            return collections;
        }

    }

}
