/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import at.bitfire.davdroid.App
import at.bitfire.davdroid.BuildConfig
import at.bitfire.davdroid.R
import at.bitfire.davdroid.log.Logger
import com.mikepenz.aboutlibraries.LibsBuilder
import kotlinx.android.synthetic.main.about_davdroid.*
import kotlinx.android.synthetic.main.activity_about.*
import org.apache.commons.io.IOUtils


class AboutActivity : AppCompatActivity() {

    companion object {
        fun fromHtml(html: String): Spanned {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            } else {
                Html.fromHtml(html)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_about)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewpager.adapter = TabsAdapter(supportFragmentManager)
        tabs.setupWithViewPager(viewpager, false)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.about_davdroid, menu)
        return true
    }

    fun showWebsite(item: MenuItem) {
        UiUtils.launchUri(this, App.homepageUrl(this))
    }


    private inner class TabsAdapter(
            fm: FragmentManager
    ) : FragmentPagerAdapter(fm) {

        override fun getCount() = 2

        override fun getPageTitle(position: Int): String =
                when (position) {
                    1 -> getString(R.string.about_libraries)
                    else -> getString(R.string.app_name)
                }

        override fun getItem(position: Int) =
                when (position) {
                    1 -> LibsBuilder()
                            .withAutoDetect(false)
                            .withFields(R.string::class.java.fields)
                            .withLicenseShown(true)
                            .supportFragment()
                    else -> DavdroidFragment()
                }!!
    }


    class DavdroidFragment : Fragment(), LoaderManager.LoaderCallbacks<Spanned> {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
                inflater.inflate(R.layout.about_davdroid, container, false)!!

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            app_version.text = getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

            infomaniak_copyright.isClickable = true
            infomaniak_copyright.movementMethod = LinkMovementMethod.getInstance()
            infomaniak_copyright.text = fromHtml(getString(R.string.about_infomaniak_copyright))

            if (true /* open-source version */) {
                warranty.text = fromHtml(getString(R.string.about_license_info_no_warranty))
                LoaderManager.getInstance(this).initLoader(0, null, this)
            }
        }

        override fun onCreateLoader(id: Int, args: Bundle?) =
                HtmlAssetLoader(requireActivity(), "gplv3.html")

        override fun onLoadFinished(loader: Loader<Spanned>, license: Spanned?) {
            Logger.log.info("LOAD FINISHED")
            license_text.text = license
        }

        override fun onLoaderReset(loader: Loader<Spanned>) {
        }

    }

    class HtmlAssetLoader(
            context: Context,
            val fileName: String
    ) : AsyncTaskLoader<Spanned>(context) {

        override fun onStartLoading() {
            forceLoad()
        }

        override fun loadInBackground(): Spanned =
                context.resources.assets.open(fileName).use {
                    fromHtml(IOUtils.toString(it, Charsets.UTF_8))
                }

    }

}
