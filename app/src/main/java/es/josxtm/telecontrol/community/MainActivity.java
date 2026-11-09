package es.josxtm.telecontrol.community;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int IMPORT = 11, BACKUP = 12, RESTORE = 13, MICROPHONE = 14;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LocalStore store;
    private LinearLayout column, details, diary;
    private TextView status;
    private EditText identifier, query, note, journal;
    private String activeId, pendingField = "id";
    private boolean busy, journalLoading;
    private int journalGeneration;
    private long journalBefore = Long.MAX_VALUE;
    private SpeechRecognizer recognizer;
    private EditText speechTarget;

    @Override public void onCreate(Bundle state) {
        String theme = getPreferences(MODE_PRIVATE).getString("theme", "system");
        int mode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        boolean dark = "dark".equals(theme) || ("system".equals(theme) && mode == android.content.res.Configuration.UI_MODE_NIGHT_YES);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store = new LocalStore(getApplicationContext());
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(this); outer.setGravity(Gravity.CENTER_HORIZONTAL);
        column = vertical(); int pad = dp(20); column.setPadding(pad, pad, pad, pad);
        int width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(840));
        outer.addView(column, new LinearLayout.LayoutParams(width, -2)); scroll.addView(outer); setContentView(scroll);
        if (Build.VERSION.SDK_INT >= 35) outer.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        label(column, "TELECONTROL Público", 26);
        label(column, "Consulta local · edición pública independiente", 16);
        // The only addition to TELECONTROL’s existing home screen.
        ImageButton mango = new ImageButton(this);
        mango.setImageResource(R.drawable.ic_mangossh);
        mango.setContentDescription("Abrir MangoSSH");
        mango.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mango.setPadding(dp(8), dp(8), dp(8), dp(8));
        column.addView(mango, new LinearLayout.LayoutParams(dp(64), dp(64)));
        mango.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT < 26) {
                new AlertDialog.Builder(this).setTitle("MangoSSH")
                    .setMessage("El cliente SSH integrado requiere Android 8 o posterior.")
                    .setPositiveButton("Aceptar", null).show();
                return;
            }
            Intent ssh = new Intent(this, io.github.josxtm.mangossh.community.MainActivity.class);
            ssh.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(ssh);
        });
        button(column, "Tema: sistema / claro / oscuro", () -> new AlertDialog.Builder(this)
                .setTitle("Tema").setItems(new String[]{"Sistema", "Claro", "Oscuro"}, (d, n) -> {
                    getPreferences(MODE_PRIVATE).edit().putString("theme", new String[]{"system", "light", "dark"}[n]).apply(); recreate();
                }).show());
        status = label(column, "Preparado. Los datos permanecen en este dispositivo.", 16);
        label(column, "Catálogo JSON", 22);
        label(column, "Lista de objetos con campos simples. Identificadores únicos y exactos; se conservan ceros iniciales en cadenas.", 16);
        identifier = edit(column, "Campo identificador (por ejemplo: id)", true, 128);
        identifier.setText(R.string.default_identifier);
        button(column, "Importar catálogo local", () -> confirm("Reemplazar catálogo",
                "La importación correcta sustituye el catálogo y elimina sus notas. El diario se conserva. Si hay errores, los datos anteriores se mantienen.", () -> {
                    pendingField = identifier.getText().toString(); pick(IMPORT, false);
                }));
        query = edit(column, "Identificador exacto", true, 256);
        button(column, "Buscar", () -> search(query.getText().toString()));
        button(column, "Seleccionar campos de la ficha", this::chooseFields);
        details = vertical(); column.addView(details);
        label(column, "Nota del registro encontrado", 22);
        note = edit(column, "Nota local (hasta 16000 caracteres)", false, JsonCodec.MAX_TEXT);
        button(column, "Guardar nota", () -> {
            if (activeId == null) { message("Busca un registro primero."); return; }
            String id = activeId, body = note.getText().toString();
            task(() -> { store.saveNote(id, body); ui(() -> message("Nota guardada.")); });
        });
        voiceButton(note);
        label(column, "Diario independiente", 22);
        journal = edit(column, "Nueva entrada del diario", false, JsonCodec.MAX_TEXT);
        button(column, "Añadir entrada", () -> {
            String body = journal.getText().toString();
            task(() -> { store.addJournal(body); ui(() -> { journal.setText(""); refreshJournal(); message("Entrada guardada."); }); });
        });
        voiceButton(journal);
        diary = vertical(); column.addView(diary);
        button(column, "Entradas más recientes", this::refreshJournal);
        button(column, "Más entradas", this::loadJournal);
        label(column, "Copia y recuperación", 22);
        label(column, "La copia contiene catálogo, campos visibles, notas y diario en texto legible. Elige una ubicación local segura: el selector puede ofrecer proveedores de nube. No se envían datos desde esta app.", 16);
        button(column, "Exportar copia local", () -> pick(BACKUP, true));
        button(column, "Restaurar copia local", () -> confirm("Restaurar datos",
                "Sustituye catálogo, notas, campos y diario de esta edición pública. Haz una copia antes. Una copia inválida no modifica los datos existentes.", () -> pick(RESTORE, false)));
        if (state != null) {
            pendingField = state.getString("pendingField", "id");
            identifier.setText(state.getString("field", "id")); query.setText(state.getString("query", ""));
            activeId = state.getString("activeId"); note.setText(state.getString("note", "")); journal.setText(state.getString("journal", ""));
        }
        String restoredId = activeId;
        task(() -> {
            String field = store.setting("identifier");
            LinkedHashMap<String, String> record = restoredId == null ? null : store.find(restoredId);
            String visible = store.setting("visible");
            ui(() -> {
                if (state == null && !field.isEmpty()) identifier.setText(field);
                render(record, visible); refreshJournal();
            });
        });
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() { LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); return box; }
    private TextView label(LinearLayout box, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setPadding(0, dp(8), 0, dp(8));
        view.setTextIsSelectable(true); box.addView(view); return view;
    }
    private EditText edit(LinearLayout box, String hint, boolean single, int limit) {
        EditText view = new EditText(this); view.setHint(hint); view.setContentDescription(hint); view.setSingleLine(single);
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        if (Build.VERSION.SDK_INT >= 26) view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        view.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | (single ? 0 : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        view.setSaveEnabled(false); box.addView(view); return view;
    }
    private void button(LinearLayout box, String text, Runnable action) {
        Button view = new Button(this); view.setText(text); box.addView(view); view.setOnClickListener(v -> {
            if (busy) { message("Espera a que termine la operación actual."); return; } action.run();
        });
    }
    private void message(String text) { status.setText(text); status.announceForAccessibility(text); }
    private void confirm(String title, String text, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("Cancelar", null)
                .setPositiveButton("Continuar", (d, n) -> action.run()).show();
    }
    private interface Work { void run() throws Exception; }
    private void ui(Runnable action) { runOnUiThread(() -> { if (!isDestroyed()) action.run(); }); }
    private void task(Work action) {
        if (busy) return;
        busy = true; message("Procesando en el dispositivo…");
        worker.execute(() -> {
            try { action.run(); }
            catch (Exception failure) { ui(() -> message("No se pudo completar. Comprueba el formato, identificadores únicos, límites, espacio libre y acceso al archivo. No se muestran contenidos en errores.")); }
            finally { ui(() -> { busy = false; if ("Procesando en el dispositivo…".contentEquals(status.getText())) message("Preparado."); }); }
        });
    }
    private void pick(int request, boolean create) {
        Intent intent = new Intent(create ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(create ? "application/json" : "*/*");
        if (create) intent.putExtra(Intent.EXTRA_TITLE, "telecontrol-community-backup.json");
        try { startActivityForResult(intent, request); }
        catch (android.content.ActivityNotFoundException unavailable) { message("No hay selector de documentos disponible."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT && request != BACKUP && request != RESTORE) return;
        if (result != RESULT_OK || data == null || data.getData() == null) { message("Selección cancelada."); return; }
        android.net.Uri uri = data.getData(); String field = pendingField;
        task(() -> {
            if (request == BACKUP) {
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw JsonCodec.invalid();
                    store.backup(new OutputStreamWriter(output, StandardCharsets.UTF_8));
                }
                ui(() -> message("Copia exportada. Conserva el archivo en un lugar seguro."));
            } else {
                int count = 0;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw JsonCodec.invalid();
                    InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT));
                    if (request == IMPORT) count = store.importCatalog(reader, field); else store.restore(reader);
                }
                String storedField = store.setting("identifier"); int imported = count;
                ui(() -> { activeId = null; note.setText(""); details.removeAllViews(); identifier.setText(storedField);
                    refreshJournal(); message(request == IMPORT ? "Importados: " + imported : "Copia restaurada."); });
            }
        });
    }
    private void search(String id) {
        task(() -> {
            LinkedHashMap<String, String> record = store.find(id); String body = record == null ? "" : store.note(id);
            String visible = store.setting("visible");
            ui(() -> { activeId = record == null ? null : id; note.setText(body); render(record, visible);
                message(record == null ? "Sin coincidencias." : "Registro encontrado."); });
        });
    }
    private void render(Map<String, String> record, String visible) {
        details.removeAllViews(); if (record == null) return;
        Map<String, String> selected;
        try { selected = visible.isEmpty() ? record : JsonCodec.decode(visible); }
        catch (Exception invalid) { selected = record; }
        for (Map.Entry<String, String> entry : record.entrySet()) if (selected.containsKey(entry.getKey()))
            label(details, entry.getKey() + ": " + (entry.getValue() == null ? "(vacío)" : entry.getValue()), 18);
    }
    private void chooseFields() {
        task(() -> {
            List<String> fields = store.fields(); String visible = store.setting("visible");
            Map<String, String> selected = visible.isEmpty() ? null : JsonCodec.decode(visible);
            boolean[] checked = new boolean[fields.size()];
            for (int i = 0; i < checked.length; i++) checked[i] = selected == null || selected.containsKey(fields.get(i));
            ui(() -> new AlertDialog.Builder(this).setTitle("Campos visibles")
                    .setMultiChoiceItems(fields.toArray(new String[0]), checked, (d, which, value) -> checked[which] = value)
                    .setNegativeButton("Cancelar", null).setPositiveButton("Guardar", (d, which) -> task(() -> {
                        LinkedHashMap<String, String> chosen = new LinkedHashMap<>();
                        for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.put(fields.get(i), "");
                        String encoded = chosen.isEmpty() ? "{}" : JsonCodec.encode(chosen);
                        store.setting("visible", encoded);
                        // Preserve any unsaved note when changing displayed fields.
                        String id = activeId; Map<String, String> record = id == null ? null : store.find(id);
                        ui(() -> { render(record, encoded); message("Campos guardados."); });
                    })).show());
        });
    }
    private void refreshJournal() { journalGeneration++; journalLoading = false; journalBefore = Long.MAX_VALUE; diary.removeAllViews(); loadJournal(); }
    private void loadJournal() {
        // Queued separately so UI callbacks from a completed transaction can refresh safely.
        if (journalLoading) return;
        journalLoading = true; int generation = journalGeneration;
        long before = journalBefore;
        worker.execute(() -> {
            try {
                List<LocalStore.Entry> entries = store.journal(before);
                ui(() -> {
                    if (generation != journalGeneration) return;
                    journalLoading = false;
                    if (!entries.isEmpty()) diary.removeAllViews();
                    for (LocalStore.Entry entry : entries) {
                        label(diary, DateFormat.getDateTimeInstance().format(new java.util.Date(entry.time)) + "\n" + entry.body, 16);
                        journalBefore = entry.id;
                    }
                    if (entries.isEmpty()) message("No hay más entradas.");
                });
            } catch (Exception error) { ui(() -> { if (generation == journalGeneration) { journalLoading = false; message("No se pudo leer el diario."); } }); }
        });
    }
    private void voiceButton(EditText target) {
        boolean available = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if (available) button(column, "Dictar sin conexión", () -> {
            speechTarget = target;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE);
            else startDictation();
        });
        else label(column, "Dictado local no disponible en este dispositivo. Usa el teclado.", 14);
    }
    private void startDictation() {
        if (Build.VERSION.SDK_INT < 31 || speechTarget == null) return;
        if (recognizer != null) recognizer.destroy();
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener() {
            public void onReadyForSpeech(Bundle params) { message("Escuchando en el dispositivo…"); }
            public void onBeginningOfSpeech() { }
            public void onRmsChanged(float rms) { }
            public void onBufferReceived(byte[] buffer) { }
            public void onEndOfSpeech() { }
            public void onError(int error) { message("Dictado local no disponible o cancelado."); }
            public void onResults(Bundle results) {
                ArrayList<String> words = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (words != null && !words.isEmpty() && speechTarget != null) speechTarget.append(words.get(0));
                message("Dictado terminado. Revisa el texto antes de guardarlo.");
            }
            public void onPartialResults(Bundle results) { }
            public void onEvent(int type, Bundle params) { }
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        try { recognizer.startListening(intent); }
        catch (RuntimeException unavailable) { message("Dictado local no disponible."); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == MICROPHONE && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startDictation();
        else if (request == MICROPHONE) message("Permiso de micrófono denegado. Puedes seguir escribiendo.");
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("pendingField", pendingField);
        state.putString("field", identifier.getText().toString()); state.putString("query", query.getText().toString());
        state.putString("activeId", activeId); state.putString("note", note.getText().toString()); state.putString("journal", journal.getText().toString());
    }
    @Override protected void onStop() { if (recognizer != null) recognizer.cancel(); super.onStop(); }
    @Override protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        worker.execute(() -> store.close()); worker.shutdown(); super.onDestroy();
    }
}
