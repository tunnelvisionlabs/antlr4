/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.misc.AbstractEqualityComparator;
import org.antlr.v4.runtime.misc.FlexibleHashMap;
import org.antlr.v4.runtime.misc.MurmurHash;
import org.antlr.v4.runtime.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

public abstract class PredictionContext {
	@NotNull
	public static final PredictionContext EMPTY_LOCAL = EmptyPredictionContext.LOCAL_CONTEXT;
	@NotNull
	public static final PredictionContext EMPTY_FULL = EmptyPredictionContext.FULL_CONTEXT;

	public static final int EMPTY_LOCAL_STATE_KEY = Integer.MIN_VALUE;
	public static final int EMPTY_FULL_STATE_KEY = Integer.MAX_VALUE;

	private static final int INITIAL_HASH = 1;

	/**
	 * Stores the computed hash code of this {@link PredictionContext}. The hash
	 * code is computed in parts to match the following reference algorithm.
	 *
	 * <pre>
	 *  private int referenceHashCode() {
	 *      int hash = {@link MurmurHash#initialize MurmurHash.initialize}({@link #INITIAL_HASH});
	 *
	 *      for (int i = 0; i &lt; {@link #size()}; i++) {
	 *          hash = {@link MurmurHash#update MurmurHash.update}(hash, {@link #getParent getParent}(i));
	 *      }
	 *
	 *      for (int i = 0; i &lt; {@link #size()}; i++) {
	 *          hash = {@link MurmurHash#update MurmurHash.update}(hash, {@link #getReturnState getReturnState}(i));
	 *      }
	 *
	 *      hash = {@link MurmurHash#finish MurmurHash.finish}(hash, 2 * {@link #size()});
	 *      return hash;
	 *  }
	 * </pre>
	 */
	private final int cachedHashCode;

	protected PredictionContext(int cachedHashCode) {
		this.cachedHashCode = cachedHashCode;
	}

	protected static int calculateEmptyHashCode() {
		int hash = MurmurHash.initialize(INITIAL_HASH);
		hash = MurmurHash.finish(hash, 0);
		return hash;
	}

	protected static int calculateHashCode(PredictionContext parent, int returnState) {
		int hash = MurmurHash.initialize(INITIAL_HASH);
		hash = MurmurHash.update(hash, parent);
		hash = MurmurHash.update(hash, returnState);
		hash = MurmurHash.finish(hash, 2);
		return hash;
	}

	protected static int calculateHashCode(PredictionContext[] parents, int[] returnStates) {
		int hash = MurmurHash.initialize(INITIAL_HASH);

		for (PredictionContext parent : parents) {
			hash = MurmurHash.update(hash, parent);
		}

		for (int returnState : returnStates) {
			hash = MurmurHash.update(hash, returnState);
		}

		hash = MurmurHash.finish(hash, 2 * parents.length);
		return hash;
	}

	public abstract int size();

	public abstract int getReturnState(int index);

	public abstract int findReturnState(int returnState);

	@NotNull
	public abstract PredictionContext getParent(int index);

	protected abstract PredictionContext addEmptyContext();

	protected abstract PredictionContext removeEmptyContext();

	public static PredictionContext fromRuleContext(@NotNull ATN atn, @NotNull RuleContext outerContext) {
		return fromRuleContext(atn, outerContext, true);
	}

	public static PredictionContext fromRuleContext(@NotNull ATN atn, @NotNull RuleContext outerContext, boolean fullContext) {
		if (outerContext.isEmpty()) {
			return fullContext ? EMPTY_FULL : EMPTY_LOCAL;
		}

		PredictionContext parent;
		if (outerContext.parent != null) {
			parent = PredictionContext.fromRuleContext(atn, outerContext.parent, fullContext);
		} else {
			parent = fullContext ? EMPTY_FULL : EMPTY_LOCAL;
		}

		ATNState state = atn.states.get(outerContext.invokingState);
		RuleTransition transition = (RuleTransition)state.transition(0);
		return parent.getChild(transition.followState.stateNumber);
	}

	private static PredictionContext addEmptyContext(PredictionContext context) {
		return context.addEmptyContext();
	}

	private static PredictionContext removeEmptyContext(PredictionContext context) {
		return context.removeEmptyContext();
	}

	public static PredictionContext join(PredictionContext context0, PredictionContext context1) {
		return join(context0, context1, PredictionContextCache.UNCACHED);
	}

	/*package*/ static PredictionContext join(@NotNull final PredictionContext context0, @NotNull final PredictionContext context1, @NotNull PredictionContextCache contextCache) {
		if (context0 == context1) {
			return context0;
		}

		if (context0.isEmpty()) {
			return isEmptyLocal(context0) ? context0 : addEmptyContext(context1);
		} else if (context1.isEmpty()) {
			return isEmptyLocal(context1) ? context1 : addEmptyContext(context0);
		}

		final int context0size = context0.size();
		final int context1size = context1.size();
		if (context0size == 1 && context1size == 1 && context0.getReturnState(0) == context1.getReturnState(0)) {
			PredictionContext merged = contextCache.join(context0.getParent(0), context1.getParent(0));
			if (merged == context0.getParent(0)) {
				return context0;
			} else if (merged == context1.getParent(0)) {
				return context1;
			} else {
				return merged.getChild(context0.getReturnState(0));
			}
		}

		int count = 0;
		PredictionContext[] parentsList = new PredictionContext[context0size + context1size];
		int[] returnStatesList = new int[parentsList.length];
		int leftIndex = 0;
		int rightIndex = 0;
		boolean canReturnLeft = true;
		boolean canReturnRight = true;
		while (leftIndex < context0size && rightIndex < context1size) {
			if (context0.getReturnState(leftIndex) == context1.getReturnState(rightIndex)) {
				parentsList[count] = contextCache.join(context0.getParent(leftIndex), context1.getParent(rightIndex));
				returnStatesList[count] = context0.getReturnState(leftIndex);
				canReturnLeft = canReturnLeft && parentsList[count] == context0.getParent(leftIndex);
				canReturnRight = canReturnRight && parentsList[count] == context1.getParent(rightIndex);
				leftIndex++;
				rightIndex++;
			}
			else if (context0.getReturnState(leftIndex) < context1.getReturnState(rightIndex)) {
				parentsList[count] = context0.getParent(leftIndex);
				returnStatesList[count] = context0.getReturnState(leftIndex);
				canReturnRight = false;
				leftIndex++;
			}
			else {
				assert context1.getReturnState(rightIndex) < context0.getReturnState(leftIndex);
				parentsList[count] = context1.getParent(rightIndex);
				returnStatesList[count] = context1.getReturnState(rightIndex);
				canReturnLeft = false;
				rightIndex++;
			}

			count++;
		}

		while (leftIndex < context0size) {
			parentsList[count] = context0.getParent(leftIndex);
			returnStatesList[count] = context0.getReturnState(leftIndex);
			leftIndex++;
			canReturnRight = false;
			count++;
		}

		while (rightIndex < context1size) {
			parentsList[count] = context1.getParent(rightIndex);
			returnStatesList[count] = context1.getReturnState(rightIndex);
			rightIndex++;
			canReturnLeft = false;
			count++;
		}

		if (canReturnLeft) {
			return context0;
		}
		else if (canReturnRight) {
			return context1;
		}

		if (count < parentsList.length) {
			parentsList = Arrays.copyOf(parentsList, count);
			returnStatesList = Arrays.copyOf(returnStatesList, count);
		}

		if (parentsList.length == 0) {
			// if one of them was EMPTY_LOCAL, it would be empty and handled at the beginning of the method
			return EMPTY_FULL;
		}
		else if (parentsList.length == 1) {
			return new SingletonPredictionContext(parentsList[0], returnStatesList[0]);
		}
		else {
			return new ArrayPredictionContext(parentsList, returnStatesList);
		}
	}

	public static boolean isEmptyLocal(PredictionContext context) {
		return context == EMPTY_LOCAL;
	}

	public static PredictionContext getCachedContext(
		@NotNull PredictionContext context,
		@NotNull ConcurrentMap<PredictionContext, PredictionContext> contextCache,
		@NotNull PredictionContext.IdentityHashMap visited) {
		if (context.isEmpty()) {
			return context;
		}

		PredictionContext existing = visited.get(context);
		if (existing != null) {
			return existing;
		}

		existing = contextCache.get(context);
		if (existing != null) {
			visited.put(context, existing);
			return existing;
		}

		boolean changed = false;
		PredictionContext[] parents = new PredictionContext[context.size()];
		for (int i = 0; i < parents.length; i++) {
			PredictionContext parent = getCachedContext(context.getParent(i), contextCache, visited);
			if (changed || parent != context.getParent(i)) {
				if (!changed) {
					parents = new PredictionContext[context.size()];
					for (int j = 0; j < context.size(); j++) {
						parents[j] = context.getParent(j);
					}

					changed = true;
				}

				parents[i] = parent;
			}
		}

		if (!changed) {
			existing = contextCache.putIfAbsent(context, context);
			visited.put(context, existing != null ? existing : context);
			return context;
		}

		// We know parents.length>0 because context.isEmpty() is checked at the beginning of the method.
		PredictionContext updated;
		if (parents.length == 1) {
			updated = new SingletonPredictionContext(parents[0], context.getReturnState(0));
		}
		else {
			ArrayPredictionContext arrayPredictionContext = (ArrayPredictionContext)context;
			updated = new ArrayPredictionContext(parents, arrayPredictionContext.returnStates, context.cachedHashCode);
		}

		existing = contextCache.putIfAbsent(updated, updated);
		visited.put(updated, existing != null ? existing : updated);
		visited.put(context, existing != null ? existing : updated);

		return updated;
	}

	public PredictionContext appendContext(int returnContext, PredictionContextCache contextCache) {
		return appendContext(PredictionContext.EMPTY_FULL.getChild(returnContext), contextCache);
	}

	public abstract PredictionContext appendContext(PredictionContext suffix, PredictionContextCache contextCache);

	public PredictionContext getChild(int returnState) {
		return new SingletonPredictionContext(this, returnState);
	}

	public abstract boolean isEmpty();

	public abstract boolean hasEmpty();

	@Override
	public final int hashCode() {
		return cachedHashCode;
	}

	@Override
	public abstract boolean equals(Object o);

	//@Override
	//public String toString() {
	//	return toString(null, Integer.MAX_VALUE);
	//}

	public String[] toStrings(Recognizer<?, ?> recognizer, int currentState) {
		return toStrings(recognizer, PredictionContext.EMPTY_FULL, currentState);
	}

	public String[] toStrings(Recognizer<?, ?> recognizer, PredictionContext stop, int currentState) {
		List<String> result = new ArrayList<String>();

		outer:
		for (int perm = 0; ; perm++) {
			int offset = 0;
			boolean last = true;
			PredictionContext p = this;
			int stateNumber = currentState;
			StringBuilder localBuffer = new StringBuilder();
			localBuffer.append("[");
			while ( !p.isEmpty() && p != stop ) {
				int index = 0;
				if (p.size() > 0) {
					int bits = 1;
					while ((1 << bits) < p.size()) {
						bits++;
					}

					int mask = (1 << bits) - 1;
					index = (perm >> offset) & mask;
					last &= index >= p.size() - 1;
					if (index >= p.size()) {
						continue outer;
					}
					offset += bits;
				}

				if ( recognizer!=null ) {
					if (localBuffer.length() > 1) {
						// first char is '[', if more than that this isn't the first rule
						localBuffer.append(' ');
					}

					ATN atn = recognizer.getATN();
					ATNState s = atn.states.get(stateNumber);
					String ruleName = recognizer.getRuleNames()[s.ruleIndex];
					localBuffer.append(ruleName);
				}
				else if ( p.getReturnState(index)!=EMPTY_FULL_STATE_KEY ) {
					if ( !p.isEmpty() ) {
						if (localBuffer.length() > 1) {
							// first char is '[', if more than that this isn't the first rule
							localBuffer.append(' ');
						}

						localBuffer.append(p.getReturnState(index));
					}
				}
				stateNumber = p.getReturnState(index);
				p = p.getParent(index);
			}
			localBuffer.append("]");
			result.add(localBuffer.toString());

			if (last) {
				break;
			}
		}

		return result.toArray(new String[result.size()]);
	}

	public static final class IdentityHashMap extends FlexibleHashMap<PredictionContext, PredictionContext> {

		public IdentityHashMap() {
			super(IdentityEqualityComparator.INSTANCE);
		}
	}

	public static final class IdentityEqualityComparator extends AbstractEqualityComparator<PredictionContext> {
		public static final IdentityEqualityComparator INSTANCE = new IdentityEqualityComparator();

		private IdentityEqualityComparator() {
		}

		@Override
		public int hashCode(PredictionContext obj) {
			return obj.hashCode();
		}

		@Override
		public boolean equals(PredictionContext a, PredictionContext b) {
			return a == b;
		}
	}
}
