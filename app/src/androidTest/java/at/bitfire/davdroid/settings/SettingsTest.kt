/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.settings

import android.support.test.InstrumentationRegistry
import android.support.test.InstrumentationRegistry.getTargetContext
import at.bitfire.davdroid.App
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class SettingsTest {

    lateinit var settings: Settings.Stub

    @Before
    fun init() {
        InstrumentationRegistry.getContext().isRestricted
        settings = Settings.getInstance(getTargetContext())!!
    }

    @After
    fun shutdown() {
        settings.close()
    }


    @Test
    fun testHas() {
        assertFalse(settings.has("notExisting"))

        // provided by DefaultsProvider
        assertTrue(settings.has(App.OVERRIDE_PROXY))
    }

}