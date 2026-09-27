package com.osskn4w7.mcmonitor.harness;

import com.osskn4w7.mcmonitor.core.auth.PasswordAuth;
import com.osskn4w7.mcmonitor.core.json.MiniJson;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Base64;
import java.util.List;
import java.util.Map;

// 冒烟测试：以客户端身份走一遍 鉴权 -> status -> logs -> cmd -> logsub 流程
public final class SmokeTest {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 25580;
        String password = args.length > 1 ? args[1] : "Steady-Wolf.42";

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(30_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));

            send(out, Map.of("type", "auth"));
            Map<String, Object> challenge = MiniJson.parseObject(in.readLine());
            if (!"challenge".equals(challenge.get("type"))) throw new AssertionError("预期 challenge, 得到: " + challenge);
            byte[] salt = Base64.getDecoder().decode((String) challenge.get("salt"));
            int iters = (int) (long) (Long) challenge.get("iters");
            byte[] nonce = Base64.getDecoder().decode((String) challenge.get("nonce"));
            byte[] verifier = PasswordAuth.pbkdf2(password, salt, iters);
            byte[] resp = PasswordAuth.hmacSha256(verifier, nonce);
            send(out, Map.of("type", "auth", "response", Base64.getEncoder().encodeToString(resp)));
            expect(in, "ok", "鉴权");

            send(out, Map.of("type", "status"));
            Map<String, Object> status = MiniJson.parseObject(in.readLine());
            System.out.println("status: 在线=" + status.get("online") + "/" + status.get("max")
                + " tps=" + status.get("tps") + " 玩家=" + status.get("players"));

            send(out, Map.of("type", "logs", "tail", 5));
            Map<String, Object> logs = MiniJson.parseObject(in.readLine());
            System.out.println("logs: " + ((List<?>) logs.get("lines")).size() + " 行");

            send(out, Map.of("type", "cmd", "command", "list"));
            Map<String, Object> cmd = MiniJson.parseObject(in.readLine());
            System.out.println("cmd: " + cmd.get("msg"));

            send(out, Map.of("type", "logsub", "enable", true));
            expect(in, "logsub", "订阅");
            Map<String, Object> push = MiniJson.parseObject(in.readLine());
            if (!"logpush".equals(push.get("type"))) throw new AssertionError("预期 logpush, 得到: " + push);
            System.out.println("logpush: " + push.get("line"));

            // 错误密码必须被拒绝（换连接再试）
            try (Socket bad = new Socket("127.0.0.1", port)) {
                BufferedReader bin = new BufferedReader(new InputStreamReader(bad.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
                BufferedWriter bout = new BufferedWriter(new OutputStreamWriter(bad.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
                send(bout, Map.of("type", "auth"));
                Map<String, Object> ch2 = MiniJson.parseObject(bin.readLine());
                byte[] v2 = PasswordAuth.pbkdf2("Wrong-Pass.99", Base64.getDecoder().decode((String) ch2.get("salt")), (int) (long) (Long) ch2.get("iters"));
                byte[] r2 = PasswordAuth.hmacSha256(v2, Base64.getDecoder().decode((String) ch2.get("nonce")));
                send(bout, Map.of("type", "auth", "response", Base64.getEncoder().encodeToString(r2)));
                Map<String, Object> deny = MiniJson.parseObject(bin.readLine());
                if (!"err".equals(deny.get("type"))) throw new AssertionError("错误密码居然登录成功!");
                System.out.println("错误密码被拒绝: " + deny.get("msg"));
            }

            System.out.println("SMOKE-TEST-PASSED");
        }
    }

    private static void expect(BufferedReader in, String type, String what) throws Exception {
        Map<String, Object> m = MiniJson.parseObject(in.readLine());
        if (!type.equals(m.get("type"))) throw new AssertionError(what + "失败: " + m);
    }

    private static void send(BufferedWriter out, Map<String, Object> msg) throws Exception {
        out.write(MiniJson.write(msg));
        out.write("\n");
        out.flush();
    }
}
