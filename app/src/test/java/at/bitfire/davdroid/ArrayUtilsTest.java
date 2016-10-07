/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package at.bitfire.davdroid;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;


public class ArrayUtilsTest {

    @Test
	public void testPartition() {
		// n == 0
		assertTrue(Arrays.deepEquals(
                new Long[0][0],
                ArrayUtils.partition(new Long[] {}, 5)));
				
		// n < max
		assertTrue(Arrays.deepEquals(
                new Long[][] { { 1l, 2l } },
                ArrayUtils.partition(new Long[] { 1l, 2l }, 5)));
		
		// n == max
		assertTrue(Arrays.deepEquals(
                new Long[][] { { 1l, 2l }, { 3l, 4l } },
                ArrayUtils.partition(new Long[] { 1l, 2l, 3l, 4l }, 2)));
		
		// n > max
		assertTrue(Arrays.deepEquals(
                new Long[][] { { 1l, 2l, 3l, 4l, 5l }, { 6l, 7l, 8l, 9l, 10l }, { 11l } },
                ArrayUtils.partition(new Long[] { 1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l, 11l }, 5)));
	}
	
}
