package es.josxtm.telecontrol.community;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.JsonReader;
import android.util.JsonWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One private public-edition database; every replacement is a single transaction. */
final class LocalStore extends SQLiteOpenHelper {
    static final int SCHEMA = 1;
    static final int MAX_ROWS = 1000000;
    LocalStore(Context context) { this(context, "community-local.db"); }
    LocalStore(Context context, String name) { super(context, name, null, SCHEMA); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)");
        db.execSQL("CREATE TABLE notes (id TEXT PRIMARY KEY REFERENCES records(id) ON DELETE CASCADE, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE journal (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, body TEXT NOT NULL)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Migración de esquema no disponible");
    }
    private void meta(SQLiteDatabase db, String key, String value) {
        ContentValues row = new ContentValues(); row.put("key", key); row.put("value", value);
        db.insertWithOnConflict("meta", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    synchronized String setting(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    synchronized void setting(String key, String value) { meta(getWritableDatabase(), key, value); }
    static String identifier(LinkedHashMap<String, String> record, String field) throws IOException {
        String id = record.get(field);
        if (id == null || id.trim().isEmpty() || id.length() > 256) throw JsonCodec.invalid();
        return id;
    }
    private void insertRecord(SQLiteDatabase db, LinkedHashMap<String, String> record, String field,
                              Set<String> fields) throws IOException {
        fields.addAll(record.keySet());
        if (fields.size() > JsonCodec.MAX_FIELDS) throw JsonCodec.invalid();
        ContentValues row = new ContentValues();
        row.put("id", identifier(record, field)); row.put("payload", JsonCodec.encode(record));
        db.insertOrThrow("records", null, row);
    }
    private void storeFields(SQLiteDatabase db, Set<String> fields) throws IOException {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (String field : fields) map.put(field, "");
        meta(db, "fields", JsonCodec.encode(map));
    }
    synchronized int importCatalog(Reader input, String field) throws IOException {
        if (field.isEmpty() || field.length() > 128) throw JsonCodec.invalid();
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (JsonReader reader = JsonCodec.reader(input)) {
            db.delete("records", null, null); // Cascades notes, never touches independent journal.
            Set<String> fields = new LinkedHashSet<>();
            int count = 0;
            reader.beginArray();
            while (reader.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                insertRecord(db, JsonCodec.record(reader), field, fields);
            }
            reader.endArray(); JsonCodec.end(reader);
            if (count == 0) throw JsonCodec.invalid();
            storeFields(db, fields); meta(db, "identifier", field); meta(db, "visible", "");
            db.setTransactionSuccessful();
            return count;
        } finally { db.endTransaction(); }
    }
    synchronized List<String> fields() throws IOException {
        String value = setting("fields");
        return value.isEmpty() ? new ArrayList<>() : new ArrayList<>(JsonCodec.decode(value).keySet());
    }
    synchronized LinkedHashMap<String, String> find(String id) throws IOException {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM records WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? JsonCodec.decode(c.getString(0)) : null;
        }
    }
    synchronized String note(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT body FROM notes WHERE id=?", new String[]{id})) {
            return c.moveToFirst() ? c.getString(0) : "";
        }
    }
    synchronized void saveNote(String id, String body) throws IOException {
        validateBody(body);
        ContentValues row = new ContentValues(); row.put("id", id); row.put("body", body);
        getWritableDatabase().insertWithOnConflict("notes", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    static void validateBody(String body) throws IOException {
        if (body.length() > JsonCodec.MAX_TEXT) throw JsonCodec.invalid();
    }
    synchronized void addJournal(String body) throws IOException {
        validateBody(body); if (body.trim().isEmpty()) throw JsonCodec.invalid();
        ContentValues row = new ContentValues(); row.put("time", System.currentTimeMillis()); row.put("body", body);
        getWritableDatabase().insertOrThrow("journal", null, row);
    }
    static final class Entry {
        final long id, time; final String body;
        Entry(long id, long time, String body) { this.id = id; this.time = time; this.body = body; }
    }
    synchronized List<Entry> journal(long before) {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,time,body FROM journal WHERE id<? ORDER BY id DESC LIMIT 25", new String[]{Long.toString(before)})) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0), c.getLong(1), c.getString(2)));
        }
        return result;
    }
    synchronized void backup(Writer output) throws IOException {
        try (JsonWriter w = new JsonWriter(output)) {
            w.beginObject().name("format").value("telecontrol-community").name("version").value(SCHEMA);
            w.name("identifier").value(setting("identifier"));
            w.name("visible").value(setting("visible"));
            w.name("records").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT payload FROM records ORDER BY id", null)) {
                while (c.moveToNext()) JsonCodec.writeRecord(w, JsonCodec.decode(c.getString(0)));
            }
            w.endArray().name("notes").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id,body FROM notes ORDER BY id", null)) {
                while (c.moveToNext()) w.beginObject().name("id").value(c.getString(0)).name("body").value(c.getString(1)).endObject();
            }
            w.endArray().name("journal").beginArray();
            try (Cursor c = getReadableDatabase().rawQuery("SELECT id,time,body FROM journal ORDER BY id", null)) {
                while (c.moveToNext()) w.beginObject().name("id").value(c.getLong(0)).name("time").value(c.getLong(1))
                        .name("body").value(c.getString(2)).endObject();
            }
            w.endArray().endObject();
        }
    }
    private static void name(JsonReader r, String expected) throws IOException {
        if (!expected.equals(r.nextName())) throw JsonCodec.invalid();
    }
    synchronized void restore(Reader input) throws IOException {
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try (JsonReader r = JsonCodec.reader(input)) {
            r.beginObject(); name(r, "format");
            if (!"telecontrol-community".equals(JsonCodec.text(r, 64))) throw JsonCodec.invalid();
            name(r, "version"); if (!Integer.toString(SCHEMA).equals(r.nextString())) throw JsonCodec.invalid();
            name(r, "identifier"); String field = JsonCodec.text(r, 128);
            name(r, "visible"); String visible = JsonCodec.text(r, JsonCodec.MAX_TEXT);
            db.delete("records", null, null); db.delete("journal", null, null); db.delete("meta", null, null);
            Set<String> fields = new LinkedHashSet<>();
            name(r, "records"); r.beginArray(); int count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                insertRecord(db, JsonCodec.record(r), field, fields);
            }
            r.endArray();
            if (!visible.isEmpty() && !fields.containsAll(JsonCodec.decode(visible).keySet())) throw JsonCodec.invalid();
            if (!fields.isEmpty()) storeFields(db, fields);
            meta(db, "identifier", field); meta(db, "visible", visible);
            name(r, "notes"); r.beginArray(); count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                r.beginObject(); name(r, "id"); String id = JsonCodec.text(r, 256);
                name(r, "body"); String body = JsonCodec.text(r, JsonCodec.MAX_TEXT); r.endObject();
                ContentValues row = new ContentValues(); row.put("id", id); row.put("body", body);
                db.insertOrThrow("notes", null, row);
            }
            r.endArray(); name(r, "journal"); r.beginArray(); count = 0;
            while (r.hasNext()) {
                if (++count > MAX_ROWS) throw JsonCodec.invalid();
                r.beginObject(); name(r, "id"); long id = r.nextLong();
                name(r, "time"); long time = r.nextLong(); name(r, "body"); String body = JsonCodec.text(r, JsonCodec.MAX_TEXT);
                r.endObject(); if (id <= 0 || time < 0 || body.trim().isEmpty()) throw JsonCodec.invalid();
                ContentValues row = new ContentValues(); row.put("id", id); row.put("time", time); row.put("body", body);
                db.insertOrThrow("journal", null, row);
            }
            r.endArray(); r.endObject(); JsonCodec.end(r); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
