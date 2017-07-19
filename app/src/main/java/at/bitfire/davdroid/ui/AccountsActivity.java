/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import java.util.ServiceLoader;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.BuildConfig;
import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.setup.LoginActivity;

import static android.content.ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS;

public class AccountsActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, SyncStatusObserver {

    private static final ServiceLoader<IAccountsDrawerHandler> serviceLoader = ServiceLoader.load(IAccountsDrawerHandler.class);
    private static final IAccountsDrawerHandler accountsDrawerHandler = serviceLoader.iterator().next();

    private Snackbar syncStatusSnackbar;
    private Object syncStatusObserver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_accounts);

        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(AccountsActivity.this, LoginActivity.class));
            }
        });

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView)findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setItemIconTintList(null);

        if (savedInstanceState == null && !getPackageName().equals(getCallingPackage())) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            for (StartupDialogFragment fragment : StartupDialogFragment.getStartupDialogs(this))
                ft.add(fragment, null);
            ft.commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.FLAVOR == App.FLAVOR_SOLDUPE) {
            AccountManager accountManager = AccountManager.get(this);
            Account[] accounts = accountManager.getAccountsByType(getString(R.string.account_type));

            FloatingActionButton fab = (FloatingActionButton)findViewById(R.id.fab);
            fab.setVisibility(accounts.length > 0 ? View.GONE : View.VISIBLE);
        }

        onStatusChanged(SYNC_OBSERVER_TYPE_SETTINGS);
        syncStatusObserver = ContentResolver.addStatusChangeListener(SYNC_OBSERVER_TYPE_SETTINGS, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (syncStatusObserver != null) {
            ContentResolver.removeStatusChangeListener(syncStatusObserver);
            syncStatusObserver = null;
        }
    }

    @Override
    public void onStatusChanged(int which) {
        if (syncStatusSnackbar != null) {
            syncStatusSnackbar.dismiss();
            syncStatusSnackbar = null;
        }

        if (!ContentResolver.getMasterSyncAutomatically()) {
            syncStatusSnackbar = Snackbar.make(findViewById(R.id.coordinator), R.string.accounts_global_sync_disabled, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.accounts_global_sync_enable, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ContentResolver.setMasterSyncAutomatically(true);
                        }
                    });
            syncStatusSnackbar.show();
        }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START))
            drawer.closeDrawer(GravityCompat.START);
        else
            super.onBackPressed();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        boolean processed = accountsDrawerHandler.onNavigationItemSelected(this, item);

        DrawerLayout drawer = (DrawerLayout)findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);

        return processed;
    }


}
