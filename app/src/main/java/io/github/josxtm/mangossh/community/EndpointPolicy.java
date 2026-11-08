package io.github.josxtm.mangossh.community;


public final class EndpointPolicy {
    private EndpointPolicy() {}
    public static boolean valid(String host, int port, String username) {
        return host != null && host.length() > 0 && host.length() <= 253
            && host.matches("[A-Za-z0-9][A-Za-z0-9.:_\\[\\]-]*")
            && !host.contains("..") && port >= 1 && port <= 65535
            && username != null && !username.trim().isEmpty() && username.length() <= 128
            && username.matches("[A-Za-z0-9_][A-Za-z0-9_.@-]*");
    }
    public static boolean diagnostic(String command) {
        return "whoami".equals(command) || "pwd".equals(command) || "date".equals(command)
            || "uname -a".equals(command) || "df -h".equals(command);
    }
    public static boolean trusted(String existing, String received) {
        return existing != null && received != null && existing.equals(received);
    }
    public static boolean changed(String existing, String received) {
        return existing != null && !trusted(existing, received);
    }
}
