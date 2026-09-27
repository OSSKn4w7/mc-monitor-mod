package com.osskn4w7.mcmonitor.core.server;

import com.osskn4w7.mcmonitor.core.auth.AuthGuard;
import com.osskn4w7.mcmonitor.core.auth.PasswordAuth;
import com.osskn4w7.mcmonitor.core.json.MiniJson;
import com.osskn4w7.mcmonitor.core.log.LogRing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 一个已接入的客户端连接：逐条读 JSON 消息、按协议分发、写回响应。
// 状态机：未鉴权 -> (challenge -> verify) -> 已鉴权 -> 可用 status/logs/cmd/logsub
final class ClientSession implements Runnable {

    private static final int CMD_PER_SECOND_LIMIT = 5;

    private final MonitorServer server;
    private final Socket socket;
    private final String remoteIp;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final Object writeLock = new Object();

    private volatile boolean authed = false;
    private volatile boolean subscribed = false;
    private long cmdWindowStart = 0;
    private int cmdCountInWindow = 0;
    private final Object cmdRateLock = new Object();

    ClientSession(MonitorServer server, Socket socket) throws Exception {
        this.server = server;
        this.socket = socket;
        this.remoteIp = socket.getInetAddress() != null ? socket.getInetAddress().getHostAddress() : "unknown";
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    String remoteIp() {
        return remoteIp;
    }

    boolean isAuthed() {
        return authed;
    }

    boolean isSubscribed() {
        return subscribed;
    }

    void pushLog(String line) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "logpush");
        m.put("line", line);
        send(m);
    }

    void close() {
        try { socket.close(); } catch (Exception ignore) {}
    }

    @Override
    public void run() {
        try {
            socket.setSoTimeout(120_000); // 空闲超时：120 秒无消息断开（客户端有 ping 保活）
            String line;
            while (!socket.isClosed() && (line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    handle(MiniJson.parseObject(line));
                } catch (Exception e) {
                    err("无法处理消息: " + e.getMessage());
                }
            }
        } catch (Exception ignore) {
            // 读异常/超时/对端关闭：走 finally 清理
        } finally {
            subscribed = false;
            server.removeSession(this);
            close();
        }
    }

    private void handle(Map<String, Object> msg) {
        String type = String.valueOf(msg.getOrDefault("type", ""));

        // 鉴权前只允许 auth / ping
        if (!authed) {
            switch (type) {
                case "ping" -> reply("pong");
                case "auth" -> handleAuth(msg);
                default -> err("请先完成鉴权");
            }
            return;
        }

        switch (type) {
            case "ping" -> reply("pong");
            case "status" -> handleStatus();
            case "logs" -> handleLogs(msg);
            case "logsub" -> handleLogSub(msg);
            case "cmd" -> handleCmd(msg);
            default -> err("未知消息类型: " + type);
        }
    }

    private void handleAuth(Map<String, Object> msg) {
        if (!server.auth().isConfigured()) {
            err("尚未设置监控密码：请在服务器控制台或游戏内执行 /mcmonitor setpass");
            return;
        }
        Object respObj = msg.get("response");
        String responseB64 = respObj == null ? "" : String.valueOf(respObj);

        // 防爆破：新挑战走完整限频；响应提交只查封禁（一次登录=挑战+响应对）
        if (responseB64.isEmpty()) {
            String deny = server.guard().precheck(remoteIp);
            if (deny != null) { err(deny); return; }
            send(server.auth().beginChallenge());
            return;
        }
        String deny = server.guard().ifBanned(remoteIp);
        if (deny != null) { err(deny); return; }

        // 第 2 步：校验响应
        if (server.auth().verify(responseB64)) {
            authed = true;
            server.guard().recordSuccess(remoteIp);
            server.audit("登录成功 <- " + remoteIp);
            reply("ok");
        } else {
            server.guard().recordFailure(remoteIp);
            server.audit("登录失败 <- " + remoteIp);
            err("密码错误或响应无效");
        }
    }

    private void handleStatus() {
        MonitorBackend.StatusInfo s = server.backend().status();
        Map<String, Object> m = new HashMap<>();
        m.put("type", "status");
        m.put("online", (long) s.playerCount);
        m.put("max", (long) s.maxPlayers);
        m.put("players", s.playerNames);
        m.put("tps", s.tps);
        m.put("uptime", (long) s.uptimeSeconds);
        m.put("version", s.serverVersion);
        send(m);
    }

    private void handleLogs(Map<String, Object> msg) {
        long tail = msg.get("tail") instanceof Number n ? n.longValue() : 200;
        List<String> lines = server.logs().tail((int) Math.min(Math.max(tail, 1), 1000));
        Map<String, Object> m = new HashMap<>();
        m.put("type", "logs");
        m.put("lines", lines);
        send(m);
    }

    private void handleLogSub(Map<String, Object> msg) {
        boolean enable = Boolean.TRUE.equals(msg.get("enable"));
        subscribed = enable;
        Map<String, Object> m = new HashMap<>();
        m.put("type", "logsub");
        m.put("enable", enable);
        send(m);
    }

    private void handleCmd(Map<String, Object> msg) {
        // 命令限速：最高权限接口，防止失控脚本刷命令
        synchronized (cmdRateLock) {
            long now = System.currentTimeMillis();
            if (now - cmdWindowStart > 1000) {
                cmdWindowStart = now;
                cmdCountInWindow = 0;
            }
            if (++cmdCountInWindow > CMD_PER_SECOND_LIMIT) {
                err("命令发送过于频繁");
                return;
            }
        }
        Object cmdObj = msg.get("command");
        String command = cmdObj == null ? "" : String.valueOf(cmdObj).trim();
        if (command.isEmpty()) { err("命令为空"); return; }

        server.audit("执行命令 [" + remoteIp + "]: /" + command);
        try {
            String result = server.backend().runCommand(command);
            Map<String, Object> m = new HashMap<>();
            m.put("type", "cmdresult");
            m.put("rc", (long) 0);
            m.put("msg", result == null ? "" : result);
            send(m);
        } catch (Exception e) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", "cmdresult");
            m.put("rc", (long) 1);
            m.put("msg", "执行失败: " + e.getMessage());
            send(m);
        }
    }

    // ---------- 输出 ----------
    private void reply(String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        send(m);
    }

    private void err(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", "err");
        m.put("msg", msg);
        send(m);
    }

    private void send(Map<String, Object> msg) {
        synchronized (writeLock) {
            try {
                out.write(MiniJson.write(msg));
                out.write("\n");
                out.flush();
            } catch (Exception e) {
                close();
            }
        }
    }
}
