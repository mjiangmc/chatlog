package newblock.chatlog;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;  // 新增：命令执行事件
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;                      // 新增：Velocity 中的 Player 接口
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Plugin(
        id = "chatlog",
        name = "ChatLog",
        version = "1.0.0",
        url = "https://newblock.top",
        authors = {"NewBlockTeam"}
)
public class Chatlog {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer proxy;

    private File logFile;       // 聊天与命令日志文件
    private File configFile;    // config.yml
    private File filterFile;    // filter.yml

    // ===== 新增字段 =====
    private List<String> checkCommands;             // 要检测并记录的命令名列表
    private boolean userNameCheck;                  // 是否开启“用户名检测”
    private String userNamePunishmentCommand;       // 用户名检测违规时执行的命令模板
    // ==================

    private String punishmentCommand;               // 聊天内容违禁词处罚命令
    private List<Pattern> forbiddenPatterns;        // 违禁词正则列表（用于匹配聊天内容与用户名）

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 创建插件目录
        File pluginDir = new File("plugins/chatlog");
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            logger.error("无法创建插件目录: " + pluginDir.getAbsolutePath());
            return;
        }

        // 创建或加载 chat.log 文件
        logFile = new File(pluginDir, "chat.log");
        try {
            if (!logFile.exists() && !logFile.createNewFile()) {
                logger.error("无法创建日志文件: " + logFile.getAbsolutePath());
            } else {
                logger.info("ChatLog 插件已初始化，日志文件位置: " + logFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("创建日志文件时发生错误", e);
        }

        // 创建或加载 config.yml
        configFile = new File(pluginDir, "config.yml");
        if (!configFile.exists()) {
            // 如果不存在，则生成包含原有与新增配置项的示例
            String defaultConfig = ""
                    + "# 原有：检测聊天内容违禁词时执行的命令模板\n"
                    + "punishment-command: \"/tempmute %player% 10m 言语违规\"\n"
                    + "\n"
                    + "# 新增：要检测并记录的命令列表，去掉前导斜杠\n"
                    + "CheckCommands:\n"
                    + "  - 'w'\n"
                    + "  - 'msg'\n"
                    + "  - 'me'\n"
                    + "  - 'tell'\n"
                    + "  - 'hh'\n"
                    + "  - 'pc'\n"
                    + "  - 'f'\n"
                    + "\n"
                    + "# 新增：是否检测用户名，若为 true，则玩家发送聊天或命令时会先检查用户名是否含违禁词。\n"
                    + "UserNameCheck: true\n"
                    + "\n"
                    + "# 新增：用户名违规时执行的命令模板，%player% 会被替换为实际玩家名\n"
                    + "UserName-punishment-command: \"kick %player% 您的用户名包含违禁词请更换用户名\"\n";
            try {
                Files.write(configFile.toPath(), defaultConfig.getBytes());
                logger.info("已生成默认 config.yml，请根据需求修改各项配置");
            } catch (IOException e) {
                logger.error("创建默认 config.yml 时出错", e);
            }
        }

        // 创建或加载 filter.yml
        filterFile = new File(pluginDir, "filter.yml");
        if (!filterFile.exists()) {
            // 如果不存在，则生成示例，用户自行添加正则
            try {
                Files.write(filterFile.toPath(), (
                        "# 在这里填写每行一个违禁词的正则表达式\n" +
                                "# 例如：\n" +
                                "# badword1\n" +
                                "# (?i)\\bexample\\b\n"
                ).getBytes());
                logger.info("已生成默认 filter.yml，请根据需求在每行添加违禁词正则");
            } catch (IOException e) {
                logger.error("创建默认 filter.yml 时出错", e);
            }
        }

        // 读取配置与编译正则
        loadConfigAndFilters();

        // 注册 /chatlog reload 命令
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("chatlog")
                        .aliases("cl", "chatlogcmd")
                        .build(),
                new ReloadCommand()
        );
        logger.info("已注册 /chatlog reload 命令");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("ChatLog 插件已关闭");
    }

    /**
     * 监听玩家执行的“命令”事件，准确检测并记录指定命令，同时进行用户名违禁检测。
     */
    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        // 获取命令来源，如果不是玩家，则直接返回
        CommandSource src = event.getCommandSource();
        if (!(src instanceof Player)) {
            return;  // 只有玩家执行的命令才处理
        }
        Player player = (Player) src;
        String playerName = player.getUsername();

        // 1. 如果开启了“用户名检测”，先检测玩家名称是否含违禁词
        if (userNameCheck) {
            for (Pattern namePattern : forbiddenPatterns) {
                if (namePattern.matcher(playerName).find()) {
                    // 用户名含有违禁词，执行处罚命令并拦截命令执行
                    String cmd = userNamePunishmentCommand.replace("%player%", playerName);
                    CommandSource console = proxy.getConsoleCommandSource();
                    proxy.getCommandManager().executeAsync(console, cmd);

                    // 取消本次命令，返回 DENIED
                    event.setResult(CommandExecuteEvent.CommandResult.denied());
                    logger.info("玩家 {} 用户名包含违禁词，已执行命令: {}", playerName, cmd);
                    return;
                }
            }
        }

        // 2. 解析命令名：event.getCommand() 返回不带前导“/”的完整命令字符串
        String fullCommand = event.getCommand().trim();  // 如 "w 玩家A Hello"
        if (fullCommand.isEmpty()) {
            return; // 空命令不处理
        }
        // 截取第一个空格前的部分作为命令名
        String cmdName = fullCommand.contains(" ")
                ? fullCommand.substring(0, fullCommand.indexOf(" "))
                : fullCommand;

        // 3. 如果该命令在配置的 CheckCommands 列表里，就写入日志
        if (checkCommands.contains(cmdName)) {
            Optional<ServerConnection> serverConn = player.getCurrentServer();
            String serverName = serverConn.map(conn -> conn.getServerInfo().getName()).orElse("<unknown>");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            // 日志格式： [时间] [COMMAND] [服务器名] 玩家名: /原始命令
            String logEntry = String.format("[%s] [COMMAND] [%s] %s: /%s", timestamp, serverName, playerName, fullCommand);
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(logEntry);
                writer.newLine();
            } catch (IOException e) {
                logger.error("写入命令日志时发生错误", e);
            }
            // 不拦截命令，保持 event.getResult() 默认值 ALLOWED
        }
        // 命令执行后，Velocity 会继续寻找该命令的注册者并执行对应逻辑
    }

    /**
     * 监听玩家普通聊天事件，进行用户名检测（仅当该内容不是命令时会触发）与聊天内容违禁词过滤。
     */
    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        String playerName = event.getPlayer().getUsername();
        String message = event.getMessage();

        // ===== 用户名检测 =====
        // 如果此时玩家在聊天框发送消息且之前没触发命令事件，就在这里再次检测用户名
        if (userNameCheck) {
            for (Pattern namePattern : forbiddenPatterns) {
                if (namePattern.matcher(playerName).find()) {
                    // 用户名含有违禁词，执行处罚命令并拦截聊天
                    String cmd = userNamePunishmentCommand.replace("%player%", playerName);
                    CommandSource console = proxy.getConsoleCommandSource();
                    proxy.getCommandManager().executeAsync(console, cmd);

                    event.setResult(PlayerChatEvent.ChatResult.denied());
                    logger.info("玩家 {} 用户名包含违禁词，已执行命令: {}", playerName, cmd);
                    return;
                }
            }
        }

        // ===== 聊天内容检测违禁词 =====
        for (Pattern p : forbiddenPatterns) {
            if (p.matcher(message).find()) {
                // 用 punishmentCommand 处罚并拦截
                String cmd = punishmentCommand.replace("%player%", playerName);
                CommandSource console = proxy.getConsoleCommandSource();
                proxy.getCommandManager().executeAsync(console, cmd);

                event.setResult(PlayerChatEvent.ChatResult.denied());
                logger.info("玩家 {} 发送消息被拦截（包含违禁词）。已执行命令: {}", playerName, cmd);
                return;
            }
        }

        // ===== 普通聊天，写入日志 =====
        Optional<ServerConnection> serverConn = event.getPlayer().getCurrentServer();
        String serverName = serverConn.map(conn -> conn.getServerInfo().getName()).orElse("<unknown>");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 控制台输出（无时间戳）
        String consoleOutput = String.format("[%s] %s: %s", serverName, playerName, message);
        logger.info(consoleOutput);

        // 日志文件输出（带时间戳）
        String logEntry = String.format("[%s] [%s] %s: %s", timestamp, serverName, playerName, message);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(logEntry);
            writer.newLine();
        } catch (IOException e) {
            logger.error("写入聊天日志时发生错误", e);
        }
    }

    /**
     * 从 config.yml 中读取各项配置：
     *   1. punishment-command（聊天违禁词处罚命令）
     *   2. CheckCommands（要检测并记录的命令列表）
     *   3. UserNameCheck（是否检测用户名）
     *   4. UserName-punishment-command（用户名检测违规时的处罚命令模板）
     * 并从 filter.yml 中读取所有正则并编译为 Pattern 列表（forbiddenPatterns）。
     */
    @SuppressWarnings("unchecked")
    private void loadConfigAndFilters() {
        // --- 加载 config.yml 中的配置 ---
        Yaml yaml = new Yaml(new SafeConstructor());
        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);

            // 1. 原有：读取 punishment-command
            Object cmdObj = data.get("punishment-command");
            if (cmdObj != null) {
                punishmentCommand = cmdObj.toString().trim();
                logger.info("已加载 punishment-command: {}", punishmentCommand);
            } else {
                punishmentCommand = "/tempmute %player% 10m 言语违规";
                logger.warn("config.yml 中未找到 punishment-command，使用默认: {}", punishmentCommand);
            }

            // 2. 新增：读取 CheckCommands 列表
            Object checkCmdsObj = data.get("CheckCommands");
            checkCommands = new ArrayList<>();
            if (checkCmdsObj instanceof List) {
                for (Object o : (List<Object>) checkCmdsObj) {
                    checkCommands.add(o.toString().trim());
                }
                logger.info("已加载 CheckCommands，命令数: {}", checkCommands.size());
            } else {
                logger.warn("config.yml 中未找到 CheckCommands，默认不检测任何命令");
            }

            // 3. 新增：读取 UserNameCheck
            Object unameCheckObj = data.get("UserNameCheck");
            if (unameCheckObj != null) {
                userNameCheck = Boolean.parseBoolean(unameCheckObj.toString());
            } else {
                userNameCheck = false;
                logger.warn("config.yml 中未找到 UserNameCheck，默认不检测用户名");
            }
            logger.info("UserNameCheck: {}", userNameCheck);

            // 4. 新增：读取 UserName-punishment-command
            Object unamePunishObj = data.get("UserName-punishment-command");
            if (unamePunishObj != null) {
                userNamePunishmentCommand = unamePunishObj.toString().trim();
                logger.info("已加载 UserName-punishment-command: {}", userNamePunishmentCommand);
            } else {
                userNamePunishmentCommand = "kick %player% 用户名违规";
                logger.warn("config.yml 中未找到 UserName-punishment-command，使用默认: {}", userNamePunishmentCommand);
            }

        } catch (IOException e) {
            // 若读取失败，则使用默认值
            punishmentCommand = "/tempmute %player% 10m 言语违规";
            checkCommands = new ArrayList<>();
            userNameCheck = false;
            userNamePunishmentCommand = "kick %player% 用户名违规";
            logger.error("读取 config.yml 时发生错误，使用默认配置", e);
        }

        // --- 加载 filter.yml 中的每行正则 ---
        forbiddenPatterns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filterFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 忽略空行或注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                try {
                    Pattern p = Pattern.compile(line);
                    forbiddenPatterns.add(p);
                } catch (Exception ex) {
                    logger.error("无法编译正则: {}，跳过此行。错误: {}", line, ex.getMessage());
                }
            }
            logger.info("已加载 {} 个违禁词正则", forbiddenPatterns.size());
        } catch (IOException e) {
            logger.error("读取 filter.yml 时发生错误，违禁词列表可能为空", e);
        }
    }

    /**
     * 处理 /chatlog reload 命令，重新加载 config.yml 与 filter.yml。
     */
    private class ReloadCommand implements com.velocitypowered.api.command.SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();
            String[] args = invocation.arguments();

            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                loadConfigAndFilters();
                sender.sendMessage(
                        net.kyori.adventure.text.Component.text("§aChatLog 配置已重新加载！")
                );
                logger.info("由 {} 执行了 /chatlog reload，配置重载完成。",
                        sender instanceof Player
                                ? ((Player) sender).getUsername()
                                : "Console");
            } else {
                sender.sendMessage(
                        net.kyori.adventure.text.Component.text("§c用法：/chatlog reload")
                );
            }
        }

        @Override
        public List<String> suggest(Invocation invocation) {
            // 提示参数
            if (invocation.arguments().length == 0) {
                List<String> suggestions = new ArrayList<>();
                suggestions.add("reload");
                return suggestions;
            }
            return new ArrayList<>();
        }
    }
}
