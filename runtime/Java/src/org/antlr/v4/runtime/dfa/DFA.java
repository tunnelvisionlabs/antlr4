/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */
package org.antlr.v4.runtime.dfa;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.Vocabulary;
import org.antlr.v4.runtime.VocabularyImpl;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.ATNType;
import org.antlr.v4.runtime.atn.LexerATNSimulator;
import org.antlr.v4.runtime.atn.StarLoopEntryState;
import org.antlr.v4.runtime.atn.TokensStartState;
import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DFA {
	/** A set of all DFA states. Use {@link Map} so we can get old state back
	 *  ({@link Set} only allows you to see if it's there).
	 *
	 * <p>Note that this collection of states holds the DFA states for both SLL
	 * and LL prediction. Only the start state needs to be differentiated for
	 * these cases, which is tracked by the {@link #s0} and {@link #s0full}
	 * fields.</p>
     */
    @NotNull
	public final ConcurrentMap<DFAState, DFAState> states = new ConcurrentHashMap<DFAState, DFAState>();

	/**
	 * This is the start state for SLL prediction.
	 *
	 * <p>When {@link #isPrecedenceDfa} is {@code true}, this state is not used
	 * directly. Rather, {@link #getPrecedenceStartState} is used to obtain the
	 * true SLL start state by traversing an outgoing edge corresponding to the
	 * current precedence level in the parser.</p>
	 */
	@NotNull
	public final AtomicReference<DFAState> s0 = new AtomicReference<DFAState>();

	/**
	 * This is the start state for full context prediction.
	 *
	 * @see #s0
	 */
	@NotNull
	public final AtomicReference<DFAState> s0full = new AtomicReference<DFAState>();

	public final int decision;

	/** From which ATN state did we create this DFA? */
	@NotNull
	public final ATNState atnStartState;

	private final AtomicInteger nextStateNumber = new AtomicInteger();

	/**
	 * This is the backing field for {@link #getMinDfaEdge()}.
	 */
	private final int minDfaEdge;

	/**
	 * This is the backing field for {@link #getMaxDfaEdge()}.
	 */
	private final int maxDfaEdge;

	/**
	 * This field initializes the outgoing edge map for the special starting DFA
	 * state created for precedence DFAs.
	 *
	 * <p>The range of allowed values assigned to this map represent the
	 * precedence levels for which an edge will be stored in the DFA. If a
	 * precedence level is encountered outside this range, the DFA will not be
	 * able to hold an edge pointing to the start state for the decision, so
	 * prediction for this precedence level will always result in recomputing
	 * the start state. Should this occur, the existing start state can still be
	 * located within {@link #states} so outgoing edges starting from the start
	 * state will not be dropped.</p>
	 */
	@NotNull
	private static final EmptyEdgeMap<DFAState> EMPTY_PRECEDENCE_EDGES =
		new EmptyEdgeMap<DFAState>(0, 200);

	/**
	 * This is the backing field for {@link #getEmptyEdgeMap()}.
	 */
	@NotNull
	private final EmptyEdgeMap<DFAState> emptyEdgeMap;

	/**
	 * This is the backing field for {@link #getEmptyContextEdgeMap()}.
	 */
	@NotNull
	private final EmptyEdgeMap<DFAState> emptyContextEdgeMap;

	/**
	 * {@code true} if this DFA is for a precedence decision; otherwise,
	 * {@code false}. This is the backing field for {@link #isPrecedenceDfa}.
	 */
	private final boolean precedenceDfa;

	/**
	 * Constructs a {@link DFA} instance associated with a lexer mode.
	 *
	 * <p>The start state for a {@link DFA} constructed with this method should
	 * be a {@link TokensStartState}, which is the start state for a lexer mode.
	 * The prediction made by this DFA determines the lexer rule which matches
	 * the current input.</p>
	 *
	 * @param atnStartState The start state for the mode.
	 */
	public DFA(@NotNull ATNState atnStartState) {
		this(atnStartState, 0);
	}

	/**
	 * Constructs a {@link DFA} instance associated with a decision.
	 *
	 * @param atnStartState The decision associated with this DFA.
	 * @param decision The decision number.
	 */
	public DFA(@NotNull ATNState atnStartState, int decision) {
		this.atnStartState = atnStartState;
		this.decision = decision;

		if (this.atnStartState.atn.grammarType == ATNType.LEXER) {
			minDfaEdge = LexerATNSimulator.MIN_DFA_EDGE;
			maxDfaEdge = LexerATNSimulator.MAX_DFA_EDGE;
		}
		else {
			minDfaEdge = Token.EOF;
			maxDfaEdge = atnStartState.atn.maxTokenType;
		}

		this.emptyEdgeMap = new EmptyEdgeMap<DFAState>(minDfaEdge, maxDfaEdge);
		this.emptyContextEdgeMap = new EmptyEdgeMap<DFAState>(-1, atnStartState.atn.states.size() - 1);

		// Precedence DFAs are associated with the special precedence decision
		// created for left-recursive rules which evaluate their alternatives
		// using a precedence hierarchy. When such a decision is encountered, we
		// mark this DFA instance as a precedence DFA and initialize the initial
		// states s0 and s0full to special DFAState instances which use outgoing
		// edges to link to the actual start state used for each precedence
		// level.
		boolean isPrecedenceDfa = false;
		if (atnStartState instanceof StarLoopEntryState) {
			if (((StarLoopEntryState)atnStartState).precedenceRuleDecision) {
				isPrecedenceDfa = true;
				this.s0.set(new DFAState(EMPTY_PRECEDENCE_EDGES, getEmptyContextEdgeMap(), new ATNConfigSet()));
				this.s0full.set(new DFAState(EMPTY_PRECEDENCE_EDGES, getEmptyContextEdgeMap(), new ATNConfigSet()));
			}
		}

		this.precedenceDfa = isPrecedenceDfa;
	}

	/**
	 * Gets the minimum input symbol value which can be stored in this DFA.
	 *
	 * @return The minimum input symbol which can be stored in this DFA.
	 *
	 * @see #getEmptyEdgeMap
	 */
	public final int getMinDfaEdge() {
		return minDfaEdge;
	}

	/**
	 * Gets the maximum input symbol value which can be stored in this DFA.
	 *
	 * @return The maximum input symbol which can be stored in this DFA.
	 *
	 * @see #getEmptyEdgeMap
	 */
	public final int getMaxDfaEdge() {
		return maxDfaEdge;
	}

	/**
	 * Gets an empty edge map initialized with the minimum and maximum symbol
	 * values allowed to be stored in this DFA.
	 *
	 * <p>Setting a range of allowed symbol values for a DFA bounds the memory
	 * overhead for storing the map of outgoing edges. The various
	 * implementations of {@link EdgeMap} use this range to determine the best
	 * memory savings will be obtained from sparse storage (e.g.
	 * {@link SingletonEdgeMap} or {@link SparseEdgeMap}) or dense storage
	 * ({@link ArrayEdgeMap}). Symbols values outside the range are supported
	 * during prediction, but since DFA edges are never created for these
	 * symbols they will always recompute the target state through a match and
	 * closure operation.</p>
	 *
	 * <p>Empty edge maps are immutable objects which track the allowed range of
	 * input symbols that can be stored as edges in the DFA. By storing an empty
	 * edge map instance in the DFA, new instances of {@link DFAState} created
	 * for the DFA can be initialized with a non-null outgoing edge map with the
	 * proper symbol range without incurring extra allocations.</p>
	 *
	 * @return The empty edge map used for initializing {@link DFAState}
	 * instances associated with this DFA.
	 */
	@NotNull
	public EmptyEdgeMap<DFAState> getEmptyEdgeMap() {
		return emptyEdgeMap;
	}

	/**
	 * Gets an empty edge map initialized with the minimum and maximum context
	 * values allowed to be stored in this DFA.
	 *
	 * <p>The value assigned to a context edge within the DFA is an ATN state
	 * number, so the range of allowed values for the context edge map is
	 * {@link ATNState#INVALID_STATE_NUMBER} through the number of states stored
	 * in {@link ATN#states} for the ATN.</p>
	 *
	 * <p>This empty edge map serves a purpose similar to
	 * {@link #getEmptyEdgeMap}. It is used for initializing {@link DFAState}
	 * instances without incurring memory overhead for the (especially) common
	 * case where no outgoing context edges are added to the DFA state.</p>
	 *
	 * @return The empty context edge map used for initializing {@link DFAState}
	 * instances associated with this DFA.
	 */
	@NotNull
	public EmptyEdgeMap<DFAState> getEmptyContextEdgeMap() {
		return emptyContextEdgeMap;
	}

	/**
	 * Gets whether this DFA is a precedence DFA. Precedence DFAs use a special
	 * start state {@link #s0} which is not stored in {@link #states}. The
	 * {@link DFAState#edges} array for this start state contains outgoing edges
	 * supplying individual start states corresponding to specific precedence
	 * values.
	 *
	 * @return {@code true} if this is a precedence DFA; otherwise,
	 * {@code false}.
	 * @see Parser#getPrecedence()
	 */
	public final boolean isPrecedenceDfa() {
		return precedenceDfa;
	}

	/**
	 * Get the start state for a specific precedence value.
	 *
	 * @param precedence The current precedence.
	 * @return The start state corresponding to the specified precedence, or
	 * {@code null} if no start state exists for the specified precedence.
	 *
	 * @throws IllegalStateException if this is not a precedence DFA.
	 * @see #isPrecedenceDfa()
	 */
	@SuppressWarnings("null")
	public final DFAState getPrecedenceStartState(int precedence, boolean fullContext) {
		if (!isPrecedenceDfa()) {
			throw new IllegalStateException("Only precedence DFAs may contain a precedence start state.");
		}

		// s0.get() and s0full.get() are never null for a precedence DFA
		if (fullContext) {
			return s0full.get().getTarget(precedence);
		}
		else {
			return s0.get().getTarget(precedence);
		}
	}

	/**
	 * Set the start state for a specific precedence value.
	 *
	 * @param precedence The current precedence.
	 * @param startState The start state corresponding to the specified
	 * precedence.
	 *
	 * @throws IllegalStateException if this is not a precedence DFA.
	 * @see #isPrecedenceDfa()
	 */
	@SuppressWarnings({"SynchronizeOnNonFinalField", "null"})
	public final void setPrecedenceStartState(int precedence, boolean fullContext, DFAState startState) {
		if (!isPrecedenceDfa()) {
			throw new IllegalStateException("Only precedence DFAs may contain a precedence start state.");
		}

		if (precedence < 0) {
			return;
		}

		if (fullContext) {
			synchronized (s0full) {
				// s0full.get() is never null for a precedence DFA
				s0full.get().setTarget(precedence, startState);
			}
		}
		else {
			synchronized (s0) {
				// s0.get() is never null for a precedence DFA
				s0.get().setTarget(precedence, startState);
			}
		}
	}

	/**
	 * Sets whether this is a precedence DFA.
	 *
	 * @param precedenceDfa {@code true} if this is a precedence DFA; otherwise,
	 * {@code false}
	 *
	 * @throws UnsupportedOperationException if {@code precedenceDfa} does not
	 * match the value of {@link #isPrecedenceDfa} for the current DFA.
	 *
	 * @deprecated This method no longer performs any action.
	 */
	@Deprecated
	public final void setPrecedenceDfa(boolean precedenceDfa) {
		if (precedenceDfa != isPrecedenceDfa()) {
			throw new UnsupportedOperationException("The precedenceDfa field cannot change after a DFA is constructed.");
		}
	}

	public boolean isEmpty() {
		if (isPrecedenceDfa()) {
			return s0.get().getEdgeMap().isEmpty() && s0full.get().getEdgeMap().isEmpty();
		}

		return s0.get() == null && s0full.get() == null;
	}

	public boolean isContextSensitive() {
		if (isPrecedenceDfa()) {
			return !s0full.get().getEdgeMap().isEmpty();
		}

		return s0full.get() != null;
	}

	public DFAState addState(DFAState state) {
		state.stateNumber = nextStateNumber.getAndIncrement();
		DFAState existing = states.putIfAbsent(state, state);
		if (existing != null) {
			return existing;
		}

		return state;
	}

	@Override
	public String toString() { return toString(VocabularyImpl.EMPTY_VOCABULARY); }

	/**
	 * @deprecated Use {@link #toString(Vocabulary)} instead.
	 */
	@Deprecated
	public String toString(@Nullable String[] tokenNames) {
		if ( s0.get()==null ) return "";
		DFASerializer serializer = new DFASerializer(this,tokenNames);
		return serializer.toString();
	}

	public String toString(@NotNull Vocabulary vocabulary) {
		if (s0.get() == null) {
			return "";
		}

		DFASerializer serializer = new DFASerializer(this, vocabulary);
		return serializer.toString();
	}

	/**
	 * @deprecated Use {@link #toString(Vocabulary, String[])} instead.
	 */
	@Deprecated
	public String toString(@Nullable String[] tokenNames, @Nullable String[] ruleNames) {
		if ( s0.get()==null ) return "";
		DFASerializer serializer = new DFASerializer(this,tokenNames,ruleNames,atnStartState.atn);
		return serializer.toString();
	}

	public String toString(@NotNull Vocabulary vocabulary, @Nullable String[] ruleNames) {
		if (s0.get() == null) {
			return "";
		}

		DFASerializer serializer = new DFASerializer(this, vocabulary, ruleNames, atnStartState.atn);
		return serializer.toString();
	}

	public String toLexerString() {
		if ( s0.get()==null ) return "";
		DFASerializer serializer = new LexerDFASerializer(this);
		return serializer.toString();
	}

}
