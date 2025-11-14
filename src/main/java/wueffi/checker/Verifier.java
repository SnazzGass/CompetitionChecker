package wueffi.checker;

import com.mojang.brigadier.ParseResults;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.block.RepeaterBlock;
import net.minecraft.text.Text;
import net.minecraft.world.tick.TickManager;
import wueffi.checker.helpers.BoardFormatter;
import wueffi.checker.helpers.ValidBoardGenerator;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import static wueffi.checker.competitionchecker.server;
import static wueffi.checker.competitionchecker.LOGGER;

public class Verifier {

    private static final List<VerificationTask> activeTasks = new ArrayList<>();
    private static final Random random = new Random();
    private static String name;
    private static ServerPlayerEntity player;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            activeTasks.removeIf(task -> !task.tick(server.getOverworld()));
        });
    }

    public static void verify(PlayerEntity player1, String name, int count, boolean seeded, long seed) {
        BlockPos nwbCorner = BlockSelectListener.getPositions().get(name);
        if (nwbCorner == null) return;

        Verifier.name = name;
        player = server.getPlayerManager().getPlayer(player1.getUuid());
        if (player == null) return;

        ServerWorld world = player.getWorld();
        if (world == null) return;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> activeTasks.add(new VerificationTask(player, world, nwbCorner, count, seeded, seed)), 50, TimeUnit.MILLISECONDS);
    }

    private static void setInputs(ServerWorld world, BlockPos nwbCorner, int[][] gameState) {
        //player.sendMessage(Text.literal("§7[CC-DEBUG] Setting inputs..."), false);

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
        private static final int TEST_TICKS = 600;

        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final BlockPos corner;
        private final int testCount;
        private final long[] testSeeds;
        private final int[] results;
        private int[][] board = new int[0][];

        private int tickCounter = 0;
        private int testCounter = 0;

        VerificationTask(ServerPlayerEntity player, ServerWorld world, BlockPos corner, int count, boolean seeded, long seed) {
            this.player = player;
            this.world = world;
            this.corner = corner;
            if (seeded) {
                testCount = 1;
                testSeeds = new long[] {seed};
                results = new int[1];
            }
            else {
                testCount = count;
                testSeeds = new long[count];
                for (int i = 0; i < count; i++) testSeeds[i] = random.nextLong();
                results = new int[count];

                // speed up the game for the duration of the tests
                ServerCommandSource source = player.getCommandSource();
                String command = "tick sprint " + TEST_TICKS * testCount;
                ParseResults<ServerCommandSource> parse = player.getServer().getCommandManager().getDispatcher().parse(command, source);
                player.getServer().getCommandManager().execute(parse, command);
            }
        }

        boolean tick(ServerWorld server) {
            if (!world.getRegistryKey().equals(server.getServer().getOverworld().getRegistryKey())) return true;

            if (tickCounter == 0) {

                // generate a board to verify
                board = ValidBoardGenerator.generateBoard(testSeeds[testCounter]);
                // test using the board
                LOGGER.info("testing board:\n" + BoardFormatter.format(board));
                setInputs(world, corner, board);
            }

            tickCounter++;
            if (tickCounter < TEST_TICKS) return true;

            // read the machine outputs
            boolean[] output = readOutputs(world, corner);

            int onCount = 0; // the number of outputs that are on (powered)
            int outIndex = -1; // the index of the output that is on
            boolean fullBoard = true;
            for (int i = 0; i < output.length; i++) {
                if (output[i]) {
                    onCount++;
                    outIndex = i;
                }
                if (board[0][i] == 0) fullBoard = false;
            }

            if (fullBoard) results[testCounter] = 0; // output doesn't matter because the game is over
            else if (onCount == 0) results[testCounter] = 1;
            else if (onCount > 1) results[testCounter] = 2;
            else if (board[0][outIndex] != 0) results[testCounter] = 3;
            else results[testCounter] = 0;

            testCounter++;
            tickCounter = 0;

            if (testCounter == testCount){
                // completed all tests
                summary();
                return false;
            }

            return true;
        }

        private void summary() {
            int passes = 0;
            for (int result : results) if (result == 0) passes++;

            player.sendMessage(Text.literal("§7[CC] Passed " + passes + "/" + testCount + " test(s)"), false);

            for (int i = 0; i < testCount; i++) {
                int result = results[i];
                String outString = "§4Unexpected result for test " + (i + 1);
                if (result == 0) outString = "§aTest " + (i + 1) + " Passed!";
                else if (result == 1) outString = "§cTest " + (i + 1) + " Failed. No output!";
                else if (result == 2) outString = "§cTest " + (i + 1) + " Failed. Multiple outputs!";
                else if (result == 3) outString = "§cTest " + (i + 1) + " Failed. Tried to play in a column that is already full!";

                player.sendMessage(Text.literal("§7[CC] " + outString).setStyle(Style.EMPTY.withClickEvent(new ClickEvent.SuggestCommand("/verify " + name + " seed " + testSeeds[i]))));
            }
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
