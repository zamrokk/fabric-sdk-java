/*
 *  Copyright 2016 DTCC, Fujitsu Australia Software Technology - All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 	  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.hyperledger.fabric.sdk.security;

import io.netty.util.internal.StringUtil;
import org.bouncycastle.asn1.sec.SECNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.util.Arrays;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.helper.SDKUtil;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;

public class CryptoPrimitives {

	private String hashAlgorithm;
	private int securityLevel;
	private String curveName;
	private static final String SECURITY_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;
	private static final String ASYMMETRIC_KEY_TYPE = "EC";
	private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
	private static final String SYMMETRIC_KEY_TYPE = "AES";
	private static final int SYMMETRIC_KEY_BYTE_COUNT = 32;
	private static final String SYMMETRIC_ALGORITHM = "AES/CFB/NoPadding";
	private static final int MAC_KEY_BYTE_COUNT = 32;

	public CryptoPrimitives(String hashAlgorithm, int securityLevel) {
		this.hashAlgorithm = hashAlgorithm;
		this.securityLevel = securityLevel;
		Security.addProvider(new BouncyCastleProvider());
		init();
	}

	public int getSecurityLevel() {
		return securityLevel;
	}

	public void setSecurityLevel(int securityLevel) {
		this.securityLevel = securityLevel;
	}

	public String getHashAlgorithm() {
		return this.hashAlgorithm;
	}

	public void setHashAlgorithm(String algorithm) {
		this.hashAlgorithm = algorithm;
	}

	public KeyPair ecdsaKeyGen() throws CryptoException {
		return generateKey("ECDSA", this.curveName);
	}

	private KeyPair generateKey(String encryptionName, String curveName) throws CryptoException {
		try {
			ECGenParameterSpec ecGenSpec = new ECGenParameterSpec(curveName);
			KeyPairGenerator g = KeyPairGenerator.getInstance(encryptionName, SECURITY_PROVIDER);
			g.initialize(ecGenSpec, new SecureRandom());
			KeyPair pair = g.generateKeyPair();
			return pair;
		} catch (Exception exp) {
			throw new CryptoException("Unable to generate key pair", exp);
		}
	}

	public byte[] eciesDecrypt(KeyPair keyPair, byte[] data) throws CryptoException {
		try {
			int ek_len = (int) (Math.floor((this.securityLevel + 7) / 8) * 2 + 1);
	        int mk_len = this.securityLevel >> 3;
	        int em_len = data.length - ek_len - mk_len;

			byte[] ephemeralPublicKeyBytes = Arrays.copyOfRange(data, 0, ek_len);
			byte[] encryptedMessage = Arrays.copyOfRange(data, ek_len, ek_len+em_len);
			byte[] tag = Arrays.copyOfRange(data, ek_len+em_len, data.length);

			// Parsing public key.
			ECParameterSpec asymmetricKeyParams = generateECParameterSpec();
			KeyFactory asymmetricKeyFactory = KeyFactory.getInstance(ASYMMETRIC_KEY_TYPE, SECURITY_PROVIDER);

			PublicKey ephemeralPublicKey = asymmetricKeyFactory.generatePublic(new ECPublicKeySpec(
					asymmetricKeyParams.getCurve().decodePoint(ephemeralPublicKeyBytes), asymmetricKeyParams));

			// Deriving shared secret.
			KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM, SECURITY_PROVIDER);
			keyAgreement.init(keyPair.getPrivate());
			keyAgreement.doPhase(ephemeralPublicKey, true);
			byte[] sharedSecret = keyAgreement.generateSecret();

			// Deriving encryption and mac keys.
			HKDFBytesGenerator hkdfBytesGenerator = new HKDFBytesGenerator(getHashDigest());

			hkdfBytesGenerator.init(new HKDFParameters(sharedSecret, null, null));
			byte[] encryptionKey = new byte[SYMMETRIC_KEY_BYTE_COUNT];
			hkdfBytesGenerator.generateBytes(encryptionKey, 0, SYMMETRIC_KEY_BYTE_COUNT);

			byte[] macKey = new byte[MAC_KEY_BYTE_COUNT];
			hkdfBytesGenerator.generateBytes(macKey, 0, MAC_KEY_BYTE_COUNT);

			// Verifying Message Authentication Code (aka mac/tag)
			byte[] expectedTag = calculateMac(macKey, encryptedMessage);
			if (!Arrays.areEqual(tag, expectedTag)) {
				throw new RuntimeException("Bad Message Authentication Code!");
			}

			// Decrypting the message.
			byte[] iv = Arrays.copyOfRange(encryptedMessage, 0, 16);
			byte[] encrypted = Arrays.copyOfRange(encryptedMessage, 16, encryptedMessage.length);
			byte[] output = aesDecrypt(encryptionKey, iv, encrypted);

			return output;

		} catch (Exception e) {
			throw new CryptoException("Could not decrypt the message", e);
		}

	}

	private byte[] calculateMac(byte[] macKey, byte[] encryptedMessage)
			throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException {
		HMac hmac = new HMac(getHashDigest());
		hmac.init(new KeyParameter(macKey));
		hmac.update(encryptedMessage, 0, encryptedMessage.length);
		byte[] out = new byte[MAC_KEY_BYTE_COUNT];
		hmac.doFinal(out, 0);
		return out;
	}

	private byte[] aesDecrypt(byte[] encryptionKey, byte[] iv, byte[] encryptedMessage)
			throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException,
			InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {

		Cipher cipher = Cipher.getInstance(SYMMETRIC_ALGORITHM);
		cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, SYMMETRIC_KEY_TYPE), new IvParameterSpec(iv));
		return cipher.doFinal(encryptedMessage);

	}

	private ECNamedCurveParameterSpec generateECParameterSpec() {
		ECNamedCurveParameterSpec bcParams = ECNamedCurveTable.getParameterSpec(this.curveName);
		return bcParams;
	}

	public byte[][] ecdsaSign(PrivateKey privateKey, byte[] data) throws CryptoException {
		try {
			byte[] encoded = SDKUtil.hash(data, getHashDigest());
			X9ECParameters params = SECNamedCurves.getByName(this.curveName);
			ECDomainParameters ecParams = new ECDomainParameters(params.getCurve(), params.getG(), params.getN(),
					params.getH());

			ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA512Digest()));
			ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(((ECPrivateKey) privateKey).getS(), ecParams);
			signer.init(true, privKey);
			BigInteger[] sigs = signer.generateSignature(encoded);
			return new byte[][]{sigs[0].toString().getBytes(), sigs[1].toString().getBytes()};
		} catch (Exception e) {
			throw new CryptoException("Could not sign the message using private key", e);
		}

	}
	
	public PrivateKey ecdsaKeyFromPrivate(byte[] key) throws CryptoException {
		try {
			EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(key);
			KeyFactory generator = KeyFactory.getInstance("ECDSA", SECURITY_PROVIDER);
			PrivateKey privateKey = generator.generatePrivate(privateKeySpec);

			return privateKey;
		} catch (Exception exp) {
			throw new CryptoException("Unable to convert byte[] into PrivateKey", exp);
		}
	}

	private void init() {
		if (securityLevel != 256 && securityLevel != 384) {
			throw new RuntimeException("Illegal level: " + securityLevel + " must be either 256 or 384");
		}
		if (StringUtil.isNullOrEmpty(this.hashAlgorithm)
				|| !(this.hashAlgorithm.equalsIgnoreCase("SHA2") || this.hashAlgorithm.equalsIgnoreCase("SHA3"))) {
			throw new RuntimeException(
					"Illegal Hash function family: " + this.hashAlgorithm + " - must be either SHA2 or SHA3");
		}

		// this.suite = this.algorithm.toLowerCase() + '-' + this.securityLevel;
		if (this.securityLevel == 256) {
			this.curveName = "secp256r1";
			//TODO: HashOutputSize=32 ?
		} else if (this.securityLevel == 384) {
			this.curveName = "secp384r1";
			//TODO: HashOutputSize=48 ?
		}
	}

	private Digest getHashDigest() {
		if (this.hashAlgorithm.equalsIgnoreCase("SHA3")) {
			return new SHA3Digest();
		} else if (this.hashAlgorithm.equalsIgnoreCase("SHA2")) {
			return new SHA256Digest();
		}

		return new SHA256Digest(); // default Digest?
	}
}
