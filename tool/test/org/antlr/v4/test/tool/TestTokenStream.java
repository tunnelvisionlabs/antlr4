/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD 3-clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.test.tool;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.BufferedTokenStream;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.xpath.XPathLexer;
import org.junit.Assert;
import org.junit.Test;

/**
 * This class contains tests for specific API functionality in {@link TokenStream} and derived types.
 */
public class TestTokenStream {

	/**
	 * This is a targeted regression test for antlr/antlr4#1584 ({@link BufferedTokenStream} cannot be reused after EOF).
	 */
	@Test
	public void testBufferedTokenStreamReuseAfterFill() {
		CharStream firstInput = new ANTLRInputStream("A");
		BufferedTokenStream tokenStream = new BufferedTokenStream(new XPathLexer(firstInput));
		tokenStream.fill();
		Assert.assertEquals(2, tokenStream.size());
		Assert.assertEquals(XPathLexer.TOKEN_REF, tokenStream.get(0).getType());
		Assert.assertEquals(Token.EOF, tokenStream.get(1).getType());

		CharStream secondInput = new ANTLRInputStream("A/");
		tokenStream.setTokenSource(new XPathLexer(secondInput));
		tokenStream.fill();
		Assert.assertEquals(3, tokenStream.size());
		Assert.assertEquals(XPathLexer.TOKEN_REF, tokenStream.get(0).getType());
		Assert.assertEquals(XPathLexer.ROOT, tokenStream.get(1).getType());
		Assert.assertEquals(Token.EOF, tokenStream.get(2).getType());
	}

}
