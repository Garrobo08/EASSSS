package edu.asu.stratego.gui;

import edu.asu.stratego.game.Game;
import edu.asu.stratego.game.ResourceBundleManager;
import edu.asu.stratego.languages.LanguageObservable;
import edu.asu.stratego.languages.LanguageObserver;
import edu.asu.stratego.media.ImageConstants;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.layout.HBox;
import services.GameService;
import services.PlayerService;
import models.Player;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

public class HistoryScene implements LanguageObserver {

    private Scene scene;
    private VBox gamesContainer = new VBox(15);
    private Label titleLabel = new Label();
    private Button backButton = new Button();
    private static final int SIDE = ClientStage.getSide();

    public HistoryScene(Runnable onBack) {
        LanguageObservable.addObserver(this);

        titleLabel.setStyle("-fx-font-size: 24px; -fx-text-fill: white; -fx-font-weight: bold;");
        backButton.setStyle("-fx-font-size: 18px; -fx-pref-width: 220px; -fx-pref-height: 45px;");
        gamesContainer.setAlignment(Pos.CENTER);
        gamesContainer.setPadding(new Insets(10));

        VBox historyBox = new VBox(20, titleLabel, gamesContainer, backButton);
        historyBox.setAlignment(Pos.CENTER);

        ImageView logoImage = new ImageView(ImageConstants.stratego_logo);
        logoImage.setFitWidth(SIDE / 2.0);
        logoImage.setPreserveRatio(true);
        VBox.setMargin(logoImage, new Insets(0, 0, 20, 0));

        VBox content = new VBox(logoImage, historyBox);
        content.setAlignment(Pos.CENTER);

        ImageView backgroundImage = new ImageView(ImageConstants.MAIN_MENU);
        backgroundImage.setFitHeight(SIDE);
        backgroundImage.setFitWidth(SIDE);
        backgroundImage.setPreserveRatio(false);

        StackPane root = new StackPane(backgroundImage, content);
        root.setMaxSize(SIDE, SIDE);
        this.scene = new Scene(root, SIDE, SIDE);

        backButton.setOnAction(e -> onBack.run());

        updateTexts();
        loadGamesHistory(); // Cargar historial al construir la escena
    }

    @Override
    public void onLanguageChanged() {
        updateTexts();
    }

    private void updateTexts() {
        titleLabel.setText(ResourceBundleManager.get("menu.history"));
        backButton.setText(ResourceBundleManager.get("menu.back"));
    }

    public Scene getScene() {
        return scene;
    }

    private void loadGamesHistory() {
        PlayerService playerService = new PlayerService();
        GameService gameService = new GameService();

        String nickname = Game.getPlayer().getNickname();
        Player dbPlayer = playerService.findByNickname(nickname);

        if (dbPlayer == null)
            return;

        List<models.Game> games = gameService.findGamesByPlayerNickname(nickname);

        Platform.runLater(() -> {
            gamesContainer.getChildren().clear();

            if (games.isEmpty()) {
                Label noGamesLabel = new Label(ResourceBundleManager.get("history.nogames"));
                noGamesLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
                gamesContainer.getChildren().add(noGamesLabel);
                return;
            }

            for (models.Game game : games) {
                gamesContainer.getChildren().add(createGameCard(game, dbPlayer));
            }
        });
    }

    private HBox createGameCard(models.Game game, Player player) {
        HBox card = new HBox(20);
        card.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5); -fx-padding: 15; -fx-background-radius: 10;");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(SIDE * 0.8);

        // Fecha
        Label dateLabel = new Label(
                game.getEndTime().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)));
        dateLabel.setFont(Font.font(14));
        dateLabel.setTextFill(Color.WHITE);

        // Resultado
        Label resultLabel = new Label();
        resultLabel.setFont(Font.font(16));
        if (game.isWasAbandoned()) {
            resultLabel.setText(ResourceBundleManager.get("history.abandoned")); // 🔁 Traducible
            resultLabel.setTextFill(Color.GOLD);
        } else if (game.getWinner() == null) {
            resultLabel.setText(ResourceBundleManager.get("history.finished"));
            resultLabel.setTextFill(Color.LIGHTGRAY);
        } else if (game.getWinner().getId().equals(player.getId())) {
            resultLabel.setText(ResourceBundleManager.get("history.won"));
            resultLabel.setTextFill(Color.LIGHTGREEN);
        } else {
            resultLabel.setText(ResourceBundleManager.get("history.lost"));
            resultLabel.setTextFill(Color.INDIANRED);
        }

        // Duración
        long minutes = Duration.between(game.getStartTime(), game.getEndTime()).toMinutes();
        Label durationLabel = new Label(minutes + " " + ResourceBundleManager.get("history.minutes"));
        durationLabel.setFont(Font.font(14));
        durationLabel.setTextFill(Color.LIGHTGRAY);

        card.getChildren().addAll(dateLabel, resultLabel, durationLabel);
        return card;
    }
}
