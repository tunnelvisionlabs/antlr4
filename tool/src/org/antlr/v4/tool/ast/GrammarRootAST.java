/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.tool.ast;

import org.antlr.runtime.Token;
import org.antlr.runtime.TokenStream;
import org.antlr.runtime.tree.Tree;
import org.antlr.v4.runtime.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class GrammarRootAST extends GrammarASTWithOptions {
	public static final Map<String, String> defaultOptions = new HashMap<String, String>();
	static {
		defaultOptions.put("language","Java");
		defaultOptions.put("abstract","false");
	}

    public int grammarType; // LEXER, PARSER, GRAMMAR (combined)
	public boolean hasErrors;
	/** Track stream used to create this tree */
	@NotNull
	public final TokenStream tokenStream;
	public Map<String, String> cmdLineOptions; // -DsuperClass=T on command line
	public String fileName;

	public GrammarRootAST(GrammarRootAST node) {
		super(node);
		this.grammarType = node.grammarType;
		this.hasErrors = node.hasErrors;
		this.tokenStream = node.tokenStream;
	}

	public GrammarRootAST(Token t, TokenStream tokenStream) {
		super(t);
		if (tokenStream == null) {
			throw new NullPointerException("tokenStream");
		}

		this.tokenStream = tokenStream;
	}

	public GrammarRootAST(int type, Token t, TokenStream tokenStream) {
		super(type, t);
		if (tokenStream == null) {
			throw new NullPointerException("tokenStream");
		}

		this.tokenStream = tokenStream;
	}

	public GrammarRootAST(int type, Token t, String text, TokenStream tokenStream) {
		super(type,t,text);
		if (tokenStream == null) {
			throw new NullPointerException("tokenStream");
		}

		this.tokenStream = tokenStream;
    }

	public String getGrammarName() {
		Tree t = getChild(0);
		if ( t!=null ) return t.getText();
		return null;
	}

	@Override
	public String getOptionString(String key) {
		if ( cmdLineOptions!=null && cmdLineOptions.containsKey(key) ) {
			return cmdLineOptions.get(key);
		}
		String value = super.getOptionString(key);
		if ( value==null ) {
			value = defaultOptions.get(key);
		}
		return value;
	}

	@Override
	public Object visit(GrammarASTVisitor v) { return v.visit(this); }

	@Override
	public GrammarRootAST dupNode() { return new GrammarRootAST(this); }
}
