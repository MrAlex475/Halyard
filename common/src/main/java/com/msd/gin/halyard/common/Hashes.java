package com.msd.gin.halyard.common;

import com.google.common.hash.Hashing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Function;

public final class Hashes {
	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	static MessageDigest getMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

	public static HashFunction getHash(String algorithm, int size) {
		switch (algorithm) {
			case "FarmHash-64":
				return new GuavaHashFunction(Hashing.farmHashFingerprint64(), size);
			case "Murmur3-128":
				return new GuavaHashFunction(Hashing.murmur3_128(), size);
			default:
				return new MessageDigestHashFunction(getMessageDigest(algorithm), size);
		}
	}

	private static final byte[] PEARSON_HASH_TABLE = {
		// 0-255 shuffled in any (random) order suffices
		39,(byte)158,(byte)178,(byte)187,(byte)131,(byte)136,1,49,50,17,(byte)141,91,47,(byte)129,60,99,
		(byte)237,18,(byte)253,(byte)225,8,(byte)208,(byte)172,(byte)244,(byte)255,126,101,79,(byte)145,(byte)235,(byte)228,121,
		123,(byte)251,67,(byte)250,(byte)161,0,107,97,(byte)241,111,(byte)181,82,(byte)249,33,69,55,
		(byte)197,96,(byte)210,45,16,(byte)227,(byte)248,(byte)202,51,(byte)152,(byte)252,125,81,(byte)206,(byte)215,(byte)186,
		90,(byte)168,(byte)156,(byte)203,(byte)177,120,2,(byte)190,(byte)188,7,100,(byte)185,(byte)174,(byte)243,(byte)162,10,
		(byte)154,35,86,(byte)171,105,34,38,(byte)200,(byte)147,58,77,118,(byte)173,(byte)246, 76,(byte)254,
		3,14,(byte)204,72,21,41,56,66,28,(byte)193,40,(byte)217,25,54,(byte)179,117,
		(byte)189,(byte)205,(byte)199,(byte)128,(byte)176,19,(byte)211,(byte)236,127,(byte)192,(byte)231,70,(byte)233,88,(byte)146,44,
		98,6,85,(byte)150,36,23,112,(byte)164,(byte)135,(byte)207,(byte)169,5,26,64,(byte)165,(byte)219,
		(byte)183,(byte)201,22,83,13,(byte)214,116,109,(byte)159,32,95,(byte)226,(byte)140,(byte)220, 57, 12,
		59,(byte)153,29,9,(byte)213,(byte)167,84,93,30,46,94,75,(byte)151,114,73,(byte)222,
		(byte)238,87,(byte)240,(byte)155,(byte)180,(byte)170,(byte)242,(byte)212,(byte)191,(byte)163,78,(byte)218,(byte)137,(byte)194,(byte)175,110,
		61,20,68,89,(byte)130,63,52,102,24,(byte)229,(byte)132,(byte)245,80,(byte)216,(byte)195,115,
		(byte)133,(byte)232,(byte)196,(byte)144,(byte)198,124,53,4,108,74,(byte)223,(byte)234,(byte)134,(byte)230,(byte)157,(byte)139,
		43,119,(byte)224,71,122,(byte)142,42,(byte)160,104,48,(byte)247,103,15,11,(byte)138,(byte)239,
		(byte)221, 31,(byte)209,(byte)182,(byte)143,92,(byte)149,(byte)184,(byte)148,62,113,65,37,27,106,(byte)166
	};

	static short hash16(byte[] key) {
		byte h1 = PEARSON_HASH_TABLE[(key[0] & 0xFF) % 256];
		byte h2 = PEARSON_HASH_TABLE[(key[key.length-1] & 0xFF) % 256];
		for(int j = 1; j < key.length; j++) {
			h1 = PEARSON_HASH_TABLE[(h1 & 0xFF) ^ (key[j] & 0xFF)];
			h2 = PEARSON_HASH_TABLE[(h2 & 0xFF) ^ (key[key.length - 1 - j] & 0xFF)];
		}
		return (short) (((h1 & 0xFF) << 8) | (h2 & 0xFF));
    }

    static int hash32(byte[] key) {
    	return Hashing.murmur3_32().hashBytes(key).asInt();
    }

    public static String encode(byte b[]) {
        return ENCODER.encodeToString(b);
    }

    public static byte[] decode(String s) {
    	return DECODER.decode(s);
    }

    /**
	 * NB: this alters the buffer.
	 */
	static CharSequence encode(ByteBuffer b) {
		return StandardCharsets.UTF_8.decode(ENCODER.encode(b));
	}


	public static abstract class HashFunction implements Function<ByteBuffer,byte[]> {
		final int size;

		static int hashSize(int size, int defaultSize) {
			return (size > 0) ? size : defaultSize;
		}

		HashFunction(int size) {
			this.size = size;
		}

		public final int size() {
			return size;
		}

		@Override
		public final byte[] apply(ByteBuffer bb) {
			byte[] hash = calculateHash(bb);
			return (size != hash.length) ? Arrays.copyOf(hash, size) : hash;
		}

		protected abstract byte[] calculateHash(ByteBuffer bb);
	}

	static final class MessageDigestHashFunction extends HashFunction {
		final MessageDigest md;

		MessageDigestHashFunction(MessageDigest md, int size) {
			super(hashSize(size, md.getDigestLength()));
			this.md = md;
		}

		@Override
		protected byte[] calculateHash(ByteBuffer bb) {
			try {
				md.update(bb);
				return md.digest();
			} finally {
				md.reset();
			}
		}
	}

	static final class GuavaHashFunction extends HashFunction {
		final com.google.common.hash.HashFunction hf;

		GuavaHashFunction(com.google.common.hash.HashFunction hf, int size) {
			super(hashSize(size, hf.bits()/Byte.SIZE));
			this.hf = hf;
		}

		@Override
		protected byte[] calculateHash(ByteBuffer bb) {
			// backwards compatible with old Guava
			byte[] b = new byte[bb.remaining()];
			bb.get(b);
			return hf.hashBytes(b).asBytes();
		}
	}
}
