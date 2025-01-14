package com.msd.gin.halyard.strategy;

import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryImplConfig;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.config.SailRepositoryFactory;
import org.eclipse.rdf4j.sail.Sail;
import org.eclipse.rdf4j.testsuite.sparql.RepositorySPARQLComplianceTestSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class StarJoinComplianceTest extends RepositorySPARQLComplianceTestSuite {

	@BeforeClass
	public static void setUpFactory() throws Exception {
		setRepositoryFactory(new SailRepositoryFactory() {
			@Override
			public Repository getRepository(RepositoryImplConfig config) throws RepositoryConfigException {
				Sail sail = new MockSailWithHalyardStrategy(0, 0, Float.MAX_VALUE, 1, Integer.MAX_VALUE, 0);
				return new SailRepository(sail);
			}
		});
	}

	@AfterClass
	public static void tearDownFactory() throws Exception {
		setRepositoryFactory(null);
	}
}
