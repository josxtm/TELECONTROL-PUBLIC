package io.github.josxtm.mangossh.community;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private SshTransport ssh;
    private LinearLayout column;
    private EditText host, port, user, password, command;
    private TextView status, terminal, history;
    private final ArrayList<String> executed = new ArrayList<>();
    private volatile boolean admin = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ssh = new SshTransport(getSharedPreferences("public-known-hosts", MODE_PRIVATE));
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        column.setPadding(p,p,p,p);
        scroll.addView(column);
        setContentView(scroll);

        label("MangoSSH Público", 26);
        label("Cliente SSH genérico · edición independiente", 15);
        status = label("Desconectado", 16);
        host = field("Host SSH", false);
        port = field("Puerto", false);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText("22");
        user = field("Usuario", false);
        password = field("Contraseña temporal (no se guarda)", true);
        android.content.SharedPreferences pfile = getSharedPreferences("public-profile", MODE_PRIVATE);
        host.setText(pfile.getString("host", ""));
        user.setText(pfile.getString("user", ""));
        port.setText(pfile.getString("port", "22"));
        button("Guardar solo host, puerto y usuario", () -> {
            pfile.edit().putString("host", host.getText().toString().trim())
                .putString("user", user.getText().toString().trim())
                .putString("port", port.getText().toString().trim()).apply();
            update("Perfil local guardado sin contraseña.");
        });
        button("Conectar SSH", this::connect);
        button("Desconectar", () -> network.execute(() -> {
            ssh.disconnect(); admin = false; update("Desconectado.");
        }));
        label("Diagnóstico de solo lectura", 20);
        label("Los comandos permitidos se ejecutan exclusivamente sobre el host introducido por el usuario.", 14);
        for (String quick : new String[]{"whoami", "pwd", "date", "uname -a", "df -h"})
            button(quick, () -> diagnostic(quick));
        label("Terminal interactiva", 20);
        button("Activar terminal con confirmación", () -> {
            if (!ssh.connected()) { update("Conecta primero."); return; }
            new AlertDialog.Builder(this)
                .setTitle("Activar terminal interactiva")
                .setMessage("El terminal permite ejecutar comandos con los permisos de la cuenta SSH remota. Úsalo únicamente en sistemas autorizados.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Activar", (dialog, which) -> network.execute(() -> {
                    try {
                        ssh.openShell(this::appendOutput);
                        admin = true;
                        update("Terminal interactiva activa.");
                    } catch (Exception e) { update("No se pudo abrir el terminal: " + e.getClass().getSimpleName()); }
                })).show();
        });
        command = field("Comando del terminal activo", false);
        button("Enviar al terminal", () -> {
            String value = command.getText().toString();
            if (!admin || !ssh.shellConnected()) { update("Activa primero el terminal."); return; }
            if (value.trim().isEmpty() || value.length() > 2048) { update("Comando vacío o demasiado largo."); return; }
            command.setText("");
            executed.add(value);
            if (executed.size() > 20) executed.remove(0);
            renderHistory();
            network.execute(() -> {
                try { ssh.send(value); } catch (Exception e) { update("No se pudo enviar el comando."); }
            });
        });
        label("Salida SSH (texto básico, sin emulación ANSI completa)", 18);
        terminal = label("", 13);
        terminal.setTextIsSelectable(true);
        terminal.setBackgroundColor(0xff20252a);
        terminal.setTextColor(0xfff0f2f5);
        int inset = (int)(10 * getResources().getDisplayMetrics().density);
        terminal.setPadding(inset,inset,inset,inset);
        label("Historial de la sesión actual (no persistente)", 18);
        history = label("Sin comandos enviados", 13);
        button("Borrar salida e historial", () -> {
            terminal.setText(""); executed.clear(); renderHistory();
        });
        label("La huella SSH de cada servidor se verifica y se guarda solo si la aceptas. Una huella cambiada se rechaza. No se usan contraseñas predeterminadas, ni telemetría, ni perfiles de infraestructuras.", 14);
    }

    private TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size);
        v.setPadding(0, 8, 0, 8);
        column.addView(v); return v;
    }
    private EditText field(String text, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(text); e.setSingleLine(true);
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        column.addView(e); return e;
    }
    private void button(String title, Runnable click) {
        Button b = new Button(this); b.setText(title); column.addView(b);
        b.setOnClickListener(v -> click.run());
    }
    private void update(String message) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) status.setText(message); });
    }
    private void appendOutput(String part) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            String previous = terminal.getText().toString();
            String next = previous + part;
            if (next.length() > 20000) next = next.substring(next.length() - 20000);
            terminal.setText(next);
        });
    }
    private void renderHistory() {
        history.setText(executed.isEmpty() ? "Sin comandos enviados" : android.text.TextUtils.join("\n", executed));
    }
    private boolean approve(String endpoint, String algorithm, String fingerprint) {
        if (isFinishing() || isDestroyed()) return false;
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean allowed = new AtomicBoolean(false);
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) { latch.countDown(); return; }
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Servidor SSH desconocido")
                .setMessage("Comprueba la huella del servidor mediante un canal fiable antes de aceptar.\n\nHost: "
                    + endpoint + "\nTipo: " + algorithm + "\n" + fingerprint)
                .setPositiveButton("Confío en esta huella", (d,w) -> { allowed.set(true); latch.countDown(); })
                .setNegativeButton("Rechazar", (d,w) -> latch.countDown())
                .setOnCancelListener(d -> latch.countDown()).create();
            dialog.show();
        });
        try { return latch.await(60, TimeUnit.SECONDS) && allowed.get(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
    private void connect() {
        final String h = host.getText().toString().trim();
        final String u = user.getText().toString().trim();
        final int p;
        try { p = Integer.parseInt(port.getText().toString().trim()); }
        catch (NumberFormatException e) { update("Puerto incorrecto."); return; }
        if (!EndpointPolicy.valid(h,p,u)) { update("Host, puerto o usuario incorrectos."); return; }
        char[] pass = password.getText().toString().toCharArray();
        password.setText("");
        admin = false;
        update("Conectando. Comprueba la identidad SSH si se solicita.");
        network.execute(() -> {
            try {
                ssh.connect(h,p,u,pass,this::approve);
                update("Conexión SSH establecida y clave de host verificada.");
            } catch (Exception e) {
                update("Conexión rechazada o fallida: " + e.getClass().getSimpleName());
            } finally { Arrays.fill(pass, '\0'); }
        });
    }
    private void diagnostic(String value) {
        if (!ssh.connected()) { update("Conecta primero."); return; }
        network.execute(() -> {
            try { appendOutput("\n> " + value + "\n" + ssh.diagnostic(value) + "\n"); }
            catch (Exception e) { update("No se pudo ejecutar el diagnóstico."); }
        });
    }
    @Override protected void onDestroy() {
        admin = false;
        network.execute(ssh::disconnect); network.shutdown();
        executed.clear();
        super.onDestroy();
    }
}
