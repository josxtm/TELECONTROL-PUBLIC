package es.josxtm.telecontrol.community;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/** Strict, bounded, scalar JSON records. No imported values enter logs or exceptions. */
final class JsonCodec {
    static final int MAX_FIELDS = 100;
    static final int MAX_TEXT = 16000;
    static JsonReader reader(Reader source) {
        return new JsonReader(new FilterReader(source) {
            private long total;
            private int run;
            private boolean quoted, escaped;
            @Override public int read(char[] buffer, int offset, int length) throws IOException {
                int count = super.read(buffer, offset, length);
                for (int i = offset; i < offset + Math.max(count, 0); i++) {
                    char c = buffer[i];
                    if (++total > 512L * 1024 * 1024) throw invalid();
                    if (quoted) {
                        if (++run > 65536) throw invalid();
                        if (escaped) escaped = false;
                        else if (c == '\\') escaped = true;
                        else if (c == '"') { quoted = false; run = 0; }
                    } else if (c == '"') { quoted = true; run = 0; }
                    else if (Character.isWhitespace(c) || "{}[],:".indexOf(c) >= 0) run = 0;
                    else if (++run > 65536) throw invalid();
                }
                return count;
            }
            @Override public int read() throws IOException {
                char[] one = new char[1];
                return read(one, 0, 1) == -1 ? -1 : one[0];
            }
        });
    }
    static IOException invalid() { return new IOException("Formato o límites no válidos"); }
    static String text(JsonReader reader, int max) throws IOException {
        if (reader.peek() != JsonToken.STRING) throw invalid();
        String value = reader.nextString();
        if (value.length() > max) throw invalid();
        return value;
    }
    static LinkedHashMap<String, String> record(JsonReader reader) throws IOException {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        int size = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (key.isEmpty() || key.length() > 128 || result.containsKey(key)
                    || result.size() >= MAX_FIELDS) throw invalid();
            JsonToken token = reader.peek();
            String value;
            if (token == JsonToken.NULL) { reader.nextNull(); value = null; }
            else if (token == JsonToken.BOOLEAN) value = Boolean.toString(reader.nextBoolean());
            else if (token == JsonToken.STRING || token == JsonToken.NUMBER) value = reader.nextString();
            else throw invalid();
            size += key.length() + (value == null ? 0 : value.length());
            if ((value != null && value.length() > MAX_TEXT) || size > 262144) throw invalid();
            result.put(key, value);
        }
        reader.endObject();
        return result;
    }
    static String encode(Map<String, String> fields) throws IOException {
        StringWriter output = new StringWriter();
        try (JsonWriter writer = new JsonWriter(output)) { writeRecord(writer, fields); }
        return output.toString();
    }
    static void writeRecord(JsonWriter writer, Map<String, String> fields) throws IOException {
        writer.beginObject();
        for (Map.Entry<String, String> entry : fields.entrySet()) writer.name(entry.getKey()).value(entry.getValue());
        writer.endObject();
    }
    static LinkedHashMap<String, String> decode(String value) throws IOException {
        try (JsonReader reader = reader(new StringReader(value))) { return record(reader); }
    }
    static void end(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid();
    }
    private JsonCodec() { }
}
