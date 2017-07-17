/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.codegen;

/**
 * Utility class to escape Unicode code points using various
 * languages' syntaxes.
 */
public enum UnicodeEscapes {
	;

	public static void appendJavaStyleEscapedCodePoint(int codePoint, StringBuilder sb) {
		if (Character.isSupplementaryCodePoint(codePoint)) {
			// char is not an 'integral' type, so we have to explicitly convert
			// to int before passing to the %X formatter or else it throws.
			sb.append(String.format("\\u%04X", (int)highSurrogate(codePoint)));
			sb.append(String.format("\\u%04X", (int)lowSurrogate(codePoint)));
		} else {
			sb.append(String.format("\\u%04X", codePoint));
		}
	}

	private static char highSurrogate(int codePoint) {
		return (char) ((codePoint >>> 10)
			+ (Character.MIN_HIGH_SURROGATE - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10)));
	}

	public static char lowSurrogate(int codePoint) {
		return (char) ((codePoint & 0x3ff) + Character.MIN_LOW_SURROGATE);
	}
}
