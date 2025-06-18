package newblock.chatlog;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理ChatLog插件的配置管理
 */
public class ChatlogConfig {
    private final Logger logger;
    private final File configFile;
    private final File filterFile;

    // 配置项
    private String punishmentCommand;               // 聊天内容违禁词处罚命令
    private List<String> checkCommands;             // 要检测并记录的命令名列表
    private boolean userNameCheck;                  // 是否开启"用户名检测"
    private String userNamePunishmentCommand;       // 用户名检测违规时执行的命令模板
    private boolean notifyReplacement;              // 是否通知玩家消息被替换

    /**
     * 创建配置管理器
     *
     * @param logger 日志记录器
     * @param pluginDir 插件目录
     */
    public ChatlogConfig(Logger logger, File pluginDir) {
        this.logger = logger;
        this.configFile = new File(pluginDir, "config.yml");
        this.filterFile = new File(pluginDir, "filter.yml");

        // 初始化默认值
        this.punishmentCommand = "/tempmute %player% 10m 言语违规";
        this.checkCommands = new ArrayList<>();
        this.userNameCheck = false;
        this.userNamePunishmentCommand = "kick %player% 用户名违规";

        // 创建配置文件（如果不存在）
        createConfigIfNotExists();
        createFilterIfNotExists();
    }

    /**
     * 创建默认配置文件（如果不存在）
     */
    private void createConfigIfNotExists() {
        if (!configFile.exists()) {
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
                    + "UserName-punishment-command: \"kick %player% 您的用户名包含违禁词请更换用户名\"\n"
                    + "\n"
                    + "# 新增：是否通知玩家消息被替换\n"
                    + "NotifyReplacement: true\n";
            try {
                Files.write(configFile.toPath(), defaultConfig.getBytes());
                logger.info("已生成默认 config.yml，请根据需求修改各项配置");
            } catch (IOException e) {
                logger.error("创建默认 config.yml 时出错", e);
            }
        }
    }

    /**
     * 创建默认过滤器文件（如果不存在）
     */
    private void createFilterIfNotExists() {
        if (!filterFile.exists()) {
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
    }

    /**
     * 加载配置文件
     */
    @SuppressWarnings("unchecked")
    public void loadConfig() {
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
    }

    /**
     * 获取过滤器文件
     * @return 过滤器文件
     */
    public File getFilterFile() {
        return filterFile;
    }

    /**
     * 获取聊天内容违禁词处罚命令
     * @return 处罚命令
     */
    public String getPunishmentCommand() {
        return punishmentCommand;
    }

    /**
     * 获取要检测并记录的命令名列表
     * @return 命令名列表
     */
    public List<String> getCheckCommands() {
        return checkCommands;
    }

    /**
     * 是否开启用户名检测
     * @return 是否开启
     */
    public boolean isUserNameCheck() {
        return userNameCheck;
    }

    /**
     * 获取用户名检测违规时执行的命令模板
     * @return 命令模板
     */
    public String getUserNamePunishmentCommand() {
        return userNamePunishmentCommand;
    }

    public boolean isNotifyReplacement() {
        return notifyReplacement;
    }
}
