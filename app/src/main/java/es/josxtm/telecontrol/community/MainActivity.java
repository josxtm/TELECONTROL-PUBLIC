package es.josxtm.telecontrol.community;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MainActivity extends Activity {
    private static final int PICK_LOCAL_FILE = 9001;
    private LinearLayout column;
    private TextView result;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        setContentView(scroll);

        title("TELECONTROL Público");
        hint("EDICIÓN PÚBLICA DE PRUEBAS — aplicación separada de la versión privada");
        title("CONSULTA LOCAL");
         
        title("Catálogo seleccionado por el usuario");
        hint("No contiene ningún catálogo ni documento incorporado en la APK. El selector funciona; el procesamiento completo queda pendiente.");
        Button choose = new Button(this);
        choose.setText("Seleccionar archivo local");
        column.addView(choose);
        choose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_LOCAL_FILE);
        });
        result = new TextView(this);
        result.setText("Ningún archivo seleccionado");
        result.setTextSize(16);
        column.addView(result);

        hint("Sin sincronización remota, sin telemetría y sin datos incorporados.");
    }

    private void title(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(22);
        view.setPadding(0, 14, 0, 14);
        column.addView(view);
    }

    private void hint(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(15);
        view.setPadding(0, 8, 0, 14);
        column.addView(view);
    }

    private void input(String placeholder) {
        EditText field = new EditText(this);
        field.setHint(placeholder);
        field.setSingleLine(true);
        column.addView(field);
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request != PICK_LOCAL_FILE || result == null) return;
        if (code == RESULT_OK && data != null && data.getData() != null) {
            result.setText("Archivo elegido. Sin importar ni transmitir sus datos.");
        } else {
            result.setText("Selección cancelada.");
        }
    }
}
