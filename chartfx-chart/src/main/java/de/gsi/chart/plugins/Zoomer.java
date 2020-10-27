package de.gsi.chart.plugins;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.controlsfx.control.RangeSlider;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.AxisMode;
import de.gsi.chart.axes.spi.AbstractAxis;
import de.gsi.chart.ui.ObservableDeque;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.dataset.event.UpdateEvent;

/**
 * Zoom capabilities along X, Y or both axis. For every zoom-in operation the current X and Y range is remembered and
 * restored upon following zoom-out operation.
 * <ul>
 * <li>zoom-in - triggered on {@link MouseEvent#MOUSE_PRESSED MOUSE_PRESSED} event that is accepted by
 * {@link #getZoomInMouseFilter() zoom-in filter}. It shows a zooming rectangle determining the zoom window once mouse
 * button is released.</li>
 * <li>zoom-out - triggered on {@link MouseEvent#MOUSE_CLICKED MOUSE_CLICKED} event that is accepted by
 * {@link #getZoomOutMouseFilter() zoom-out filter}. It restores the previous ranges on both axis.</li>
 * <li>zoom-origin - triggered on {@link MouseEvent#MOUSE_CLICKED MOUSE_CLICKED} event that is accepted by
 * {@link #getZoomOriginMouseFilter() zoom-origin filter}. It restores the initial ranges on both axis as it was at the
 * moment of the first zoom-in operation.</li>
 * </ul>
 * <p>
 * CSS class name of the zoom rectangle: {@value #STYLE_CLASS_ZOOM_RECT}.
 * </p>
 *
 * @author Grzegorz Kruk
 * @author rstein - adapted to XYChartPane, corrected some features (mouse zoom events outside canvas, auto-ranging on
 *         zoom-out, scrolling, toolbar)
 */
public class Zoomer extends ChartPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(Zoomer.class);
    public static final String ZOOMER_OMIT_AXIS = "OmitAxisZoom";
    public static final String STYLE_CLASS_ZOOM_RECT = "chart-zoom-rect";
    private static final int ZOOM_RECT_MIN_SIZE = 5;
    private static final Duration DEFAULT_ZOOM_DURATION = Duration.millis(500);
    private static final int DEFAULT_AUTO_ZOOM_THRESHOLD = 15; // [pixels]
    private static final int DEFAULT_FLICKER_THRESHOLD = 3; // [pixels]
    private static final int FONT_SIZE = 22;
    private static final String ICON_ZOOM_OUT = "fa-arrows-alt:" + FONT_SIZE;
    private static final String ICON_ZOOM_HV = "fa-arrows:" + FONT_SIZE;
    private static final String ICON_ZOOM_H = "fa-arrows-h:" + FONT_SIZE;
    private static final String ICON_ZOOM_V = "fa-arrows-v:" + FONT_SIZE;

    /**
     * Default pan mouse filter passing on left mouse button with {@link MouseEvent#isControlDown() control key down}.
     */
    public static final Predicate<MouseEvent> DEFAULT_MOUSE_FILTER = MouseEventsHelper::isOnlyMiddleButtonDown;
    private double panShiftX;
    private double panShiftY;
    private Point2D previousMouseLocation;
    private final BooleanProperty enablePanner = new SimpleBooleanProperty(this, "enablePanner", true);
    private final BooleanProperty autoZoomEnable = new SimpleBooleanProperty(this, "enableAutoZoom", false);
    private final IntegerProperty autoZoomThreshold = new SimpleIntegerProperty(this, "autoZoomThreshold",
            DEFAULT_AUTO_ZOOM_THRESHOLD);
    private final EventHandler<MouseEvent> panStartHandler = event -> {
        if (isPannerEnabled() && DEFAULT_MOUSE_FILTER.test(event)) {
            panStarted(event);
            event.consume();
        }
    };

    private final EventHandler<MouseEvent> panDragHandler = event -> {
        if (panOngoing()) {
            panDragged(event);
            event.consume();
        }
    };

    private final EventHandler<MouseEvent> panEndHandler = event -> {
        if (panOngoing()) {
            panEnded();
            event.consume();
        }
    };

    /**
     * Default zoom-in mouse filter passing on left mouse button (only).
     */
    public final Predicate<MouseEvent> defaultZoomInMouseFilter = event -> MouseEventsHelper.isOnlyPrimaryButtonDown(event) && MouseEventsHelper.modifierKeysUp(event) && isMouseEventWithinCanvas(event);

    /**
     * Default zoom-out mouse filter passing on right mouse button (only).
     */
    public final Predicate<MouseEvent> defaultZoomOutMouseFilter = event -> MouseEventsHelper.isOnlySecondaryButtonDown(event) && MouseEventsHelper.modifierKeysUp(event) && isMouseEventWithinCanvas(event);

    /**
     * Default zoom-origin mouse filter passing on right mouse button with {@link MouseEvent#isControlDown() control key
     * down}.
     */
    public final Predicate<MouseEvent> defaultZoomOriginFilter = event -> MouseEventsHelper.isOnlySecondaryButtonDown(event) && MouseEventsHelper.isOnlyCtrlModifierDown(event) && isMouseEventWithinCanvas(event);

    /**
     * Default zoom scroll filter with {@link MouseEvent#isControlDown() control key down}.
     */
    public final Predicate<ScrollEvent> defaultScrollFilter = this::isMouseEventWithinCanvas;

    private Predicate<MouseEvent> zoomInMouseFilter = defaultZoomInMouseFilter;
    private Predicate<MouseEvent> zoomOutMouseFilter = defaultZoomOutMouseFilter;
    private Predicate<MouseEvent> zoomOriginMouseFilter = defaultZoomOriginFilter;
    private Predicate<ScrollEvent> zoomScrollFilter = defaultScrollFilter;

    private final Rectangle zoomRectangle = new Rectangle();
    private Point2D zoomStartPoint;
    private Point2D zoomEndPoint;
    private final ObservableDeque<Map<Axis, ZoomState>> zoomStacks = new ObservableDeque<>(new ArrayDeque<>());
    private final HBox zoomButtons = getZoomInteractorBar();
    private ZoomRangeSlider xRangeSlider;
    private boolean xRangeSliderInit;
    private final ObservableList<Axis> omitAxisZoom = FXCollections.observableArrayList();

    private final ObjectProperty<AxisMode> axisMode = new SimpleObjectProperty<>(this, "axisMode", AxisMode.XY) {
        @Override
        protected void invalidated() {
            Objects.requireNonNull(get(), "The " + getName() + " must not be null");
        }
    };

    private Cursor originalCursor;

    private final ObjectProperty<Cursor> dragCursor = new SimpleObjectProperty<>(this, "dragCursor");
    private final ObjectProperty<Cursor> zoomCursor = new SimpleObjectProperty<>(this, "zoomCursor");

    private final BooleanProperty animated = new SimpleBooleanProperty(this, "animated", false);

    private final ObjectProperty<Duration> zoomDuration = new SimpleObjectProperty<>(this, "zoomDuration",
            DEFAULT_ZOOM_DURATION) {
        @Override
        protected void invalidated() {
            Objects.requireNonNull(get(), "The " + getName() + " must not be null");
        }
    };

    private final BooleanProperty updateTickUnit = new SimpleBooleanProperty(this, "updateTickUnit", true);

    private final BooleanProperty sliderVisible = new SimpleBooleanProperty(this, "sliderVisible", true);

    private final EventHandler<MouseEvent> zoomInStartHandler = event -> {
        if (getZoomInMouseFilter() == null || getZoomInMouseFilter().test(event)) {
            zoomInStarted(event);
            event.consume();
        }
    };

    private final EventHandler<MouseEvent> zoomInDragHandler = event -> {
        if (zoomOngoing()) {
            zoomInDragged(event);
            event.consume();
        }
    };

    private final EventHandler<MouseEvent> zoomInEndHandler = event -> {
        if (zoomOngoing()) {
            zoomInEnded();
            event.consume();
        }
    };

    private final EventHandler<ScrollEvent> zoomScrollHandler = event -> {
        if (getZoomScrollFilter() == null || getZoomScrollFilter().test(event)) {
            final AxisMode mode = getAxisMode();
            if (zoomStacks.isEmpty()) {
                makeSnapshotOfView();
            }
            for (final Axis axis : getChart().getAxes()) {
                if (axis.getSide() == null || !(axis.getSide().isHorizontal() ? mode.allowsX() : mode.allowsY())
                        || isOmitZoomInternal(axis)) {
                    continue;
                }
                zoomOnAxis(axis, event);
            }
            event.consume();
            return;
        }
        // Zoom on individual axis
        for (final Axis axis : getChart().getAxes()) {
            // only listen to events within the axis
            final Point2D mouseLoc = new Point2D(event.getScreenX(), event.getScreenY());
            final Canvas canvas = axis.getCanvas();
            final Bounds screenBounds = canvas.localToScreen(canvas.getBoundsInLocal());
            if (screenBounds.contains(mouseLoc)) {
                // allow to return to default view
                if (zoomStacks.isEmpty()) {
                    makeSnapshotOfView();
                }
                zoomOnAxis(axis, event);
                event.consume();
                return;
            }
        }
    };

    private final EventHandler<MouseEvent> zoomOutHandler = event -> {
        if (getZoomOutMouseFilter() == null || getZoomOutMouseFilter().test(event)) {
            final boolean zoomOutPerformed = zoomOut();
            if (zoomOutPerformed) {
                event.consume();
            }
        }
    };

    private final EventHandler<MouseEvent> zoomOriginHandler = event -> {
        if (getZoomOriginMouseFilter() == null || getZoomOriginMouseFilter().test(event)) {
            final boolean zoomOutPerformed = zoomOrigin();
            if (zoomOutPerformed) {
                event.consume();
            }
        }
    };

    /**
     * Creates a new instance of Zoomer with animation disabled and with {@link #axisModeProperty() zoomMode}
     * initialized to {@link AxisMode#XY}.
     */
    public Zoomer() {
        this(AxisMode.XY);
    }

    /**
     * Creates a new instance of Zoomer with animation disabled.
     *
     * @param zoomMode initial value of {@link #axisModeProperty() zoomMode} property
     */
    public Zoomer(final AxisMode zoomMode) {
        this(zoomMode, false);
    }

    /**
     * Creates a new instance of Zoomer.
     *
     * @param zoomMode initial value of {@link #axisModeProperty() axisMode} property
     * @param animated initial value of {@link #animatedProperty() animated} property
     */
    public Zoomer(final AxisMode zoomMode, final boolean animated) {
        super();
        setAxisMode(zoomMode);
        setAnimated(animated);
        setZoomCursor(Cursor.CROSSHAIR);
        setDragCursor(Cursor.CLOSED_HAND);

        zoomRectangle.setManaged(false);
        zoomRectangle.getStyleClass().add(STYLE_CLASS_ZOOM_RECT);
        getChartChildren().add(zoomRectangle);
        registerMouseHandlers();

        chartProperty().addListener((change, o, n) -> {
            if (o != null) {
                o.getToolBar().getChildren().remove(zoomButtons);
                o.getPlotArea().setBottom(null);
                xRangeSlider.prefWidthProperty().unbind();
            }
            if (n != null) {
                if (isAddButtonsToToolBar()) {
                    n.getToolBar().getChildren().add(zoomButtons);
                }
                /* always create the slider, even if not visible at first */
                final ZoomRangeSlider slider = new ZoomRangeSlider(n);
                if (isSliderVisible()) {
                    n.getPlotArea().setBottom(slider);
                    xRangeSlider.prefWidthProperty().bind(n.getCanvasForeground().widthProperty());
                }
            }
        });
    }

    /**
     * Creates a new instance of Zoomer with {@link #axisModeProperty() zoomMode} initialized to {@link AxisMode#XY}.
     *
     * @param animated initial value of {@link #animatedProperty() animated} property
     */
    public Zoomer(final boolean animated) {
        this(AxisMode.XY, animated);
    }

    /**
     * When {@code true} zooming will be animated. By default it's {@code false}.
     *
     * @return the animated property
     * @see #zoomDurationProperty()
     */
    public final BooleanProperty animatedProperty() {
        return animated;
    }

    /**
     * When {@code true} auto-zooming feature is being enabled, ie. more horizontal drags do x-zoom only, more vertical
     * drags do y-zoom only, and xy-zoom otherwise
     *
     * @return the autoZoom property
     */
    public final BooleanProperty autoZoomEnabledProperty() {
        return autoZoomEnable;
    }

    public IntegerProperty autoZoomThresholdProperty() {
        return autoZoomThreshold;
    }

    /**
     * The mode defining axis along which the zoom can be performed. By default initialised to {@link AxisMode#XY}.
     *
     * @return the axis mode property
     */
    public final ObjectProperty<AxisMode> axisModeProperty() {
        return axisMode;
    }

    /**
     * Clears the stack of zoom windows saved during zoom-in operations.
     */
    public void clear() {
        zoomStacks.clear();
    }

    /**
     * Clears the stack of zoom states saved during zoom-in operations for a specific given axis.
     *
     * @param axis axis zoom history that shall be removed
     */
    public void clear(final Axis axis) {
        for (Map<Axis, ZoomState> stackStage : zoomStacks) {
            stackStage.remove(axis);
        }
    }

    /**
     * Mouse cursor to be used during drag operation.
     *
     * @return the mouse cursor property
     */
    public final ObjectProperty<Cursor> dragCursorProperty() {
        return dragCursor;
    }

    public int getAutoZoomThreshold() {
        return autoZoomThresholdProperty().get();
    }

    /**
     * Returns the value of the {@link #axisModeProperty()}.
     *
     * @return current mode
     */
    public final AxisMode getAxisMode() {
        return axisModeProperty().get();
    }

    /**
     * Returns the value of the {@link #dragCursorProperty()}
     *
     * @return the current cursor
     */
    public final Cursor getDragCursor() {
        return dragCursorProperty().get();
    }

    public RangeSlider getRangeSlider() {
        return xRangeSlider;
    }

    /**
     * Returns the value of the {@link #zoomCursorProperty()}
     *
     * @return the current cursor
     */
    public final Cursor getZoomCursor() {
        return zoomCursorProperty().get();
    }

    /**
     * Returns the value of the {@link #zoomDurationProperty()}.
     *
     * @return the current zoom duration
     */
    public final Duration getZoomDuration() {
        return zoomDurationProperty().get();
    }

    /**
     * Returns zoom-in mouse event filter.
     *
     * @return zoom-in mouse event filter
     * @see #setZoomInMouseFilter(Predicate)
     */
    public Predicate<MouseEvent> getZoomInMouseFilter() {
        return zoomInMouseFilter;
    }

    public HBox getZoomInteractorBar() {
        final Separator separator = new Separator();
        separator.setOrientation(Orientation.VERTICAL);
        final HBox buttonBar = new HBox();
        buttonBar.setPadding(new Insets(1, 1, 1, 1));
        final Button zoomOut = new Button(null, new FontIcon(ICON_ZOOM_OUT));
        zoomOut.setPadding(new Insets(3, 3, 3, 3));
        zoomOut.setTooltip(new Tooltip("zooms to origin and enables auto-ranging"));
        final Button zoomModeXY = new Button(null, new FontIcon(ICON_ZOOM_HV));
        zoomModeXY.setPadding(new Insets(3, 3, 3, 3));
        zoomModeXY.setTooltip(new Tooltip("set zoom-mode to X & Y range (N.B. disables auto-ranging)"));
        final Button zoomModeX = new Button(null, new FontIcon(ICON_ZOOM_H));
        zoomModeX.setPadding(new Insets(3, 3, 3, 3));
        zoomModeX.setTooltip(new Tooltip("set zoom-mode to X range (N.B. disables auto-ranging)"));
        final Button zoomModeY = new Button(null, new FontIcon(ICON_ZOOM_V));
        zoomModeY.setPadding(new Insets(3, 3, 3, 3));
        zoomModeY.setTooltip(new Tooltip("set zoom-mode to Y range (N.B. disables auto-ranging)"));

        zoomOut.setOnAction(evt -> {
            zoomOrigin();
            getChart().getAxes().forEach(axis -> axis.setAutoRanging(true));
        });
        zoomModeXY.setOnAction(evt -> setAxisMode(AxisMode.XY));
        zoomModeX.setOnAction(evt -> setAxisMode(AxisMode.X));
        zoomModeY.setOnAction(evt -> setAxisMode(AxisMode.Y));
        buttonBar.getChildren().addAll(separator, zoomOut, zoomModeXY, zoomModeX, zoomModeY);
        return buttonBar;
    }

    /**
     * Returns zoom-origin mouse filter.
     *
     * @return zoom-origin mouse filter
     * @see #setZoomOriginMouseFilter(Predicate)
     */
    public Predicate<MouseEvent> getZoomOriginMouseFilter() {
        return zoomOriginMouseFilter;
    }

    /**
     * Returns zoom-out mouse filter.
     *
     * @return zoom-out mouse filter
     * @see #setZoomOutMouseFilter(Predicate)
     */
    public Predicate<MouseEvent> getZoomOutMouseFilter() {
        return zoomOutMouseFilter;
    }

    /**
     * Returns zoom-scroll filter.
     *
     * @return predicate of filter
     */
    public Predicate<ScrollEvent> getZoomScrollFilter() {
        return zoomScrollFilter;
    }

    /**
     * Returns the value of the {@link #animatedProperty()}.
     *
     * @return {@code true} if zoom is animated, {@code false} otherwise
     * @see #getZoomDuration()
     */
    public final boolean isAnimated() {
        return animatedProperty().get();
    }

    /**
     * @return {@code true} if auto-zooming feature is being enabled, ie. more horizontal drags do x-zoom only, more
     *         vertical drags do y-zoom only, and xy-zoom otherwise
     */
    public final boolean isAutoZoomEnabled() {
        return autoZoomEnabledProperty().get();
    }

    public final boolean isPannerEnabled() {
        return pannerEnabledProperty().get();
    }

    /**
     * Returns the value of the {@link #sliderVisibleProperty()}.
     *
     * @return {@code true} if horizontal range slider is shown
     */
    public final boolean isSliderVisible() {
        return sliderVisibleProperty().get();
    }

    /**
     * Returns the value of the {@link #animatedProperty()}.
     *
     * @return {@code true} if zoom is animated, {@code false} otherwise
     * @see #getZoomDuration()
     */
    public final boolean isUpdateTickUnit() {
        return updateTickUnitProperty().get();
    }

    /**
     * @return list of axes that shall be ignored when performing zoom-in or outs
     */
    public final ObservableList<Axis> omitAxisZoomList() {
        return omitAxisZoom;
    }

    /**
     * When {@code true} pressing the middle mouse button and dragging pans the plot
     *
     * @return the pannerEnabled property
     */
    public final BooleanProperty pannerEnabledProperty() {
        return enablePanner;
    }

    /**
     * Sets the value of the {@link #animatedProperty()}.
     *
     * @param value if {@code true} zoom will be animated
     * @see #setZoomDuration(Duration)
     */
    public final void setAnimated(final boolean value) {
        animatedProperty().set(value);
    }

    /**
     * Sets the value of the {@link #autoZoomEnabledProperty()}.
     *
     * @param state if {@code true} auto-zooming feature is being enabled, ie. more horizontal drags do x-zoom only,
     *            more vertical drags do y-zoom only, and xy-zoom otherwise
     */
    public final void setAutoZoomEnabled(final boolean state) {
        autoZoomEnabledProperty().set(state);
    }

    public void setAutoZoomThreshold(final int value) {
        autoZoomThresholdProperty().set(value);
    }

    /**
     * Sets the value of the {@link #axisModeProperty()}.
     *
     * @param mode the mode to be used
     */
    public final void setAxisMode(final AxisMode mode) {
        axisModeProperty().set(mode);
    }

    /**
     * Sets value of the {@link #dragCursorProperty()}.
     *
     * @param cursor the cursor to be used by the plugin
     */
    public final void setDragCursor(final Cursor cursor) {
        dragCursorProperty().set(cursor);
    }

    /**
     * Sets the value of the {@link #pannerEnabledProperty()}.
     *
     * @param state if {@code true} the panner (middle mouse button is enabled
     */
    public final void setPannerEnabled(final boolean state) {
        pannerEnabledProperty().set(state);
    }

    /**
     * Sets the value of the {@link #sliderVisibleProperty()}.
     *
     * @param state if {@code true} the horizontal range slider is shown
     */
    public final void setSliderVisible(final boolean state) {
        sliderVisibleProperty().set(state);
    }

    /**
     * Sets the value of the {@link #animatedProperty()}.
     *
     * @param value if {@code true} zoom will be animated
     * @see #setZoomDuration(Duration)
     */
    public final void setUpdateTickUnit(final boolean value) {
        updateTickUnitProperty().set(value);
    }

    /**
     * Sets value of the {@link #zoomCursorProperty()}.
     *
     * @param cursor the cursor to be used by the plugin
     */
    public final void setZoomCursor(final Cursor cursor) {
        zoomCursorProperty().set(cursor);
    }

    /**
     * Sets the value of the {@link #zoomDurationProperty()}.
     *
     * @param duration duration of the zoom
     */
    public final void setZoomDuration(final Duration duration) {
        zoomDurationProperty().set(duration);
    }

    /**
     * Sets filter on {@link MouseEvent#DRAG_DETECTED DRAG_DETECTED} events that should start zoom-in operation.
     *
     * @param zoomInMouseFilter the filter to accept zoom-in mouse event. If {@code null} then any DRAG_DETECTED event
     *            will start zoom-in operation. By default it's set to {@link #defaultZoomInMouseFilter}.
     * @see #getZoomInMouseFilter()
     */
    public void setZoomInMouseFilter(final Predicate<MouseEvent> zoomInMouseFilter) {
        this.zoomInMouseFilter = zoomInMouseFilter;
    }

    /**
     * Sets filter on {@link MouseEvent#MOUSE_CLICKED MOUSE_CLICKED} events that should trigger zoom-origin operation.
     *
     * @param zoomOriginMouseFilter the filter to accept zoom-origin mouse event. If {@code null} then any MOUSE_CLICKED
     *            event will start zoom-origin operation. By default it's set to {@link #defaultZoomOriginFilter}.
     * @see #getZoomOriginMouseFilter()
     */
    public void setZoomOriginMouseFilter(final Predicate<MouseEvent> zoomOriginMouseFilter) {
        this.zoomOriginMouseFilter = zoomOriginMouseFilter;
    }

    /**
     * Sets filter on {@link MouseEvent#MOUSE_CLICKED MOUSE_CLICKED} events that should trigger zoom-out operation.
     *
     * @param zoomOutMouseFilter the filter to accept zoom-out mouse event. If {@code null} then any MOUSE_CLICKED event
     *            will start zoom-out operation. By default it's set to {@link #defaultZoomOutMouseFilter}.
     * @see #getZoomOutMouseFilter()
     */
    public void setZoomOutMouseFilter(final Predicate<MouseEvent> zoomOutMouseFilter) {
        this.zoomOutMouseFilter = zoomOutMouseFilter;
    }

    /**
     * Sets filter on {@link MouseEvent#MOUSE_CLICKED MOUSE_CLICKED} events that should trigger zoom-origin operation.
     *
     * @param zoomScrollFilter filter
     */
    public void setZoomScrollFilter(final Predicate<ScrollEvent> zoomScrollFilter) {
        this.zoomScrollFilter = zoomScrollFilter;
    }

    /**
     * When {@code true} an additional horizontal range slider is shown in a HiddeSidesPane at the bottom. By default
     * it's {@code true}.
     *
     * @return the sliderVisible property
     * @see #getRangeSlider()
     */
    public final BooleanProperty sliderVisibleProperty() {
        return sliderVisible;
    }

    /**
     * When {@code true} zooming will be animated. By default it's {@code false}.
     *
     * @return the animated property
     * @see #zoomDurationProperty()
     */
    public final BooleanProperty updateTickUnitProperty() {
        return updateTickUnit;
    }

    /**
     * Mouse cursor to be used during zoom operation.
     *
     * @return the mouse cursor property
     */
    public final ObjectProperty<Cursor> zoomCursorProperty() {
        return zoomCursor;
    }

    /**
     * Duration of the animated zoom (in and out). Used only when {@link #animatedProperty()} is set to {@code true}. By
     * default initialised to 500ms.
     *
     * @return the zoom duration property
     */
    public final ObjectProperty<Duration> zoomDurationProperty() {
        return zoomDuration;
    }

    public boolean zoomOrigin() {
        clearZoomStackIfAxisAutoRangingIsEnabled();
        final Map<Axis, ZoomState> zoomWindows = zoomStacks.peekLast();
        if (zoomWindows == null || zoomWindows.isEmpty()) {
            return false;
        }
        clear();
        performZoom(zoomWindows, false);
        if (xRangeSlider != null) {
            xRangeSlider.reset();
        }
        for (Axis axis : getChart().getAxes()) {
            axis.forceRedraw();
        }
        return true;
    }

    /**
     * @return observable queue (allows to attach ListChangeListener listener)
     */
    public ObservableDeque<Map<Axis, ZoomState>> zoomStackDeque() {
        return zoomStacks;
    }

    /**
     * While performing zoom-in on all charts we disable auto-ranging on axes (depending on the axisMode) so if user has
     * enabled back the auto-ranging - he wants the chart to adapt to the data. Therefore keeping the zoom stack doesn't
     * make sense - performing zoom-out would again disable auto-ranging and put back ranges saved during the previous
     * zoom-in operation. Also if user enables auto-ranging between two zoom-in operations, the saved zoom stack becomes
     * irrelevant.
     */
    private void clearZoomStackIfAxisAutoRangingIsEnabled() {
        Chart chart = getChart();
        if (chart == null) {
            return;
        }

        for (Axis axis : getChart().getAxes()) {
            if (axis.getSide().isHorizontal()) {
                if (getAxisMode().allowsX() && (axis.isAutoRanging() || axis.isAutoGrowRanging())) {
                    clear(axis);
                }
            } else {
                if (getAxisMode().allowsY() && (axis.isAutoRanging() || axis.isAutoGrowRanging())) {
                    clear(axis);
                }
            }
        }
    }

    private Map<Axis, ZoomState> getZoomDataWindows() {
        ConcurrentHashMap<Axis, ZoomState> axisStateMap = new ConcurrentHashMap<>();
        if (getChart() == null) {
            return axisStateMap;
        }
        final double minX = zoomRectangle.getX();
        final double minY = zoomRectangle.getY() + zoomRectangle.getHeight();
        final double maxX = zoomRectangle.getX() + zoomRectangle.getWidth();
        final double maxY = zoomRectangle.getY();

        // pixel coordinates w.r.t. plot area
        final Point2D minPlotCoordinate = getChart().toPlotArea(minX, minY);
        final Point2D maxPlotCoordinate = getChart().toPlotArea(maxX, maxY);
        for (Axis axis : getChart().getAxes()) {
            double dataMin;
            double dataMax;
            if (axis.getSide().isVertical()) {
                dataMin = axis.getValueForDisplay(minPlotCoordinate.getY());
                dataMax = axis.getValueForDisplay(maxPlotCoordinate.getY());
            } else {
                dataMin = axis.getValueForDisplay(minPlotCoordinate.getX());
                dataMax = axis.getValueForDisplay(maxPlotCoordinate.getX());
            }
            switch (getAxisMode()) {
            case X:
                if (axis.getSide().isHorizontal()) {
                    axisStateMap.put(axis,
                            new ZoomState(dataMin, dataMax, axis.isAutoRanging(), axis.isAutoGrowRanging()));
                }
                break;
            case Y:
                if (axis.getSide().isVertical()) {
                    axisStateMap.put(axis,
                            new ZoomState(dataMin, dataMax, axis.isAutoRanging(), axis.isAutoGrowRanging()));
                }
                break;
            case XY:
            default:
                axisStateMap.put(axis, new ZoomState(dataMin, dataMax, axis.isAutoRanging(), axis.isAutoGrowRanging()));
                break;
            }
        }

        return axisStateMap;
    }

    private void installDragCursor() {
        final Region chart = getChart();
        originalCursor = chart.getCursor();
        if (getDragCursor() != null) {
            chart.setCursor(getDragCursor());
        }
    }

    private void installZoomCursor() {
        final Region chart = getChart();
        originalCursor = chart.getCursor();
        if (getDragCursor() != null) {
            chart.setCursor(getZoomCursor());
        }
    }

    private boolean isMouseEventWithinCanvas(final MouseEvent mouseEvent) {
        final Canvas canvas = getChart().getCanvas();
        // listen to only events within the canvas
        final Point2D mouseLoc = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
        final Bounds screenBounds = canvas.localToScreen(canvas.getBoundsInLocal());
        return screenBounds.contains(mouseLoc);
    }

    private boolean isMouseEventWithinCanvas(final ScrollEvent mouseEvent) {
        final Canvas canvas = getChart().getCanvas();
        // listen to only events within the canvas
        final Point2D mouseLoc = new Point2D(mouseEvent.getScreenX(), mouseEvent.getScreenY());
        final Bounds screenBounds = canvas.localToScreen(canvas.getBoundsInLocal());
        return screenBounds.contains(mouseLoc);
    }

    /**
     * @param axis the axis to be modified
     * @return {@code true} if axis is zoomable, {@code false} otherwise
     */
    private boolean isOmitZoomInternal(final Axis axis) {
        final boolean propertyState = Zoomer.isOmitZoom(axis);

        return propertyState || omitAxisZoomList().contains(axis);
    }

    /**
     * take a snapshot of present view (needed for scroll zoom interactor
     */
    private void makeSnapshotOfView() {
        final Bounds bounds = getChart().getBoundsInLocal();
        final double minX = bounds.getMinX();
        final double minY = bounds.getMinY();
        final double maxX = bounds.getMaxX();
        final double maxY = bounds.getMaxY();

        zoomRectangle.setX(bounds.getMinX());
        zoomRectangle.setY(bounds.getMinY());
        zoomRectangle.setWidth(maxX - minX);
        zoomRectangle.setHeight(maxY - minY);

        pushCurrentZoomWindows();
        performZoom(getZoomDataWindows(), true);
        zoomRectangle.setVisible(false);
    }

    private void panChart(final Chart chart, final Point2D mouseLocation) {
        if (!(chart instanceof XYChart)) {
            return;
        }

        final double oldMouseX = previousMouseLocation.getX();
        final double oldMouseY = previousMouseLocation.getY();
        final double newMouseX = mouseLocation.getX();
        final double newMouseY = mouseLocation.getY();
        panShiftX += oldMouseX - newMouseX;
        panShiftY += oldMouseY - newMouseY;

        for (final Axis axis : chart.getAxes()) {
            if (axis.getSide() == null || isOmitZoomInternal(axis)) {
                continue;
            }

            final Side side = axis.getSide();

            final double prevData = axis.getValueForDisplay(side.isHorizontal() ? oldMouseX : oldMouseY);
            final double newData = axis.getValueForDisplay(side.isHorizontal() ? newMouseX : newMouseY);
            final double offset = prevData - newData;

            final boolean allowsShift = side.isHorizontal() ? getAxisMode().allowsX() : getAxisMode().allowsY();
            if (!hasBoundedRange(axis) && allowsShift) {
                axis.setAutoRanging(false);
                // shift bounds
                axis.set(axis.getMin() + offset, axis.getMax() + offset);
            }
        }
        previousMouseLocation = mouseLocation;
    }

    private void panDragged(final MouseEvent event) {
        final Point2D mouseLocation = getLocationInPlotArea(event);
        panChart(getChart(), mouseLocation);
        previousMouseLocation = mouseLocation;
    }

    private void panEnded() {
        Chart chart = getChart();
        if (chart == null || panShiftX == 0.0 || panShiftY == 0.0 || previousMouseLocation == null) {
            return;
        }

        for (final Axis axis : chart.getAxes()) {
            if (axis.getSide() == null || isOmitZoomInternal(axis)) {
                continue;
            }
            final Side side = axis.getSide();

            final boolean allowsShift = side.isHorizontal() ? getAxisMode().allowsX() : getAxisMode().allowsY();
            if (!hasBoundedRange(axis) && allowsShift) {
                axis.setAutoRanging(false);
            }
        }

        panShiftX = 0.0;
        panShiftY = 0.0;
        previousMouseLocation = null;
        uninstallCursor();
    }

    protected static boolean hasBoundedRange(Axis axis) {
        return axis.minProperty().isBound() || axis.maxProperty().isBound();
    }

    private boolean panOngoing() {
        return previousMouseLocation != null;
    }

    private void panStarted(final MouseEvent event) {
        previousMouseLocation = getLocationInPlotArea(event);
        panShiftX = 0.0;
        panShiftY = 0.0;
        installDragCursor();
        clearZoomStackIfAxisAutoRangingIsEnabled();
        pushCurrentZoomWindows();
    }

    private void performZoom(Entry<Axis, ZoomState> zoomStateEntry, final boolean isZoomIn) {
        ZoomState zoomState = zoomStateEntry.getValue();
        if (zoomState.zoomRangeMax - zoomState.zoomRangeMin == 0) {
            LOGGER.atDebug().log("Cannot zoom in deeper than numerical precision");
            return;
        }

        Axis axis = zoomStateEntry.getKey();
        if (isZoomIn && ((axis.getSide().isHorizontal() && getAxisMode().allowsX()) || (axis.getSide().isVertical() && getAxisMode().allowsY()))) {
            // perform only zoom-in if axis is horizontal (or vertical) and corresponding horizontal (or vertical)
            // zooming is allowed
            axis.setAutoRanging(false);
        }

        if (isAnimated()) {
            if (!hasBoundedRange(axis)) {
                final Timeline xZoomAnimation = new Timeline();
                xZoomAnimation.getKeyFrames().setAll(
                        new KeyFrame(Duration.ZERO, new KeyValue(axis.minProperty(), axis.getMin()),
                                new KeyValue(axis.maxProperty(), axis.getMax())),
                        new KeyFrame(getZoomDuration(), new KeyValue(axis.minProperty(), zoomState.zoomRangeMin),
                                new KeyValue(axis.maxProperty(), zoomState.zoomRangeMax)));
                xZoomAnimation.play();
            }
        } else {
            if (!hasBoundedRange(axis)) {
                // only update if this axis is not bound to another (e.g. auto-range) managed axis)
                axis.set(zoomState.zoomRangeMin, zoomState.zoomRangeMax);
            }
        }

        if (!isZoomIn) {
            axis.setAutoRanging(zoomState.wasAutoRanging);
            axis.setAutoGrowRanging(zoomState.wasAutoGrowRanging);
        }
    }

    private void performZoom(Map<Axis, ZoomState> zoomWindows, final boolean isZoomIn) {
        for (final Entry<Axis, ZoomState> entry : zoomWindows.entrySet()) {
            if (!isOmitZoomInternal(entry.getKey())) {
                performZoom(entry, isZoomIn);
            }
        }

        for (Axis a : getChart().getAxes()) {
            a.forceRedraw();
        }
    }

    private void performZoomIn() {
        clearZoomStackIfAxisAutoRangingIsEnabled();
        pushCurrentZoomWindows();
        performZoom(getZoomDataWindows(), true);
    }

    private void pushCurrentZoomWindows() {
        if (getChart() == null) {
            return;
        }
        ConcurrentHashMap<Axis, ZoomState> axisStateMap = new ConcurrentHashMap<>();
        for (Axis axis : getChart().getAxes()) {
            switch (getAxisMode()) {
            case X:
                if (axis.getSide().isHorizontal()) {
                    axisStateMap.put(axis, new ZoomState(axis.getMin(), axis.getMax(), axis.isAutoRanging(),
                                                   axis.isAutoGrowRanging())); // NOPMD necessary in-loop instantiation
                }
                break;
            case Y:
                if (axis.getSide().isVertical()) {
                    axisStateMap.put(axis, new ZoomState(axis.getMin(), axis.getMax(), axis.isAutoRanging(),
                                                   axis.isAutoGrowRanging())); // NOPMD necessary in-loop instantiation
                }
                break;
            case XY:
            default:
                axisStateMap.put(axis,
                        new ZoomState(axis.getMin(), axis.getMax(), axis.isAutoRanging(), axis.isAutoGrowRanging())); // NOPMD
                // necessary
                // in-loop
                // instantiation
                break;
            }
        }
        if (!axisStateMap.keySet().isEmpty()) {
            zoomStacks.addFirst(axisStateMap);
        }
    }

    private void registerMouseHandlers() {
        registerInputEventHandler(MouseEvent.MOUSE_PRESSED, zoomInStartHandler);
        registerInputEventHandler(MouseEvent.MOUSE_DRAGGED, zoomInDragHandler);
        registerInputEventHandler(MouseEvent.MOUSE_RELEASED, zoomInEndHandler);
        registerInputEventHandler(MouseEvent.MOUSE_CLICKED, zoomOutHandler);
        registerInputEventHandler(MouseEvent.MOUSE_CLICKED, zoomOriginHandler);
        registerInputEventHandler(ScrollEvent.SCROLL, zoomScrollHandler);
        registerInputEventHandler(MouseEvent.MOUSE_PRESSED, panStartHandler);
        registerInputEventHandler(MouseEvent.MOUSE_DRAGGED, panDragHandler);
        registerInputEventHandler(MouseEvent.MOUSE_RELEASED, panEndHandler);
    }

    private void uninstallCursor() {
        getChart().setCursor(originalCursor);
    }

    private void zoomInDragged(final MouseEvent event) {
        final Bounds plotAreaBounds = getChart().getPlotArea().getBoundsInLocal();
        zoomEndPoint = limitToPlotArea(event, plotAreaBounds);

        double zoomRectX = plotAreaBounds.getMinX();
        double zoomRectY = plotAreaBounds.getMinY();
        double zoomRectWidth = plotAreaBounds.getWidth();
        double zoomRectHeight = plotAreaBounds.getHeight();

        if (isAutoZoomEnabled()) {
            final double diffX = zoomEndPoint.getX() - zoomStartPoint.getX();
            final double diffY = zoomEndPoint.getY() - zoomStartPoint.getY();

            final int limit = Math.abs(getAutoZoomThreshold());

            // pixel distance based algorithm + aspect ratio to prevent flickering when starting selection
            final boolean isZoomX = Math.abs(diffY) <= limit && Math.abs(diffX) >= limit
                                    && Math.abs(diffX / diffY) > DEFAULT_FLICKER_THRESHOLD;
            final boolean isZoomY = Math.abs(diffX) <= limit && Math.abs(diffY) >= limit
                                    && Math.abs(diffY / diffX) > DEFAULT_FLICKER_THRESHOLD;

            // alternate angle-based algorithm
            // final int angle = (int) Math.toDegrees(Math.atan2(diffY, diffX));
            // final boolean isZoomX = Math.abs(angle) <= limit || Math.abs((angle - 180) % 180) <= limit;
            // final boolean isZoomY = Math.abs((angle - 90) % 180) <= limit || Math.abs((angle - 270) % 180) <= limit;

            if (isZoomX) {
                this.setAxisMode(AxisMode.X);
            } else if (isZoomY) {
                this.setAxisMode(AxisMode.Y);
            } else {
                this.setAxisMode(AxisMode.XY);
            }
        }

        if (getAxisMode().allowsX()) {
            zoomRectX = Math.min(zoomStartPoint.getX(), zoomEndPoint.getX());
            zoomRectWidth = Math.abs(zoomEndPoint.getX() - zoomStartPoint.getX());
        }
        if (getAxisMode().allowsY()) {
            zoomRectY = Math.min(zoomStartPoint.getY(), zoomEndPoint.getY());
            zoomRectHeight = Math.abs(zoomEndPoint.getY() - zoomStartPoint.getY());
        }
        zoomRectangle.setX(zoomRectX);
        zoomRectangle.setY(zoomRectY);
        zoomRectangle.setWidth(zoomRectWidth);
        zoomRectangle.setHeight(zoomRectHeight);
    }

    private void zoomInEnded() {
        zoomRectangle.setVisible(false);
        if (zoomRectangle.getWidth() > ZOOM_RECT_MIN_SIZE && zoomRectangle.getHeight() > ZOOM_RECT_MIN_SIZE) {
            performZoomIn();
        }
        zoomStartPoint = zoomEndPoint = null;
        uninstallCursor();
    }

    private void zoomInStarted(final MouseEvent event) {
        zoomStartPoint = new Point2D(event.getX(), event.getY());

        zoomRectangle.setX(zoomStartPoint.getX());
        zoomRectangle.setY(zoomStartPoint.getY());
        zoomRectangle.setWidth(0);
        zoomRectangle.setHeight(0);
        zoomRectangle.setVisible(true);
        installZoomCursor();
    }

    private boolean zoomOngoing() {
        return zoomStartPoint != null;
    }

    private boolean zoomOut() {
        clearZoomStackIfAxisAutoRangingIsEnabled();
        final Map<Axis, ZoomState> zoomWindows = zoomStacks.pollFirst();
        if (zoomWindows == null || zoomWindows.isEmpty()) {
            return zoomOrigin();
        }
        performZoom(zoomWindows, false);
        return true;
    }

    /**
     * @param axis the axis to be modified
     * @return {@code true} if axis is zoomable, {@code false} otherwise
     */
    public static boolean isOmitZoom(final Axis axis) {
        return (axis instanceof Node) && ((Node) axis).getProperties().get(ZOOMER_OMIT_AXIS) == Boolean.TRUE;
    }

    /**
     * @param axis the axis to be modified
     * @param state true: axis is not taken into account when zooming
     */
    public static void setOmitZoom(final Axis axis, final boolean state) {
        if (!(axis instanceof Node)) {
            return;
        }
        if (state) {
            ((Node) axis).getProperties().put(ZOOMER_OMIT_AXIS, true);
        } else {
            ((Node) axis).getProperties().remove(ZOOMER_OMIT_AXIS);
        }
    }

    /**
     * limits the mouse event position to the min/max range of the canavs (N.B. event can occur to be
     * negative/larger/outside than the canvas) This is to avoid zooming outside the visible canvas range
     *
     * @param event the mouse event
     * @param plotBounds of the canvas
     * @return the clipped mouse location
     */
    private static Point2D limitToPlotArea(final MouseEvent event, final Bounds plotBounds) {
        final double limitedX = Math.max(Math.min(event.getX() - plotBounds.getMinX(), plotBounds.getMaxX()),
                plotBounds.getMinX());
        final double limitedY = Math.max(Math.min(event.getY() - plotBounds.getMinY(), plotBounds.getMaxY()),
                plotBounds.getMinY());
        return new Point2D(limitedX, limitedY);
    }

    private void zoomOnAxis(final Axis axis, final ScrollEvent event) {
        axis.setAutoRanging(false); // disable auto-ranging, s.t. axes are not bound anymore by it
        if (hasBoundedRange(axis)) { // skip axes which are externally bound
            return;
        }
        final boolean isZoomIn = event.getDeltaY() > 0;
        final boolean isHorizontal = axis.getSide().isHorizontal();

        final Point2D pos = getChart().getCanvas().sceneToLocal(event.getSceneX(), event.getSceneY());
        final double mousePos = isHorizontal ? pos.getX() : pos.getY();
        final double posOnAxis = axis.getValueForDisplay(mousePos);
        final double max = axis.getMax();
        final double min = axis.getMin();
        final double scaling = isZoomIn ? 0.9 : 1 / 0.9;
        final double diffHalf1 = scaling * Math.abs(posOnAxis - min);
        final double diffHalf2 = scaling * Math.abs(max - posOnAxis);

        axis.set(posOnAxis - diffHalf1, posOnAxis + diffHalf2);

        axis.forceRedraw();
    }

    /**
     * small class used to remember whether the autorange axis was on/off to be able to restore the original state on
     * unzooming
     */
    public class ZoomState {
        protected double zoomRangeMin;
        protected double zoomRangeMax;
        protected boolean wasAutoRanging;
        protected boolean wasAutoGrowRanging;

        private ZoomState(final double zoomRangeMin, final double zoomRangeMax, final boolean isAutoRanging,
                final boolean isAutoGrowRanging) {
            this.zoomRangeMin = Math.min(zoomRangeMin, zoomRangeMax);
            this.zoomRangeMax = Math.max(zoomRangeMin, zoomRangeMax);
            this.wasAutoRanging = isAutoRanging;
            this.wasAutoGrowRanging = isAutoGrowRanging;
        }

        /**
         * @return the zoomRangeMax
         */
        public double getZoomRangeMax() {
            return zoomRangeMax;
        }

        /**
         * @return the zoomRangeMin
         */
        public double getZoomRangeMin() {
            return zoomRangeMin;
        }

        @Override
        public String toString() {
            return "ZoomState[zoomRangeMin= " + zoomRangeMin + ", zoomRangeMax= " + zoomRangeMax + ", wasAutoRanging= "
                    + wasAutoRanging + ", wasAutoGrowRanging= " + wasAutoGrowRanging + "]";
        }

        /**
         * @return the wasAutoGrowRanging
         */
        public boolean wasAutoGrowRanging() {
            return wasAutoGrowRanging;
        }

        /**
         * @return the wasAutoRanging
         */
        public boolean wasAutoRanging() {
            return wasAutoRanging;
        }
    }

    private class ZoomRangeSlider extends RangeSlider {
        private final BooleanProperty invertedSlide = new SimpleBooleanProperty(this, "invertedSlide", false);
        private boolean isUpdating;
        private final ChangeListener<Boolean> sliderResetHandler = (ch, o, n) -> resetSlider(n);

        protected void resetSlider(Boolean n) {
            if (getChart() == null) {
                return;
            }
            final Axis axis = getChart().getFirstAxis(Orientation.HORIZONTAL);
            if (Boolean.TRUE.equals(n)) {
                setMin(axis.getMin());
                setMax(axis.getMax());
            }
        }

        private final ChangeListener<Number> rangeChangeListener = (ch, o, n) -> {
            if (isUpdating) {
                return;
            }
            isUpdating = true;
            final Axis xAxis = getChart().getFirstAxis(Orientation.HORIZONTAL);
            xAxis.getMax();
            xAxis.getMin();
            // add a little bit of margin to allow zoom outside the dataset
            final double minBound = Math.min(xAxis.getMin(), getMin());
            final double maxBound = Math.max(xAxis.getMax(), getMax());
            if (xRangeSliderInit) {
                setMin(minBound);
                setMax(maxBound);
            }
            isUpdating = false;
        };

        private final ChangeListener<Number> sliderValueChanged = (ch, o, n) -> {
            if (!isSliderVisible() || n == null || isUpdating) {
                return;
            }
            isUpdating = true;
            final Axis xAxis = getChart().getFirstAxis(Orientation.HORIZONTAL);
            if (xAxis.isAutoRanging() || xAxis.isAutoGrowRanging()) {
                setMin(xAxis.getMin());
                setMax(xAxis.getMax());
                isUpdating = false;
                return;
            }
            isUpdating = false;
        };

        private final EventHandler<? super MouseEvent> mouseEventHandler = (final MouseEvent event) -> {
            // disable auto ranging only when the slider interactor was used
            // by mouse/user
            // this is a work-around since the ChangeListener interface does
            // not contain
            // a event source object
            if (zoomStacks.isEmpty()) {
                makeSnapshotOfView();
            }
            final Axis xAxis = getChart().getFirstAxis(Orientation.HORIZONTAL);
            xAxis.setAutoRanging(false);
            xAxis.setAutoGrowRanging(false);
            xAxis.set(getLowValue(), getHighValue());
        };

        public ZoomRangeSlider(final Chart chart) {
            super();
            final Axis xAxis = chart.getFirstAxis(Orientation.HORIZONTAL);
            xRangeSlider = this;
            setPrefWidth(-1);
            setMaxWidth(Double.MAX_VALUE);

            xAxis.invertAxisProperty().bindBidirectional(invertedSlide);
            invertedSlide.addListener((ch, o, n) -> setRotate(Boolean.TRUE.equals(n) ? 180 : 0));

            xAxis.autoRangingProperty().addListener(sliderResetHandler);
            xAxis.autoGrowRangingProperty().addListener(sliderResetHandler);

            xAxis.minProperty().addListener(rangeChangeListener);
            xAxis.maxProperty().addListener(rangeChangeListener);

            // rstein: needed in case of autoranging/sliding xAxis (see
            // RollingBuffer for example)
            lowValueProperty().addListener(sliderValueChanged);
            highValueProperty().addListener(sliderValueChanged);

            setOnMouseReleased(mouseEventHandler);

            lowValueProperty().bindBidirectional(xAxis.minProperty());
            highValueProperty().bindBidirectional(xAxis.maxProperty());

            sliderVisibleProperty().addListener((ch, o, n) -> {
                if (getChart() == null || n.equals(o) || isUpdating) {
                    return;
                }
                isUpdating = true;
                if (Boolean.TRUE.equals(n)) {
                    getChart().getPlotArea().setBottom(xRangeSlider);
                    prefWidthProperty().bind(getChart().getCanvasForeground().widthProperty());
                } else {
                    getChart().getPlotArea().setBottom(null);
                    prefWidthProperty().unbind();
                }
                isUpdating = false;
            });

            addButtonsToToolBarProperty().addListener((ch, o, n) -> {
                final Chart chartLocal = getChart();
                if (chartLocal == null || n.equals(o)) {
                    return;
                }
                if (Boolean.TRUE.equals(n)) {
                    chartLocal.getToolBar().getChildren().add(zoomButtons);
                } else {
                    chartLocal.getToolBar().getChildren().remove(zoomButtons);
                }
            });

            xRangeSliderInit = true;
        }

        public void reset() {
            resetSlider(true);
        }

    } // ZoomRangeSlider
}
