package winsome.utils.cryptography;

import java.util.Objects;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import winsome.utils.cryptography.exceptions.FailedHashException;

/** A class representing the cryptographic hash of a string. */
public class Hash {
    /** The hashed string */
    public final String digest;

    /**
     * Creates a Hash from a hashed byte array.
     * @param bytes the hashed byte array
     */
    private Hash(byte[] bytes){
        this.digest = encodeHex(Objects.requireNonNull(bytes, "given digest is null"));
    }
    /**
     * Creates a Hash from the digest string.
     * @param digest the hashed string
     */
    private Hash(String digest){
        this.digest = Objects.requireNonNull(digest, "given digest is null");
    }

    /**
     * Hashes a plain text string into a Hash.
     * 
     * Taken from: https://stackoverflow.com/a/36163051
     * @param plain the given plain text
     * @return the hashed version of the given string
     */
    public static Hash fromPlainText(String plain){
        Objects.requireNonNull(plain, "plain string must not be null");

        // getting the algorithm
        MessageDigest md;
        try { md = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex){ 
            throw new FailedHashException("could not hash a string", ex);
        }

        md.update(
            plain.getBytes(StandardCharsets.UTF_8)
        );
        return new Hash(md.digest());
    }

    /**
     * Checks if this hash equals some plain string.
     * @param plain the given plain string
     * @return true if and only if this is the hash of plain
     */
    public boolean equalsPlain(String plain){
        Hash other = Hash.fromPlainText(plain);

        return this.digest.equals(other.digest);
    }

    /**
     * Checks if this is the hash of the empty string.
     * @return true if and only if this is the hash of the empty string
     */
    public boolean isEmpty(){ return this.equalsPlain(""); }

    /**
     * Encodes a byte array as a string, using hex values.
     * @param array the given byte array
     * @return a string encoding the given array
     * 
     * Taken from: https://stackoverflow.com/a/36163051
     */
    private String encodeHex(byte[] array){
        StringBuilder builder = new StringBuilder();
        for(byte b : array)
            builder.append(
                Integer.toString((b & 0xff) + 0x100, 16)
            ).substring(1);
        return builder.toString();
    }    
}