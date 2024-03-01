/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.tree.pattern;

import org.antlr.v4.runtime.annotations.NotNull;

/**
 * Represents a span of raw text (concrete syntax) between tags in a tree
 * pattern string.
 */
class TextChunk extends Chunk {
	/**
	 * This is the backing field for {@link #getText}.
	 */
	@NotNull
	private final String text;

	/**
	 * Constructs a new instance of {@link TextChunk} with the specified text.
	 *
	 * @param text The text of this chunk.
	 * @exception IllegalArgumentException if {@code text} is {@code null}.
	 */
	public TextChunk(@NotNull String text) {
		if (text == null) {
			throw new IllegalArgumentException("text cannot be null");
		}

		this.text = text;
	}

	/**
	 * Gets the raw text of this chunk.
	 *
	 * @return The text of the chunk.
	 */
	@NotNull
	public final String getText() {
		return text;
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>The implementation for {@link TextChunk} returns the result of
	 * {@link #getText()} in single quotes.</p>
	 */
	@Override
	public String toString() {
		return "'"+text+"'";
	}
}
