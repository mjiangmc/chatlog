package newblock.chatlog;

import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 管理日志文件的写入
 */
public class LogManager {
    private final Logger logger;
    private final File logFile;       // 聊天与命令日志文件
    private final File warnFile;      // 违规消息专用日志文件
    private final DateTimeFormatter dateFormatter;

    /**
     * 创建日志管理器
     *
     * @param logger 日志记录器
     * @param pluginDir 插件目录
     */
    public LogManager(Logger logger, File pluginDir) {
        this.logger = logger;
        this.logFile = new File(pluginDir, "chat.log");
        this.warnFile = new File(pluginDir, "warn.log");
        this.dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        initializeLogFiles();
    }

    /**
     * 初始化日志文件
     */
    private void initializeLogFiles() {
        // 创建或加载 chat.log 文件
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                logger.error("无法创建日志文件: " + logFile.getAbsolutePath());
            } else {
                logger.info("ChatLog 插件已初始化，日志文件位置: " + logFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("创建日志文件时发生错误", e);
        }

        // 创建或加载 warn.log 文件
        try {
            if (!warnFile.exists() && !warnFile.createNewFile()) {
                logger.error("无法创建 warn.log 文件: " + warnFile.getAbsolutePath());
            } else {
                logger.info("已初始化 warn.log，位置: " + warnFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("创建 warn.log 文件时发生错误", e);
        }
    }

    /**
     * 记录聊天消息
     *
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     * @param message 聊天消息
     */
    public void logChat(String serverName, String playerName, String message) {
        String timestamp = LocalDateTime.now().format(dateFormatter);

        // 控制台输出（无时间戳）
        String consoleOutput = String.format("[%s] %s: %s", serverName, playerName, message);
        logger.info(consoleOutput);

        // 日志文件输出（带时间戳）
        String logEntry = String.format("[%s] [%s] %s: %s", timestamp, serverName, playerName, message);
        writeToFile(logFile, logEntry);
    }

    /**
     * 记录命令执行
     *
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     * @param command 执行的命令
     */
    public void logCommand(String serverName, String playerName, String command) {
        String timestamp = LocalDateTime.now().format(dateFormatter);
        String logEntry = String.format("[%s] [COMMAND] [%s] %s: /%s",
                timestamp, serverName, playerName, command);
        writeToFile(logFile, logEntry);
    }

    /**
     * 记录违规警告
     *
     * @param serverName 服务器名称
     * @param playerName 玩家名称
     * @param message 违规消息
     */
    public void logWarning(String serverName, String playerName, String message) {
        String timestamp = LocalDateTime.now().format(dateFormatter);
        String warnEntry = String.format("[%s] [WARN] [%s] %s: %s",
                timestamp, serverName, playerName, message);
        writeToFile(warnFile, warnEntry);
    }

    /**
     * 写入内容到指定文件
     *
     * @param file 目标文件
     * @param content 要写入的内容
     */
    private void writeToFile(File file, String content) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(content);
            writer.newLine();
        } catch (IOException e) {
            logger.error("写入日志时发生错误: {}", file.getName(), e);
        }
    }

    /**
     * 获取主日志文件路径
     *
     * @return 日志文件的绝对路径
     */
    public String getLogFilePath() {
        return logFile.getAbsolutePath();
    }
}
