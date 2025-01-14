package com.msd.gin.halyard.sail;

import com.msd.gin.halyard.common.HBaseServerTestInstance;

import org.eclipse.rdf4j.testsuite.repository.RDFStarSupportTest;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.sail.Sail;

public class HBaseRDFStarTest extends RDFStarSupportTest {
	private static final int QUERY_TIMEOUT = 15;

	@Override
	protected Repository createRepository() {
		Sail sail;
		try {
			sail = new HBaseSail(HBaseServerTestInstance.getInstanceConfig(), "rdfstartable", true, 0, true, QUERY_TIMEOUT, null, null);
		} catch (Exception e) {
			throw new AssertionError(e);
		}
		Repository repo = new SailRepository(sail);
		repo.init();
		return repo;
	}

}
