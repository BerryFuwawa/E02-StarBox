package com.e02.rootconsole;

import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fresh challenge proof for the owned service; a backend's UID alone is insufficient. */
final class RootIdentity {
    final int uid,pid;
    final String generation;
    RootIdentity(int uid,int pid,String generation) { this.uid=uid;this.pid=pid;this.generation=generation; }
    static String challenge() {
        byte[] bytes=new byte[16];new SecureRandom().nextBytes(bytes);return hex(bytes);
    }
    static boolean generation(String value) { return "legacy".equals(value)||RootPermit.nonce(value); }
    static String proof(String secret,String challenge,int uid,int pid,String generation)throws GeneralSecurityException {
        if(!RootPermit.nonce(secret)||!RootPermit.nonce(challenge)||pid<=0||!generation(generation))throw new GeneralSecurityException("Invalid identity");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.US_ASCII),"HmacSHA256"));
        return hex(mac.doFinal(("E02-ROOT-IDENTITY-1|"+challenge+"|"+uid+"|"+pid+"|"+generation).getBytes(StandardCharsets.US_ASCII)));
    }
    static boolean verify(String secret,String challenge,int uid,int pid,String generation,String proof,String expected) {
        if(uid!=0||pid<=0||!generation(generation)||proof==null||!proof.matches("[0-9a-f]{64}")||expected!=null&&!expected.equals(generation))return false;
        try { return MessageDigest.isEqual(proof(secret,challenge,uid,pid,generation).getBytes(StandardCharsets.US_ASCII),proof.getBytes(StandardCharsets.US_ASCII)); }
        catch(GeneralSecurityException e) { return false; }
    }
    private static String hex(byte[] bytes) { StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString(); }
}
