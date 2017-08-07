/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime;

public interface UnicodeCharStream extends CharStream {

	/**
	 * Determines if the current stream supports Unicode code points.
	 *
	 * @return {@code true} if the current input stream supports Unicode code
	 * points; otherwise, {@code false} if the current input stream returns
	 * UTF-16 code units for code points above U+FFFF.
	 */
	boolean supportsUnicodeCodePoints();

}
