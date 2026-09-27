package com.osskn4w7.mcmonitor.core.server;

import java.util.List;

// 核心模块与游戏服之间的桥接接口：各加载器（NeoForge/Forge/Fabric）各自实现
public interface MonitorBackend {

    StatusInfo status();

    // 在服务器主线程执行一条控制台命令（权限等同服主），返回给用户看的结果文本
    String runCommand(String command);

    final class StatusInfo {
        public final int playerCount;
        public final int maxPlayers;
        public final List<String> playerNames;
        public final double tps;
        public final long uptimeSeconds;
        public final String serverVersion;

        public StatusInfo(int playerCount, int maxPlayers, List<String> playerNames,
                          double tps, long uptimeSeconds, String serverVersion) {
            this.playerCount = playerCount;
            this.maxPlayers = maxPlayers;
            this.playerNames = playerNames;
            this.tps = tps;
            this.uptimeSeconds = uptimeSeconds;
            this.serverVersion = serverVersion;
        }
    }
}
