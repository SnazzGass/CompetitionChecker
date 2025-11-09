package wueffi.checker.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wueffi.checker.BlockSelectListener;
import wueffi.checker.client.commands.checkCommand;

public class competitioncheckerClient implements ClientModInitializer {
    public static Logger LOGGER = LoggerFactory.getLogger("[Competition Checker]");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing Competition Checker...");
        checkCommand.setup();
        LOGGER.info("Registered Commands");
        BlockSelectListener.register();
        LOGGER.info("Registered Select Listener");
    }

    public static void sendClientMessage(String message) {
        assert MinecraftClient.getInstance().player != null;
        MinecraftClient.getInstance().player.networkHandler.sendChatMessage(message);
    }
}
