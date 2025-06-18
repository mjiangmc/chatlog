package newblock.chatlog;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bstats.velocity.Metrics;
import org.slf4j.Logger;

import java.io.File;
import java.util.Optional;

@Plugin(
        id = "chatlog",
        name = "ChatLog",
        version = "1.0.0",
        url = "https://newblock.top",
        authors = {"NewBlockTeam"}
)
public class Chatlog {

    private static final int BSTATS_PLUGIN_ID = 26202;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Metrics.Factory metricsFactory;

    private File pluginDir;
    private ChatlogConfig config;
    private FilterManager filterManager;
    private LogManager logManager;
    private LangManager langManager;
    private FilterReplaceConfig filterReplaceConfig;

    @Inject
    public Chatlog(ProxyServer proxy, Logger logger, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 创建插件目录
        pluginDir = new File("plugins/chatlog");
        if (!pluginDir.exists() && !pluginDir.mkdirs()) {
            logger.error("无法创建插件目录: " + pluginDir.getAbsolutePath());
            return;
        }

        // 初始化 bStats
        Metrics metrics = metricsFactory.make(this, BSTATS_PLUGIN_ID);

        // 初始化各个管理器
        langManager = new LangManager(logger, pluginDir);
        config = new ChatlogConfig(logger, pluginDir);
        config.loadConfig();
        filterManager = new FilterManager(logger, config);
        filterReplaceConfig = new FilterReplaceConfig(logger, pluginDir);
        filterManager.setReplaceConfig(filterReplaceConfig);
        logManager = new LogManager(logger, pluginDir);

        // 注册命令
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("chatlog")
                        .aliases("cl", "chatlogcmd")
                        .build(),
                new ReloadCommand(logger, this)
        );

        // 打印启动信息
        String logPath = logManager.getLogFilePath();
        logger.info(getMessage("plugin.startup", logPath));
    }

    public String getMessage(String key, Object... args) {
        return langManager.getMessage(key, args);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info(getMessage("plugin.shutdown"));
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        CommandSource src = event.getCommandSource();
        if (!(src instanceof Player player)) return;

        String playerName = player.getUsername();
        if (config.isUserNameCheck() && filterManager.containsForbiddenWords(playerName)) {
            String cmd = config.getUserNamePunishmentCommand().replace("%player%", playerName);
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cmd);
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            logger.info(getMessage("violation.username", playerName, cmd));
            return;
        }

        String fullCommand = event.getCommand().trim();
        if (fullCommand.isEmpty()) return;

        String cmdName = fullCommand.split(" ")[0];
        if (config.getCheckCommands().contains(cmdName)) {
            String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("<unknown>");
            logManager.logCommand(serverName, playerName, fullCommand);
        }
    }

    @Subscribe
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        String playerName = player.getUsername();
        String message = event.getMessage();
        String serverName = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("<unknown>");

        if (config.isUserNameCheck() && filterManager.containsForbiddenWords(playerName)) {
            String cmd = config.getUserNamePunishmentCommand().replace("%player%", playerName);
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cmd);
            event.setResult(PlayerChatEvent.ChatResult.denied());
            logger.info(getMessage("violation.username", playerName, cmd));
            return;
        }

        if (filterManager.containsForbiddenWords(message)) {
            logManager.logWarning(serverName, playerName, message);
            String cmd = config.getPunishmentCommand().replace("%player%", playerName);
            proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), cmd);
            event.setResult(PlayerChatEvent.ChatResult.denied());
            logger.info(getMessage("violation.chat", playerName, message, cmd));
            return;
        }

        if (filterManager.hasReplaceConfig()) {
            String replaced = filterManager.replaceFilteredWords(message);
            if (replaced != null && !replaced.equals(message)) {
                event.setResult(PlayerChatEvent.ChatResult.message(replaced));
                logManager.logChat(serverName, playerName, message + " -> " + replaced);

                if (config.isNotifyReplacement()) {
                    Component notify = Component.text("[ChatLog] ", NamedTextColor.GOLD)
                            .append(Component.text(langManager.getMessage("message.replaced"), NamedTextColor.YELLOW));
                    player.sendMessage(notify);
                }
                return;
            }
        }

        logManager.logChat(serverName, playerName, message);
    }

    public void reloadConfig() {
        langManager.loadLang();
        config.loadConfig();
        filterManager.loadFilters();
        logger.info(getMessage("plugin.reload"));
    }
}
