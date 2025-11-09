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
        scheduler.schedule(() -> activeTasks.add(new VerificationTask(player, world, nwbCorner)), 50, TimeUnit.MILLISECONDS);
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
        private static final int CHECK_INTERVAL = 2;

        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final BlockPos corner;
        private final boolean[] originalOutput;
        private final boolean[] firstOutput = new boolean[7];
        private final boolean[] finalOutput = new boolean[7];

        private int tickCounter = 0;
        private int ticksUntilFirstOutput = -1;

        VerificationTask(ServerPlayerEntity player, ServerWorld world, BlockPos corner) {
            this.player = player;
            this.world = world;
            this.corner = corner;
            this.originalOutput = readOutputs(world, corner);
        }

        boolean tick(ServerWorld server) {
            if (!world.getRegistryKey().equals(server.getServer().getOverworld().getRegistryKey())) return true;

            if (tickCounter == 0) {
                ServerCommandSource source = player.getCommandSource();

                String command = "tick rate 1200";
                ParseResults<ServerCommandSource> parse = player.getServer().getCommandManager().getDispatcher().parse(command, source);

                player.getServer().getCommandManager().execute(parse, command);
            }

            int originalOutputs = 0;
            for (boolean b : originalOutput) if (b) originalOutputs++;

            if (originalOutputs != 0) ticksUntilFirstOutput = -2;

            tickCounter++;
            if (tickCounter % CHECK_INTERVAL != 0) return true;

            boolean[] current = readOutputs(world, corner);
            int onCount = 0;
            for (boolean b : current) if (b) onCount++;

            if (ticksUntilFirstOutput == -1 && onCount > 0 && !Arrays.equals(current, originalOutput)) {
                ticksUntilFirstOutput = tickCounter;
                System.arraycopy(current, 0, firstOutput, 0, 7);
                player.sendMessage(Text.literal("§7[CC] Output detected at " + tickCounter + " ticks (" + tickCounter/20.0 + " s)!"), false);
            }

            if (ticksUntilFirstOutput >= 0 && tickCounter > ticksUntilFirstOutput) {
                if (!Arrays.equals(current, firstOutput)) {
                    player.sendMessage(Text.literal("§7[CC] Output changed! Expected: " + Arrays.toString(firstOutput) + " Got: " + Arrays.toString(current)), false);
                    return false;
                }
            }

            if (tickCounter >= TOTAL_TICKS - CHECK_INTERVAL) {
                System.arraycopy(current, 0, finalOutput, 0, 7);
            }

            if (tickCounter >= TOTAL_TICKS) {
                ServerCommandSource source = player.getCommandSource();

                String command = "tick rate 20";
                ParseResults<ServerCommandSource> parse = player.getServer().getCommandManager().getDispatcher().parse(command, source);

                player.getServer().getCommandManager().execute(parse, command);

                validateFinalOutput();
                return false;
            }

            return true;
        }

        private void validateFinalOutput() {
            int outputsOn = 0;
            for (boolean b : finalOutput) if (b) outputsOn++;

            if (ticksUntilFirstOutput == -1) player.sendMessage(Text.literal("§7[CC] No output detected within 30 seconds!"), false);
            else if (outputsOn == 0) player.sendMessage(Text.literal("§7[CC] Output turned off by end!"), false);
            else if (outputsOn > 1) player.sendMessage(Text.literal("§7[CC] Multiple outputs! Expected 1, got " + outputsOn), false);
            else if (ticksUntilFirstOutput == -2) player.sendMessage(Text.literal("§7[CC] Valid output detected after 0 ticks. (0,0s)"), false);
            else if (!Arrays.equals(finalOutput, firstOutput)) player.sendMessage(Text.literal("§7[CC] Output changed! Initial: " + Arrays.toString(firstOutput) + " Final: " + Arrays.toString(finalOutput)), false);
            else player.sendMessage(Text.literal("§7[CC] Valid output detected after " + ticksUntilFirstOutput + " ticks (" + ticksUntilFirstOutput/20.0 + " s)"), false);
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
