package com.osskn4w7.mcmonitor.core.server;

import com.osskn4w7.mcmonitor.core.auth.AuthGuard;
import com.osskn4w7.mcmonitor.core.auth.PasswordAuth;
import com.osskn4w7.mcmonitor.core.log.LogRing;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

// 监控 TCP 服务：纯 Java，不依赖任何游戏代码。
// 各加载器适配层在服务器启动时 start()，关闭时 stop()。
public final class MonitorServer {

    public static final int MAX_SESSIONS = 3;

    private static final Logger LOG = Logger.getLogger("McMonitorCore");

    private final int port;
    private final String bindAddress;
    private final PasswordAuth auth;
    private final MonitorBackend backend;
    private final AuthGuard guard = new AuthGuard();
    private final LogRing logs = new LogRing(1000);
    private final Set<ClientSession> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public MonitorServer(int port, String bindAddress, PasswordAuth auth, MonitorBackend backend) {
        this.port = port;
        this.bindAddress = bindAddress;
        this.auth = auth;
        this.backend = backend;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) return;
        serverSocket = new ServerSocket();
        // 监控通道拒绝半开连接堆积：backlog 设小
        serverSocket.bind(new InetSocketAddress(bindAddress, port), 16);
        acceptThread = new Thread(this::acceptLoop, "McMonitor-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        audit("监控通道已开启: " + bindAddress + ":" + port + (auth.isConfigured() ? "" : "（尚未设置密码，登录被拒绝）"));
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) return;
        try { serverSocket.close(); } catch (Exception ignore) {}
        for (ClientSession s : sessions) s.close();
        sessions.clear();
        audit("监控通道已关闭");
    }

    public boolean isRunning() {
        return running.get();
    }

    public int sessionCount() {
        return sessions.size();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                if (sessions.size() >= MAX_SESSIONS) {
                    reject(socket, "连接数已达上限");
                    continue;
                }
                try {
                    ClientSession session = new ClientSession(this, socket);
                    sessions.add(session);
                    Thread t = new Thread(session, "McMonitor-Session-" + session.remoteIp());
                    t.setDaemon(true);
                    t.start();
                } catch (Exception e) {
                    audit("会话创建失败: " + e.getMessage());
                    try { socket.close(); } catch (Exception ignore) {}
                }
            } catch (IOException e) {
                if (running.get()) audit("accept 异常: " + e.getMessage());
            }
        }
    }

    private void reject(Socket socket, String reason) {
        try {
            socket.getOutputStream().write((MiniJsonLite.err(reason) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignore) {}
        try { socket.close(); } catch (Exception ignore) {}
    }

    // 适配层每捕获一行服务器日志就调这里：进环形缓冲 + 推给已订阅会话
    public void publishLog(String line) {
        if (line == null || line.isEmpty()) return;
        logs.add(line);
        for (ClientSession s : sessions) {
            if (s.isAuthed() && s.isSubscribed()) s.pushLog(line);
        }
    }

    // 审计：敏感操作记录，进日志流，App 端可见
    public void audit(String line) {
        LOG.info("[McMonitor] " + line);
        publishLog("[审计] " + line);
    }

    void removeSession(ClientSession s) {
        sessions.remove(s);
    }

    PasswordAuth auth() { return auth; }
    AuthGuard guard() { return guard; }
    LogRing logs() { return logs; }
    MonitorBackend backend() { return backend; }

    // reject 时避免引客户端依赖写的内联小工具
    private static final class MiniJsonLite {
        static String err(String msg) {
            return "{\"type\":\"err\",\"msg\":" + quote(msg) + "}";
        }
        static String quote(String s) {
            StringBuilder b = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '"' || c == '\\') b.append('\\');
                if (c < 0x20) b.append(' ');
                else b.append(c);
            }
            return b.append('"').toString();
        }
    }
}
