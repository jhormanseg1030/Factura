package com.prueba.factura.Services;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class ClaveAcceso {

    private static final int ITERACIONES = 65536;
    private static final int BITS = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private ClaveAcceso() {
    }

    public static String hash(String clave) {
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(clave, sal);
        return Base64.getEncoder().encodeToString(sal) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    public static boolean coincide(String clave, String almacenada) {
        if (clave == null || almacenada == null || !almacenada.contains(":")) {
            return false;
        }
        String[] partes = almacenada.split(":", 2);
        try {
            byte[] sal = Base64.getDecoder().decode(partes[0]);
            byte[] esperado = Base64.getDecoder().decode(partes[1]);
            byte[] actual = derivar(clave, sal);
            return MessageDigest.isEqual(actual, esperado);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(String clave, byte[] sal) {
        try {
            PBEKeySpec spec = new PBEKeySpec(clave.toCharArray(), sal, ITERACIONES, BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("No se pudo proteger la contraseña", e);
        }
    }
}
