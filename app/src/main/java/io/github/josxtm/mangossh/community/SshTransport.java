package io.github.josxtm.mangossh.community;

import android.content.SharedPreferences;
import android.util.Base64;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.HostKeyRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class SshTransport {
    public interface Approval { boolean accept(String host, String algorithm, String sha256); }
    public interface Output { void onText(String text); }
    private final SharedPreferences knownHosts;
    private volatile Session session;
    private volatile ChannelShell shell;
    private volatile OutputStream shellInput;

    public SshTransport(SharedPreferences knownHosts) { this.knownHosts = knownHosts; }

    public synchronized void connect(String host, int port, String user, char[] password, Approval approval)
            throws Exception {
        if (!EndpointPolicy.valid(host, port, user)) throw new IllegalArgumentException("Perfil no válido");
        disconnect();
        JSch ssh = new JSch();
        ssh.setHostKeyRepository(new PinnedRepository(knownHosts, approval));
        Session active = ssh.getSession(user, host, port);
        active.setConfig("StrictHostKeyChecking", "yes");
        active.setConfig("PreferredAuthentications", "password");
        active.setPassword(new String(password));
        active.setTimeout(12000);
        try {
            active.connect(12000);
            session = active;
        } catch (Exception e) {
            active.disconnect();
            throw e;
        } finally { Arrays.fill(password, '\0'); }
    }
    public boolean connected() { return session != null && session.isConnected(); }
    public boolean shellConnected() { return shell != null && shell.isConnected(); }
    public synchronized String diagnostic(String command) throws Exception {
        if (!EndpointPolicy.diagnostic(command)) throw new IllegalArgumentException("Diagnóstico no permitido");
        Session active = session;
        if (active == null || !active.isConnected()) throw new IllegalStateException("Sin conexión SSH");
        ChannelExec channel = (ChannelExec) active.openChannel("exec");
        try {
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            java.util.concurrent.atomic.AtomicBoolean stderrSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
            channel.setErrStream(new OutputStream() { @Override public void write(int value) { stderrSeen.set(true); } });
            channel.connect(10000);
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = stdout.read(buffer)) >= 0) {
                if (result.size() + count > 65536) {
                    result.write(buffer, 0, Math.max(0, 65536 - result.size()));
                    break;
                }
                result.write(buffer, 0, count);
            }
            String text = new String(result.toByteArray(), StandardCharsets.UTF_8);
            return text + (stderrSeen.get() ? "\n[stderr disponible]" : "") +
                (channel.getExitStatus() == 0 ? "" : "\n[Salida del comando: " + channel.getExitStatus() + "]");
        } finally { channel.disconnect(); }
    }
    public synchronized void openShell(Output output) throws Exception {
        Session active = session;
        if (active == null || !active.isConnected()) throw new IllegalStateException("Sin conexión SSH");
        if (shell != null) shell.disconnect();
        ChannelShell next = (ChannelShell) active.openChannel("shell");
        next.setPty(true);
        next.setPtyType("xterm");
        InputStream stream = next.getInputStream();
        OutputStream input = next.getOutputStream();
        try { next.connect(10000); } catch (Exception e) { next.disconnect(); throw e; }
        shell = next;
        shellInput = input;
        Thread reader = new Thread(() -> {
            byte[] b = new byte[4096];
            try {
                int len;
                while (next.isConnected() && (len = stream.read(b)) >= 0) {
                    // Plain-text preview: does not interpret terminal control sequences.
                    String raw = new String(b, 0, len, StandardCharsets.UTF_8);
                    String safe = raw.replaceAll("\\u001B\\[[0-?]*[ -/]*[@-~]", "");
                    output.onText(safe);
                }
            } catch (Exception ignored) { output.onText("\n[Sesión de terminal finalizada]"); }
        }, "public-ssh-terminal-reader");
        reader.setDaemon(true);
        reader.start();
    }
    public synchronized void send(String command) throws Exception {
        if (!shellConnected()) throw new IllegalStateException("Terminal sin abrir");
        if (command.length() > 2048) throw new IllegalArgumentException("Comando demasiado largo");
        shellInput.write((command + "\n").getBytes(StandardCharsets.UTF_8));
        shellInput.flush();
    }
    public synchronized void disconnect() {
        if (shell != null) shell.disconnect();
        shell = null; shellInput = null;
        if (session != null) session.disconnect();
        session = null;
    }
    private static String sha256(byte[] key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key);
            return "SHA256:" + Base64.encodeToString(digest, Base64.NO_WRAP | Base64.NO_PADDING);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String entry(String host) { return "pin." + sha256(host.getBytes(StandardCharsets.UTF_8)); }

    /** Explicit trust on first use; a changed key is always refused. */
    private static final class PinnedRepository implements HostKeyRepository {
        private final SharedPreferences preferences;
        private final Approval callback;
        PinnedRepository(SharedPreferences preferences, Approval callback) {
            this.preferences = preferences; this.callback = callback;
        }
        @Override public int check(String host, byte[] key) {
            String fp = sha256(key);
            String old = preferences.getString(entry(host), null);
            if (EndpointPolicy.changed(old, fp)) return CHANGED;
            if (EndpointPolicy.trusted(old, fp)) return OK;
            String algorithm;
            try { algorithm = new HostKey(host, key).getType(); }
            catch (JSchException e) { return NOT_INCLUDED; }
            if (!callback.accept(host, algorithm, fp)) return NOT_INCLUDED;
            return preferences.edit().putString(entry(host), fp).commit() ? OK : NOT_INCLUDED;
        }
        @Override public void add(HostKey key, UserInfo userInfo) {}
        @Override public void remove(String host, String type) {}
        @Override public void remove(String host, String type, byte[] key) {}
        @Override public String getKnownHostsRepositoryID() { return "Pinned fingerprint store"; }
        @Override public HostKey[] getHostKey() { return new HostKey[0]; }
        @Override public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
    }
}
