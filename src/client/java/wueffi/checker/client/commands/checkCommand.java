package wueffi.checker.client.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import wueffi.checker.BlockSelectListener;

import java.util.Objects;

public class checkCommand {

    public static void setup() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var checkCommand = CommandManager.literal("check")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                            .executes(wueffi.checker.client.commands.checkCommand::execute));

            var verifyCommand = CommandManager.literal("verify")
                    .then(CommandManager.argument("name", StringArgumentType.string())
                            .executes(wueffi.checker.client.commands.checkCommand::execute));

            dispatcher.register(checkCommand);
            dispatcher.register(verifyCommand);
        });
    }

    private static int execute(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        context.getSource().sendFeedback(() -> Text.literal("Checking: " + name), false);
        BlockSelectListener.awaitSelection(Objects.requireNonNull(context.getSource().getPlayer()), name);
        return 1;
    }
}
