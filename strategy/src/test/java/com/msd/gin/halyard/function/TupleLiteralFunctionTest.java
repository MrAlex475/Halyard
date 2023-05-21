package com.msd.gin.halyard.function;

import com.msd.gin.halyard.algebra.evaluation.EmptyTripleSource;
import com.msd.gin.halyard.common.TupleLiteral;

import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.TripleSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TupleLiteralFunctionTest {
	@Test
	public void test() {
		TripleSource ts = new EmptyTripleSource();
		ValueFactory vf = ts.getValueFactory();
		Value v1 = vf.createLiteral("foobar");
		Value v2 = vf.createIRI("http://foobar.org/");
		TupleLiteral l = (TupleLiteral) new TupleLiteralFunction().evaluate(ts, v1, v2);
		assertEquals(v1, l.objectValue()[0]);
		assertEquals(v2, l.objectValue()[1]);
	}
}