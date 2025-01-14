package com.msd.gin.halyard.repository;

import com.msd.gin.halyard.query.algebra.ExtendedQueryRoot;
import com.msd.gin.halyard.sail.HBaseSail;
import com.msd.gin.halyard.sail.HBaseSailConnection;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.Operation;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.TupleQueryResultHandlerException;
import org.eclipse.rdf4j.query.algebra.QueryRoot;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.impl.AbstractParserQuery;
import org.eclipse.rdf4j.query.impl.AbstractParserUpdate;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.ParsedUpdate;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailBooleanQuery;
import org.eclipse.rdf4j.repository.sail.SailGraphQuery;
import org.eclipse.rdf4j.repository.sail.SailQuery;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailTupleQuery;
import org.eclipse.rdf4j.sail.SailException;

public class HBaseRepositoryConnection extends SailRepositoryConnection {
	private final HBaseSail sail;

	protected HBaseRepositoryConnection(HBaseRepository repository, HBaseSailConnection sailConnection) {
		super(repository, sailConnection);
		this.sail = (HBaseSail) repository.getSail();
	}

	private void addImplicitBindings(Operation op) {
		ValueFactory vf = getValueFactory();
		String sourceString;
		if (op instanceof AbstractParserQuery) {
			sourceString = ((AbstractParserQuery) op).getParsedQuery().getSourceString();
		} else if (op instanceof AbstractParserUpdate) {
			sourceString = ((AbstractParserUpdate) op).getParsedUpdate().getSourceString();
		} else {
			sourceString = null;
		}
		if (sourceString != null) {
			op.setBinding(HBaseSailConnection.SOURCE_STRING_BINDING, vf.createLiteral(sourceString));
		}
	}

	private void swapRoot(ParsedQuery q) {
		TupleExpr root = q.getTupleExpr();
		if (root.getClass() == QueryRoot.class) {
			TupleExpr tree = ((QueryRoot) root).getArg();
			q.setTupleExpr(new ExtendedQueryRoot(tree));
		}
	}

	@Override
	public SailQuery prepareQuery(QueryLanguage ql, String queryString, String baseURI) throws MalformedQueryException {
		SailQuery query = super.prepareQuery(ql, queryString, baseURI);
		swapRoot(query.getParsedQuery());
		addImplicitBindings(query);
		return query;
	}

	@Override
	public SailTupleQuery prepareTupleQuery(QueryLanguage ql, String queryString, String baseURI) throws MalformedQueryException {
		Optional<TupleExpr> sailTupleExpr = getSailConnection().prepareQuery(ql, Query.QueryType.TUPLE, queryString, baseURI);

		ParsedTupleQuery parsedQuery = sailTupleExpr.map(expr -> new ParsedTupleQuery(queryString, expr)).orElse(QueryParserUtil.parseTupleQuery(ql, queryString, baseURI));
		swapRoot(parsedQuery);
		SailTupleQuery query = new SailTupleQuery(parsedQuery, this) {
			@Override
			public void evaluate(TupleQueryResultHandler handler) throws QueryEvaluationException, TupleQueryResultHandlerException {
				TupleExpr tupleExpr = getParsedQuery().getTupleExpr();
				try {
					HBaseSailConnection sailCon = (HBaseSailConnection) getConnection().getSailConnection();
					handler.startQueryResult(new ArrayList<>(tupleExpr.getBindingNames()));
					sailCon.evaluate(handler::handleSolution, tupleExpr, getActiveDataset(), getBindings(), getIncludeInferred());
					handler.endQueryResult();
				} catch (SailException e) {
					throw new QueryEvaluationException(e.getMessage(), e);
				}
			}
		};
		addImplicitBindings(query);
		return query;
	}

	@Override
	public SailGraphQuery prepareGraphQuery(QueryLanguage ql, String queryString, String baseURI) throws MalformedQueryException {
		SailGraphQuery query = super.prepareGraphQuery(ql, queryString, baseURI);
		swapRoot(query.getParsedQuery());
		addImplicitBindings(query);
		return query;
	}

	@Override
	public SailBooleanQuery prepareBooleanQuery(QueryLanguage ql, String queryString, String baseURI) throws MalformedQueryException {
		SailBooleanQuery query = super.prepareBooleanQuery(ql, queryString, baseURI);
		swapRoot(query.getParsedQuery());
		addImplicitBindings(query);
		return query;
	}

	@Override
	public HBaseUpdate prepareUpdate(String update) throws RepositoryException, MalformedQueryException {
		return prepareUpdate(QueryLanguage.SPARQL, update, null);
	}

	@Override
	public HBaseUpdate prepareUpdate(QueryLanguage ql, String updateQuery, String baseURI) throws RepositoryException, MalformedQueryException {
		ParsedUpdate parsedUpdate = QueryParserUtil.parseUpdate(ql, updateQuery, baseURI);

		HBaseUpdate update = new HBaseUpdate(parsedUpdate, sail, this);
		addImplicitBindings(update);
		return update;
	}
}
