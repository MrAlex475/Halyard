package com.msd.gin.halyard.repository;

import com.msd.gin.halyard.common.HBaseServerTestInstance;
import com.msd.gin.halyard.repository.HBaseRepository;
import com.msd.gin.halyard.repository.HBaseRepositoryFactory;
import com.msd.gin.halyard.sail.HBaseSail;
import com.msd.gin.halyard.strategy.StrategyConfig;

import org.apache.hadoop.conf.Configuration;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.config.RepositoryConfigException;
import org.eclipse.rdf4j.repository.config.RepositoryImplConfig;
import org.eclipse.rdf4j.testsuite.sparql.RepositorySPARQLComplianceTestSuite;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class HBaseSPARQLComplianceTest extends RepositorySPARQLComplianceTestSuite {
	private static final int QUERY_TIMEOUT = 15;

	@BeforeClass
	public static void setUpFactory() throws Exception {
		Configuration conf = HBaseServerTestInstance.getInstanceConfig();
		conf.setInt(StrategyConfig.HALYARD_EVALUATION_THREADS, 5);
		setRepositoryFactory(new HBaseRepositoryFactory() {
			@Override
			public Repository getRepository(RepositoryImplConfig config) throws RepositoryConfigException {
				HBaseSail sail = new HBaseSail(conf, "complianceTestSuite", true, 0, true, QUERY_TIMEOUT, null, null);
				return new HBaseRepository(sail);
			}
		});
	}

	@AfterClass
	public static void tearDownFactory() throws Exception {
		setRepositoryFactory(null);
	}
}
