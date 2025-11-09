package wueffi.checker.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static wueffi.checker.competitionchecker.LOGGER;

public class ValidBoardGenerator {
    private static final Random random = new Random();

    public static int[][] generateBoard() {
        int[][] board = new int[6][7];
        int redCount = 0;
        int yellowCount = 0;

        int totalMoves = random.nextInt(6 * 7 - 1) + 1;
        LOGGER.info("Generating board with {} moves.", totalMoves);

        for (int move = 0; move < totalMoves; move++) {
            List<Integer> freeCols = new ArrayList<>();
            for (int c = 0; c < 7; c++) {
                if (board[0][c] == 0) freeCols.add(c);
            }
            if (freeCols.isEmpty()) break;

            int col = freeCols.get(random.nextInt(freeCols.size()));
            int row = 5;
            while (row >= 0 && board[row][col] != 0) row--;

            if (row < 0) continue;

            int player = redCount <= yellowCount ? 1 : 2;
            board[row][col] = player;
            if (player == 1) redCount++;
            else yellowCount++;

            if (hasWinner(board)) {
                board[row][col] = 0;
                if (player == 1) redCount--;
                else yellowCount--;
                break;
            }
        }

        return board;
    }


    private static boolean hasWinner(int[][] board) {

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 4; col++) {
                int p = board[row][col];
                if (p != 0 && p == board[row][col + 1] && p == board[row][col + 2] && p == board[row][col + 3])
                    return true;
            }
        }

        for (int col = 0; col < 7; col++) {
            for (int row = 0; row < 3; row++) {
                int p = board[row][col];
                if (p != 0 && p == board[row + 1][col] && p == board[row + 2][col] && p == board[row + 3][col])
                    return true;
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int p = board[row][col];
                if (p != 0 && p == board[row + 1][col + 1] && p == board[row + 2][col + 2] && p == board[row + 3][col + 3])
                    return true;
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 3; col < 7; col++) {
                int p = board[row][col];
                if (p != 0 && p == board[row + 1][col - 1] && p == board[row + 2][col - 2] && p == board[row + 3][col - 3])
                    return true;
            }
        }
        return false;
    }
}