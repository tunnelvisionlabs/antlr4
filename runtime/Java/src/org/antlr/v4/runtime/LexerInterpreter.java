/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime;

import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNType;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.util.Collection;

public class LexerInterpreter extends Lexer {
	protected final String grammarFileName;
	protected final ATN atn;

	@Deprecated
	protected final String[] tokenNames;
	protected final String[] ruleNames;
	protected final String[] channelNames;
	protected final String[] modeNames;
	@NotNull
	private final Vocabulary vocabulary;


	@Deprecated
	public LexerInterpreter(String grammarFileName, Collection<String> tokenNames, Collection<String> ruleNames, Collection<String> modeNames, ATN atn, CharStream input) {
		this(grammarFileName, VocabularyImpl.fromTokenNames(tokenNames.toArray(new String[tokenNames.size()])), ruleNames, null, modeNames, atn, input);
	}

	@Deprecated
	public LexerInterpreter(String grammarFileName, @NotNull Vocabulary vocabulary, Collection<String> ruleNames, Collection<String> modeNames, ATN atn, CharStream input) {
		this(grammarFileName, vocabulary, ruleNames, null, modeNames, atn, input);
	}

	public LexerInterpreter(String grammarFileName, @NotNull Vocabulary vocabulary, Collection<String> ruleNames, @Nullable Collection<String> channelNames, Collection<String> modeNames, ATN atn, CharStream input) {
		super(input);

		if (atn.grammarType != ATNType.LEXER) {
			throw new IllegalArgumentException("The ATN must be a lexer ATN.");
		}

		this.grammarFileName = grammarFileName;
		this.atn = atn;
		this.tokenNames = new String[atn.maxTokenType];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = vocabulary.getDisplayName(i);
		}

		this.ruleNames = ruleNames.toArray(new String[ruleNames.size()]);
		this.channelNames = channelNames != null ? channelNames.toArray(new String[channelNames.size()]) : null;
		this.modeNames = modeNames.toArray(new String[modeNames.size()]);
		this.vocabulary = vocabulary;
		this._interp = new LexerATNSimulator(this,atn);
	}

	@Override
	public ATN getATN() {
		return atn;
	}

	@Override
	public String getGrammarFileName() {
		return grammarFileName;
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override
	public String[] getRuleNames() {
		return ruleNames;
	}

	@Override
	public String[] getChannelNames() {
		return channelNames;
	}

	@Override
	public String[] getModeNames() {
		return modeNames;
	}

	@Override
	public Vocabulary getVocabulary() {
		if (vocabulary != null) {
			return vocabulary;
		}

		return super.getVocabulary();
	}
}
