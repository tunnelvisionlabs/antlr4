/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime;

import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.util.Collection;

/**
 * This implementation of {@link ANTLRErrorListener} dispatches all calls to a
 * collection of delegate listeners. This reduces the effort required to support multiple
 * listeners.
 *
 * @author Sam Harwell
 */
public class ProxyErrorListener<Symbol> implements ANTLRErrorListener<Symbol> {
	private final Collection<? extends ANTLRErrorListener<? super Symbol>> delegates;

	public ProxyErrorListener(Collection<? extends ANTLRErrorListener<? super Symbol>> delegates) {
		if (delegates == null) {
			throw new NullPointerException("delegates");
		}

		this.delegates = delegates;
	}

	protected Collection<? extends ANTLRErrorListener<? super Symbol>> getDelegates() {
		return delegates;
	}

	@Override
	public <T extends Symbol> void syntaxError(@NotNull Recognizer<T, ?> recognizer,
											   @Nullable T offendingSymbol,
											   int line,
											   int charPositionInLine,
											   @NotNull String msg,
											   @Nullable RecognitionException e)
	{
		for (ANTLRErrorListener<? super Symbol> listener : delegates) {
			listener.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e);
		}
	}
}
