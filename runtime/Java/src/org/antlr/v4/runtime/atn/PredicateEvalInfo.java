/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.annotations.NotNull;

/**
 * This class represents profiling event information for semantic predicate
 * evaluations which occur during prediction.
 *
 * @see ParserATNSimulator#evalSemanticContext
 *
 * @since 4.3
 */
public class PredicateEvalInfo extends DecisionEventInfo {
	/**
	 * The semantic context which was evaluated.
	 */
	public final SemanticContext semctx;
	/**
	 * The alternative number for the decision which is guarded by the semantic
	 * context {@link #semctx}. Note that other ATN
	 * configurations may predict the same alternative which are guarded by
	 * other semantic contexts and/or {@link SemanticContext#NONE}.
	 */
	public final int predictedAlt;
	/**
	 * The result of evaluating the semantic context {@link #semctx}.
	 */
	public final boolean evalResult;

	/**
	 * Constructs a new instance of the {@link PredicateEvalInfo} class with the
	 * specified detailed predicate evaluation information.
	 *
	 * @param state The simulator state
	 * @param decision The decision number
	 * @param input The input token stream
	 * @param startIndex The start index for the current prediction
	 * @param stopIndex The index at which the predicate evaluation was
	 * triggered. Note that the input stream may be reset to other positions for
	 * the actual evaluation of individual predicates.
	 * @param semctx The semantic context which was evaluated
	 * @param evalResult The results of evaluating the semantic context
	 * @param predictedAlt The alternative number for the decision which is
	 * guarded by the semantic context {@code semctx}. See {@link #predictedAlt}
	 * for more information.
	 *
	 * @see ParserATNSimulator#evalSemanticContext(SemanticContext, ParserRuleContext, int)
	 * @see SemanticContext#eval(Recognizer, RuleContext)
	 */
	public PredicateEvalInfo(@NotNull SimulatorState state, int decision,
							 @NotNull TokenStream input, int startIndex, int stopIndex,
							 @NotNull SemanticContext semctx,
							 boolean evalResult,
							 int predictedAlt)
	{
		super(decision, state, input, startIndex, stopIndex, state.useContext);
		this.semctx = semctx;
		this.evalResult = evalResult;
		this.predictedAlt = predictedAlt;
	}
}
