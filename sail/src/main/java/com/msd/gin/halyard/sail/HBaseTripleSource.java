/*
 * Copyright 2016 Merck Sharp & Dohme Corp. a subsidiary of Merck & Co.,
 * Inc., Kenilworth, NJ, USA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.msd.gin.halyard.sail;

import com.msd.gin.halyard.common.HalyardTableUtils;
import com.msd.gin.halyard.common.KeyspaceConnection;
import com.msd.gin.halyard.common.RDFContext;
import com.msd.gin.halyard.common.RDFFactory;
import com.msd.gin.halyard.common.RDFObject;
import com.msd.gin.halyard.common.RDFPredicate;
import com.msd.gin.halyard.common.RDFRole;
import com.msd.gin.halyard.common.RDFSubject;
import com.msd.gin.halyard.common.StatementIndex;
import com.msd.gin.halyard.common.StatementIndices;
import com.msd.gin.halyard.common.TimestampedValueFactory;
import com.msd.gin.halyard.common.ValueConstraint;
import com.msd.gin.halyard.model.vocabulary.HALYARD;
import com.msd.gin.halyard.query.algebra.evaluation.CloseableTripleSource;
import com.msd.gin.halyard.query.algebra.evaluation.ExtendedTripleSource;
import com.msd.gin.halyard.query.algebra.evaluation.PartitionableTripleSource;
import com.msd.gin.halyard.query.algebra.evaluation.QueryPreparer;
import com.msd.gin.halyard.query.algebra.evaluation.function.ParallelSplitFunction;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.common.iteration.ConvertingIteration;
import org.eclipse.rdf4j.common.iteration.ExceptionConvertingIteration;
import org.eclipse.rdf4j.common.iteration.FilterIteration;
import org.eclipse.rdf4j.common.iteration.TimeLimitIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Triple;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SPIN;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.algebra.evaluation.RDFStarTripleSource;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HBaseTripleSource implements ExtendedTripleSource, RDFStarTripleSource, PartitionableTripleSource, CloseableTripleSource {
	private static final Logger LOG = LoggerFactory.getLogger(HBaseTripleSource.class);

	protected final KeyspaceConnection keyspaceConn;
	protected final ValueFactory vf;
	protected final StatementIndices stmtIndices;
	private final long timeoutSecs;
	private final QueryPreparer.Factory queryPreparerFactory;
	protected final RDFFactory rdfFactory;
	private final HBaseSail.ScanSettings settings;
	private final HBaseSail.Ticker ticker;
	private final int forkIndex;

	public HBaseTripleSource(KeyspaceConnection keyspaceConn, ValueFactory vf, StatementIndices stmtIndices, long timeoutSecs, QueryPreparer.Factory qpFactory) {
		this(keyspaceConn, vf, stmtIndices, timeoutSecs, qpFactory, null, null, StatementIndices.NO_PARTITIONING);
	}

	protected HBaseTripleSource(KeyspaceConnection keyspaceConn, ValueFactory vf, StatementIndices stmtIndices, long timeoutSecs, QueryPreparer.Factory qpFactory, HBaseSail.ScanSettings settings, HBaseSail.Ticker ticker, int forkIndex) {
		this.keyspaceConn = keyspaceConn;
		this.vf = vf;
		this.stmtIndices = stmtIndices;
		this.queryPreparerFactory = qpFactory;
		this.rdfFactory = stmtIndices.getRDFFactory();
		this.timeoutSecs = timeoutSecs;
		this.settings = settings;
		this.ticker = ticker;
		this.forkIndex = forkIndex;
	}

	@Override
	public QueryPreparer newQueryPreparer() {
		return queryPreparerFactory.create();
	}

	@Override
	public int getPartitionIndex() {
		return forkIndex;
	}

	public KeyspaceConnection getKeyspaceConnection() {
		return keyspaceConn;
	}

	public StatementIndices getStatementIndices() {
		return stmtIndices;
	}

	static final class QueryContexts {
		final List<Resource> contextsToScan;
		final Set<Resource> contextsToFilter;

		QueryContexts(Resource... contexts) {
			if (contexts == null || contexts.length == 0) {
				// if all contexts then scan the default context
				contextsToScan = Collections.singletonList(null);
				contextsToFilter = null;
			} else if (Arrays.stream(contexts).anyMatch(Objects::isNull)) {
				// if any context is the default context then just scan the default context (everything)
				contextsToScan = Collections.singletonList(null);
				// filter out any scan that includes the default context (everything) to the specified contexts
				contextsToFilter = new HashSet<>();
				Collections.addAll(contextsToFilter, contexts);
			} else {
				contextsToScan = Arrays.asList(contexts);
				contextsToFilter = null;
			}
		}
	}

	@Override
	public final CloseableIteration<? extends Statement, QueryEvaluationException> getStatements(Resource subj, IRI pred, Value obj, Resource... contexts) throws QueryEvaluationException {
		if (RDF.TYPE.equals(pred) && SPIN.MAGIC_PROPERTY_CLASS.equals(obj)) {
			// cache magic property definitions here
			return EMPTY_ITERATION;
		} else {
			QueryContexts queryContexts = new QueryContexts(contexts);
			return getStatementsInternal(subj, pred, obj, queryContexts);
		}
	}

	@Override
	public final boolean hasStatement(Resource subj, IRI pred, Value obj, Resource... contexts) throws QueryEvaluationException {
		if (RDF.TYPE.equals(pred) && SPIN.MAGIC_PROPERTY_CLASS.equals(obj)) {
			// cache magic property definitions here
			return false;
		} else {
			QueryContexts queryContexts = new QueryContexts(contexts);
			return hasStatementInternal(subj, pred, obj, queryContexts);
		}
	}

	private CloseableIteration<? extends Statement, QueryEvaluationException> getStatementsInternal(Resource subj, IRI pred, Value obj, QueryContexts queryContexts) {
		CloseableIteration<? extends Statement, QueryEvaluationException> iter = timeLimit(
				new ExceptionConvertingIteration<Statement, QueryEvaluationException>(createStatementScanner(subj, pred, obj, queryContexts.contextsToScan)) {
			@Override
			protected QueryEvaluationException convert(Exception e) {
				return new QueryEvaluationException(e);
			}
		}, timeoutSecs);
		if (queryContexts.contextsToFilter != null) {
			iter = new FilterIteration<Statement, QueryEvaluationException>(iter) {
				@Override
				protected boolean accept(Statement st) {
					return queryContexts.contextsToFilter.contains(st.getContext());
				}
			};
		}
		return iter;
	}

	protected CloseableIteration<? extends Statement, IOException> createStatementScanner(Resource subj, IRI pred, Value obj, List<Resource> contexts) {
		return new StatementScanner(subj, pred, obj, contexts);
	}

	protected boolean hasStatementInternal(Resource subj, IRI pred, Value obj, QueryContexts queryContexts) throws QueryEvaluationException {
		if (queryContexts.contextsToFilter != null) {
			// not possible to optimise
			return hasStatementFallback(subj, pred, obj, queryContexts);
		} else {
			RDFSubject subject = rdfFactory.createSubject(subj);
			RDFPredicate predicate = rdfFactory.createPredicate(pred);
			RDFObject object = rdfFactory.createObject(obj);
			for (Resource ctx : queryContexts.contextsToScan) {
				RDFContext context = rdfFactory.createContext(ctx);
				try {
					Scan scan = scan(subject, predicate, object, context);
					if (scan == null) {
						return false;
					}
					return HalyardTableUtils.exists(keyspaceConn, scan);
				} catch (IOException e) {
					throw new QueryEvaluationException(e);
				}
			}
			throw new AssertionError();
		}
	}

	protected final boolean hasStatementFallback(Resource subj, IRI pred, Value obj, QueryContexts queryContexts) {
		try (CloseableIteration<? extends Statement, QueryEvaluationException> iter = getStatementsInternal(subj, pred, obj, queryContexts)) {
			return iter.hasNext();
		}
	}

	protected Scan scan(RDFSubject subj, RDFPredicate pred, RDFObject obj, RDFContext ctx) throws IOException {
		Scan scan = stmtIndices.scan(subj, pred, obj, ctx);
		applySettings(scan);
		return scan;
	}

	private void applySettings(Scan scan) throws IOException {
		if (scan != null && settings != null) {
			scan.setTimeRange(settings.minTimestamp, settings.maxTimestamp);
			scan.readVersions(settings.maxVersions);
		}
	}

	public TripleSource getTimestampedTripleSource() {
		return new HBaseTripleSource(keyspaceConn, new TimestampedValueFactory(rdfFactory), stmtIndices, timeoutSecs, queryPreparerFactory, settings, ticker, forkIndex);
	}

	@Override
	public TripleSource partition(RDFRole.Name roleName, @Nullable StatementIndex.Name indexToUse, int partitionCount, ValueConstraint constraint) {
		if (forkIndex >= partitionCount) {
			throw new IllegalArgumentException(String.format("Partition number %d must be less than %d", forkIndex, partitionCount));
		}
		return new HBaseTripleSource(keyspaceConn, vf, stmtIndices, timeoutSecs, queryPreparerFactory, settings, ticker, forkIndex) {
			@Override
			protected Scan scan(RDFSubject subj, RDFPredicate pred, RDFObject obj, RDFContext ctx) throws IOException {
				Scan scan = stmtIndices.scanWithConstraint(subj, pred, obj, ctx, roleName, indexToUse, forkIndex, ParallelSplitFunction.powerOf2BitCount(partitionCount), constraint);
				applySettings(scan);
				return scan;
			}
		};
	}

	@Override
	public final ValueFactory getValueFactory() {
		return vf;
	}

	@Override
	public final void close() {
		try {
			keyspaceConn.close();
		} catch (IOException ioe) {
			throw new QueryEvaluationException(ioe);
		}
	}

	@Override
	public String toString() {
		return super.toString() + "[keyspace = " + keyspaceConn.toString() + "]";
	}

	protected class StatementScanner extends AbstractStatementScanner {

		protected List<Resource> contextsList;
		protected Iterator<Resource> contexts;
		private ResultScanner rs = null;

		public StatementScanner(Resource subj, IRI pred, Value obj, List<Resource> contextsList) {
			super(HBaseTripleSource.this.stmtIndices, HBaseTripleSource.this.vf);
			this.subj = rdfFactory.createSubject(subj);
			this.pred = rdfFactory.createPredicate(pred);
			this.obj = rdfFactory.createObject(obj);
			this.contextsList = contextsList;
			this.contexts = contextsList.iterator();
			LOG.trace("New StatementScanner {} {} {} {}", subj, pred, obj, contextsList);
		}

		protected Result nextResult() throws IOException { // gets the next result to consider from the HBase Scan
			while (true) {
				if (rs == null) {
					if (contexts.hasNext()) {

						// build a ResultScanner from an HBase Scan that finds potential matches
						ctx = rdfFactory.createContext(contexts.next());
						Scan scan = scan(subj, pred, obj, ctx);
						if (scan == null) {
							return null;
						}
						rs = keyspaceConn.getScanner(scan);
					} else {
						return null;
					}
				}
				Result res = rs.next();
				if (ticker != null) {
					ticker.tick(); // sends a tick for keep alive purposes
				}
				if (res == null) { // no more results from this ResultScanner, close and clean up.
					rs.close();
					rs = null;
				} else {
					return res;
				}
			}
		}

		@Override
		protected void handleClose() throws IOException {
			super.handleClose();
			if (rs != null) {
				rs.close();
				rs = null;
			}
		}
	}

	@Override
	public final CloseableIteration<? extends Triple, QueryEvaluationException> getRdfStarTriples(Resource subj, IRI pred, Value obj) throws QueryEvaluationException {
		CloseableIteration<? extends Triple, QueryEvaluationException> iter = new ConvertingIteration<Statement, Triple, QueryEvaluationException>(
				new ExceptionConvertingIteration<Statement, QueryEvaluationException>(createStatementScanner(subj, pred, obj, Collections.singletonList(HALYARD.TRIPLE_GRAPH_CONTEXT))) {
				@Override
				protected QueryEvaluationException convert(Exception e) {
					return new QueryEvaluationException(e);
				}
			}) {

			@Override
			protected Triple convert(Statement stmt) {
				return vf.createTriple(stmt.getSubject(), stmt.getPredicate(), stmt.getObject());
			}
		};
		return timeLimit(iter, timeoutSecs);
	}

	private static <X, E extends Exception> CloseableIteration<X, E> timeLimit(CloseableIteration<X, E> iter, long timeoutSecs) {
		if (timeoutSecs > 0) {
			return new TimeLimitIteration<X, E>(iter, TimeUnit.SECONDS.toMillis(timeoutSecs)) {
				@Override
				protected void throwInterruptedException() {
					throw new QueryEvaluationException(String.format("Statements scanning exceeded specified timeout %ds", timeoutSecs));
				}
			};
		} else {
			return iter;
		}
	}
}
