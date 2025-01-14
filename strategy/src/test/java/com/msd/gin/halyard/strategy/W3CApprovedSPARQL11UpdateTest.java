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
package com.msd.gin.halyard.strategy;

import java.util.Map;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.testsuite.query.parser.sparql.manifest.SPARQL11UpdateComplianceTest;
import org.eclipse.rdf4j.repository.contextaware.ContextAwareRepository;
import org.eclipse.rdf4j.repository.sail.SailRepository;

/**
 *
 * @author Adam Sotona (MSD)
 */
public class W3CApprovedSPARQL11UpdateTest extends SPARQL11UpdateComplianceTest {

    public W3CApprovedSPARQL11UpdateTest(String displayName, String testURI, String name, String requestFile,
			IRI defaultGraphURI, Map<String, IRI> inputNamedGraphs, IRI resultDefaultGraphURI,
			Map<String, IRI> resultNamedGraphs) {
    	super(displayName, testURI, name, requestFile, defaultGraphURI, inputNamedGraphs, resultDefaultGraphURI, resultNamedGraphs);
    }

    @Override
    protected ContextAwareRepository newRepository() throws Exception {
        SailRepository repo = new SailRepository(new MockSailWithHalyardStrategy());
        return new ContextAwareRepository(repo);
    }
}
