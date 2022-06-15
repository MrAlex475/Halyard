package com.msd.gin.halyard.sail.connection;

import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.parser.ParsedBooleanQuery;
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.ParsedUpdate;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class SailConnectionQueryTest {
	private Sail sail;
	private SailConnection conn;
	private SailConnectionQueryPreparer qp;

	@Before
	public void setup() {
		sail = new MemoryStore();
		sail.init();
		conn = sail.getConnection();
		conn.begin();
		conn.addStatement(sail.getValueFactory().createBNode(), RDF.TYPE, RDF.LIST);
		conn.commit();
		qp = new SailConnectionQueryPreparer(conn, true, sail.getValueFactory());
	}

	@After
	public void tearDown() {
		conn.close();
		sail.shutDown();
	}

	@Test
	public void testBooleanQuery() {
		ParsedBooleanQuery q = QueryParserUtil.parseBooleanQuery(QueryLanguage.SPARQL, "ask {?s ?p ?o}", null);
		BooleanQuery sq = qp.prepare(q);
		assertTrue(sq.evaluate());
	}

	@Test
	public void testTupleQuery() {
		ParsedTupleQuery q = QueryParserUtil.parseTupleQuery(QueryLanguage.SPARQL, "select * {?s ?p ?o}", null);
		TupleQuery sq = qp.prepare(q);
		assertTrue(sq.evaluate().hasNext());
	}

	@Test
	public void testGraphQuery() {
		ParsedGraphQuery q = QueryParserUtil.parseGraphQuery(QueryLanguage.SPARQL, "construct {?s ?p ?o} where {?s ?p ?o}", null);
		GraphQuery sq = qp.prepare(q);
		assertTrue(sq.evaluate().hasNext());
	}

	@Test
	public void testUpdateQuery() {
		ParsedUpdate q = QueryParserUtil.parseUpdate(QueryLanguage.SPARQL, "delete where {?s ?p ?o}", null);
		Update sq = qp.prepare(q);
		sq.execute();
	}
}
