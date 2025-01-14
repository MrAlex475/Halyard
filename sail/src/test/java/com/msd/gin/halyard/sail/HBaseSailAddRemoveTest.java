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

import com.msd.gin.halyard.common.HBaseServerTestInstance;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.sail.SailConnection;
import org.eclipse.rdf4j.sail.SailException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

/**
 *
 * @author Adam Sotona (MSD)
 */
@RunWith(Parameterized.class)
public class HBaseSailAddRemoveTest {
	private static final int QUERY_TIMEOUT = 15;
    private static final Resource SUBJ = SimpleValueFactory.getInstance().createIRI("http://whatever/subject/");
    private static final IRI PRED = SimpleValueFactory.getInstance().createIRI("http://whatever/pred/");
    private static final Value OBJ = SimpleValueFactory.getInstance().createLiteral("whatever literal");
    private static final IRI CONTEXT = SimpleValueFactory.getInstance().createIRI("http://whatever/cont/");

    private static HBaseSail sail;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                 {null, null, null},
                 {SUBJ, null, null},
                 {null, PRED, null},
                 {null, null,  OBJ},
                 {SUBJ, PRED, null},
                 {null, PRED,  OBJ},
                 {SUBJ, null,  OBJ},
                 {SUBJ, PRED,  OBJ},
        });
    }

    @BeforeClass
    public static void setup() throws Exception {
		sail = new HBaseSail(HBaseServerTestInstance.getInstanceConfig(), "testAddRemove", true, 0, true, QUERY_TIMEOUT, null, null);
		sail.init();
    }

    @AfterClass
    public static void teardown() throws Exception {
        sail.shutDown();
    }

    private final Resource subj;
    private final IRI pred;
    private final Value obj;

    public HBaseSailAddRemoveTest(Resource subj, IRI pred, Value obj) {
        this.subj = subj;
        this.pred = pred;
        this.obj = obj;
    }

    @Test
    public void testAddAndRemoveExplicitStatements() throws Exception {
		try (SailConnection conn = sail.getConnection()) {
			conn.begin();
			conn.addStatement(SUBJ, PRED, OBJ);
			conn.addStatement(SUBJ, PRED, OBJ, CONTEXT);
			try (CloseableIteration<? extends Statement, SailException> iter = conn.getStatements(null, null, null, true)) {
				assertTrue(iter.hasNext());
				iter.next();
				assertTrue(iter.hasNext());
				Statement st = iter.next();
				assertEquals(SUBJ, st.getSubject());
				assertEquals(PRED, st.getPredicate());
				assertEquals(OBJ, st.getObject());
				assertFalse(iter.hasNext());
			}
			try (CloseableIteration<? extends Statement, SailException> iter = conn.getStatements(null, null, null, true, CONTEXT)) {
				assertTrue(iter.hasNext());
				Statement st = iter.next();
				assertEquals(SUBJ, st.getSubject());
				assertEquals(PRED, st.getPredicate());
				assertEquals(OBJ, st.getObject());
				assertEquals(CONTEXT, st.getContext());
				assertFalse(iter.hasNext());
			}
			conn.removeStatements(subj, pred, obj, CONTEXT);
			try (CloseableIteration<? extends Statement, SailException> iter = conn.getStatements(null, null, null, true)) {
				assertTrue(iter.hasNext());
			}
			conn.removeStatements(subj, pred, obj);
			try (CloseableIteration<? extends Statement, SailException> iter = conn.getStatements(null, null, null, true)) {
				assertFalse(iter.hasNext());
			}
			conn.commit();
		}
    }
}
