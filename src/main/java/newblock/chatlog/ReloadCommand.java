package newblock.chatlog;

import com.velocitypowered.api.command.SimpleCommand;
import org.slf4j.Logger;

public class ReloadCommand implements SimpleCommand {
    private final Logger logger;
    private final Chatlog plugin;

    public ReloadCommand(Logger logger, Chatlog plugin) {
        this.logger = logger;
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length == 0) {
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text(
                plugin.getMessage("plugin.reload_usage")
            ));
            return;
        }

        if (invocation.arguments()[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text(
                plugin.getMessage("plugin.reload")
            ));
        } else {
            invocation.source().sendMessage(net.kyori.adventure.text.Component.text(
                plugin.getMessage("plugin.reload_usage")
            ));
        }
    }

    @Override
    public boolean hasPermission(final Invocation invocation) {
        return invocation.source().hasPermission("chatlog.admin");
    }
}
