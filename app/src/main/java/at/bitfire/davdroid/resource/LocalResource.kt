/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import android.net.Uri

interface LocalResource<in TData: Any> {

    companion object {
        /**
         * Resource is present on remote server. This flag is used to identify resources
         * which are not present on the remote server anymore and can be deleted at the end
         * of the synchronization.
         */
        const val FLAG_REMOTELY_PRESENT = 1
    }


    /**
     * Unique ID which identifies the resource in the local storage. May be null if the
     * resource has not been saved yet.
     */
    val id: Long?

    val fileName: String?
    var eTag: String?
    val flags: Int

    fun assignNameAndUID()
    fun clearDirty(eTag: String?)
    fun updateFlags(flags: Int)

    /**
     * Adds the data object to the content provider and ensures that the dirty flag is clear.
     * @return content URI of the created row (e.g. event URI)
     */
    fun add(): Uri

    /**
     * Updates the data object in the content provider and ensures that the dirty flag is clear.
     * @return content URI of the updated row (e.g. event URI)
     */
    fun update(data: TData): Uri

    /**
     * Deletes the data object from the content provider.
     * @return number of affected rows
     */
    fun delete(): Int

}