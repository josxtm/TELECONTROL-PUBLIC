package es.josxtm.telecontrol.sync;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Recoverable exchange-file encryption, and a separate device-only Android Keystore wrapping key. */
public final class SyncCrypto {
    private static final String ALIAS="telecontrol-drive-wrap-v1";
    private static final SecureRandom RANDOM=new SecureRandom();
    private SyncCrypto() { }
    public static byte[] random(int count){byte[] bytes=new byte[count];RANDOM.nextBytes(bytes);return bytes;}
    public static String b64(byte[] data){return Base64.encodeToString(data,Base64.NO_WRAP);}
    public static byte[] un64(String value){return Base64.decode(value,Base64.NO_WRAP);}
    public static String hash(String value)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    private static synchronized SecretKey wrappingKey()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    public static synchronized void resetWrappingKey()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(ALIAS);}
    public static String wrap(String plain)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,wrappingKey());
        return new JSONObject().put("iv",b64(cipher.getIV())).put("body",b64(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)))).toString();
    }
    public static String unwrap(String envelope)throws Exception{
        JSONObject e=new JSONObject(envelope);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,wrappingKey(),new GCMParameterSpec(128,un64(e.getString("iv"))));
        return new String(cipher.doFinal(un64(e.getString("body"))),StandardCharsets.UTF_8);
    }
    // HMAC-SHA256 is available on API 23 too. No provider-specific PBKDF2 alias or embedded key.
    static byte[] derive(String phrase,byte[] salt)throws Exception{
        if(phrase==null||phrase.length()<16||phrase.length()>1024||salt.length!=16)throw new GeneralSecurityException("Invalid recovery phrase");
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(phrase.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        byte[] first=Arrays.copyOf(salt,salt.length+4);first[first.length-1]=1;byte[] u=mac.doFinal(first),out=u.clone();
        for(int i=1;i<310000;i++){u=mac.doFinal(u);for(int j=0;j<32;j++)out[j]^=u[j];}return out;
    }
    static Vault legacy(String phrase,String account,String format){
        if(!"TELECONTROL_GITHUB_OP_V1".equals(format)&&!"TELECONTROL_DRIVE_OP_V1".equals(format))throw new IllegalArgumentException("Unknown legacy format");
        return new Vault(phrase,account,format);
    }
    public static final class Vault {
        private final String phrase,account,format;private final Map<String,SecretKey> cache=new HashMap<>();
        public Vault(String phrase,String account){this(phrase,account,"TELECONTROL_ST_OP_V1");}
        private Vault(String phrase,String account,String format){this.phrase=phrase;this.account=account;this.format=format;}
        private SecretKey key(String salt)throws Exception{SecretKey k=cache.get(salt);if(k==null){k=new SecretKeySpec(derive(phrase,un64(salt)),"AES");cache.put(salt,k);}return k;}
        private byte[] aad(String id){return (format+":"+account+":"+id).getBytes(StandardCharsets.UTF_8);}
        public String encrypt(String id,String plain,String salt)throws Exception{
            byte[] iv=random(12);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(salt),new GCMParameterSpec(128,iv));cipher.updateAAD(aad(id));
            return new JSONObject().put("format",format).put("op",id).put("salt",salt).put("iv",b64(iv)).put("body",b64(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)))).toString();
        }
        public String decrypt(String id,String envelope)throws Exception{
            if(envelope.length()>8*1024*1024)throw new GeneralSecurityException("Envelope too large");JSONObject e=new JSONObject(envelope);
            if(!format.equals(e.getString("format"))||!id.equals(e.getString("op"))||un64(e.getString("iv")).length!=12)throw new GeneralSecurityException("Invalid envelope");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(e.getString("salt")),new GCMParameterSpec(128,un64(e.getString("iv"))));cipher.updateAAD(aad(id));
            return new String(cipher.doFinal(un64(e.getString("body"))),StandardCharsets.UTF_8);
        }
    }
}
