package wueffi.checker;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class competitionchecker implements ModInitializer {
    public static Logger LOGGER = LoggerFactory.getLogger("[Competition Checker]");
    public static MinecraftServer server;

    @Override
    public void onInitialize() {
        ServerTickEvents.START_SERVER_TICK.register(server1 -> {
            server = server1;
        });
        Verifier.init();
        LOGGER.info("Registered Events");
    }
}
