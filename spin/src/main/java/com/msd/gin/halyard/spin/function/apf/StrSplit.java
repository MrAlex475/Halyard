/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package com.msd.gin.halyard.spin.function.apf;

import com.msd.gin.halyard.spin.function.spif.Split;

import org.eclipse.rdf4j.model.vocabulary.APF;

public class StrSplit extends Split {

	@Override
	public String getURI() {
		return APF.STR_SPLIT.toString();
	}
}
