package com.msd.gin.halyard.sail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.annotation.Nonnull;

import org.apache.hadoop.conf.Configuration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.Dataset;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategy;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;

final class QueryCache {
	private final Cache<PreparedQueryKey, PreparedQuery> cache;

	QueryCache(Configuration conf) {
		int queryCacheMaxSize = conf.getInt(EvaluationConfig.QUERY_CACHE_MAX_SIZE, 100);
		cache = CacheBuilder.newBuilder().concurrencyLevel(1).maximumSize(queryCacheMaxSize).build();
	}

	TupleExpr getOptimizedQuery(HBaseSailConnection conn, String sourceString, int updatePart, TupleExpr tupleExpr, Dataset dataset, BindingSet bindings, final boolean includeInferred, TripleSource tripleSource,
			EvaluationStrategy strategy) {
		PreparedQueryKey pqkey = new PreparedQueryKey(sourceString, updatePart, dataset, bindings, includeInferred);
		PreparedQuery preparedQuery;
		try {
			preparedQuery = cache.get(pqkey, () -> {
				TupleExpr optimizedTupleExpr = conn.optimize(tupleExpr, dataset, bindings, includeInferred, tripleSource, strategy);
				return new PreparedQuery(optimizedTupleExpr);
			});
		} catch (ExecutionException e) {
			if (e.getCause() instanceof RuntimeException) {
				throw (RuntimeException) e.getCause();
			} else {
				throw new AssertionError(e);
			}
		}
		return preparedQuery.getTupleExpression();
	}


	private static final class PreparedQueryKey implements Serializable {
		private static final long serialVersionUID = -8673870599435959092L;

		final String sourceString;
		final Integer updatePart;
		final Set<IRI> datasetGraphs;
		final Set<IRI> datasetNamedGraphs;
		final IRI datasetInsertGraph;
		final Set<IRI> datasetRemoveGraphs;
		final Set<String> bindingNames;
		final boolean includeInferred;

		static <E> Set<E> copy(Set<E> set) {
			switch (set.size()) {
				case 0:
					return Collections.emptySet();
				case 1:
					return Collections.singleton(set.iterator().next());
				default:
					return new HashSet<>(set);
			}
		}

		PreparedQueryKey(@Nonnull String sourceString, int updatePart, Dataset dataset, BindingSet bindings, boolean includeInferred) {
			this.sourceString = sourceString;
			this.updatePart = Integer.valueOf(updatePart);
			this.datasetGraphs = dataset != null ? copy(dataset.getDefaultGraphs()) : null;
			this.datasetNamedGraphs = dataset != null ? copy(dataset.getNamedGraphs()) : null;
			this.datasetInsertGraph = dataset != null ? dataset.getDefaultInsertGraph() : null;
			this.datasetRemoveGraphs = dataset != null ? copy(dataset.getDefaultRemoveGraphs()) : null;
			this.bindingNames = copy(bindings.getBindingNames());
			this.includeInferred = includeInferred;
		}

		private Object[] toArray() {
			return new Object[] { sourceString, updatePart, bindingNames, includeInferred, datasetGraphs, datasetNamedGraphs, datasetInsertGraph, datasetRemoveGraphs };
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof PreparedQueryKey)) {
				return false;
			}
			PreparedQueryKey other = (PreparedQueryKey) o;
			return Arrays.equals(this.toArray(), other.toArray());
		}

		@Override
		public int hashCode() {
			return Objects.hash(toArray());
		}
	}


	private static final class PreparedQuery {
		private final TupleExpr tree; // our golden copy

		PreparedQuery(TupleExpr tree) {
			this.tree = tree;
		}

		/**
		 * Returns a copy of the query tree for execution.
		 */
		private TupleExpr getTupleExpression() {
			return tree.clone();
		}
	}
}