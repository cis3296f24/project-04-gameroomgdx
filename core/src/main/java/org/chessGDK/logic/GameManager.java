package org.chessGDK.logic;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import org.chessGDK.pieces.*;
import org.chessGDK.ai.StockfishAI;
import org.chessGDK.ui.ChessBoardScreen;

import java.io.IOException;


public class GameManager extends ScreenAdapter {
    private final Object turnLock = new Object();
    private boolean whiteTurn;
    private final Piece[][] board;
    private final Piece[] castlingPieces;
    private final StockfishAI stockfishAI;
    private final int DEPTH = 12;
    private int halfMoves;
    private String castlingRights;
    private String enPassantSquare;

    public GameManager() throws IOException {
        board = new Piece[8][8];
        whiteTurn = true;
        castlingPieces = new Piece[6];
        setupPieces();
        stockfishAI = new StockfishAI(DEPTH);
        printBoard();
        halfMoves = 0;
        castlingRights = "KQkq";
        enPassantSquare = null;
    }

    private void setupPieces() {
        // Place white pawns on the second row (index 1)
        for (int col = 0; col < 8; col++) {
            board[1][col] = new Pawn(true); // White pawns
        }
        // Place white major pieces on the first row (index 0)
        board[0][0] = new Rook(true);    // White rook
        board[0][7] = new Rook(true);    // White rook
        board[0][1] = new Knight(true);  // White knight
        board[0][6] = new Knight(true);  // White knight
        board[0][2] = new Bishop(true);  // White bishop
        board[0][5] = new Bishop(true);  // White bishop
        board[0][3] = new Queen(true);   // White queen
        board[0][4] = new King(true);    // White king

        // Place black pawns on the seventh row (index 6)
        for (int col = 0; col < 8; col++) {
            board[6][col] = new Pawn(false); // Black pawns
        }
        // Place black major pieces on the eighth row (index 7)
        board[7][0] = new Rook(false);   // Black rook
        board[7][7] = new Rook(false);   // Black rook
        board[7][1] = new Knight(false); // Black knight
        board[7][6] = new Knight(false); // Black knight
        board[7][2] = new Bishop(false); // Black bishop
        board[7][5] = new Bishop(false); // Black bishop
        board[7][3] = new Queen(false);  // Black queen
        board[7][4] = new King(false);   // Black king

        // white queenside rook
        castlingPieces[0] = board[0][0];
        // white king
        castlingPieces[1] = board[0][4];
        // white kingside rook
        castlingPieces[2] = board[0][7];

        // black queenside rook
        castlingPieces[3] = board[7][0];
        // black king
        castlingPieces[4] = board[7][4];
        // black kingside rook
        castlingPieces[5] = board[7][7];
    }

    public boolean movePiece(String move) {

        if (move.isEmpty()) {
            return false;
        }
        char[] parsedMove = parseMove(move);
        int startCol = parsedMove[0];
        int startRow = parsedMove[1];
        int endCol = parsedMove[2];
        int endRow = parsedMove[3];
        char newRank;
        Piece piece = board[startRow][startCol];
        enPassantSquare = null;

        // Ensure the right piece color is moving according to the turn
        if (piece != null && piece.isValidMove(startCol, startRow, endCol, endRow, board)) {
            board[endRow][endCol] = piece;
            board[startRow][startCol] = null;
            //screen.startPieceAnimation(piece, startCol, startRow, endCol, endRow);
            if (parsedMove.length == 5) {
                newRank = parsedMove[4];
                promote(newRank, endRow, endCol);
            }
            if (piece instanceof Pawn && piece.enPassant()) {
                int direction = startRow < endRow ? 1 : -1;
                char temp = move.charAt(3);
                temp += (char)direction;
                enPassantSquare = (char)('a' + endCol) + "" + temp ;
            }
            //piece.toggleAnimating();
            printBoard();
            whiteTurn = !whiteTurn;
            halfMoves++;
            piece.setPosition(endCol * Gdx.graphics.getWidth()/8, endRow * Gdx.graphics.getHeight()/8);
            if (!whiteTurn) {
                aiTurn();
            }
            return true;
        }
        return false;
    }

    private static char[] parseMove(String bestMove) {
        if (bestMove == null) {
            return null;
        }
        char[] parsed;
        //parsed = bestMove.getBytes(StandardCharsets.US_ASCII);
        parsed = bestMove.toCharArray();

        // Changes rank/file ASCII representation into array indexes
        parsed[0] -= 'a';
        parsed[1] -= '1';
        parsed[2] -= 'a';
        parsed[3] -= '1';
        return parsed;
    }

    private boolean promote(char rank, int endRow, int endCol) {
        return switch (rank) {
            case 'q' -> {
                board[endRow][endCol] = new Queen(whiteTurn);
                yield true;
            }
            case 'r' -> {
                board[endRow][endCol] = new Rook(whiteTurn);
                yield true;
            }
            case 'b' -> {
                board[endRow][endCol] = new Bishop(whiteTurn);
                yield true;
            }
            case 'n' -> {
                board[endRow][endCol] = new Knight(whiteTurn);
                yield true;
            }
            default -> false;
        };
    }

    public String generateFen() {
        StringBuilder fen = new StringBuilder();
        // 1. Piece Placement
        for (int row = 7; row >= 0; row--) {
            int emptyCount = 0;

            for (int col = 0; col < 8; col++) {
                Piece piece = board[row][col];

                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    fen.append(piece);
                }
            }

            if (emptyCount > 0) {
                fen.append(emptyCount);
            }

            if (row > 0) {
                fen.append("/");
            }
        }
        // 2. Active Color (w or b)
        fen.append(whiteTurn ? " w " : " b ");

        // 3. Castling Availability (KQkq or -)
        if (!castlingRights.isEmpty()) {
            if (castlingPieces[1].hasMoved()) {
                castlingRights = castlingRights.replace("K", "");
                castlingRights = castlingRights.replace("Q", "");
            }
            if (castlingPieces[4].hasMoved()) {
                castlingRights = castlingRights.replace("k", "");
                castlingRights = castlingRights.replace("q", "");
            }
            if (castlingPieces[0].hasMoved()) {
                castlingRights = castlingRights.replace("Q", "");
            }
            if (castlingPieces[2].hasMoved()) {
                castlingRights = castlingRights.replace("K", "");
            }
            if (castlingPieces[3].hasMoved()) {
                castlingRights = castlingRights.replace("q", "");
            }
            if (castlingPieces[5].hasMoved()) {
                castlingRights = castlingRights.replace("k", "");
            }
        }

        fen.append(castlingRights.isEmpty() ? "-" : castlingRights);
        fen.append(" ");

        // 4. En Passant Target Square (e.g., e3 or -)
        fen.append(enPassantSquare != null ? enPassantSquare : "-");
        fen.append(" ");

        // 5. Halfmove Clock
        fen.append(halfMoves).append(" ");

        // 6. Fullmove Number
        fen.append(halfMoves / 2);

        return fen.toString();
    }

    public void makeNextMove() {
        synchronized (turnLock) {  // Use synchronized block to ensure only one thread runs this section at a time
            if (whiteTurn) {
                boolean moveMade = aiTurn(); // White player move logic
                if (!moveMade) {
                    System.out.println("White move failed");
                }
            } else {
                boolean moveMade = aiTurn(); // Black (AI) move logic
                if (!moveMade) {
                    System.out.println("Black move failed");
                }
            }
        }
    }

    private boolean playerTakeTurn() {

        return true;
    }

    public boolean aiTakeTurn() {
        String bestMove;
        String fen;
        boolean moved = false;
        fen = generateFen();
        try {
            bestMove = getBestMove(fen);
            System.out.println("FEN: " + fen + "\nBest Move: " + bestMove);
            if (bestMove.equalsIgnoreCase("(none)"))
                return false;
            moved = movePiece(bestMove);
            System.out.println("Move " + bestMove + ": " + moved);
            printBoard();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    public boolean aiTurn() {
        String fen;
        fen = generateFen();
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                try {
                    // Retrieve the best move from Stockfish after the delay
                    String bestMove = getBestMove(fen);
                    System.out.println("FEN: " + fen + "\nBest Move: " + bestMove);
                    if (bestMove.equalsIgnoreCase("(none)"))
                        return;

                    boolean moved = movePiece(bestMove);
                    System.out.println("Move " + bestMove + ": " + moved);
                    //printBoard();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, .5f); // Delay by 1 second
        return true;
    }

    public String getBestMove(String fen) throws IOException {
        return stockfishAI.getBestMove(fen);
    }

    public StockfishAI getAI() {
        return stockfishAI;
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public Piece[][] getBoard() {
        return board;
    }

    public void printBoard() {
        for (int row = 7; row >= 0; row--) {
            System.out.print((row + 1) + " ");
            for (int col = 0; col < 8; col++) {
                Piece piece = board[row][col];
                if (piece != null) {
                    System.out.print(piece + " ");
                } else {
                    System.out.print("- ");
                }
            }
            System.out.println();
        }
        System.out.println("  a b c d e f g h");
    }

    @Override
    public void render(float delta) {
        super.render(delta);

    }

    public void exitGame() {
        if (stockfishAI != null) {
            try {
                stockfishAI.close();  // Close the Stockfish AI
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // Perform any other cleanup needed for the game
        System.out.println("Game exited.");
    }
}