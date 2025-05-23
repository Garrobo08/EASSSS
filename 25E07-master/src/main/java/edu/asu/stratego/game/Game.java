package edu.asu.stratego.game;

import java.time.LocalDateTime;

import edu.asu.stratego.game.board.ClientBoard;
import edu.asu.stratego.game.pieces.PieceColor;
import jakarta.persistence.ManyToOne;

/**
 * Contains information about the Stratego game, which is shared between the
 * JavaFX GUI and the ClientGameManager.
 * 
 * @see edu.asu.stratego.gui.ClientStage
 * @see edu.asu.stratego.game.ClientGameManager
 */
public class Game {

    private static Player player;
    private static Player opponent;

    private static Move move;
    private static MoveStatus moveStatus;

    private static GameStatus status;
    private static PieceColor turn;
    private static ClientBoard board;
    private static LocalDateTime startTime;

    @ManyToOne
    private Player currentPlayer;

    /**
     * Initializes data fields for a new game.
     */
    public Game() {
        player = new Player();
        opponent = new Player();

        move = new Move();
        moveStatus = MoveStatus.OPP_TURN;

        status = GameStatus.SETTING_UP;
        turn = PieceColor.RED;

        board = new ClientBoard();
    }

    /**
     * @return Player object containing information about the player.
     */
    public static Player getPlayer() {
        return player;
    }

    /**
     * @param player Player object containing information about the player.
     */
    public static void setPlayer(Player player) {
        Game.player = player;
    }

    /**
     * @return Player object containing information about the opponent.
     */
    public static Player getOpponent() {
        return opponent;
    }

    /**
     * @param opponent Player object containing information about the opponent.
     */
    public static void setOpponent(Player opponent) {
        Game.opponent = opponent;
    }

    /**
     * @return value the status of the game.
     */
    public static GameStatus getStatus() {
        return status;
    }

    /**
     * @param status the status of the game.
     */
    public static void setStatus(GameStatus status) {
        Game.status = status;
    }

    /**
     * @return value the color of the current player's turn.
     */
    public static PieceColor getTurn() {
        return turn;
    }

    /**
     * @param turn the color of the current player's turn.
     */
    public static void setTurn(PieceColor turn) {
        Game.turn = turn;
    }

    /**
     * @return the game board.
     */
    public static ClientBoard getBoard() {
        return board;
    }

    /**
     * @param board the game board.
     */
    public static void setBoard(ClientBoard board) {
        Game.board = board;
    }

    /**
     * @return the Move object.
     */
    public static Move getMove() {
        return move;
    }

    /**
     * @param move the Move object.
     */
    public static void setMove(Move move) {
        Game.move = move;
    }

    /**
     * @return the MoveStatus.
     */
    public static MoveStatus getMoveStatus() {
        return moveStatus;
    }

    /**
     * @param moveStatus the MoveStatus.
     */
    public static void setMoveStatus(MoveStatus moveStatus) {
        Game.moveStatus = moveStatus;
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public void setCurrentPlayer(Player player) {
        this.currentPlayer = player;
    }

    public static void setStartTime(LocalDateTime start) {
        startTime = start;
    }

    public static LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * Resets the game state to prepare for a new game
     */
    public static void resetGame() {
        // Reset game board
        board = new ClientBoard();

        // Reset players
        if (player != null) {
            player.setColor(null);
        }
        opponent = null;

        // Reset game status
        status = GameStatus.SETTING_UP;
        turn = PieceColor.RED;
        move = new Move(); // 🔄 Reiniciar con un objeto vacío
        moveStatus = MoveStatus.OPP_TURN;
    }

}
