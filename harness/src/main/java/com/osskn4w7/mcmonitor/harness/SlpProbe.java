package com.osskn4w7.mcmonitor.harness;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

// 自包含 SLP 客户端探针（与 App 的 McPing 相同字节级行为），验证应答器与网络路径
public final class SlpProbe {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 25565;
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(5000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // handshake（与 App McPing 相同构造）
            ByteArrayOutputStream handshake = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(handshake);
            writeVarInt(h, 0x00);
            writeVarInt(h, -1);
            byte[] hb = host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            writeVarInt(h, hb.length);
            h.write(hb);
            h.writeShort(port);
            writeVarInt(h, 1);
            sendPacket(out, handshake.toByteArray());
            sendPacket(out, new byte[]{0x00});

            byte[] body = readPacket(in);
            DataInputStream r = new DataInputStream(new ByteArrayInputStream(body));
            int packetId = readVarInt(r);
            byte[] payload = new byte[readVarInt(r)];
            r.readFully(payload);
            String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            System.out.println("packetId=" + packetId + " jsonBytes=" + payload.length
                + " latency=" + (System.currentTimeMillis() - start) + "ms");
            System.out.println("json=" + json.substring(0, Math.min(json.length(), 200)));
        }
    }

    private static void sendPacket(DataOutputStream out, byte[] body) throws Exception {
        writeVarInt(out, body.length);
        out.write(body);
        out.flush();
    }

    private static byte[] readPacket(DataInputStream in) throws Exception {
        byte[] payload = new byte[readVarInt(in)];
        in.readFully(payload);
        return payload;
    }

    private static void writeVarInt(DataOutputStream out, int v) throws Exception {
        int value = v;
        while (true) {
            if ((value & 0xFFFFFF80) == 0) { out.writeByte(value); return; }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws Exception {
        int value = 0, shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
            if (shift > 35) throw new IllegalStateException("VarInt 过长");
        }
    }
}
