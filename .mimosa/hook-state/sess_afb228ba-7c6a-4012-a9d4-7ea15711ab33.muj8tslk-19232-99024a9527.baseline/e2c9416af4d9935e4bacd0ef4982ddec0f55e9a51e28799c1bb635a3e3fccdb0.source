package com.osskn4w7.mcmonitor.neoforge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.function.Consumer;

// 挂在 log4j2 root logger 上的捕获器：把服务器日志行喂给监控通道
public final class LogCapture {

    private final AbstractAppender appender;
    private final Consumer<String> sink;
    private Logger root;

    private LogCapture(Consumer<String> sink) {
        this.sink = sink;
        this.appender = new AbstractAppender("McMonitorCapture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                String line = "[" + event.getLevel() + "] "
                    + (event.getLoggerName() != null ? event.getLoggerName() : "")
                    + ": " + event.getMessage().getFormattedMessage();
                // 脱敏兜底：任何提到 setpass 的行都不进监控流
                if (line.contains("setpass")) {
                    line = line.replaceAll("setpass.*", "setpass ***");
                }
                sink.accept(line);
            }
        };
    }

    public static LogCapture attach(Consumer<String> sink) {
        LogCapture capture = new LogCapture(sink);
        capture.appender.start();
        capture.root = (Logger) LogManager.getRootLogger();
        capture.root.addAppender(capture.appender);
        return capture;
    }

    public void detach() {
        if (root != null) root.removeAppender(appender);
        appender.stop();
    }
}
