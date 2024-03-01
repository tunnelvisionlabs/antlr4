/* Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa;

import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 *
 * @author Sam Harwell
 */
public interface EdgeMap<T> {

	int size();

	boolean isEmpty();

	boolean containsKey(int key);

	@Nullable
	T get(int key);

	@NotNull
	EdgeMap<T> put(int key, @Nullable T value);

	@NotNull
	EdgeMap<T> remove(int key);

	@NotNull
	EdgeMap<T> putAll(@NotNull EdgeMap<? extends T> m);

	@NotNull
	EdgeMap<T> clear();

	@NotNull
	Map<Integer, T> toMap();

	@NotNull
	Set<Map.Entry<Integer, T>> entrySet();

}
