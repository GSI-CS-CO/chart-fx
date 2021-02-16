package de.gsi.dataset.spi;

import java.util.ArrayList;
import java.util.List;

import de.gsi.dataset.AxisDescription;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.DataSet2D;
import de.gsi.dataset.DataSetError;
import de.gsi.dataset.event.AddedDataEvent;
import de.gsi.dataset.event.RemovedDataEvent;
import de.gsi.dataset.spi.utils.DoublePointError;
import de.gsi.dataset.utils.LimitedQueue;

/**
 * Limited Fifo DoubleErrorDataSet.
 * Maximum number of samples and maximum horizontal span are configurable
 * @author rstein
 */
public class FifoDoubleErrorDataSet extends AbstractErrorDataSet<DoubleErrorDataSet> implements DataSetError, DataSet2D {
    private static final int SAFE_BET = 1;
    private static final long serialVersionUID = -7153702141838930486L;
    protected final transient LimitedQueue<DataBlob> data;
    protected double maxDistance = Double.MAX_VALUE;

    /**
     * Creates a new instance of <code>FifoDoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum circular buffer capacity
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public FifoDoubleErrorDataSet(final String name, final int initalSize) {
        this(name, initalSize, Double.MAX_VALUE);
    }

    /**
     * Creates a new instance of <code>FifoDoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum circular buffer capacity
     * @param maxDistance maximum range before data points are being dropped
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public FifoDoubleErrorDataSet(final String name, final int initalSize, final double maxDistance) {
        super(name, 2, ErrorType.NO_ERROR, ErrorType.SYMMETRIC);
        if (initalSize <= 0) {
            throw new IllegalArgumentException("negative or zero initalSize = " + initalSize);
        }
        if (maxDistance <= 0) {
            throw new IllegalArgumentException("negative or zero maxDistance = " + maxDistance);
        }
        this.maxDistance = maxDistance;
        data = new LimitedQueue<>(initalSize);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the -dy error
     * @param yErrorPos the +dy error
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos) {
        return add(x, y, yErrorNeg, yErrorPos, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the -dy error
     * @param yErrorPos the +dy error
     * @param tag the data tag
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos,
            final String tag) {
        return add(x, y, yErrorNeg, yErrorPos, tag, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the -dy error
     * @param yErrorPos the +dy error
     * @param tag the data tag
     * @param style the data point style
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos,
            final String tag, final String style) {
        lock().writeLockGuard(() -> {
            data.add(new DataBlob(x, y, yErrorNeg, yErrorPos, tag, style));
            this.getAxisDescription(DIM_X).add(x);
            this.getAxisDescription(DIM_Y).add(y - yErrorNeg);
            this.getAxisDescription(DIM_Y).add(y + yErrorPos);

            // remove old fields if necessary
            expire(x);
        });
        fireInvalidated(new AddedDataEvent(this));

        return this;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues the new x coordinates
     * @param yValues the new y coordinates
     * @param yErrorsNeg the -dy errors
     * @param yErrorsPos the +dy errors
     * @return itself
     */
    public FifoDoubleErrorDataSet add(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        lock().writeLockGuard(() -> {
            for (int i = 0; i < xValues.length; i++) {
                this.add(xValues[i], yValues[i], yErrorsNeg[i], yErrorsPos[i]);
            }
        });
        fireInvalidated(new AddedDataEvent(this));
        return this;
    }

    /**
     * expire data points that are older than now minus length of the buffer, notifies a 'fireInvalidated()' in case
     * data has been removed
     *
     * @param now the newest time-stamp
     * @return number of items that have been removed
     */
    public int expire(final double now) {
        final int dataPointsToRemove = lock().writeLockGuard(() -> {
            final List<DataBlob> toRemoveList = new ArrayList<>(SAFE_BET);
            for (final DataBlob blob : data) {
                final double x = blob.getX();

                if (!Double.isFinite(x) || Math.abs(now - x) > maxDistance) {
                    toRemoveList.add(blob);
                }
            }

            if (!toRemoveList.isEmpty()) {
                // remove elements and invalidate ranges if necessary
                data.removeAll(toRemoveList);
                getAxisDescriptions().forEach(AxisDescription::clear);
            }
            return toRemoveList.size();
        });
        if (dataPointsToRemove != 0) {
            fireInvalidated(new RemovedDataEvent(this, "expired data"));
        }
        return dataPointsToRemove;
    }

    @Override
    public final double get(final int dimIndex, final int index) {
        return dimIndex == DataSet.DIM_X ? data.get(index).getX() : data.get(index).getY();
    }

    /**
     * @return the internal data container (N.B. this is not thread-safe)
     */
    public LimitedQueue<DataBlob> getData() {
        return data;
    }

    @Override
    public int getDataCount() {
        return data.size();
    }

    @Override
    public String getDataLabel(final int index) {
        return data.get(index).getDataLabel();
    }

    @Override
    public double getErrorNegative(final int dimIndex, final int index) {
        return dimIndex == DIM_X ? 0.0 : data.get(index).getErrorX();
    }

    @Override
    public double getErrorPositive(final int dimIndex, final int index) {
        return dimIndex == DIM_X ? 0.0 : data.get(index).getErrorY();
    }

    /**
     * @return maximum range before data points are being dropped
     */
    public double getMaxDistance() {
        return maxDistance;
    }

    @Override
    public String getStyle(final int index) {
        return data.get(index).getStyle();
    }

    /**
     * remove all data points
     */
    public void reset() {
        data.clear();
        fireInvalidated(new RemovedDataEvent(this, "reset"));
    }

    /**
     * @param maxDistance maximum range before data points are being dropped
     */
    public void setMaxDistance(final double maxDistance) {
        this.maxDistance = maxDistance;
    }

    protected static class DataBlob extends DoublePointError {
        protected String style;
        protected String tag;

        protected DataBlob(final double x, final double y, final double errorYNeg, final double errorYPos, final String tag,
                final String style) {
            super(x, y, errorYNeg, errorYPos);
            this.tag = tag;
            this.style = style;
        }

        public String getDataLabel() {
            return tag;
        }

        public String getStyle() {
            return style;
        }
    }

    @Override
    public DataSet set(final DataSet other, final boolean copy) {
        throw new UnsupportedOperationException("copy setting transposed data set is not implemented");
    }
}
