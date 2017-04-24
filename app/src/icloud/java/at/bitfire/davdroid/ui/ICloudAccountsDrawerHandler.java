package at.bitfire.davdroid.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.view.MenuItem;

import at.bitfire.davdroid.App;
import at.bitfire.davdroid.R;

public class ICloudAccountsDrawerHandler implements IAccountsDrawerHandler {

    @Override
    public boolean onNavigationItemSelected(@NonNull Activity activity, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.nav_app_license:
                activity.startActivity(new Intent(activity, SubscriptionActivity.class));
                break;
            case R.id.nav_app_settings:
                activity.startActivity(new Intent(activity, AppSettingsActivity.class));
                break;
            case R.id.nav_about:
                activity.startActivity(new Intent(activity, AboutActivity.class));
                break;
            case R.id.nav_faq:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.navigation_drawer_faq_url))));
                break;
            case R.id.nav_website:
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(activity.getString(R.string.homepage_url))));
                break;
            default:
                return false;
        }

        return true;
    }

}
