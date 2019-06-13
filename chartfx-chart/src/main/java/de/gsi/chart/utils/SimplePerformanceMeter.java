package de.gsi.chart.utils;

import java.lang.management.ManagementFactory;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import com.sun.javafx.perf.PerformanceTracker;
import com.sun.management.OperatingSystemMXBean;

import javafx.animation.AnimationTimer;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Scene;

public class SimplePerformanceMeter extends AnimationTimer {
    private OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
    private static PerformanceTracker tracker;
    private final long[] frameTimes = new long[100];
    private int frameTimeIndex = 0;
    private boolean arrayFilled = false;
    // IIR-alpha typically: alpha ~ Ts /(Ts+T) with Ts = 100 & alpha = 0.01 ->
    // T~10s
    private DoubleProperty averageFactor = new SimpleDoubleProperty(this, "averageFactor", 0.01);
    private DoubleProperty frameRate = new SimpleDoubleProperty(this, "frameRate", 0);
    private DoubleProperty avgFrameRate = new SimpleDoubleProperty(this, "avgFrameRate", 0);
    private DoubleProperty trackerFrameRate = new SimpleDoubleProperty(this, "trackerFrameRate", 0);
    private DoubleProperty avgTrackerFrameRate = new SimpleDoubleProperty(this, "avgTrackerFrameRate", 0);
    private DoubleProperty processCpuLoad = new SimpleDoubleProperty(this, "processCpuLoad", 0);

    private DoubleProperty minProcessCpuLoad = new SimpleDoubleProperty(this, "minProcessCpuLoad", -1);
    private DoubleProperty avgProcessCpuLoad = new SimpleDoubleProperty(this, "avgProcessCpuLoad", -1);
    private DoubleProperty maxProcessCpuLoad = new SimpleDoubleProperty(this, "maxProcessCpuLoad", -1);

    private DoubleProperty systemCpuLoad = new SimpleDoubleProperty(this, "systemCpuLoad", 0);
    private DoubleProperty avgSystemCpuLoad = new SimpleDoubleProperty(this, "avgSystemCpuLoad", -1);
    private double frameRateLocal;

    /**
     * 
     * IIR-alpha filter constant as in y(n) = alpha * x(n) + (1-alpha) * y(n-1)
     * 
     * typically: alpha ~ Ts /(Ts+T) with
     * 
     * 'Ts' being the sampling period, and 'T' the desired IIR time constant
     * 
     * @return average factor alpha
     */
    public DoubleProperty averageFactorProperty() {
        return averageFactor;
    }

    public double getFxFrameRate() {
        return fxFrameRateProperty().get();
    }

    public ReadOnlyDoubleProperty fxFrameRateProperty() {
        return frameRate;
    }

    public double getAverageFxFrameRate() {
        return averageFxFrameRateProperty().get();
    }

    public ReadOnlyDoubleProperty averageFxFrameRateProperty() {
        return avgFrameRate;
    }

    public double getActualFrameRate() {
        return actualFrameRateProperty().get();
    }

    public ReadOnlyDoubleProperty actualFrameRateProperty() {
        return trackerFrameRate;
    }

    public double getAverageFrameRate() {
        return averageFrameRateProperty().get();
    }

    public ReadOnlyDoubleProperty averageFrameRateProperty() {
        return avgTrackerFrameRate;
    }

    public double getProcessCpuLoad() {
        return processCpuLoadProperty().get();
    }

    public ReadOnlyDoubleProperty processCpuLoadProperty() {
        return processCpuLoad;
    }

    public double getMinProcessCpuLoad() {
        return minProcessCpuLoadProperty().get();
    }

    public ReadOnlyDoubleProperty minProcessCpuLoadProperty() {
        return minProcessCpuLoad;
    }

    public double getAverageProcessCpuLoad() {
        return averageProcessCpuLoadProperty().get();
    }

    public double getMaxProcessCpuLoad() {
        return minProcessCpuLoadProperty().get();
    }

    public ReadOnlyDoubleProperty maxProcessCpuLoadProperty() {
        return maxProcessCpuLoad;
    }

    public ReadOnlyDoubleProperty averageProcessCpuLoadProperty() {
        return avgProcessCpuLoad;
    }

    public double getSystemCpuLoad() {
        return systemCpuLoadProperty().get();
    }

    public ReadOnlyDoubleProperty systemCpuLoadProperty() {
        return systemCpuLoad;
    }

    public double getAverageSystemCpuLoad() {
        return averageSystemCpuLoadProperty().get();
    }

    public ReadOnlyDoubleProperty averageSystemCpuLoadProperty() {
        return avgSystemCpuLoad;
    }

    @Override
    public void handle(long now) {
        long oldFrameTime = frameTimes[frameTimeIndex];
        frameTimes[frameTimeIndex] = now;
        frameTimeIndex = (frameTimeIndex + 1) % frameTimes.length;
        if (frameTimeIndex == 0) {
            arrayFilled = true;
        }
        if (!arrayFilled) {
            return;
        }
        long elapsedNanos = now - oldFrameTime;
        long elapsedNanosPerFrame = elapsedNanos / frameTimes.length;
        frameRateLocal = 1_000_000_000.0 / elapsedNanosPerFrame;
    }

    private void updateProperties() {
        // to be compatible with 'top' definition multiply CPU loads with number
        // of cores, ie. one fully loaded core yields 100%
        int nCores = osBean.getAvailableProcessors();
        frameRate.set(frameRateLocal);
        avgFrameRate.set(tracker.getAveragePulses());
        trackerFrameRate.set(tracker.getInstantFPS());
        avgTrackerFrameRate.set(tracker.getAverageFPS());
        processCpuLoad.set(osBean.getProcessCpuLoad() * 100 * nCores);
        systemCpuLoad.set(osBean.getSystemCpuLoad() * 100 * nCores);

        final double alpha = averageFactor.get();
        double oldProcessLoad = this.avgProcessCpuLoad.get();
        if (oldProcessLoad < 0) {
            avgProcessCpuLoad.set(processCpuLoad.get());
        } else {
            avgProcessCpuLoad.set((1 - alpha) * oldProcessLoad + alpha * processCpuLoad.get());
        }
        if (minProcessCpuLoad.get() < 0) {
            minProcessCpuLoad.set(processCpuLoad.get());
        } else {
            minProcessCpuLoad.set(Math.min(minProcessCpuLoad.get(), processCpuLoad.get()));
        }
        if (maxProcessCpuLoad.get() < 0) {
            maxProcessCpuLoad.set(processCpuLoad.get());
        } else {
            maxProcessCpuLoad.set(Math.max(maxProcessCpuLoad.get(), processCpuLoad.get()));
        }

        double oldSystemLoad = this.avgSystemCpuLoad.get();
        if (oldSystemLoad < 0) {
            avgSystemCpuLoad.set(systemCpuLoad.get());
        } else {
            avgSystemCpuLoad.set((1 - alpha) * oldSystemLoad + alpha * systemCpuLoad.get());
        }
    }

    public void resetAverages() {
        tracker.resetAveragePulses();
        tracker.resetAverageFPS();
        minProcessCpuLoad.set(-1);
        avgProcessCpuLoad.set(-1);
        maxProcessCpuLoad.set(-1);
        avgSystemCpuLoad.set(-1);
    }

    public SimplePerformanceMeter(Scene scene, long updateDuration) {

        tracker = PerformanceTracker.getSceneTracker(scene);
        this.start();

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    FXUtils.runLater(SimplePerformanceMeter.this::updateProperties);
                } catch (ExecutionException e) {
                    // not of concern
                }
            }

        }, 0, updateDuration);
    }

}
