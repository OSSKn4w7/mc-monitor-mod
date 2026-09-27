package com.osskn4w7.mcmonitor.neoforge;

import com.osskn4w7.mcmonitor.core.server.MonitorBackend;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

// core 与 Minecraft 服务器的桥接实现
final class NeoBackend implements MonitorBackend {

    private final MinecraftServer server;
    private final TickTracker tickTracker;
    private final LongSupplier uptimeSeconds;

    NeoBackend(MinecraftServer server, TickTracker tickTracker, LongSupplier uptimeSeconds) {
        this.server = server;
        this.tickTracker = tickTracker;
        this.uptimeSeconds = uptimeSeconds;
    }

    @Override
    public StatusInfo status() {
        List<String> names = server.getPlayerList().getPlayers().stream()
            .map(p -> p.getGameProfile().getName())
            .toList();
        return new StatusInfo(
            server.getPlayerCount(),
            server.getMaxPlayers(),
            names,
            tickTracker.tps(),
            uptimeSeconds.getAsLong(),
            server.getServerVersion()
        );
    }

    @Override
    public String runCommand(String command) {
        try {
            // 必须切回服务器主线程执行；等待结果但设超时，防止 App 端卡死
            return server.submit(() -> {
                CommandSourceStack css = server.createCommandSourceStack()
                    .withPermission(4)          // 服主级权限
                    .withSuppressedOutput();    // 输出统一走日志流，避免重复广播
                String withoutSlash = command.startsWith("/") ? command.substring(1) : command;
                // 1.21.1 映射下 performPrefixedCommand 返回 void，这里走 brigadier 拿返回码
                com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher =
                    server.getCommands().getDispatcher();
                var parseResults = dispatcher.parse(withoutSlash, css);
                int rc;
                try {
                    rc = dispatcher.execute(parseResults);
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException syntaxEx) {
                    // 命令语法错误属于正常业务结果，把提示原样带回
                    return "命令无法执行: " + syntaxEx.getMessage();
                }
                return "已执行（返回码 " + rc + "），完整输出见日志流";
            }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException(cause.getMessage(), cause);
        }
    }
}
