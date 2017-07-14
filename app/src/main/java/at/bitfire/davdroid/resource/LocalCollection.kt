/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.resource

import at.bitfire.ical4android.CalendarStorageException
import at.bitfire.vcard4android.ContactsStorageException
import java.io.FileNotFoundException

interface LocalCollection<out T: LocalResource> {

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun getDeleted(): List<T>

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun getWithoutFileName(): List<T>

    @Throws(CalendarStorageException::class, ContactsStorageException::class, FileNotFoundException::class)
    fun getDirty(): List<T>

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun getAll(): List<T>

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun getCTag(): String?

    @Throws(CalendarStorageException::class, ContactsStorageException::class)
    fun setCTag(cTag: String?)

}
