/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.ListFragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.AsyncTaskLoader
import androidx.loader.content.Loader
import at.bitfire.davdroid.R
import kotlinx.android.synthetic.main.account_list_item.view.*

class AccountListFragment: ListFragment(), LoaderManager.LoaderCallbacks<Array<Account>> {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        listAdapter = AccountListAdapter(requireActivity())

        return inflater.inflate(R.layout.account_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LoaderManager.getInstance(this).initLoader(0, arguments, this)

        listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val account = listAdapter?.getItem(position) as Account
            val intent = Intent(activity, AccountActivity::class.java)
            intent.putExtra(AccountActivity.EXTRA_ACCOUNT, account)
            startActivity(intent)
        }
    }


    // loader

    override fun onCreateLoader(id: Int, args: Bundle?) =
            AccountLoader(requireActivity())

    override fun onLoadFinished(loader: Loader<Array<Account>>, accounts: Array<Account>) {
        val adapter = listAdapter as AccountListAdapter
        adapter.clear()
        adapter.addAll(*accounts)
    }

    override fun onLoaderReset(loader: Loader<Array<Account>>) {
        (listAdapter as AccountListAdapter).clear()
    }

    class AccountLoader(
            context: Context
    ): AsyncTaskLoader<Array<Account>>(context) {

        private val accountManager = AccountManager.get(context)!!
        private var listener: OnAccountsUpdateListener? = null

        override fun onStartLoading() {
            if (listener == null) {
                listener = OnAccountsUpdateListener { onContentChanged() }
                accountManager.addOnAccountsUpdatedListener(listener, null, false)
            }

            forceLoad()
        }

        override fun onReset() {
            listener?.let {
                try {
                    accountManager.removeOnAccountsUpdatedListener(it)
                } catch(ignored: IllegalArgumentException) {}
                listener = null
            }
        }

        override fun loadInBackground(): Array<Account> =
            AccountManager.get(context).getAccountsByType(context.getString(R.string.account_type))

    }


    // list adapter

    class AccountListAdapter(
            context: Context
    ): ArrayAdapter<Account>(context, R.layout.account_list_item) {

        override fun getView(position: Int, _v: View?, parent: ViewGroup?): View {
            val account = getItem(position)!!

            val v = _v ?: LayoutInflater.from(context).inflate(R.layout.account_list_item, parent, false)
            v.account_name.text = account.name

            return v
        }
    }

}
