package com.tasteflow.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * TasteFlow Animation Engine — Pure JavaFX, zero external dependencies.
 *
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │  ANIMATIONS SELECTED FOR A RESTAURANT POS SYSTEM                   │
 * ├───────────────────────┬─────────────────────────────────────────────┤
 * │  FadeTransition       │ Module transitions, notifications          │
 * │  ScaleTransition      │ Hover grow, click pulse, bounce entrance   │
 * │  TranslateTransition  │ Slide panels in/out, shake for errors      │
 * │  RotateTransition     │ Refresh button spin, loading indicator     │
 * │  FillTransition       │ Order status color morphing                │
 * │  StrokeTransition     │ Input validation glow effects              │
 * │  PauseTransition      │ Auto-dismiss status messages               │
 * │  ParallelTransition   │ Combine fade + slide + scale together      │
 * │  SequentialTransition │ Choreograph multi-step sequences           │
 * │  Timeline (KeyFrame)  │ Counter roll-up, typing effects            │
 * ├───────────────────────┴─────────────────────────────────────────────┤
 * │  NOT USED: PathTransition (for games), AnimationTimer (game loops) │
 * └─────────────────────────────────────────────────────────────────────┘
 */
public final class AnimationUtil {

    private AnimationUtil() { /* no instances */ }

    /* ═══════════════════════════════════════════════════════
     *  ① FadeTransition — OPACITY CONTROL
     *  Used for: smooth appearance / disappearance
     * ═══════════════════════════════════════════════════════ */

    /** Fade a node from invisible to fully visible. */
    public static void fadeIn(Node node, double durationMs, double delaySec) {
        node.setOpacity(0);
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(0);
        ft.setToValue(1);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ft.setDelay(Duration.seconds(delaySec));
        ft.play();
    }

    /** Fade a node out, then remove it from its parent. */
    public static void fadeOutAndRemove(Node node, double durationMs) {
        FadeTransition ft = new FadeTransition(Duration.millis(durationMs), node);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setInterpolator(Interpolator.EASE_IN);
        ft.setOnFinished(e -> {
            if (node.getParent() instanceof javafx.scene.layout.Pane pane) {
                pane.getChildren().remove(node);
            }
        });
        ft.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ② ScaleTransition — SIZE ANIMATION
     *  Used for: hover grow, click pulse, bounce entrance
     * ═══════════════════════════════════════════════════════ */

    /** Hover: smoothly scale up to 1.05× on mouse enter, back on exit. */
    public static void applyHoverScale(Node node) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(180), node);
        scaleUp.setToX(1.05);
        scaleUp.setToY(1.05);
        scaleUp.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(180), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        scaleDown.setInterpolator(Interpolator.EASE_OUT);

        node.setOnMouseEntered(e -> { scaleDown.stop(); scaleUp.playFromStart(); });
        node.setOnMouseExited(e -> { scaleUp.stop(); scaleDown.playFromStart(); });
    }

    /** Click: quick squish-down (0.93×) then spring-back on press. */
    public static void applyClickPulse(Node node) {
        ScaleTransition down = new ScaleTransition(Duration.millis(80), node);
        down.setToX(0.93);
        down.setToY(0.93);
        down.setInterpolator(Interpolator.EASE_IN);

        ScaleTransition up = new ScaleTransition(Duration.millis(80), node);
        up.setToX(1.0);
        up.setToY(1.0);
        up.setInterpolator(Interpolator.EASE_OUT);

        SequentialTransition pulse = new SequentialTransition(down, up);
        node.setOnMousePressed(e -> pulse.playFromStart());
    }

    /* ═══════════════════════════════════════════════════════
     *  ③ TranslateTransition — POSITION MOVEMENT
     *  Used for: slide-in panels, error shake
     * ═══════════════════════════════════════════════════════ */

    /** Shake a node horizontally — great for invalid input feedback. */
    public static void shake(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setOnFinished(e -> node.setTranslateX(0));
        tt.play();
    }

    /** Slide a node in from the left edge. */
    public static void slideInFromLeft(Node node, double delaySec) {
        node.setOpacity(0);
        node.setTranslateX(-40);

        TranslateTransition slide = new TranslateTransition(Duration.millis(400), node);
        slide.setFromX(-40);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(400), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        ParallelTransition pt = new ParallelTransition(slide, fade);
        pt.setDelay(Duration.seconds(delaySec));
        pt.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ④ RotateTransition — ROTATION
     *  Used for: refresh button spin, loading indicator
     * ═══════════════════════════════════════════════════════ */

    /** Spin a node 360° once — perfect for refresh buttons. */
    public static void spinOnce(Node node) {
        RotateTransition rt = new RotateTransition(Duration.millis(600), node);
        rt.setByAngle(360);
        rt.setCycleCount(1);
        rt.setInterpolator(Interpolator.EASE_BOTH);
        rt.play();
    }

    /** Continuous spin — for loading indicators. Call .stop() on returned animation. */
    public static RotateTransition spinContinuous(Node node) {
        RotateTransition rt = new RotateTransition(Duration.millis(1200), node);
        rt.setByAngle(360);
        rt.setCycleCount(Animation.INDEFINITE);
        rt.setInterpolator(Interpolator.LINEAR);
        rt.play();
        return rt;
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑤ FillTransition — COLOR MORPHING
     *  Used for: order status indicators changing color
     *  (e.g. yellow "Preparing" → green "Ready")
     * ═══════════════════════════════════════════════════════ */

    /** Morph the fill color of a Shape from one color to another. */
    public static void morphColor(Shape shape, Color from, Color to, double durationMs) {
        FillTransition ft = new FillTransition(Duration.millis(durationMs), shape);
        ft.setFromValue(from);
        ft.setToValue(to);
        ft.setInterpolator(Interpolator.EASE_BOTH);
        ft.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑥ StrokeTransition — BORDER COLOR ANIMATION
     *  Used for: input validation glow (red → normal on fix)
     * ═══════════════════════════════════════════════════════ */

    /** Pulse the border of a shape between two colors — validation feedback. */
    public static void pulseStroke(Shape shape, Color from, Color to, int cycles) {
        StrokeTransition st = new StrokeTransition(Duration.millis(400), shape);
        st.setFromValue(from);
        st.setToValue(to);
        st.setCycleCount(cycles * 2); // each full pulse = 2 half-cycles
        st.setAutoReverse(true);
        st.setInterpolator(Interpolator.EASE_BOTH);
        st.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑦ PauseTransition — TIMED DELAY
     *  Used for: auto-dismiss status messages after N seconds
     * ═══════════════════════════════════════════════════════ */

    /** Show a message on a label, then auto-clear it after a delay. */
    public static void flashMessage(Label label, String message, double seconds) {
        label.setText(message);
        label.setOpacity(1);

        PauseTransition pause = new PauseTransition(Duration.seconds(seconds));
        pause.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(400), label);
            fadeOut.setToValue(0);
            fadeOut.setOnFinished(ev -> { label.setText(""); label.setOpacity(1); });
            fadeOut.play();
        });
        pause.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑧ ParallelTransition — COMBINE EFFECTS
     *  Used for: fade+slide+scale all at once
     * ═══════════════════════════════════════════════════════ */

    /** Fade in + slide up 20px simultaneously — dashboard card entrance. */
    public static void fadeInAndSlideUp(Node node, double delaySec) {
        node.setOpacity(0);
        node.setTranslateY(20);

        FadeTransition fade = new FadeTransition(Duration.millis(450), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(450), node);
        slide.setFromY(20);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.seconds(delaySec));
        pt.play();
    }

    /** Fade in + slide from left — sidebar button entrance. */
    public static void fadeInAndSlideRight(Node node, double delaySec) {
        node.setOpacity(0);
        node.setTranslateX(-30);

        FadeTransition fade = new FadeTransition(Duration.millis(450), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(450), node);
        slide.setFromX(-30);
        slide.setToX(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, slide);
        pt.setDelay(Duration.seconds(delaySec));
        pt.play();
    }

    /** Zoom fade-in: scale from 0.92 → 1.0 with fade — module content entrance. */
    public static void zoomFadeIn(Node node, double delaySec) {
        node.setOpacity(0);
        node.setScaleX(0.92);
        node.setScaleY(0.92);

        FadeTransition fade = new FadeTransition(Duration.millis(350), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(350), node);
        scale.setFromX(0.92);
        scale.setFromY(0.92);
        scale.setToX(1.0);
        scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(fade, scale);
        pt.setDelay(Duration.seconds(delaySec));
        pt.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑨ SequentialTransition — CHOREOGRAPH SEQUENCES
     *  Used for: multi-step choreographies
     * ═══════════════════════════════════════════════════════ */

    /** Bounce-in: overshoot entrance (scale 0.3 → 1.08 → 0.97 → 1.0). */
    public static void bounceIn(Node node, double delaySec) {
        node.setOpacity(0);
        node.setScaleX(0.3);
        node.setScaleY(0.3);

        // Step 1: Grow past target with fade-in
        ScaleTransition grow = new ScaleTransition(Duration.millis(200), node);
        grow.setToX(1.08);
        grow.setToY(1.08);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), node);
        fadeIn.setToValue(1);
        ParallelTransition step1 = new ParallelTransition(grow, fadeIn);

        // Step 2: Shrink slightly past target
        ScaleTransition shrink = new ScaleTransition(Duration.millis(140), node);
        shrink.setToX(0.97);
        shrink.setToY(0.97);

        // Step 3: Settle at 1.0
        ScaleTransition settle = new ScaleTransition(Duration.millis(100), node);
        settle.setToX(1.0);
        settle.setToY(1.0);

        SequentialTransition bounce = new SequentialTransition(step1, shrink, settle);
        bounce.setDelay(Duration.seconds(delaySec));
        bounce.play();
    }

    /**
     * Success celebration: scale up → hold → scale back.
     * Great after placing an order or completing a reservation.
     */
    public static void celebrationPulse(Node node) {
        ScaleTransition grow = new ScaleTransition(Duration.millis(150), node);
        grow.setToX(1.15);
        grow.setToY(1.15);
        grow.setInterpolator(Interpolator.EASE_OUT);

        PauseTransition hold = new PauseTransition(Duration.millis(100));

        ScaleTransition shrink = new ScaleTransition(Duration.millis(200), node);
        shrink.setToX(1.0);
        shrink.setToY(1.0);
        shrink.setInterpolator(Interpolator.EASE_IN);

        SequentialTransition seq = new SequentialTransition(grow, hold, shrink);
        seq.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  ⑩ Timeline (KeyFrame/KeyValue) — CUSTOM ANIMATIONS
     *  Used for: number counter roll-up, complex keyframe sequences
     * ═══════════════════════════════════════════════════════ */

    /**
     * Animated counter: rolls a label's text from 0 up to a target number.
     * Perfect for dashboard stat cards (e.g., "Total Orders: 147").
     *
     * @param label   the label to animate
     * @param target  the final number
     * @param prefix  text before the number (e.g., "$")
     * @param suffix  text after the number (e.g., " orders")
     * @param durationMs animation duration
     */
    public static void animateCounter(Label label, int target, String prefix,
                                      String suffix, double durationMs) {
        // Use a wrapper to hold the animated value
        javafx.beans.property.IntegerProperty counter =
                new javafx.beans.property.SimpleIntegerProperty(0);

        // Bind label text to the counter
        counter.addListener((obs, oldVal, newVal) ->
                label.setText(prefix + newVal.intValue() + suffix));

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(counter, 0)),
                new KeyFrame(Duration.millis(durationMs),
                        new KeyValue(counter, target, Interpolator.EASE_OUT))
        );
        timeline.play();
    }

    /**
     * Typewriter effect: reveals text one character at a time.
     * Great for welcome messages or status updates.
     */
    public static void typewriterEffect(Label label, String fullText, double charDelayMs) {
        label.setText("");
        Timeline timeline = new Timeline();

        for (int i = 0; i <= fullText.length(); i++) {
            final String partial = fullText.substring(0, i);
            KeyFrame kf = new KeyFrame(
                    Duration.millis(charDelayMs * i),
                    e -> label.setText(partial)
            );
            timeline.getKeyFrames().add(kf);
        }
        timeline.play();
    }

    /* ═══════════════════════════════════════════════════════
     *  COMPOSITE METHODS — combining multiple animation types
     * ═══════════════════════════════════════════════════════ */

    /**
     * Animated module swap with zoom-shrink-out + zoom-fade-in.
     * Uses: ScaleTransition + FadeTransition + ParallelTransition
     */
    public static void transitionContent(StackPane container, Node newContent) {
        if (container.getChildren().isEmpty()) {
            container.getChildren().add(newContent);
            zoomFadeIn(newContent, 0);
            return;
        }

        Node old = container.getChildren().get(container.getChildren().size() - 1);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(180), old);
        shrink.setToX(0.95);
        shrink.setToY(0.95);
        shrink.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(180), old);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_IN);

        ParallelTransition exit = new ParallelTransition(shrink, fadeOut);
        exit.setOnFinished(e -> {
            container.getChildren().setAll(newContent);
            zoomFadeIn(newContent, 0);
        });
        exit.play();
    }

    /**
     * Theme switch flash overlay.
     * Uses: FadeTransition on a temporary Rectangle
     */
    public static void themeFlash(StackPane root) {
        Rectangle flash = new Rectangle();
        flash.widthProperty().bind(root.widthProperty());
        flash.heightProperty().bind(root.heightProperty());
        flash.setFill(Color.web("#FFFFFF", 0.12));
        flash.setMouseTransparent(true);
        root.getChildren().add(flash);

        FadeTransition ft = new FadeTransition(Duration.millis(400), flash);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(e -> root.getChildren().remove(flash));
        ft.play();
    }
}
