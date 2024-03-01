/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.misc;

import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Utils {
	public static String join(Iterable<?> iter, String separator) {
		return join(iter.iterator(), separator);
	}

	public static <T> String join(T[] array, String separator) {
		return join(Arrays.asList(array), separator);
	}

    // Seriously: why isn't this built in to java? ugh!
    public static <T> String join(Iterator<T> iter, String separator) {
        StringBuilder buf = new StringBuilder();
        while ( iter.hasNext() ) {
            buf.append(iter.next());
            if ( iter.hasNext() ) {
                buf.append(separator);
            }
        }
        return buf.toString();
    }

	public static boolean equals(Object x, Object y) {
		if (x == y) {
			return true;
		}

		if (x == null || y == null) {
			return false;
		}

		return x.equals(y);
	}

	public static int numNonnull(Object[] data) {
		int n = 0;
		if ( data == null ) return n;
		for (Object o : data) {
			if ( o!=null ) n++;
		}
		return n;
	}

	public  static <T> void removeAllElements(Collection<T> data, T value) {
		if ( data==null ) return;
		while ( data.contains(value) ) data.remove(value);
	}

	public static String escapeWhitespace(String s, boolean escapeSpaces) {
		StringBuilder buf = new StringBuilder();
		for (char c : s.toCharArray()) {
			if ( c==' ' && escapeSpaces ) buf.append('\u00B7');
			else if ( c=='\t' ) buf.append("\\t");
			else if ( c=='\n' ) buf.append("\\n");
			else if ( c=='\r' ) buf.append("\\r");
			else buf.append(c);
		}
		return buf.toString();
	}

	public static void writeFile(@NotNull File file, @NotNull byte[] content) throws IOException {
		FileOutputStream fos = new FileOutputStream(file);
		try {
			fos.write(content);
		}
		finally {
			fos.close();
		}
	}

	public static void writeFile(@NotNull String fileName, @NotNull String content) throws IOException {
		writeFile(fileName, content, null);
	}

	public static void writeFile(@NotNull String fileName, @NotNull String content, @Nullable String encoding) throws IOException {
		File f = new File(fileName);
		FileOutputStream fos = new FileOutputStream(f);
		OutputStreamWriter osw;
		if (encoding != null) {
			osw = new OutputStreamWriter(fos, encoding);
		}
		else {
			osw = new OutputStreamWriter(fos);
		}

		try {
			osw.write(content);
		}
		finally {
			osw.close();
		}
	}

	@NotNull
	public static char[] readFile(@NotNull String fileName) throws IOException {
		return readFile(fileName, null);
	}

	@NotNull
	public static char[] readFile(@NotNull String fileName, @Nullable String encoding) throws IOException {
		File f = new File(fileName);
		int size = (int)f.length();
		InputStreamReader isr;
		FileInputStream fis = new FileInputStream(fileName);
		if ( encoding!=null ) {
			isr = new InputStreamReader(fis, encoding);
		}
		else {
			isr = new InputStreamReader(fis);
		}
		char[] data = null;
		try {
			data = new char[size];
			int n = isr.read(data);
			if (n < data.length) {
				data = Arrays.copyOf(data, n);
			}
		}
		finally {
			isr.close();
		}
		return data;
	}

	public static <T> void removeAll(@NotNull List<T> list, @NotNull Predicate<? super T> predicate) {
		int j = 0;
		for (int i = 0; i < list.size(); i++) {
			T item = list.get(i);
			if (!predicate.eval(item)) {
				if (j != i) {
					list.set(j, item);
				}

				j++;
			}
		}

		if (j < list.size()) {
			list.subList(j, list.size()).clear();
		}
	}

	public static <T> void removeAll(@NotNull Iterable<T> iterable, @NotNull Predicate<? super T> predicate) {
		if (iterable instanceof List<?>) {
			removeAll((List<T>)iterable, predicate);
			return;
		}

		for (Iterator<T> iterator = iterable.iterator(); iterator.hasNext(); ) {
			T item = iterator.next();
			if (predicate.eval(item)) {
				iterator.remove();
			}
		}
	}

	/** Convert array of strings to string&rarr;index map. Useful for
	 *  converting rulenames to name&rarr;ruleindex map.
	 */
	public static Map<String, Integer> toMap(String[] keys) {
		Map<String, Integer> m = new HashMap<String, Integer>();
		for (int i=0; i<keys.length; i++) {
			m.put(keys[i], i);
		}
		return m;
	}

	/** Convert the list to a UTF-16 encoded char array. If all values are less
	 *  than the 0xFFFF 16-bit code point limit then this is just a char array
	 *  of 16-bit char as usual. For values in the supplementary range, encode
	 * them as two UTF-16 code units.
	 */
	public static char[] toCharArray(IntegerList data) {
		if ( data==null ) return null;

		// Optimize for the common case (all data values are
		// < 0xFFFF) to avoid an extra scan
		char[] resultArray = new char[data.size()];
		int resultIdx = 0;
		boolean calculatedPreciseResultSize = false;
		for (int i = 0; i < data.size(); i++) {
			int codePoint = data.get(i);
			// Calculate the precise result size if we encounter
			// a code point > 0xFFFF
			if (!calculatedPreciseResultSize &&
			    Character.isSupplementaryCodePoint(codePoint)) {
				resultArray = Arrays.copyOf(resultArray, charArraySize(data));
				calculatedPreciseResultSize = true;
			}

			// This will throw IllegalArgumentException if
			// the code point is not a valid Unicode code point
			int charsWritten = Character.toChars(codePoint, resultArray, resultIdx);
			resultIdx += charsWritten;
		}

		return resultArray;
	}

	private static int charArraySize(IntegerList data) {
		int result = 0;
		for (int i = 0; i < data.size(); i++) {
			result += Character.charCount(data.get(i));
		}

		return result;
	}

	/**
	 * @since 4.5
	 */
	@NotNull
	public static IntervalSet toSet(@NotNull BitSet bits) {
		IntervalSet s = new IntervalSet();
		int i = bits.nextSetBit(0);
		while ( i >= 0 ) {
			s.add(i);
			i = bits.nextSetBit(i+1);
		}
		return s;
	}
}
