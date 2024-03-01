/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.misc;

import org.antlr.v4.runtime.annotations.NotNull;

/**
 *
 * @author Sam Harwell
 */
public class IntegerStack extends IntegerList {

	public IntegerStack() {
	}

	public IntegerStack(int capacity) {
		super(capacity);
	}

	public IntegerStack(@NotNull IntegerStack list) {
		super(list);
	}

	public final void push(int value) {
		add(value);
	}

	public final int pop() {
		return removeAt(size() - 1);
	}

	public final int peek() {
		return get(size() - 1);
	}

}
