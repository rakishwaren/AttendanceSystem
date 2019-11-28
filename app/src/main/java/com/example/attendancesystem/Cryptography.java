package com.example.attendancesystem;

import androidx.annotation.NonNull;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

import javax.crypto.*;


public class Cryptography {

    private final int KEY_SIZE = 1024;

    private RSAKey keys;
    private KeyPairGenerator key_generator;
    private KeyPair key_pairs;
    private byte[] encrypted_bytes, decrypted_bytes;
    private Cipher cipher_01, cipher_02;
    private String encrypted, decrypted;

    public RSAKey GenerateKeyPairs() throws NoSuchAlgorithmException{
        key_generator = KeyPairGenerator.getInstance("RSA");
        key_generator.initialize(KEY_SIZE);
        key_pairs = key_generator.genKeyPair();

        RSAKey key = new RSAKey();
        key.private_key = key_pairs.getPrivate();
        key.public_key = key_pairs.getPublic();

        return key;
    }

    @NonNull
    public String EncryptRSA(String plain, RSAKey key_pair) throws NullPointerException, InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, IllegalBlockSizeException {
        if(plain == null || key_pair == null || key_pair.public_key == null || key_pair.private_key == null)
            throw new NullPointerException("Missing field data, please check.");

        cipher_01 = Cipher.getInstance("RSA");
        cipher_01.init(Cipher.ENCRYPT_MODE, key_pair.public_key);
        encrypted_bytes = cipher_01.doFinal(plain.getBytes());

        encrypted = this.bytesToString(encrypted_bytes);

        return encrypted;
    }

    @NonNull
    public String DecryptRSA(String cipher_txt, RSAKey key_pair) throws NullPointerException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        if(cipher_txt == null || key_pair == null || key_pair.public_key == null || key_pair.private_key == null)
            throw new NullPointerException("Missing field data, please check.");

        cipher_02 = Cipher.getInstance("RSA");
        cipher_02.init(Cipher.DECRYPT_MODE, key_pair.private_key);
        decrypted_bytes = cipher_02.doFinal(this.stringToBytes(cipher_txt));
        decrypted = new String(decrypted_bytes);

        return decrypted;
    }

    public class RSAKey{
        public PublicKey public_key;
        public PrivateKey private_key;
    }

    private String bytesToString(byte[] b){
        byte[] b2 = new byte[b.length + 1];
        b2[0] = 1;
        System.arraycopy(b, 0, b2, 1, b.length);

        return new BigInteger(b2).toString(36);
    }

    private byte[] stringToBytes(String s){
        byte[] b2 = new BigInteger(s, 36).toByteArray();

        return Arrays.copyOfRange(b2, 1, b2.length);
    }
}

