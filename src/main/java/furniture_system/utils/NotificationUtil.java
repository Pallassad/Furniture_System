package furniture_system.utils;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

public class NotificationUtil {

    public enum Type { SUCCESS, ERROR, WARNING, INFO }

    private static final int DISPLAY_MS = 3000;
    private static final int FADE_MS    = 280;

    public static void success(Node anchor, String msg) { show(anchor, Type.SUCCESS, "✔  " + msg); }
    public static void error  (Node anchor, String msg) { show(anchor, Type.ERROR,   "✖  " + msg); }
    public static void warning(Node anchor, String msg) { show(anchor, Type.WARNING, "⚠  " + msg); }
    public static void info   (Node anchor, String msg) { show(anchor, Type.INFO,    "ℹ  " + msg); }

    private static void show(Node anchor, Type type, String text) {
        Platform.runLater(() -> {
            StackPane stack = findContentArea(anchor);
            if (stack == null) return;

            Label lbl = new Label(text);
            lbl.setWrapText(true);
            lbl.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;"
            );

            HBox toast = new HBox(lbl);
            toast.setAlignment(Pos.CENTER_LEFT);
            toast.setPadding(new Insets(16));
            toast.setStyle(
                "-fx-background-color: " + bgColor(type) + ";" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.30), 10, 0, 0, 3);"
            );
            toast.setOpacity(0);

            // KEY FIX: remove from layout flow entirely,
            // then position manually after stack gets its size
            toast.setManaged(false);
            stack.getChildren().add(toast);

            // Position after layout pass so we know stack's actual size
            stack.layout();
            double toastW = 280;
            double toastH = 56;
            double x = (stack.getWidth()  - toastW) / 2;
            double y =  stack.getHeight() - toastH - 28;
            toast.resizeRelocate(x, y, toastW, toastH);

            // Re-position if stack size changes (e.g. window resize)
            stack.widthProperty().addListener((obs, o, n) -> {
                toast.resizeRelocate((n.doubleValue() - toastW) / 2,
                        stack.getHeight() - toastH - 28, toastW, toastH);
            });

            Timeline tl = new Timeline(
                new KeyFrame(Duration.ZERO,
                    new KeyValue(toast.opacityProperty(), 0)),
                new KeyFrame(Duration.millis(FADE_MS),
                    new KeyValue(toast.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(FADE_MS + DISPLAY_MS),
                    new KeyValue(toast.opacityProperty(), 1)),
                new KeyFrame(Duration.millis(FADE_MS + DISPLAY_MS + FADE_MS),
                    new KeyValue(toast.opacityProperty(), 0))
            );
            tl.setOnFinished(e -> stack.getChildren().remove(toast));
            tl.play();
        });
    }

    private static StackPane findContentArea(Node anchor) {
        for (Window w : Stage.getWindows()) {
            if (w.isFocused() && w.getScene() != null) {
                Node found = w.getScene().getRoot().lookup("#contentArea");
                if (found instanceof StackPane sp) return sp;
            }
        }
        if (anchor != null && anchor.getScene() != null) {
            Node found = anchor.getScene().getRoot().lookup("#contentArea");
            if (found instanceof StackPane sp) return sp;
        }
        for (Window w : Stage.getWindows()) {
            if (w.isShowing() && w.getScene() != null) {
                Node found = w.getScene().getRoot().lookup("#contentArea");
                if (found instanceof StackPane sp) return sp;
            }
        }
        return null;
    }

    private static String bgColor(Type type) {
        return switch (type) {
            case SUCCESS -> "#1e7e4a";
            case ERROR   -> "#c0392b";
            case WARNING -> "#d97706";
            case INFO    -> "#1e40af";
        };
    }
}