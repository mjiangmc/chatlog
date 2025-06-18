package newblock.chatlog;

import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 管理违禁词替换配置
 */
public class FilterReplaceConfig {
    private final Logger logger;
    private final File configFile;
    private List<String> replacePatterns;
    private String replaceWith;

    /**
     * 创建违禁词替换配置管理器
     *
     * @param logger 日志记录器
     * @param pluginDir 插件目录
     */
    public FilterReplaceConfig(Logger logger, File pluginDir) {
        this.logger = logger;
        this.configFile = new File(pluginDir, "filter_replace.yml");
        this.replacePatterns = new ArrayList<>();
        this.replaceWith = "*";
        createDefaultConfig();
        loadConfig();
    }

    /**
     * 创建默认配置文件
     */
    private void createDefaultConfig() {
        if (!configFile.exists()) {
            String defaultConfig = ""
                    + "# ChatLog 违禁词替换配置\n"
                    + "\n"
                    + "# 替换为的字符，默认为*\n"
                    + "replace_with: '*'\n"
                    + "\n"
                    + "# 需要替换的违禁词正则表达式列表\n"
                    + "# 每行一个正则表达式\n"
                    + "patterns:\n"
                    + "  - 'cnm'\n";

            try {
                Files.write(configFile.toPath(), defaultConfig.getBytes());
                logger.info("已生成默认 filter_replace.yml");
            } catch (IOException e) {
                logger.error("创建默认 filter_replace.yml 时出错", e);
            }
        }
    }

    /**
     * 加载配置
     */
    @SuppressWarnings("unchecked")
    public void loadConfig() {
        replacePatterns.clear();
        Yaml yaml = new Yaml(new SafeConstructor());

        try (InputStream in = new FileInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);

            // 加载替换字符
            if (data.containsKey("replace_with")) {
                replaceWith = String.valueOf(data.get("replace_with"));
            }

            // 加载替换模式
            if (data.containsKey("patterns")) {
                Object patternsObj = data.get("patterns");
                if (patternsObj instanceof List) {
                    List<String> patterns = (List<String>) patternsObj;
                    for (String pattern : patterns) {
                        if (pattern != null && !pattern.trim().isEmpty()) {
                            try {
                                // 预编译检查正则表达式的有效性
                                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
                                replacePatterns.add(pattern.trim());
                            } catch (PatternSyntaxException e) {
                                logger.error("无效的替换正则表达式: {}", pattern, e);
                            }
                        }
                    }
                    logger.info("已加载 {} 个有效的替换正则表达式", replacePatterns.size());
                } else {
                    logger.error("patterns配置项必须是一个列表");
                }
            } else {
                logger.warn("未找到patterns配置项，替换列表将为空");
            }
        } catch (IOException e) {
            logger.error("读取 filter_replace.yml 时发生错误", e);
        }
    }

    /**
     * 获取替换模式列表
     */
    public List<String> getReplacePatterns() {
        return replacePatterns;
    }

    /**
     * 获取替换字符
     */
    public String getReplaceWith() {
        return replaceWith;
    }
}
