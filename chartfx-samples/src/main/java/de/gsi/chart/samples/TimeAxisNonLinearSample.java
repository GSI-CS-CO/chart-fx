package de.gsi.chart.samples;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import de.gsi.chart.XYChart;
import de.gsi.chart.axes.AxisLabelOverlapPolicy;
import de.gsi.chart.axes.spi.AxisRange;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.axes.spi.format.DefaultTimeFormatter;
import de.gsi.chart.plugins.DataPointTooltip;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.XValueIndicator;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.utils.FXUtils;
import de.gsi.dataset.spi.DoubleDataSet;
import de.gsi.dataset.spi.LimitedIndexedTreeDataSet;
import de.gsi.dataset.utils.ProcessingProfiler;

public class TimeAxisNonLinearSample extends Application {
    private static final Timer timer = new Timer();

    @Override
    public void start(final Stage primaryStage) {
        ProcessingProfiler.setVerboseOutputState(true);
        ProcessingProfiler.setLoggerOutputState(true);
        ProcessingProfiler.setDebugState(false);

        final var root = new BorderPane();
        final var scene = new Scene(root, 1400, 600);

        final var xAxis1 = new NonLinearTimeAxis("time", "iso");
        xAxis1.setThreshold(0.6);
        xAxis1.setWeight(0.975);
        final var yAxis1 = new DefaultNumericAxis("y-axis", "a.u.");

        final var chart = new XYChart(xAxis1, yAxis1);
        chart.legendVisibleProperty().set(true);
        chart.getPlugins().add(new Zoomer());
        chart.getPlugins().add(new EditAxis());
        chart.getPlugins().add(new DataPointTooltip());
        // set them false to make the plot faster
        chart.setAnimated(false);
        ((ErrorDataSetRenderer) (chart.getRenderers().get(0))).setAllowNaNs(true);
        ((ErrorDataSetRenderer) (chart.getRenderers().get(0))).setPointReduction(false);

        yAxis1.setAutoRangeRounding(true);

        final var dataSet = new LimitedIndexedTreeDataSet("TestData", 100_000, 60);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                final double now = System.currentTimeMillis() / 1000.0 + 1; // N.B. '+1'
                dataSet.add(now - (dataSet.getMaxLength()), Double.NaN, 0., 0.); // first point for long-term history
                dataSet.add(now, 100 * Math.cos(2.0 * Math.PI * now), 0., 0.);
            }
        }, 1000, 40);

        long startTime = ProcessingProfiler.getTimeStamp();
        chart.getDatasets().add(dataSet);
        ProcessingProfiler.getTimeDiff(startTime, "adding data to chart");

        startTime = ProcessingProfiler.getTimeStamp();
        final var spThreshold = new Slider(0.0, 1.0, xAxis1.getThreshold());
        spThreshold.setMajorTickUnit(0.1);
        spThreshold.setSnapToTicks(true);
        spThreshold.setShowTickLabels(true);
        spThreshold.setShowTickMarks(true);
        HBox.setHgrow(spThreshold, Priority.ALWAYS);
        spThreshold.valueProperty().bindBidirectional(xAxis1.thresholdProperty());

        final var xValueIndicator = new XValueIndicator(xAxis1, xAxis1.getWidth() * xAxis1.getThreshold(), "long-short");
        xValueIndicator.setEditable(false);
        dataSet.addListener(evt -> {
            final var locator = xAxis1.getValueForDisplay(xAxis1.getThreshold() * xAxis1.getWidth());
            FXUtils.runFX(() -> xValueIndicator.setValue(locator));
        });
        chart.getPlugins().add(xValueIndicator);

        final var spWeight = new Slider(0.0, 1.0, xAxis1.getWeight());
        spWeight.setMajorTickUnit(0.1);
        spWeight.setShowTickLabels(true);
        spWeight.setSnapToTicks(true);
        spWeight.setShowTickMarks(true);
        HBox.setHgrow(spWeight, Priority.ALWAYS);
        spWeight.valueProperty().bindBidirectional(xAxis1.weightProperty());

        root.setTop(new VBox(new HBox(new Label("threshold: "), spThreshold), new HBox(new Label("weight: "), spWeight)));
        root.setCenter(chart);
        ProcessingProfiler.getTimeDiff(startTime, "adding chart into StackPane");

        startTime = ProcessingProfiler.getTimeStamp();
        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(evt -> Platform.exit());
        primaryStage.show();
        ProcessingProfiler.getTimeDiff(startTime, "for showing");

        final var diagChart = new XYChart();
        diagChart.getPlugins().addAll(new Zoomer(), new EditAxis(), new DataPointTooltip());
        final var function = new DoubleDataSet("function");
        final var inverse = new DoubleDataSet("inverse");
        final var identity = new DoubleDataSet("identity");
        diagChart.getDatasets().addAll(function, inverse, identity);

        final var nSamples = 1000;
        Runnable updateFunction = () -> {
            function.clearData();
            inverse.clearData();
            identity.clearData();
            for (var i = 0; i < nSamples; i++) {
                final double x = (double) i / (nSamples - 1);
                function.add(x, NonLinearTimeAxis.forwardTransform(x, xAxis1.getThreshold(), xAxis1.getWeight()));
                inverse.add(x, NonLinearTimeAxis.backwardTransform(x, xAxis1.getThreshold(), xAxis1.getWeight()));
                identity.add(x, NonLinearTimeAxis.backwardTransform(NonLinearTimeAxis.forwardTransform(x, xAxis1.getThreshold(), xAxis1.getWeight()), xAxis1.getThreshold(), xAxis1.getWeight()));
            }
        };
        updateFunction.run();
        spThreshold.valueProperty().addListener(evt -> updateFunction.run());
        spWeight.valueProperty().addListener(evt -> updateFunction.run());
        root.setBottom(diagChart);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }

    public static class NonLinearTimeAxis extends DefaultNumericAxis { // NOPMD NOSONAR -- inheritance depth of 8 vs. desired 5 (unavoidable with JavaFX)
        private final transient DoubleProperty threshold = new SimpleDoubleProperty(this, "threshold", 0.6); // 0.6
        private final transient DoubleProperty weight = new SimpleDoubleProperty(this, "weight", 0.9);
        private final transient DefaultTimeFormatter lowerFormat = new DefaultTimeFormatter();
        private final transient DefaultTimeFormatter upperFormat = new DefaultTimeFormatter();

        NonLinearTimeAxis(final String axisLabel, final String unit) {
            super(axisLabel, unit);

            setOverlapPolicy(AxisLabelOverlapPolicy.SKIP_ALT);
            setAutoRangeRounding(false);
            super.setTimeAxis(true);
        }

        @Override
        public double getDisplayPosition(final double value) {
            final double diffMin = value - getMin();
            final double range = Math.abs(getMax() - getMin());
            final double relPos = diffMin / range;
            return forwardTransform(relPos, getThreshold(), getWeight()) * getWidth();
        }

        public double getThreshold() {
            return threshold.get();
        }

        public void setThreshold(final double threshold) {
            this.threshold.set(threshold);
        }

        @Override
        public double getValueForDisplay(final double displayPosition) {
            final double relPosition = displayPosition / getWidth();
            final double range = Math.abs(getMax() - getMin());
            return getMin() + backwardTransform(relPosition, getThreshold(), getWeight()) * range;
        }

        public double getWeight() {
            return weight.get();
        }

        public void setWeight(final double weight) {
            this.weight.set(weight);
        }

        public DoubleProperty thresholdProperty() {
            return threshold;
        }

        public DoubleProperty weightProperty() {
            return weight;
        }

        @Override
        protected List<Double> calculateMajorTickValues(final double axisLength, final AxisRange axisRange) {
            final int nTicks = getMaxMajorTickLabelCount();
            final List<Double> tickValues = new ArrayList<>(nTicks);

            final double nTicksHalf1 = nTicks * getThreshold();
            final var lower = new ArrayList<Double>((int) nTicksHalf1);
            final double min = getValueForDisplay(0.01 * axisLength);
            lower.add(min);
            tickValues.add(min);

            for (var i = 1; i < nTicksHalf1; i++) {
                final var axisPos = (double) i / nTicksHalf1 * getThreshold() * axisLength;
                final double value = (getValueForDisplay(axisPos));
                tickValues.add(value);
                lower.add(value);
            }

            final double nTicksHalf2 = nTicks * (1.0 - getThreshold());
            final var upper = new ArrayList<Double>((int) nTicksHalf2);
            final double atThreshold = getValueForDisplay(getThreshold() * axisLength);
            tickValues.add(atThreshold); // fixed limit at threshold boundary
            upper.add(atThreshold);
            lower.add(atThreshold);
            lowerFormat.updateFormatter(lower, 1.0);

            for (var i = 1; i < nTicksHalf2 - 2; i++) {
                final var axisPos = (1.0 + (double) i / nTicksHalf2) * getThreshold() * axisLength;
                final double value = (getValueForDisplay(axisPos));
                tickValues.add(value);
                upper.add(value);
            }
            upperFormat.updateFormatter(upper, 1.0);

            return tickValues;
        }

        @Override
        public String getTickMarkLabel(final double value) {
            final Double boxedValue = value;
            if (getDisplayPosition(value) < getThreshold() * getWidth()) {
                return (getWeight() > getThreshold() ? lowerFormat : upperFormat).toString(boxedValue); // large values
            }
            return (getWeight() > getThreshold() ? upperFormat : lowerFormat).toString(boxedValue); // small values
        }

        public static double backwardTransform(final double x, final double threshold, final double weight) {
            if (x < threshold) {
                return weight * x / threshold;
            }
            return weight + (1.0 - weight) / (1.0 - threshold) * (x - threshold);
        }

        public static double forwardTransform(final double x, final double threshold, final double weight) {
            if (x < weight) {
                return threshold * x / weight;
            }
            return threshold + (1.0 - threshold) / (1.0 - weight) * (x - weight);
        }
    }
}
