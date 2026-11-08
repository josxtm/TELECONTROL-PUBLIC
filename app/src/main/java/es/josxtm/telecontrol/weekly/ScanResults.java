package es.josxtm.telecontrol.weekly;
import org.json.JSONArray;
import java.util.UUID;
/** One bounded, transient handoff. No images, files, logs or large Binder payload. */
public final class ScanResults {
 private static String token;private static JSONArray rows;
 private ScanResults(){}
 public static synchronized String put(JSONArray value){token=UUID.randomUUID().toString();rows=value;return token;}
 public static synchronized JSONArray take(String key){if(token==null||!token.equals(key))return null;JSONArray value=rows;token=null;rows=null;return value;}
}
