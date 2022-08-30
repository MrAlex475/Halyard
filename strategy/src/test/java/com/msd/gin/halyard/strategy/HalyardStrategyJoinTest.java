package com.msd.gin.halyard.strategy;

import com.msd.gin.halyard.algebra.HashJoin;
import com.msd.gin.halyard.algebra.NestedLoops;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.algebra.Join;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;
import org.eclipse.rdf4j.query.resultio.QueryResultIO;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultFormat;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class HalyardStrategyJoinTest {

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		List<Object[]> testValues = new ArrayList<>();
		testValues.add(new Object[] {0});
		testValues.add(new Object[] {Integer.MAX_VALUE});
		return testValues;
	}

    private final int hashJoinLimit;
    private Repository repo;
    private RepositoryConnection con;
    private MemoryStoreWithHalyardStrategy strategy;

    public HalyardStrategyJoinTest(int hashJoinLimit) {
       this.hashJoinLimit = hashJoinLimit;
    }

    @Before
    public void setUp() throws Exception {
    	strategy = new MemoryStoreWithHalyardStrategy(hashJoinLimit);
        repo = new SailRepository(strategy);
        repo.init();
        con = repo.getConnection();
    }

    @After
    public void tearDown() throws Exception {
        con.close();
        repo.shutDown();
    }

    private String expectedAlgo() {
    	if (hashJoinLimit == 0) {
    		return NestedLoops.NAME;
    	} else {
    		return HashJoin.NAME;
    	}
    }

    @Test
    public void testJoin_1var() throws Exception {
        String q ="prefix : <http://example/> select ?s ?t where {?s :r/:s ?t}";
        joinTest(q, "/test-cases/join-results-1.srx", 1, expectedAlgo());
    }

    @Test
    public void testJoin_2var() throws Exception {
        // star join
        String q ="prefix : <http://example/> select ?x ?y where {?x :p ?y. ?x :t ?y}";
        joinTest(q, "/test-cases/join-results-2.srx", 0, null);
    }

    @Test
    public void testJoin_0var() throws Exception {
        String q ="prefix : <http://example/> select * where {?s :r ?t. ?x :s ?y}";
        joinTest(q, "/test-cases/join-results-0.srx", 1, expectedAlgo());
    }

    @Test
    public void testJoin_empty_0var() throws Exception {
        String q ="prefix : <http://example/> select * where {:x1 :q \"a\". ?x :p ?y}";
        joinTest(q, "/test-cases/join-results-empty-0.srx", 1, NestedLoops.NAME);
    }

    private void joinTest(String q, String expectedOutput, int expectedJoins, String expectedAlgo) throws Exception {
        con.add(getClass().getResource("/test-cases/join-data.ttl"));
        Set<BindingSet> results;
        try (TupleQueryResult res = con.prepareTupleQuery(q).evaluate()) {
            results = toSet(res);
        }
        Set<BindingSet> expectedResults;
        try (InputStream in = getClass().getResourceAsStream(expectedOutput)) {
            try (TupleQueryResult res = QueryResultIO.parseTuple(in, TupleQueryResultFormat.SPARQL)) {
                expectedResults = toSet(res);
            }
        }
        assertEquals(expectedResults, results);
        List<String> joinAlgos = new ArrayList<>();
        TupleExpr expr = strategy.getQueryHistory().getLast();
        expr.visit(new AbstractQueryModelVisitor<RuntimeException>() {
			@Override
			public void meet(Join node) throws RuntimeException {
				joinAlgos.add(node.getAlgorithmName());
				super.meet(node);
			}
        });
        assertEquals(expectedJoins, joinAlgos.size());
        for (String algo : joinAlgos) {
        	assertEquals(expr.toString(), expectedAlgo, algo);
        }
    }

    private static Set<BindingSet> toSet(TupleQueryResult res) {
        Set<BindingSet> results = new HashSet<>();
        while (res.hasNext()) {
            BindingSet bs = res.next();
            results.add(bs);
        }
        return results;
    }
}
