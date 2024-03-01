/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.dfa.DFAState;
import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.misc.Tuple2;

import java.util.BitSet;

/**
 * @since 4.3
 */
public class ProfilingATNSimulator extends ParserATNSimulator {
	protected final DecisionInfo[] decisions;
	protected int numDecisions;

	protected TokenStream _input;
	protected int _startIndex;
	protected int _sllStopIndex;
	protected int _llStopIndex;

	protected int currentDecision;
	protected SimulatorState currentState;

 	/** At the point of LL failover, we record how SLL would resolve the conflict so that
	 *  we can determine whether or not a decision / input pair is context-sensitive.
	 *  If LL gives a different result than SLL's predicted alternative, we have a
	 *  context sensitivity for sure. The converse is not necessarily true, however.
	 *  It's possible that after conflict resolution chooses minimum alternatives,
	 *  SLL could get the same answer as LL. Regardless of whether or not the result indicates
	 *  an ambiguity, it is not treated as a context sensitivity because LL prediction
	 *  was not required in order to produce a correct prediction for this decision and input sequence.
	 *  It may in fact still be a context sensitivity but we don't know by looking at the
	 *  minimum alternatives for the current input.
 	 */
	protected int conflictingAltResolvedBySLL;

	public ProfilingATNSimulator(Parser parser) {
		super(parser, parser.getInterpreter().atn);
		optimize_ll1 = false;
		reportAmbiguities = true;
		numDecisions = atn.decisionToState.size();
		decisions = new DecisionInfo[numDecisions];
		for (int i=0; i<numDecisions; i++) {
			decisions[i] = new DecisionInfo(i);
		}
	}

	@Override
	public int adaptivePredict(TokenStream input, int decision, ParserRuleContext outerContext) {
		try {
			this._input = input;
			this._startIndex = input.index();
			// it's possible for SLL to reach a conflict state without consuming any input
			this._sllStopIndex = _startIndex - 1;
			this._llStopIndex = -1;
			this.currentDecision = decision;
			this.currentState = null;
			this.conflictingAltResolvedBySLL = ATN.INVALID_ALT_NUMBER;
			long start = System.nanoTime(); // expensive but useful info
			int alt = super.adaptivePredict(input, decision, outerContext);
			long stop = System.nanoTime();
			decisions[decision].timeInPrediction += (stop-start);
			decisions[decision].invocations++;

			int SLL_k = _sllStopIndex - _startIndex + 1;
			decisions[decision].SLL_TotalLook += SLL_k;
			decisions[decision].SLL_MinLook = decisions[decision].SLL_MinLook==0 ? SLL_k : Math.min(decisions[decision].SLL_MinLook, SLL_k);
			if ( SLL_k > decisions[decision].SLL_MaxLook ) {
				decisions[decision].SLL_MaxLook = SLL_k;
				decisions[decision].SLL_MaxLookEvent =
						new LookaheadEventInfo(decision, null, alt, input, _startIndex, _sllStopIndex, false);
			}

			if (_llStopIndex >= 0) {
				int LL_k = _llStopIndex - _startIndex + 1;
				decisions[decision].LL_TotalLook += LL_k;
				decisions[decision].LL_MinLook = decisions[decision].LL_MinLook==0 ? LL_k : Math.min(decisions[decision].LL_MinLook, LL_k);
				if ( LL_k > decisions[decision].LL_MaxLook ) {
					decisions[decision].LL_MaxLook = LL_k;
					decisions[decision].LL_MaxLookEvent =
							new LookaheadEventInfo(decision, null, alt, input, _startIndex, _llStopIndex, true);
				}
			}

			return alt;
		}
		finally {
			this._input = null;
			this.currentDecision = -1;
		}
	}

	@Override
	protected SimulatorState getStartState(DFA dfa, TokenStream input, ParserRuleContext outerContext, boolean useContext) {
		SimulatorState state = super.getStartState(dfa, input, outerContext, useContext);
		currentState = state;
		return state;
	}

	@Override
	protected SimulatorState computeStartState(DFA dfa, ParserRuleContext globalContext, boolean useContext) {
		SimulatorState state = super.computeStartState(dfa, globalContext, useContext);
		currentState = state;
		return state;
	}

	@Override
	protected SimulatorState computeReachSet(DFA dfa, SimulatorState previous, int t, PredictionContextCache contextCache) {
		SimulatorState reachState = super.computeReachSet(dfa, previous, t, contextCache);
		if (reachState == null) {
			// no reach on current lookahead symbol. ERROR.
			decisions[currentDecision].errors.add(
				new ErrorInfo(currentDecision, previous, _input, _startIndex, _input.index())
			);
		}

		currentState = reachState;
		return reachState;
	}

	@Override
	protected DFAState getExistingTargetState(DFAState previousD, int t) {
		// this method is called after each time the input position advances
		if (currentState.useContext) {
			_llStopIndex = _input.index();
		}
		else {
			_sllStopIndex = _input.index();
		}

		DFAState existingTargetState = super.getExistingTargetState(previousD, t);
		if ( existingTargetState!=null ) {
			// this method is directly called by execDFA; must construct a SimulatorState
			// to represent the current state for this case
			currentState = new SimulatorState(currentState.outerContext, existingTargetState, currentState.useContext, currentState.remainingOuterContext);

			if (currentState.useContext) {
				decisions[currentDecision].LL_DFATransitions++;
			}
			else {
				decisions[currentDecision].SLL_DFATransitions++; // count only if we transition over a DFA state
			}

			if ( existingTargetState==ERROR ) {
				SimulatorState state = new SimulatorState(currentState.outerContext, previousD, currentState.useContext, currentState.remainingOuterContext);
				decisions[currentDecision].errors.add(
					new ErrorInfo(currentDecision, state, _input, _startIndex, _input.index())
				);
			}
		}

		return existingTargetState;
	}

	@Override
	protected Tuple2<DFAState, ParserRuleContext> computeTargetState(DFA dfa, DFAState s, ParserRuleContext remainingGlobalContext, int t, boolean useContext, PredictionContextCache contextCache) {
		Tuple2<DFAState, ParserRuleContext> targetState = super.computeTargetState(dfa, s, remainingGlobalContext, t, useContext, contextCache);

		if (useContext) {
			decisions[currentDecision].LL_ATNTransitions++;
		}
		else {
			decisions[currentDecision].SLL_ATNTransitions++;
		}

		return targetState;
	}

	@Override
	protected boolean evalSemanticContext(SemanticContext pred, ParserRuleContext parserCallStack, int alt) {
		boolean result = super.evalSemanticContext(pred, parserCallStack, alt);
		if (!(pred instanceof SemanticContext.PrecedencePredicate)) {
			boolean fullContext = _llStopIndex >= 0;
			int stopIndex = fullContext ? _llStopIndex : _sllStopIndex;
			decisions[currentDecision].predicateEvals.add(
				new PredicateEvalInfo(currentState, currentDecision, _input, _startIndex, stopIndex, pred, result, alt)
			);
		}

		return result;
	}

	@Override
	protected void reportContextSensitivity(DFA dfa, int prediction, SimulatorState acceptState, int startIndex, int stopIndex) {
		if ( prediction != conflictingAltResolvedBySLL ) {
			decisions[currentDecision].contextSensitivities.add(
				new ContextSensitivityInfo(currentDecision, acceptState, _input, startIndex, stopIndex)
			);
		}
		super.reportContextSensitivity(dfa, prediction, acceptState, startIndex, stopIndex);
	}

	@Override
	protected void reportAttemptingFullContext(DFA dfa, BitSet conflictingAlts, SimulatorState conflictState, int startIndex, int stopIndex) {
		if ( conflictingAlts!=null ) {
			conflictingAltResolvedBySLL = conflictingAlts.nextSetBit(0);
		}
		else {
			conflictingAltResolvedBySLL = conflictState.s0.configs.getRepresentedAlternatives().nextSetBit(0);
		}
		decisions[currentDecision].LL_Fallback++;
		super.reportAttemptingFullContext(dfa, conflictingAlts, conflictState, startIndex, stopIndex);
	}

	@Override
	protected void reportAmbiguity(@NotNull DFA dfa, DFAState D, int startIndex, int stopIndex, boolean exact, @NotNull BitSet ambigAlts, @NotNull ATNConfigSet configs) {
		int prediction;
		if ( ambigAlts!=null ) {
			prediction = ambigAlts.nextSetBit(0);
		}
		else {
			prediction = configs.getRepresentedAlternatives().nextSetBit(0);
		}
		if ( conflictingAltResolvedBySLL != ATN.INVALID_ALT_NUMBER && prediction != conflictingAltResolvedBySLL ) {
			// Even though this is an ambiguity we are reporting, we can
			// still detect some context sensitivities.  Both SLL and LL
			// are showing a conflict, hence an ambiguity, but if they resolve
			// to different minimum alternatives we have also identified a
			// context sensitivity.
			decisions[currentDecision].contextSensitivities.add(
					new ContextSensitivityInfo(currentDecision, currentState, _input, startIndex, stopIndex)
			);
		}
		decisions[currentDecision].ambiguities.add(
			new AmbiguityInfo(currentDecision, currentState, ambigAlts, _input, startIndex, stopIndex)
		);
		super.reportAmbiguity(dfa, D, startIndex, stopIndex, exact, ambigAlts, configs);
	}

	// ---------------------------------------------------------------------

	public DecisionInfo[] getDecisionInfo() {
		return decisions;
	}

	public SimulatorState getCurrentState() {
		return currentState;
	}
}
