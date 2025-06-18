package newblock.chatlog;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理插件的语言配置
 */
public class LangManager {
    private final Logger logger;
    private final File langFile;
    private Map<String, String> messages;

    /**
     * 创建语言管理器
     *
     * @param logger 日志记录器
     * @param pluginDir 插件目录
     */
    public LangManager(Logger logger, File pluginDir) {
        this.logger = logger;
        this.langFile = new File(pluginDir, "lang.yml");
        this.messages = new HashMap<>();
        createDefaultLangFile();
        loadLang();
    }

    /**
     * 创建默认语言文件
     */
    private void createDefaultLangFile() {
        if (!langFile.exists()) {
            String defaultLang = ""
                    + "# ChatLog 语言配置文件\n"
                    + "\n"
                    + "# 插件消息\n"
                    + "plugin:\n"
                    + "  reload: \"§aChatLog 配置已重新加载！\"\n"
                    + "  reload_usage: \"§c用法：/chatlog reload\"\n"
                    + "  startup: \"ChatLog 插件已初始化，日志文件位置: {0}\"\n"
                    + "  shutdown: \"ChatLog 插件已关闭\"\n"
                    + "\n"
                    + "# 错误消息\n"
                    + "error:\n"
                    + "  create_dir: \"无法创建插件目录: {0}\"\n"
                    + "  create_file: \"无法创建{0}文件: {1}\"\n"
                    + "  write_log: \"写入{0}时发生错误\"\n"
                    + "\n"
                    + "# 违规处理消息\n"
                    + "violation:\n"
                    + "  username: \"玩家 {0} 用户名包含违禁词，已执行命令: {1}\"\n"
                    + "  chat: \"玩家 {0} 发送消息被拦截（包含违禁词）。内容: {1} 已执行命令: {2}\"\n"
                    + "\n"
                    + "# 配置相关消息\n"
                    + "config:\n"
                    + "  loaded_commands: \"已加载 CheckCommands，命令数: {0}\"\n"
                    + "  loaded_filters: \"已加载 {0} 个违禁词正则\"\n"
                    + "  default_config: \"已生成默认 config.yml，请根据需求修改各项配置\"\n"
                    + "  default_filter: \"已生成默认 filter.yml，请根据需求在每行添加违禁词正则\"\n"
                    + "  load_error: \"读取 {0} 时发生错误，使用默认配置\"\n"
                    + "\n"
                    + "# 消息替换提示\n"
                    + "message:\n"
                    + "  replaced: \"您的消息中包含敏感词，已被自动替换\"\n";

            try {
                Files.write(langFile.toPath(), defaultLang.getBytes());
                logger.info("已生成默认 lang.yml");
            } catch (IOException e) {
                logger.error("创建默认 lang.yml 时出错", e);
            }
        }
    }

    /**
     * 加载语言配置
     */
    @SuppressWarnings("unchecked")
    public void loadLang() {
        messages.clear();
        Yaml yaml = new Yaml(new SafeConstructor());

        try (InputStream in = new FileInputStream(langFile)) {
            Map<String, Object> data = yaml.load(in);
            flattenMap("", data);
            logger.info("已加载语言配置");
        } catch (IOException e) {
            logger.error("读取 lang.yml 时发生错误", e);
        }
    }

    /**
     * 将嵌套的Map扁平化为点分隔的key
     */
    @SuppressWarnings("unchecked")
    private void flattenMap(String prefix, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map) {
                flattenMap(key, (Map<String, Object>) entry.getValue());
            } else {
                messages.put(key, String.valueOf(entry.getValue()));
            }
        }
    }

    /**
     * 获取指定key的消息，并替换参数
     *
     * @param key 消息key
     * @param args 替换参数
     * @return 格式化后的消息
     */
    public String getMessage(String key, Object... args) {
        String message = messages.getOrDefault(key, key);
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return message;
    }
}
