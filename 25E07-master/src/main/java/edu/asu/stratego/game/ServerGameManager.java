package edu.asu.stratego.game;

import java.awt.Point;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.logging.*;

import edu.asu.stratego.game.board.ServerBoard;
import edu.asu.stratego.game.gameRules.OriginalRulesFactory;
import edu.asu.stratego.game.gameRules.RulesFactory;
import edu.asu.stratego.game.pieces.PieceColor;
import edu.asu.stratego.game.pieces.PieceType;
import edu.asu.stratego.game.gameRules.GameRules;
import edu.asu.stratego.util.CoordinateUtils;
import services.PlayerService;
import edu.asu.stratego.game.pieces.Piece;

/**
 * Task to manage a Stratego game between two clients.
 */
public class ServerGameManager implements Runnable {

    private static final Logger logger = Logger.getLogger(ServerGameManager.class.getName());

    private final String session;

    private ServerBoard board = new ServerBoard();

    private ObjectOutputStream toPlayerOne;
    private ObjectOutputStream toPlayerTwo;
    private ObjectInputStream fromPlayerOne;
    private ObjectInputStream fromPlayerTwo;

    private Player playerOne = new Player();
    private Player playerTwo = new Player();

    private Point playerOneFlag;
    private Point playerTwoFlag;

    private PieceColor turn;
    private Move move;

    private Socket socketOne;
    private Socket socketTwo;

    private volatile boolean gameAbandoned = false;

    RulesFactory rulesFactory = new OriginalRulesFactory();
    GameRules gameRules;

    /**
     * Creates a new instance of ServerGameManager.
     * 
     * @param sockOne    socket connected to Player 1's client.
     * @param sockTwo    socket connected to Player 2's client.
     * @param sessionNum the nth game session created by Server.
     * 
     * @see edu.asu.stratego.Server
     */
    public ServerGameManager(Socket sockOne, Socket sockTwo, int sessionNum) {
        this.session = "Session " + sessionNum + ": ";
        this.socketOne = sockOne;
        this.socketTwo = sockTwo;

        if (Math.random() < 0.5)
            this.turn = PieceColor.RED;
        else
            this.turn = PieceColor.BLUE;

        this.gameRules = rulesFactory.createOriginalRules(board, this);
    }

    /**
     * See ClientGameManager's run() method to understand how the server
     * interacts with the client.
     * 
     * @see edu.asu.stratego.game.ClientGameManager
     */
    @Override
    public void run() {
        createIOStreams();
        exchangePlayers();
        exchangeSetup();

        playGame();
    }

    private void resetServerBoard() {
        this.board = new ServerBoard(); // 🔄 Crear un nuevo tablero vacío
        this.playerOneFlag = null;
        this.playerTwoFlag = null;
        this.turn = (Math.random() < 0.5) ? PieceColor.RED : PieceColor.BLUE;
        this.move = null;
        logger.info(session + "Server board has been reset.");
    }

    /**
     * Establish IO object streams to facilitate communication between the
     * client and server.
     */
    private void createIOStreams() {
        try {
            logger.info(session + "Attempting to create IO Streams...");

            // 🔍 Verificar el estado de los sockets
            if (socketOne == null || socketTwo == null) {
                logger.severe(session + "One or both sockets are null. Cannot create streams.");
                return;
            }

            if (socketOne.isClosed() || socketTwo.isClosed()) {
                logger.severe(session + "One or both sockets are already closed. Cannot create streams.");
                return;
            }

            // 🔄 Crear los streams
            if (toPlayerOne == null) {
                logger.info(session + "Creating ObjectOutputStream for Player One...");
                toPlayerOne = new ObjectOutputStream(socketOne.getOutputStream());
                toPlayerOne.flush();
            } else {
                logger.warning(session + "ObjectOutputStream for Player One already exists.");
            }

            if (fromPlayerOne == null) {
                logger.info(session + "Creating ObjectInputStream for Player One...");
                fromPlayerOne = new ObjectInputStream(socketOne.getInputStream());
            } else {
                logger.warning(session + "ObjectInputStream for Player One already exists.");
            }

            if (toPlayerTwo == null) {
                logger.info(session + "Creating ObjectOutputStream for Player Two...");
                toPlayerTwo = new ObjectOutputStream(socketTwo.getOutputStream());
                toPlayerTwo.flush();
            } else {
                logger.warning(session + "ObjectOutputStream for Player Two already exists.");
            }

            if (fromPlayerTwo == null) {
                logger.info(session + "Creating ObjectInputStream for Player Two...");
                fromPlayerTwo = new ObjectInputStream(socketTwo.getInputStream());
            } else {
                logger.warning(session + "ObjectInputStream for Player Two already exists.");
            }

            logger.info(session + "Streams successfully created.");

        } catch (IOException e) {
            logger.log(Level.SEVERE, session + "Error establishing communication streams.", e);
            closeConnections(); // 🔴 Cerramos correctamente para evitar "sockets huérfanos".
            Thread.currentThread().interrupt(); // 🔴 Interrumpimos el hilo para evitar bucles infinitos.
        }
    }

    /**
     * Closes the socket connections and I/O streams safely.
     */
    private void closeConnections() {
        try {
            if (toPlayerOne != null)
                toPlayerOne.close();
            if (fromPlayerOne != null)
                fromPlayerOne.close();
            if (toPlayerTwo != null)
                toPlayerTwo.close();
            if (fromPlayerTwo != null)
                fromPlayerTwo.close();
            if (socketOne != null)
                socketOne.close();
            if (socketTwo != null)
                socketTwo.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, session + "Error while closing connections.", e);
        }
    }

    /**
     * Receive player information from the clients. Determines the players'
     * colors, and sends the player information of the opponents back to the
     * clients.
     */
    private void exchangePlayers() {
        try {
            logger.info(session + "Attempting to exchange players...");

            // 🔍 Verificar los streams antes de leer
            if (fromPlayerOne == null || fromPlayerTwo == null) {
                logger.log(Level.SEVERE, session + "Input streams are null. Cannot exchange players.");
                return;
            }

            // 🔄 Leer los jugadores
            logger.info(session + "Reading Player One from input stream...");
            playerOne = (Player) fromPlayerOne.readObject();
            logger.info(session + "Player One received: " + playerOne.getNickname());

            logger.info(session + "Reading Player Two from input stream...");
            playerTwo = (Player) fromPlayerTwo.readObject();
            logger.info(session + "Player Two received: " + playerTwo.getNickname());

            // 🔄 Asignar colores
            if (Math.random() < 0.5) {
                playerOne.setColor(PieceColor.RED);
                playerTwo.setColor(PieceColor.BLUE);
            } else {
                playerOne.setColor(PieceColor.BLUE);
                playerTwo.setColor(PieceColor.RED);
            }

            logger.info(session + "Assigned colors: " + playerOne.getColor() + " to " + playerOne.getNickname() +
                    ", " + playerTwo.getColor() + " to " + playerTwo.getNickname());

            // 🔄 Enviar información de los oponentes
            toPlayerOne.writeObject(playerTwo);
            toPlayerTwo.writeObject(playerOne);

            logger.info(session + "Player information exchanged successfully.");

        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, session + "Error receiving player information: Class not found.", e);
        } catch (IOException e) {
            logger.log(Level.SEVERE, session + "Error in I/O communication with players.", e);
        }
    }

    /**
     * Handles the initial exchange of the setup boards between the two players.
     * Pieces are registered on the server board and rotated 180 degrees for correct
     * orientation.
     */
    private void exchangeSetup() {
        try {
            // 🔍 Verificar que los streams no son nulos antes de leer
            if (fromPlayerOne == null || fromPlayerTwo == null) {
                logger.log(Level.SEVERE, session + "Error during setup exchange: Streams are null.");
                return;
            }
            SetupBoard setupBoardOne = (SetupBoard) fromPlayerOne.readObject();
            SetupBoard setupBoardTwo = (SetupBoard) fromPlayerTwo.readObject();

            if (setupBoardOne == null || setupBoardTwo == null) {
                logger.log(Level.SEVERE, session + "Error during setup exchange: Setup boards are null.");
                return;
            }

            // Register pieces on the server board
            for (int row = 0; row < 4; ++row) {
                for (int col = 0; col < 10; ++col) {
                    board.getSquare(row, col).setPiece(setupBoardOne.getPiece(3 - row, 9 - col));
                    board.getSquare(row + 6, col).setPiece(setupBoardTwo.getPiece(row, col));
                    if (setupBoardOne.getPiece(3 - row, 9 - col).getPieceType() == PieceType.FLAG)
                        playerOneFlag = new Point(row, col);
                    if (setupBoardTwo.getPiece(row, col).getPieceType() == PieceType.FLAG)
                        playerTwoFlag = new Point(row + 6, col);
                }
            }

            // Rotate pieces by 180 degrees
            for (int row = 0; row < 2; ++row) {
                for (int col = 0; col < 10; ++col) {
                    // Player One
                    Piece temp = setupBoardOne.getPiece(row, col);
                    setupBoardOne.setPiece(setupBoardOne.getPiece(3 - row, 9 - col), row, col);
                    setupBoardOne.setPiece(temp, 3 - row, 9 - col);
                    // Player Two
                    temp = setupBoardTwo.getPiece(row, col);
                    setupBoardTwo.setPiece(setupBoardTwo.getPiece(3 - row, 9 - col), row, col);
                    setupBoardTwo.setPiece(temp, 3 - row, 9 - col);
                }
            }

            GameStatus winCondition = checkWinCondition();

            toPlayerOne.writeObject(setupBoardTwo);
            toPlayerTwo.writeObject(setupBoardOne);
            toPlayerOne.writeObject(winCondition);
            toPlayerTwo.writeObject(winCondition);
        } catch (ClassNotFoundException | IOException e) {
            logger.log(Level.SEVERE, session + "Error during setup exchange.", e);
        }
    }

    /**
     * Handles game abandonment with different status options
     * 
     * @param status the abandonment reason (RED_DISCONNECTED, BLUE_DISCONNECTED, or
     *               DISCONNECTED)
     */
    public synchronized void abandonGame(GameStatus status) {
        if (gameAbandoned) {
            return;
        }

        logger.info(session + "Game abandoned with status: " + status);
        gameAbandoned = true;

        try {
            // Update points based on abandonment reason
            updatePlayerPoints(status);

            // Send abandonment status to both players
            toPlayerOne.writeObject(status);
            toPlayerTwo.writeObject(status);

            toPlayerOne.flush();
            toPlayerTwo.flush();

        } catch (IOException e) {
            logger.log(Level.SEVERE, session + "Error sending abandon status", e);
        } finally {
            resetServerBoard();
            closeConnections();
        }
    }

    /**
     * Handles game abandonment when called without specific status (defaults to
     * DISCONNECTED)
     */
    public synchronized void abandonGame() {
        abandonGame(GameStatus.DISCONNECTED);
    }

    /**
     * Updates player points based on game outcome
     * 
     * @param winCondition the game status that determines the winner
     */
    private void updatePlayerPoints(GameStatus winCondition) {
        PlayerService service = new PlayerService();
        try {
            // Determinar el color ganador y perdedor primero
            PieceColor winnerColor;
            PieceColor loserColor;

            switch (winCondition) {
                case RED_NO_MOVES:
                case RED_CAPTURED:
                    winnerColor = PieceColor.BLUE;
                    loserColor = PieceColor.RED;
                    break;

                case BLUE_NO_MOVES:
                case BLUE_CAPTURED:
                    winnerColor = PieceColor.RED;
                    loserColor = PieceColor.BLUE;
                    break;

                case RED_DISCONNECTED:
                    winnerColor = PieceColor.BLUE;
                    loserColor = PieceColor.RED;
                    break;

                case BLUE_DISCONNECTED:
                    winnerColor = PieceColor.RED;
                    loserColor = PieceColor.BLUE;
                    break;

                default:
                    logger.warning(session + "Unknown win condition: " + winCondition);
                    return;
            }

            // Ahora encontrar qué jugador tiene el color ganador
            models.Player winner = (playerOne.getColor() == winnerColor) ? service.findByEmail(playerOne.getEmail())
                    : service.findByEmail(playerTwo.getEmail());

            models.Player loser = (playerOne.getColor() == loserColor) ? service.findByEmail(playerOne.getEmail())
                    : service.findByEmail(playerTwo.getEmail());

            // Asignar puntos
            int pointsToAdd = (winCondition == GameStatus.RED_DISCONNECTED ||
                    winCondition == GameStatus.BLUE_DISCONNECTED) ? 50 : 100;

            winner.setPoints(winner.getPoints() + pointsToAdd);
            service.savePlayer(winner);

            logger.info(session + String.format(
                    "Awarded %d points to %s winner: %s (Color: %s)",
                    pointsToAdd,
                    winnerColor,
                    winner.getEmail(),
                    winnerColor));

            logger.info(session + String.format(
                    "Loser: %s (Color: %s)",
                    loser.getEmail(),
                    loserColor));

        } catch (Exception e) {
            logger.log(Level.SEVERE, session + "Error updating player points", e);
        }
    }

    /**
     * Main game loop.
     * Receives moves from players in turn, processes them, checks for a win
     * condition, and sends the result back to both players.
     */
    private void playGame() {
        while (!gameAbandoned) {
            try {
                // Get the move from the player based on the current turn
                move = getMoveFromPlayer(turn);

                // Check if game was abandoned during move reception
                if (gameAbandoned || move == null) {
                    break;
                }

                // Initialize the moves that will be sent to each player
                Move moveToPlayerOne = new Move();
                Move moveToPlayerTwo = new Move();

                // Register move on the board
                gameRules.processMove(move, moveToPlayerOne, moveToPlayerTwo);

                // Check if someone has won the game
                GameStatus winCondition = checkWinCondition();

                // If game is over, update points and send final status
                if (winCondition != GameStatus.IN_PROGRESS) {
                    updatePlayerPoints(winCondition);
                    sendMoveToPlayers(moveToPlayerOne, moveToPlayerTwo, winCondition);
                    break;
                }

                // Send updated moves and game status to both players
                sendMoveToPlayers(moveToPlayerOne, moveToPlayerTwo, winCondition);

                // Change turn color
                turn = (turn == PieceColor.RED) ? PieceColor.BLUE : PieceColor.RED;

            } catch (IOException | ClassNotFoundException e) {
                logger.log(Level.SEVERE, session + "Error occurred during network I/O", e);
                // If there's an IO error, treat it as abandonment
                abandonGame(GameStatus.DISCONNECTED);
                return;
            }
        }
        closeConnections();
    }

    /**
     * Evaluates the current game state to determine if there is a win condition.
     * Checks if either player has no available moves or if their flag has been
     * captured.
     * 
     * @return GameStatus representing the current status of the game.
     */
    private GameStatus checkWinCondition() {
        if (!hasAvailableMoves(PieceColor.RED))
            return GameStatus.RED_NO_MOVES;

        else if (isCaptured(PieceColor.RED))
            return GameStatus.RED_CAPTURED;

        if (!hasAvailableMoves(PieceColor.BLUE))
            return GameStatus.BLUE_NO_MOVES;

        else if (isCaptured(PieceColor.BLUE))
            return GameStatus.BLUE_CAPTURED;

        return GameStatus.IN_PROGRESS;
    }

    /**
     * Checks whether a player's flag has been captured.
     *
     * @param inColor The color of the player to check.
     * @return true if the flag is no longer present, false otherwise.
     */
    private boolean isCaptured(PieceColor inColor) {
        if (playerOne.getColor() == inColor) {
            if (board.getSquare(playerOneFlag.x, playerOneFlag.y).getPiece().getPieceType() != PieceType.FLAG)
                return true;
        }
        if (playerTwo.getColor() == inColor) {
            if (board.getSquare(playerTwoFlag.x, playerTwoFlag.y).getPiece().getPieceType() != PieceType.FLAG)
                return true;
        }

        return false;
    }

    /**
     * Checks if the player has at least one valid move available.
     *
     * @param inColor The color of the player to check.
     * @return true if at least one move exists, false otherwise.
     */
    private boolean hasAvailableMoves(PieceColor inColor) {
        for (int row = 0; row < 10; ++row) {
            for (int col = 0; col < 10; ++col) {
                if (board.getSquare(row, col).getPiece() != null
                        && board.getSquare(row, col).getPiece().getPieceColor() == inColor) {
                    if (gameRules.computeValidMoves(row, col, inColor).size() > 0) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Rotates the move coordinates by 180 degrees for Player One, while keeping
     * Player Two’s perspective intact.
     * Sets the move details for both players accordingly.
     *
     * @param move            The original move received.
     * @param moveToPlayerOne Move object populated for Player One (rotated).
     * @param moveToPlayerTwo Move object populated for Player Two (original).
     * @param startPiece      The piece that starts the move.
     * @param endPiece        The piece at the destination.
     * @param attackWin       Whether the attacking piece wins.
     * @param defendWin       Whether the defending piece wins.
     */
    public void rotateMove(Move move, Move moveToPlayerOne, Move moveToPlayerTwo, Piece startPiece, Piece endPiece,
            boolean attackWin, boolean defendWin) {
        moveToPlayerOne.setStart(CoordinateUtils.rotate180(move.getStart()));
        moveToPlayerOne.setEnd(CoordinateUtils.rotate180(move.getEnd()));
        moveToPlayerOne.setMoveColor(move.getMoveColor());
        moveToPlayerOne.setStartPiece(startPiece);
        moveToPlayerOne.setEndPiece(endPiece);
        moveToPlayerOne.setAttackWin(attackWin);
        moveToPlayerOne.setDefendWin(defendWin);

        moveToPlayerTwo.setStart(new Point(move.getStart().x, move.getStart().y));
        moveToPlayerTwo.setEnd(new Point(move.getEnd().x, move.getEnd().y));
        moveToPlayerTwo.setMoveColor(move.getMoveColor());
        moveToPlayerTwo.setStartPiece(startPiece);
        moveToPlayerTwo.setEndPiece(endPiece);
        moveToPlayerTwo.setAttackWin(attackWin);
        moveToPlayerTwo.setDefendWin(defendWin);
    }

    /**
     * Sends the turn color to both players and receives the move from the current
     * player.
     * Rotates the move coordinates for Player One to match the internal board
     * representation.
     *
     * @param turn The current player's color.
     * @return The move received from the appropriate player.
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private Move getMoveFromPlayer(PieceColor turn) throws IOException, ClassNotFoundException {
        // Send player turn color to clients
        toPlayerOne.writeObject(turn);
        toPlayerTwo.writeObject(turn);

        // Get move from client
        Object received = (playerOne.getColor() == turn) ? fromPlayerOne.readObject() : fromPlayerTwo.readObject();

        // Check if it's an abandon signal
        if (received instanceof String && ((String) received).equals("ABANDON")) {
            // Determine which player is abandoning
            GameStatus abandonStatus = (playerOne.getColor() == turn)
                    ? (playerOne.getColor() == PieceColor.RED ? GameStatus.RED_DISCONNECTED
                            : GameStatus.BLUE_DISCONNECTED)
                    : (playerTwo.getColor() == PieceColor.RED ? GameStatus.RED_DISCONNECTED
                            : GameStatus.BLUE_DISCONNECTED);

            abandonGame(abandonStatus);
            return null;
        }

        // Process normal move
        move = (Move) received;
        if (playerOne.getColor() == turn) {
            move.setStart(CoordinateUtils.rotate180(move.getStart()));
            move.setEnd(CoordinateUtils.rotate180(move.getEnd()));
        }
        return move;
    }

    /**
     * Sends the processed move and current game status to both players.
     *
     * @param moveToPlayerOne Move object to send to Player One.
     * @param moveToPlayerTwo Move object to send to Player Two.
     * @param winCondition    Current game status to send to both players.
     * @throws IOException
     */
    private void sendMoveToPlayers(Move moveToPlayerOne, Move moveToPlayerTwo, GameStatus winCondition)
            throws IOException {
        toPlayerOne.writeObject(moveToPlayerOne);
        toPlayerTwo.writeObject(moveToPlayerTwo);

        toPlayerOne.writeObject(winCondition);
        toPlayerTwo.writeObject(winCondition);
    }

}
