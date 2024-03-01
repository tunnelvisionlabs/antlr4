/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa;

import org.antlr.v4.runtime.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 *
 * @author Sam Harwell
 */
public final class HashEdgeMap<T> extends AbstractEdgeMap<T> {
	private static final int DEFAULT_MAX_SIZE = 2;

	private final AtomicIntegerArray keys;
	private final T[] values;

	public HashEdgeMap(int minIndex, int maxIndex) {
		this(minIndex, maxIndex, DEFAULT_MAX_SIZE);
	}

	@SuppressWarnings("unchecked")
	public HashEdgeMap(int minIndex, int maxIndex, int maxSparseSize) {
		super(minIndex, maxIndex);
		this.keys = new AtomicIntegerArray(maxSparseSize);
		this.values = (T[])new Object[maxSparseSize];
	}

	@SuppressWarnings("unchecked")
	private HashEdgeMap(@NotNull HashEdgeMap<T> map, int maxSparseSize) {
		super(map.minIndex, map.maxIndex);
		synchronized (map) {
			if (maxSparseSize < map.values.length) {
				throw new IllegalArgumentException();
			}

			keys = new AtomicIntegerArray(maxSparseSize);
			values = (T[])new Object[maxSparseSize];
			for (int i = 0; i < map.values.length; i++) {
				int key = map.keys.get(i);
				T value = map.values[i];
				if (value != null) {
					int bucket = bucket(key);
					keys.set(bucket, key);
					values[bucket] = value;
				}
			}
		}
	}

	private static int bucket(int length, int key) {
		// Note: this returns a valid array index even if key is outside the
		// allowed range or the minIndex is negative.
		return key & (length - 1);
	}

	private int bucket(int key) {
		// Note: this returns a valid array index even if key is outside the
		// allowed range or the minIndex is negative.
		return key & (values.length - 1);
	}

	@NotNull
	/*package*/ AtomicIntegerArray getKeys() {
		return keys;
	}

	@NotNull
	/*package*/ T[] getValues() {
		return values;
	}

	@Override
	public int size() {
		int size = 0;
		for (T edge : values) {
			if (edge != null) {
				size++;
			}
		}

		return size;
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean containsKey(int key) {
		return get(key) != null;
	}

	@Override
	public T get(int key) {
		int bucket = bucket(key);

		// Read the value first
		T value = values[bucket];
		if (value == null || keys.get(bucket) != key) {
			return null;
		}

		return value;
	}

	@Override
	public AbstractEdgeMap<T> put(int key, T value) {
		if (key < minIndex || key > maxIndex) {
			return this;
		}

		if (value == null) {
			return remove(key);
		}

		synchronized (this) {
			int bucket = bucket(key);
			int currentKey = keys.get(bucket);
			if (currentKey == key) {
				values[bucket] = value;
				return this;
			}

			T currentValue = values[bucket];
			if (currentValue == null) {
				// Write the key first
				keys.set(bucket, key);
				values[bucket] = value;
				return this;
			}

			// Resize on collision
			int newSize = values.length;
			while (true) {
				newSize *= 2;
				if (newSize >= (maxIndex - minIndex + 1) / 2) {
					ArrayEdgeMap<T> arrayMap = new ArrayEdgeMap<T>(minIndex, maxIndex);
					arrayMap = arrayMap.putAll(this);
					arrayMap.put(key, value);
					return arrayMap;
				}

				// Check for another collision
				if (bucket(newSize, currentKey) != bucket(newSize, key)) {
					break;
				}
			}

			HashEdgeMap<T> resized = new HashEdgeMap<T>(this, newSize);
			resized.put(key, value);
			return resized;
		}
	}

	@Override
	public HashEdgeMap<T> remove(int key) {
		if (get(key) == null) {
			return this;
		}

		HashEdgeMap<T> result = new HashEdgeMap<T>(this, values.length);
		int bucket = result.bucket(key);
		result.keys.set(bucket, 0);
		result.values[bucket] = null;
		return result;
	}

	@Override
	public AbstractEdgeMap<T> clear() {
		if (isEmpty()) {
			return this;
		}

		return new EmptyEdgeMap<T>(minIndex, maxIndex);
	}

	@Override
	public Map<Integer, T> toMap() {
		if (isEmpty()) {
			return Collections.emptyMap();
		}

		synchronized (this) {
			Map<Integer, T> result = new TreeMap<Integer, T>();
			for (int i = 0; i < values.length; i++) {
				int key = keys.get(i);
				T value = values[i];
				if (value != null) {
					result.put(key, value);
				}
			}

			return result;
		}
	}

	@Override
	public Set<Map.Entry<Integer, T>> entrySet() {
		return toMap().entrySet();
	}
}
