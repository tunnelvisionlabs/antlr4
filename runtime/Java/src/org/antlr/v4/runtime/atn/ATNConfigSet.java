/*
 * Copyright (c) 2012 The ANTLR Project. All rights reserved.
 * Use of this file is governed by the BSD-3-Clause license that
 * can be found in the LICENSE.txt file in the project root.
 */

package org.antlr.v4.runtime.atn;

import org.antlr.v4.runtime.annotations.NotNull;
import org.antlr.v4.runtime.annotations.Nullable;
import org.antlr.v4.runtime.misc.Utils;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Represents a set of ATN configurations (see {@link ATNConfig}). As
 * configurations are added to the set, they are merged with other
 * {@link ATNConfig} instances already in the set when possible using the
 * graph-structured stack.
 *
 * <p>An instance of this class represents the complete set of positions (with
 * context) in an ATN which would be associated with a single DFA state. Its
 * internal representation is more complex than traditional state used for NFA
 * to DFA conversion due to performance requirements (both improving speed and
 * reducing memory overhead) as well as supporting features such as semantic
 * predicates and non-greedy operators in a form to support ANTLR's prediction
 * algorithm.</p>
 *
 * @author Sam Harwell
 */
public class ATNConfigSet implements Set<ATNConfig> {

	/**
	 * This maps (state, alt) -> merged {@link ATNConfig}. The key does not account for
	 * the {@link ATNConfig#getSemanticContext} of the value, which is only a problem if a single
	 * {@code ATNConfigSet} contains two configs with the same state and alternative
	 * but different semantic contexts. When this case arises, the first config
	 * added to this map stays, and the remaining configs are placed in {@link #unmerged}.
	 * <p>
	 * This map is only used for optimizing the process of adding configs to the set,
	 * and is {@code null} for read-only sets stored in the DFA.
	 */
	private final HashMap<Long, ATNConfig> mergedConfigs;
	/**
	 * This is an "overflow" list holding configs which cannot be merged with one
	 * of the configs in {@link #mergedConfigs} but have a colliding key. This
	 * occurs when two configs in the set have the same state and alternative but
	 * different semantic contexts.
	 * <p>
	 * This list is only used for optimizing the process of adding configs to the set,
	 * and is {@code null} for read-only sets stored in the DFA.
	 */
	private final ArrayList<ATNConfig> unmerged;
	/**
	 * This is a list of all configs in this set.
	 */
	private final ArrayList<ATNConfig> configs;

	private int uniqueAlt;
	private ConflictInfo conflictInfo;
	// Used in parser and lexer. In lexer, it indicates we hit a pred
	// while computing a closure operation.  Don't make a DFA state from this.
	private boolean hasSemanticContext;
	private boolean dipsIntoOuterContext;
	/**
	 * When {@code true}, this config set represents configurations where the entire
	 * outer context has been consumed by the ATN interpreter. This prevents the
	 * {@link ParserATNSimulator#closure} from pursuing the global FOLLOW when a
	 * rule stop state is reached with an empty prediction context.
	 * <p>
	 * Note: {@code outermostConfigSet} and {@link #dipsIntoOuterContext} should never
	 * be true at the same time.
	 */
	private boolean outermostConfigSet;

	private int cachedHashCode = -1;

	public ATNConfigSet() {
		this.mergedConfigs = new HashMap<Long, ATNConfig>();
		this.unmerged = new ArrayList<ATNConfig>();
		this.configs = new ArrayList<ATNConfig>();

		this.uniqueAlt = ATN.INVALID_ALT_NUMBER;
	}

	@SuppressWarnings("unchecked")
	protected ATNConfigSet(ATNConfigSet set, boolean readonly) {
		if (readonly) {
			this.mergedConfigs = null;
			this.unmerged = null;
		} else if (!set.isReadOnly()) {
			this.mergedConfigs = (HashMap<Long, ATNConfig>)set.mergedConfigs.clone();
			this.unmerged = (ArrayList<ATNConfig>)set.unmerged.clone();
		} else {
			this.mergedConfigs = new HashMap<Long, ATNConfig>(set.configs.size());
			this.unmerged = new ArrayList<ATNConfig>();
		}

		this.configs = (ArrayList<ATNConfig>)set.configs.clone();

		this.dipsIntoOuterContext = set.dipsIntoOuterContext;
		this.hasSemanticContext = set.hasSemanticContext;
		this.outermostConfigSet = set.outermostConfigSet;

		if (readonly || !set.isReadOnly()) {
			this.uniqueAlt = set.uniqueAlt;
			this.conflictInfo = set.conflictInfo;
		}

		// if (!readonly && set.isReadOnly()) -> addAll is called from clone()
	}

	/**
	 * Get the set of all alternatives represented by configurations in this
	 * set.
	 */
	@NotNull
	public BitSet getRepresentedAlternatives() {
		if (conflictInfo != null) {
			return (BitSet)conflictInfo.getConflictedAlts().clone();
		}

		BitSet alts = new BitSet();
		for (ATNConfig config : this) {
			alts.set(config.getAlt());
		}

		return alts;
	}

	public final boolean isReadOnly() {
		return mergedConfigs == null;
	}

	public boolean isOutermostConfigSet() {
		return outermostConfigSet;
	}

	public void setOutermostConfigSet(boolean outermostConfigSet) {
		if (this.outermostConfigSet && !outermostConfigSet) {
			throw new IllegalStateException();
		}

		assert !outermostConfigSet || !dipsIntoOuterContext;
		this.outermostConfigSet = outermostConfigSet;
	}

	public Set<ATNState> getStates() {
		Set<ATNState> states = new HashSet<ATNState>();
		for (ATNConfig c : this.configs) {
			states.add(c.getState());
		}

		return states;
	}

	public void optimizeConfigs(ATNSimulator interpreter) {
		if (configs.isEmpty()) {
			return;
		}

		for (int i = 0; i < configs.size(); i++) {
			ATNConfig config = configs.get(i);
			config.setContext(interpreter.atn.getCachedContext(config.getContext()));
		}
	}

	public ATNConfigSet clone(boolean readonly) {
		ATNConfigSet copy = new ATNConfigSet(this, readonly);
		if (!readonly && this.isReadOnly()) {
			copy.addAll(this.configs);
		}

		return copy;
	}

	@Override
	public int size() {
		return configs.size();
	}

	@Override
	public boolean isEmpty() {
		return configs.isEmpty();
	}

	@Override
	public boolean contains(Object o) {
		if (!(o instanceof ATNConfig)) {
			return false;
		}

		ATNConfig config = (ATNConfig)o;
		long configKey = getKey(config);
		ATNConfig mergedConfig = mergedConfigs.get(configKey);
		if (mergedConfig != null && canMerge(config, configKey, mergedConfig)) {
			return mergedConfig.contains(config);
		}

		for (ATNConfig c : unmerged) {
			if (c.contains(config)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Iterator<ATNConfig> iterator() {
		return new ATNConfigSetIterator();
	}

	@Override
	public Object[] toArray() {
		return configs.toArray();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		return configs.toArray(a);
	}

	@Override
	public boolean add(ATNConfig e) {
		return add(e, null);
	}

	public boolean add(ATNConfig e, @Nullable PredictionContextCache contextCache) {
		ensureWritable();
		assert !outermostConfigSet || !e.getReachesIntoOuterContext();

		if (contextCache == null) {
			contextCache = PredictionContextCache.UNCACHED;
		}

		boolean addKey;
		long key = getKey(e);
		ATNConfig mergedConfig = mergedConfigs.get(key);
		addKey = (mergedConfig == null);
		if (mergedConfig != null && canMerge(e, key, mergedConfig)) {
			mergedConfig.setOuterContextDepth(Math.max(mergedConfig.getOuterContextDepth(), e.getOuterContextDepth()));
			if (e.isPrecedenceFilterSuppressed()) {
				mergedConfig.setPrecedenceFilterSuppressed(true);
			}

			PredictionContext joined = PredictionContext.join(mergedConfig.getContext(), e.getContext(), contextCache);
			updatePropertiesForMergedConfig(e);
			if (mergedConfig.getContext() == joined) {
				return false;
			}

			mergedConfig.setContext(joined);
			return true;
		}

		for (int i = 0; i < unmerged.size(); i++) {
			ATNConfig unmergedConfig = unmerged.get(i);
			if (canMerge(e, key, unmergedConfig)) {
				unmergedConfig.setOuterContextDepth(Math.max(unmergedConfig.getOuterContextDepth(), e.getOuterContextDepth()));
				if (e.isPrecedenceFilterSuppressed()) {
					unmergedConfig.setPrecedenceFilterSuppressed(true);
				}

				PredictionContext joined = PredictionContext.join(unmergedConfig.getContext(), e.getContext(), contextCache);
				updatePropertiesForMergedConfig(e);
				if (unmergedConfig.getContext() == joined) {
					return false;
				}

				unmergedConfig.setContext(joined);

				if (addKey) {
					mergedConfigs.put(key, unmergedConfig);
					unmerged.remove(i);
				}

				return true;
			}
		}

		configs.add(e);
		if (addKey) {
			mergedConfigs.put(key, e);
		} else {
			unmerged.add(e);
		}

		updatePropertiesForAddedConfig(e);
		return true;
	}

	private void updatePropertiesForMergedConfig(ATNConfig config) {
		// merged configs can't change the alt or semantic context
		dipsIntoOuterContext |= config.getReachesIntoOuterContext();
		assert !outermostConfigSet || !dipsIntoOuterContext;
	}

	private void updatePropertiesForAddedConfig(ATNConfig config) {
		if (configs.size() == 1) {
			uniqueAlt = config.getAlt();
		} else if (uniqueAlt != config.getAlt()) {
			uniqueAlt = ATN.INVALID_ALT_NUMBER;
		}

		hasSemanticContext |= !SemanticContext.NONE.equals(config.getSemanticContext());
		dipsIntoOuterContext |= config.getReachesIntoOuterContext();
		assert !outermostConfigSet || !dipsIntoOuterContext;
	}

	protected boolean canMerge(ATNConfig left, long leftKey, ATNConfig right) {
		if (left.getState().stateNumber != right.getState().stateNumber) {
			return false;
		}

		if (leftKey != getKey(right)) {
			return false;
		}

		return left.getSemanticContext().equals(right.getSemanticContext());
	}

	protected long getKey(ATNConfig e) {
		long key = e.getState().stateNumber;
		key = (key << 12) | (e.getAlt() & 0xFFF);
		return key;
	}

	@Override
	public boolean remove(Object o) {
		ensureWritable();

		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c) {
			if (!(o instanceof ATNConfig)) {
				return false;
			}

			if (!contains((ATNConfig)o)) {
				return false;
			}
		}

		return true;
	}

	@Override
	public boolean addAll(Collection<? extends ATNConfig> c) {
		return addAll(c, null);
	}

	public boolean addAll(Collection<? extends ATNConfig> c, PredictionContextCache contextCache) {
		ensureWritable();

		boolean changed = false;
		for (ATNConfig group : c) {
			changed |= add(group, contextCache);
		}

		return changed;
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		ensureWritable();
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		ensureWritable();
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void clear() {
		ensureWritable();

		mergedConfigs.clear();
		unmerged.clear();
		configs.clear();

		dipsIntoOuterContext = false;
		hasSemanticContext = false;
		uniqueAlt = ATN.INVALID_ALT_NUMBER;
		conflictInfo = null;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}

		if (!(obj instanceof ATNConfigSet)) {
			return false;
		}

		ATNConfigSet other = (ATNConfigSet)obj;
		return this.outermostConfigSet == other.outermostConfigSet
			&& Utils.equals(conflictInfo, other.conflictInfo)
			&& configs.equals(other.configs);
	}

	@Override
	public int hashCode() {
		if (isReadOnly() && cachedHashCode != -1) {
			return cachedHashCode;
		}

		int hashCode = 1;
		hashCode = 5 * hashCode ^ (outermostConfigSet ? 1 : 0);
		hashCode = 5 * hashCode ^ configs.hashCode();

		if (isReadOnly()) {
			cachedHashCode = hashCode;
		}

		return hashCode;
	}

	@Override
	public String toString() {
		return toString(false);
	}

	public String toString(boolean showContext) {
		StringBuilder buf = new StringBuilder();
		List<ATNConfig> sortedConfigs = new ArrayList<ATNConfig>(configs);
		Collections.sort(sortedConfigs, new Comparator<ATNConfig>() {
			@Override
			public int compare(ATNConfig o1, ATNConfig o2) {
				if (o1.getAlt() != o2.getAlt()) {
					return o1.getAlt() - o2.getAlt();
				}
				else if (o1.getState().stateNumber != o2.getState().stateNumber) {
					return o1.getState().stateNumber - o2.getState().stateNumber;
				}
				else {
					return o1.getSemanticContext().toString().compareTo(o2.getSemanticContext().toString());
				}
			}
		});

		buf.append("[");
		for (int i = 0; i < sortedConfigs.size(); i++) {
			if (i > 0) {
				buf.append(", ");
			}
			buf.append(sortedConfigs.get(i).toString(null, true, showContext));
		}
		buf.append("]");

		if ( hasSemanticContext ) buf.append(",hasSemanticContext=").append(hasSemanticContext);
		if ( uniqueAlt!=ATN.INVALID_ALT_NUMBER ) buf.append(",uniqueAlt=").append(uniqueAlt);
		if ( conflictInfo!=null ) {
			buf.append(",conflictingAlts=").append(conflictInfo.getConflictedAlts());
			if (!conflictInfo.isExact()) {
				buf.append("*");
			}
		}
		if ( dipsIntoOuterContext ) buf.append(",dipsIntoOuterContext");
		return buf.toString();
	}

	public int getUniqueAlt() {
		return uniqueAlt;
	}

	public boolean hasSemanticContext() {
		return hasSemanticContext;
	}

	public void clearExplicitSemanticContext() {
		ensureWritable();
		hasSemanticContext = false;
	}

	public void markExplicitSemanticContext() {
		ensureWritable();
		hasSemanticContext = true;
	}

	public ConflictInfo getConflictInfo() {
		return conflictInfo;
	}

	public void setConflictInfo(ConflictInfo conflictInfo) {
		ensureWritable();
		this.conflictInfo = conflictInfo;
	}

	public BitSet getConflictingAlts() {
		if (conflictInfo == null) {
			return null;
		}

		return conflictInfo.getConflictedAlts();
	}

	public boolean isExactConflict() {
		if (conflictInfo == null) {
			return false;
		}

		return conflictInfo.isExact();
	}

	public boolean getDipsIntoOuterContext() {
		return dipsIntoOuterContext;
	}

	public ATNConfig get(int index) {
		return configs.get(index);
	}

	public void remove(int index) {
		ensureWritable();
		ATNConfig config = configs.get(index);
		configs.remove(config);
		long key = getKey(config);
		if (mergedConfigs.get(key) == config) {
			mergedConfigs.remove(key);
		} else {
			for (int i = 0; i < unmerged.size(); i++) {
				if (unmerged.get(i) == config) {
					unmerged.remove(i);
					return;
				}
			}
		}
	}

	protected final void ensureWritable() {
		if (isReadOnly()) {
			throw new IllegalStateException("This ATNConfigSet is read only.");
		}
	}

	private final class ATNConfigSetIterator implements Iterator<ATNConfig> {

		int index = -1;
		boolean removed = false;

		@Override
		public boolean hasNext() {
			return index + 1 < configs.size();
		}

		@Override
		public ATNConfig next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}

			index++;
			removed = false;
			return configs.get(index);
		}

		@Override
		public void remove() {
			if (removed || index < 0 || index >= configs.size()) {
				throw new IllegalStateException();
			}

			ATNConfigSet.this.remove(index);
			removed = true;
		}

	}
}
