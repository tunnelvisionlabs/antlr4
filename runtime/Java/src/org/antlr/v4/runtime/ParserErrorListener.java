/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime;

import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.DecisionInfo;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.atn.SimulatorState;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.util.BitSet;

/** How to emit recognition errors for parsers.
 */
public interface ParserErrorListener extends ANTLRErrorListener<Token> {
	/**
	 * This method is called by the parser when a full-context prediction
	 * results in an ambiguity.
	 *
	 * <p>Each full-context prediction which does not result in a syntax error
	 * will call either {@link #reportContextSensitivity} or
	 * {@link #reportAmbiguity}.</p>
	 *
	 * <p>
	 * When {@code ambigAlts} is not null, it contains the set of potentially
	 * viable alternatives identified by the prediction algorithm. When
	 * {@code ambigAlts} is null, use
	 * {@link ATNConfigSet#getRepresentedAlternatives} to obtain the represented
	 * alternatives from the {@code configs} argument.</p>
	 *
	 * <p>When {@code exact} is {@code true}, <em>all</em> of the potentially
	 * viable alternatives are truly viable, i.e. this is reporting an exact
	 * ambiguity. When {@code exact} is {@code false}, <em>at least two</em> of
	 * the potentially viable alternatives are viable for the current input, but
	 * the prediction algorithm terminated as soon as it determined that at
	 * least the <em>minimum</em> potentially viable alternative is truly
	 * viable.</p>
	 *
	 * <p>When the {@link PredictionMode#LL_EXACT_AMBIG_DETECTION} prediction
	 * mode is used, the parser is required to identify exact ambiguities so
	 * {@code exact} will always be {@code true}.</p>
	 *
	 * @param recognizer the parser instance
	 * @param dfa the DFA for the current decision
	 * @param startIndex the input index where the decision started
	 * @param stopIndex the input input where the ambiguity was identified
	 * @param exact {@code true} if the ambiguity is exactly known, otherwise
	 * {@code false}. This is always {@code true} when
	 * {@link PredictionMode#LL_EXACT_AMBIG_DETECTION} is used.
	 * @param ambigAlts the potentially ambiguous alternatives, or {@code null}
	 * to indicate that the potentially ambiguous alternatives are the complete
	 * set of represented alternatives in {@code configs}
	 * @param configs the ATN configuration set where the ambiguity was
	 * identified
	 */
	void reportAmbiguity(@NotNull Parser recognizer,
						 @NotNull DFA dfa,
						 int startIndex,
						 int stopIndex,
						 boolean exact,
						 @Nullable BitSet ambigAlts,
						 @NotNull ATNConfigSet configs);

	/**
	 * This method is called when an SLL conflict occurs and the parser is about
	 * to use the full context information to make an LL decision.
	 *
	 * <p>If one or more configurations in {@code configs} contains a semantic
	 * predicate, the predicates are evaluated before this method is called. The
	 * subset of alternatives which are still viable after predicates are
	 * evaluated is reported in {@code conflictingAlts}.</p>
	 *
	 * @param recognizer the parser instance
	 * @param dfa the DFA for the current decision
	 * @param startIndex the input index where the decision started
	 * @param stopIndex the input index where the SLL conflict occurred
	 * @param conflictingAlts The specific conflicting alternatives. If this is
	 * {@code null}, the conflicting alternatives are all alternatives
	 * represented in {@code configs}.
	 * @param conflictState the simulator state when the SLL conflict was
	 * detected
	 */
	void reportAttemptingFullContext(@NotNull Parser recognizer,
									 @NotNull DFA dfa,
									 int startIndex,
									 int stopIndex,
									 @Nullable BitSet conflictingAlts,
									 @NotNull SimulatorState conflictState);

	/**
	 * This method is called by the parser when a full-context prediction has a
	 * unique result.
	 *
	 * <p>Each full-context prediction which does not result in a syntax error
	 * will call either {@link #reportContextSensitivity} or
	 * {@link #reportAmbiguity}.</p>
	 *
	 * <p>For prediction implementations that only evaluate full-context
	 * predictions when an SLL conflict is found (including the default
	 * {@link ParserATNSimulator} implementation), this method reports cases
	 * where SLL conflicts were resolved to unique full-context predictions,
	 * i.e. the decision was context-sensitive. This report does not necessarily
	 * indicate a problem, and it may appear even in completely unambiguous
	 * grammars.</p>
	 *
	 * <p>{@code configs} may have more than one represented alternative if the
	 * full-context prediction algorithm does not evaluate predicates before
	 * beginning the full-context prediction. In all cases, the final prediction
	 * is passed as the {@code prediction} argument.</p>
	 *
	 * <p>Note that the definition of "context sensitivity" in this method
	 * differs from the concept in {@link DecisionInfo#contextSensitivities}.
	 * This method reports all instances where an SLL conflict occurred but LL
	 * parsing produced a unique result, whether or not that unique result
	 * matches the minimum alternative in the SLL conflicting set.</p>
	 *
	 * @param recognizer the parser instance
	 * @param dfa the DFA for the current decision
	 * @param startIndex the input index where the decision started
	 * @param stopIndex the input index where the context sensitivity was
	 * finally determined
	 * @param prediction the unambiguous result of the full-context prediction
	 * @param acceptState the simulator state when the unambiguous prediction
	 * was determined
	 */
	void reportContextSensitivity(@NotNull Parser recognizer,
								  @NotNull DFA dfa,
								  int startIndex,
								  int stopIndex,
								  int prediction,
								  @NotNull SimulatorState acceptState);
}
