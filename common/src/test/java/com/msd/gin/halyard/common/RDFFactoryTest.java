package com.msd.gin.halyard.common;

import com.msd.gin.halyard.vocab.HALYARD;
import com.msd.gin.halyard.vocab.WIKIDATA;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.eclipse.rdf4j.model.BNode;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.GEO;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class RDFFactoryTest {
    private static final StatementIndices stmtIndices = StatementIndices.create();
	private static final RDFFactory rdfFactory = stmtIndices.getRDFFactory();

	private static String longString(String s) {
		String[] copies = new String[1000/s.length()+1];
		Arrays.fill(copies, s);
		return String.join(" ", copies);
	}

	static List<Object[]> createData(ValueFactory vf) {
		return Arrays.asList(
			new Object[] {RDF.TYPE, ValueIO.IRI_HASH_TYPE},
			new Object[] {vf.createLiteral("foo"), ValueIO.UNCOMPRESSED_STRING_TYPE},
			new Object[] {vf.createBNode("__foobar__"), ValueIO.BNODE_TYPE},
			new Object[] {vf.createIRI("test:/foo"), ValueIO.IRI_TYPE},
			new Object[] {vf.createIRI("http://www.testmyiri.com"), ValueIO.COMPRESSED_IRI_TYPE},
			new Object[] {vf.createIRI("https://www.testmyiri.com"), ValueIO.COMPRESSED_IRI_TYPE},
			new Object[] {vf.createIRI("http://dx.doi.org/", "blah"), ValueIO.COMPRESSED_IRI_TYPE},
			new Object[] {vf.createIRI("https://dx.doi.org/", "blah"), ValueIO.COMPRESSED_IRI_TYPE},
			new Object[] {vf.createLiteral("5423"), ValueIO.UNCOMPRESSED_STRING_TYPE},
			new Object[] {vf.createLiteral("\u98DF"), ValueIO.UNCOMPRESSED_STRING_TYPE},
			new Object[] {vf.createLiteral(true), ValueIO.TRUE_TYPE},
			new Object[] {vf.createLiteral(false), ValueIO.FALSE_TYPE},
			new Object[] {vf.createLiteral((byte) 6), ValueIO.BYTE_TYPE},
			new Object[] {vf.createLiteral((short) 7843), ValueIO.SHORT_TYPE},
			new Object[] {vf.createLiteral(34), ValueIO.INT_TYPE},
			new Object[] {vf.createLiteral(87.232), ValueIO.DOUBLE_TYPE},
			new Object[] {vf.createLiteral(74234l), ValueIO.LONG_TYPE},
			new Object[] {vf.createLiteral(4.809f), ValueIO.FLOAT_TYPE},
			new Object[] {vf.createLiteral(BigInteger.valueOf(96)), ValueIO.SHORT_COMPRESSED_BIG_INT_TYPE},
			new Object[] {vf.createLiteral(BigInteger.valueOf(Integer.MIN_VALUE)), ValueIO.INT_COMPRESSED_BIG_INT_TYPE},
			new Object[] {vf.createLiteral(String.valueOf(Long.MAX_VALUE)+String.valueOf(Long.MAX_VALUE), XSD.INTEGER), ValueIO.BIG_INT_TYPE},
			new Object[] {vf.createLiteral(BigDecimal.valueOf(856.03)), ValueIO.BIG_FLOAT_TYPE},
			new Object[] {vf.createLiteral("z", XSD.INT), ValueIO.DATATYPE_LITERAL_TYPE},
			new Object[] {vf.createIRI(RDF.NAMESPACE), ValueIO.NAMESPACE_HASH_TYPE},
			new Object[] {vf.createLiteral("xyz", vf.createIRI(RDF.NAMESPACE)), ValueIO.DATATYPE_LITERAL_TYPE},
			new Object[] {vf.createLiteral(new Date(946684800000l)), ValueIO.DATETIME_TYPE},  // "2000-01-01T00:00:00Z"^^xsd:dateTime
			new Object[] {vf.createLiteral(LocalDateTime.of(1990, 6, 20, 0, 0, 0, 20005000)), ValueIO.DATETIME_TYPE},
			new Object[] {vf.createLiteral("13:03:22", XSD.TIME), ValueIO.TIME_TYPE},
			new Object[] {vf.createLiteral(LocalTime.of(13, 3, 22, 40030000)), ValueIO.TIME_TYPE},
			new Object[] {vf.createLiteral("1980-02-14", XSD.DATE), ValueIO.DATE_TYPE},
			new Object[] {vf.createLiteral("2022-09-09+03:00", XSD.DATE), ValueIO.DATE_TYPE},
			new Object[] {vf.createLiteral("foo", vf.createIRI("urn:bar:1")), ValueIO.DATATYPE_LITERAL_TYPE},
			new Object[] {vf.createLiteral("foo", "en-GB"), ValueIO.LANGUAGE_HASH_LITERAL_TYPE},
			new Object[] {vf.createLiteral("bar", "zx-XY"), ValueIO.LANGUAGE_LITERAL_TYPE},
			new Object[] {vf.createLiteral("漫画", "ja"), ValueIO.LANGUAGE_HASH_LITERAL_TYPE},
			new Object[] {vf.createLiteral("POINT (139.81 35.6972)", GEO.WKT_LITERAL), ValueIO.WKT_LITERAL_TYPE},
			new Object[] {vf.createLiteral("invalid still works (139.81 35.6972)", GEO.WKT_LITERAL), ValueIO.WKT_LITERAL_TYPE},
			new Object[] {vf.createLiteral("<?xml version=\"1.0\" encoding=\"UTF-8\"?><test attr=\"foo\">bar</test>", RDF.XMLLITERAL), ValueIO.XML_TYPE},
			new Object[] {vf.createLiteral("<invalid xml still works", RDF.XMLLITERAL), ValueIO.XML_TYPE},
			new Object[] {vf.createLiteral("0000-06-20T00:00:00Z", XSD.DATETIME), ValueIO.DATATYPE_LITERAL_TYPE},
			new Object[] {vf.createLiteral(longString("The cat slept on the mat.")), ValueIO.COMPRESSED_STRING_TYPE},
			new Object[] {vf.createLiteral(longString("¿Dónde está el gato?"), "es"), ValueIO.LANGUAGE_HASH_LITERAL_TYPE},
			new Object[] {vf.createIRI(HALYARD.VALUE_ID_NS.getName(), "eRg5UlsxjZuh-4meqlYQe3-J8X8"), ValueIO.ENCODED_IRI_TYPE},
			new Object[] {vf.createIRI(WIKIDATA.WDV_NAMESPACE, "400f9abd3fd761c62af23dbe8f8432158a6ce272"), ValueIO.ENCODED_IRI_TYPE},
			new Object[] {vf.createIRI(WIKIDATA.WDV_NAMESPACE, "invalid"), ValueIO.NAMESPACE_HASH_TYPE},
			new Object[] {vf.createIRI(WIKIDATA.WDV_NAMESPACE+"400f9abd3fd761c62af23dbe8f8432158a6ce272/"), ValueIO.END_SLASH_ENCODED_IRI_TYPE}
		);
	}

	@Parameterized.Parameters(name = "{0}")
	public static Collection<Object[]> data() {
		List<Object[]> testValues = new ArrayList<>();
		testValues.addAll(createData(SimpleValueFactory.getInstance()));
		testValues.addAll(createData(new IdValueFactory(rdfFactory)));
		return testValues;
	}

	private Value expectedValue;
	private byte expectedEncodingType;

	public RDFFactoryTest(Value v, byte encodingType) {
		this.expectedValue = v;
		this.expectedEncodingType = encodingType;
	}

	@Test
	public void testToAndFromBytesNoBuffer() {
		testToAndFromBytes(0);
	}

	@Test
	public void testToAndFromBytesBigBuffer() {
		testToAndFromBytes(10240);
	}

	private void testToAndFromBytes(int bufferSize) {
        ValueIO.Writer writer = rdfFactory.valueWriter;
        ValueIO.Reader reader = rdfFactory.valueReader;
        ValueFactory vf = new IdValueFactory(rdfFactory);

        ByteBuffer buf = ByteBuffer.allocate(bufferSize);
		buf = writer.writeTo(expectedValue, buf);
		buf.flip();
		byte actualEncodingType = buf.get(0);
		assertEquals("Incorrect encoding type for "+expectedValue.toString(), expectedEncodingType, actualEncodingType);
		int size = buf.limit();
		Value actual = reader.readValue(buf, vf);
		assertEquals(expectedValue, actual);
		assertEquals(actual, expectedValue);
		assertEquals(expectedValue.hashCode(), actual.hashCode());

		// check readValue() works on a subsequence
		int beforeLen = 3;
		int afterLen = 7;
		ByteBuffer extbuf = ByteBuffer.allocate(beforeLen + size + afterLen);
		// place b somewhere in the middle
		extbuf.position(beforeLen);
		extbuf.mark();
		buf.flip();
		extbuf.put(buf);
		extbuf.limit(extbuf.position());
		extbuf.reset();
		actual = reader.readValue(extbuf, vf);
		assertEquals("Buffer position", beforeLen + size, extbuf.position());
		assertEquals("Buffer state", extbuf.limit(), extbuf.position());
		assertEquals(expectedValue, actual);
	}

	@Test
	public void testRDFValue() {
		ValueIdentifier id = rdfFactory.id(expectedValue);
		if (expectedValue instanceof IdentifiableValue) {
			assertEquals(id, ((IdentifiableValue)expectedValue).getId(rdfFactory));
		}

		ValueIdentifier.Format idFormat = rdfFactory.idFormat;
		assertEquals("isIRI", expectedValue.isIRI(), id.isIRI(idFormat));
		assertEquals("isLiteral", expectedValue.isLiteral(), id.isLiteral(idFormat));
		assertEquals("isBNode", expectedValue.isBNode(), id.isBNode(idFormat));
		assertEquals("isTriple", expectedValue.isTriple(), id.isTriple(idFormat));

		if (expectedValue instanceof Literal) {
			IRI dt = ((Literal)expectedValue).getDatatype();
			assertEquals("isString", XSD.STRING.equals(dt) || RDF.LANGSTRING.equals(dt), id.isString(idFormat));
			RDFObject obj = rdfFactory.createObject(expectedValue);
			assertRDFValueHashes(id, obj);
		} else if (expectedValue instanceof IRI) {
			RDFObject obj = rdfFactory.createObject(expectedValue);
			assertRDFValueHashes(id, obj);
			RDFSubject subj = rdfFactory.createSubject((IRI) expectedValue);
			assertRDFValueHashes(id, subj);
			RDFContext ctx = rdfFactory.createContext((IRI) expectedValue);
			assertRDFValueHashes(id, ctx);
			RDFPredicate pred = rdfFactory.createPredicate((IRI) expectedValue);
			assertRDFValueHashes(id, pred);
		} else if (expectedValue instanceof BNode) {
			RDFObject obj = rdfFactory.createObject(expectedValue);
			assertRDFValueHashes(id, obj);
			RDFSubject subj = rdfFactory.createSubject((Resource) expectedValue);
			assertRDFValueHashes(id, subj);
			RDFContext ctx = rdfFactory.createContext((Resource) expectedValue);
			assertRDFValueHashes(id, ctx);
		} else {
			throw new AssertionError();
		}
	}

	private static void assertRDFValueHashes(ValueIdentifier id, RDFValue<?,?> v) {
        StatementIndex<SPOC.S,SPOC.P,SPOC.O,SPOC.C> spo = stmtIndices.getSPOIndex();
        StatementIndex<SPOC.P,SPOC.O,SPOC.S,SPOC.C> pos = stmtIndices.getPOSIndex();
        StatementIndex<SPOC.O,SPOC.S,SPOC.P,SPOC.C> osp = stmtIndices.getOSPIndex();
        StatementIndex<SPOC.C,SPOC.S,SPOC.P,SPOC.O> cspo = stmtIndices.getCSPOIndex();
        StatementIndex<SPOC.C,SPOC.P,SPOC.O,SPOC.S> cpos = stmtIndices.getCPOSIndex();
        StatementIndex<SPOC.C,SPOC.O,SPOC.S,SPOC.P> cosp = stmtIndices.getCOSPIndex();
        RDFFactory rdfFactory = stmtIndices.getRDFFactory();
		ValueIdentifier.Format idFormat = rdfFactory.idFormat;
		for(StatementIndex<?,?,?,?> idx : new StatementIndex[] {spo, pos, osp, cspo, cpos, cosp}) {
			String testName = v.toString() + " for " + idx.toString();
			RDFRole<?> role = idx.getRole(v.getRoleName());
			byte[] keyHash = role.keyHash(v.getId(), idFormat);
			int keyHashSize = role.keyHashSize();
			assertEquals(testName, keyHashSize, keyHash.length);

			byte[] idxIdBytes = new byte[idFormat.size];
			idFormat.unrotate(keyHash, 0, keyHashSize, role.getByteShift(), idxIdBytes);
			role.writeQualifierHashTo(v.getId(), ByteBuffer.wrap(idxIdBytes, keyHashSize, idxIdBytes.length-keyHashSize));
			assertEquals(testName, id, rdfFactory.id(idxIdBytes));
		}
	}
}
