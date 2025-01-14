package com.msd.gin.halyard.common;

import com.msd.gin.halyard.common.Hashes.HashFunction;

import org.apache.hadoop.hbase.util.Bytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HashesTest {
	@Test
	public void testHash16() {
		assertEquals(0x0000, Hashes.hash16(new byte[] {0}));
		assertEquals((short)0xAF96, Hashes.hash16(Bytes.toBytes("foo")));
		assertEquals((short)0x93C5, Hashes.hash16(Bytes.toBytes("bar")));
		assertEquals((short)0xB025, Hashes.hash16(Bytes.toBytes("foobar")));
	}

	@Test
	public void testMurmur3() {
		HashFunction hf = Hashes.getHash("Murmur3-128", 0);
		byte[] h128 = hf.apply(Bytes.toBytes("foobar"));
		assertEquals(128/Byte.SIZE, h128.length);

		hf = Hashes.getHash("Murmur3-128", 8);
		byte[] h64 = hf.apply(Bytes.toBytes("foobar"));
		assertEquals(8, h64.length);
	}

    @Test
    public void testInvalidHadh() {
    	assertThrows(RuntimeException.class, () ->
        	Hashes.getHash("invalid", 0)
        );
    }

    @Test
    public void testEncode() {
        assertEquals("AQIDBAU", ByteUtils.encode(new byte[]{1, 2, 3, 4, 5}));
    }
}
