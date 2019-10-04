/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import at.bitfire.dav4jvm.DavResource
import at.bitfire.davdroid.HttpClient
import at.bitfire.davdroid.R
import at.bitfire.davdroid.model.CollectionInfo
import at.bitfire.davdroid.model.ServiceDB
import at.bitfire.davdroid.settings.AccountSettings

class DeleteCollectionFragment: DialogFragment(), LoaderManager.LoaderCallbacks<Exception> {

    companion object {
        const val ARG_ACCOUNT = "account"
        const val ARG_COLLECTION_INFO = "collectionInfo"
    }

    private lateinit var account: Account
    private lateinit var collectionInfo: CollectionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        account = arguments!!.getParcelable(ARG_ACCOUNT)!!
        collectionInfo = arguments!!.getParcelable(ARG_COLLECTION_INFO)!!

        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val progress = ProgressDialog(context)
        progress.setTitle(R.string.delete_collection_deleting_collection)
        progress.setMessage(getString(R.string.please_wait))
        progress.isIndeterminate = true
        progress.setCanceledOnTouchOutside(false)
        isCancelable = false
        return progress
    }


    override fun onCreateLoader(id: Int, args: Bundle?) =
            DeleteCollectionLoader(activity!!, account, collectionInfo)

    override fun onLoadFinished(loader: Loader<Exception>, exception: Exception?) {
        dismiss()

        if (exception != null)
            requireFragmentManager().beginTransaction()
                    .add(ExceptionInfoFragment.newInstance(exception, account), null)
                    .commit()
        else
            (activity as? AccountActivity)?.reload()
    }

    override fun onLoaderReset(loader: Loader<Exception>) {}


    class DeleteCollectionLoader(
            context: Context,
            val account: Account,
            val collectionInfo: CollectionInfo
    ): AsyncTaskLoader<Exception>(context) {

        override fun onStartLoading() = forceLoad()

        override fun loadInBackground(): Exception? {
            HttpClient.Builder(context, AccountSettings(context, account))
                    .setForeground(true)
                    .build().use { httpClient ->
                try {
                    val collection = DavResource(httpClient.okHttpClient, collectionInfo.url)

                    // delete collection from server
                    collection.delete(null) {}

                    // delete collection locally
                    ServiceDB.OpenHelper(context).use { dbHelper ->
                        val db = dbHelper.writableDatabase
                        db.delete(ServiceDB.Collections._TABLE, "${ServiceDB.Collections.ID}=?", arrayOf(collectionInfo.id.toString()))
                    }
                } catch(e: Exception) {
                    return e
                }
            }
            return null
        }
    }

}
