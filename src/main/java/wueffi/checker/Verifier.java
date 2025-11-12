package wueffi.checker;

import com.mojang.brigadier.ParseResults;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.text.Text;
import net.minecraft.world.tick.TickManager;
import wueffi.checker.helpers.ValidBoardGenerator;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static wueffi.checker.competitionchecker.server;

public class Verifier {

    private static final List<VerificationTask> activeTasks = new ArrayList<>();
    private static ServerPlayerEntity player;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            activeTasks.removeIf(task -> !task.tick(server.getOverworld()));
        });
    }

    public static void verify(PlayerEntity player1, String name) {
        BlockPos nwbCorner = BlockSelectListener.getPositions().get(name);
        if (nwbCorner == null) return;

        player = server.getPlayerManager().getPlayer(player1.getUuid());
        if (player == null) return;

        ServerWorld world = player.getWorld();
        if (world == null) return;

        int[][] board = ValidBoardGenerator.generateBoard();
        setInputs(world, nwbCorner, board);

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> activeTasks.add(new VerificationTask(player, world, nwbCorner, board, 1)), 50, TimeUnit.MILLISECONDS);
    }

    private static void setInputs(ServerWorld world, BlockPos nwbCorner, int[][] gameState) {
        player.sendMessage(Text.literal("§7[CC-DEBUG] Setting inputs..."), false);

        int startX = nwbCorner.getX() - 1;
        int startY = nwbCorner.getY() + 26;
        int startZ = nwbCorner.getZ() + 6;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int y = startY - (row * 4);
                int z = startZ + (col * 4);
                BlockPos redPos = new BlockPos(startX, y, z);
                BlockPos yellowPos = new BlockPos(startX, y, z + 2);
                int state = gameState[row][col];

                world.setBlockState(redPos, (state == 1 || state == 3) ? Blocks.REDSTONE_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
                world.setBlockState(yellowPos, (state == 2 || state == 3) ? Blocks.REDSTONE_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
            }
        }
    }

    private static class VerificationTask {
        private static final int TOTAL_TICKS = 600;

        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final BlockPos corner;
        private final int testCount;
        private final int[][] gameState;

        private int tickCounter = 0;

        VerificationTask(ServerPlayerEntity player, ServerWorld world, BlockPos corner, int[][] gameState, int count) {
            this.player = player;
            this.world = world;
            this.corner = corner;
            this.testCount = count;
            this.gameState = gameState;
        }

        boolean tick(ServerWorld server) {
            if (!world.getRegistryKey().equals(server.getServer().getOverworld().getRegistryKey())) return true;

            if (tickCounter == 0) {
                ServerCommandSource source = player.getCommandSource();

                // speed up the game for the duration of the tests
                String command = "tick sprint " + 600 * testCount;
                ParseResults<ServerCommandSource> parse = player.getServer().getCommandManager().getDispatcher().parse(command, source);
                player.getServer().getCommandManager().execute(parse, command);
            }

            tickCounter++;
            if (tickCounter < TOTAL_TICKS) return true;

            // read the machine outputs
            boolean[] output = readOutputs(world, corner);

            int onCount = 0; // the number of outputs that are on (powered)
            int outIndex = -1; // the index of the output that is on
            for (int i = 0; i < output.length; i++) {
                if (output[i]) {
                    onCount++;
                    outIndex = i;
                }
            }

            if (onCount == 0) player.sendMessage(Text.literal("§7[CC] No output is on!"), false);
            else if (onCount > 1) player.sendMessage(Text.literal("§7[CC] Multiple outputs! Expected 1, got " + onCount), false);
            else if (gameState[0][outIndex] != 0) player.sendMessage(Text.literal("§7[CC] Invalid output! Tried to play in a column that is already full"), false);
            else player.sendMessage(Text.literal("§7[CC] Test passed!"), false);

            return false;
        }
    }

    private static boolean[] readOutputs(ServerWorld world, BlockPos nwbCorner) {
        boolean[] outputs = new boolean[7];
        int outputX = nwbCorner.getX() + 51;
        int outputY = nwbCorner.getY() + 26;
        int startZ = nwbCorner.getZ() + 7;

        for (int i = 0; i < 7; i++) {
            int z = startZ + (i * 4);
            if (world.getBlockState(new BlockPos(outputX, outputY, z)).getBlock() instanceof RepeaterBlock) {
                outputs[i] = world.getBlockState(new BlockPos(outputX, outputY, z)).get(RepeaterBlock.POWERED);
            } else {
                outputs[i] = false;
            }
        }
        return outputs;
    }
}
