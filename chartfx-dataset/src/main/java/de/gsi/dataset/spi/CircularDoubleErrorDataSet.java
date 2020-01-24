package de.gsi.dataset.spi;

import de.gsi.dataset.AxisDescription;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.DataSet2D;
import de.gsi.dataset.DataSetError;
import de.gsi.dataset.event.AddedDataEvent;
import de.gsi.dataset.event.RemovedDataEvent;
import de.gsi.dataset.utils.AssertUtils;
import de.gsi.dataset.utils.CircularBuffer;
import de.gsi.dataset.utils.DoubleCircularBuffer;

/**
 * @author rstein
 */
public class CircularDoubleErrorDataSet extends AbstractErrorDataSet<CircularDoubleErrorDataSet>
        implements DataSet2D, DataSetError {
    private static final long serialVersionUID = -8010355203980379253L;
    protected DoubleCircularBuffer xValues;
    protected DoubleCircularBuffer yValues;
    protected DoubleCircularBuffer yErrorsPos;
    protected DoubleCircularBuffer yErrorsNeg;
    protected CircularBuffer<String> dataTag;
    protected CircularBuffer<String> dataStyles;

    /**
     * Creates a new instance of <code>CircularDoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum circular buffer capacity
     * @throws IllegalArgumentException if <code>name</code> is <code>null</code>
     */
    public CircularDoubleErrorDataSet(final String name, final int initalSize) {
        super(name, 2, ErrorType.NO_ERROR, ErrorType.ASYMMETRIC);
        AssertUtils.gtEqThanZero("initalSize", initalSize);
        xValues = new DoubleCircularBuffer(initalSize);
        yValues = new DoubleCircularBuffer(initalSize);
        yErrorsPos = new DoubleCircularBuffer(initalSize);
        yErrorsNeg = new DoubleCircularBuffer(initalSize);
        dataTag = new CircularBuffer<>(initalSize);
        dataStyles = new CircularBuffer<>(initalSize);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg,
            final double yErrorPos) {
        return add(x, y, yErrorNeg, yErrorPos, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @param tag the data tag
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg,
            final double yErrorPos, final String tag) {
        return add(x, y, yErrorNeg, yErrorPos, tag, null);
    }

    /**
     * Add point to the DoublePoints object
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @param yErrorNeg the +dy error
     * @param yErrorPos the -dy error
     * @param tag the data tag
     * @param style the data style string
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg,
            final double yErrorPos, final String tag, final String style) {
        lock().writeLockGuard(() -> {
            xValues.put(x);
            yValues.put(y);
            yErrorsPos.put(yErrorPos);
            yErrorsNeg.put(yErrorNeg);
            dataTag.put(tag);
            dataStyles.put(style);

            recomputeLimits(DIM_X);
            recomputeLimits(DIM_Y);
        });

        return fireInvalidated(new AddedDataEvent(this));
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xVals the new x coordinates
     * @param yVals the new y coordinates
     * @param yErrNeg the +dy errors
     * @param yErrPos the -dy errors
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double[] xVals, final double[] yVals, final double[] yErrNeg,
            final double[] yErrPos) {
        AssertUtils.notNull("X coordinates", xVals);
        AssertUtils.notNull("Y coordinates", yVals);
        AssertUtils.notNull("Y error neg", yErrNeg);
        AssertUtils.notNull("Y error pos", yErrPos);
        AssertUtils.equalDoubleArrays(xVals, yVals);
        AssertUtils.equalDoubleArrays(xVals, yErrNeg);
        AssertUtils.equalDoubleArrays(xVals, yErrPos);

        lock().writeLockGuard(() -> {
            this.xValues.put(xVals, xVals.length);
            this.yValues.put(yVals, yVals.length);
            this.yErrorsNeg.put(yErrNeg, yErrNeg.length);
            this.yErrorsPos.put(yErrPos, yErrPos.length);
            dataTag.put(new String[yErrPos.length], yErrPos.length);
            dataStyles.put(new String[yErrPos.length], yErrPos.length);

            recomputeLimits(DIM_X);
            recomputeLimits(DIM_Y);
        });

        return fireInvalidated(new AddedDataEvent(this));
    }

    @Override
    public int getDataCount(final int dimIndex) {
        return xValues.available();
    }

    /**
     * Returns label of a data point specified by the index. The label can be used as a category name if
     * CategoryStepsDefinition is used or for annotations displayed for data points.
     *
     * @param index data point index
     * @return label of a data point specified by the index or <code>null</code> if none label has been specified for
     *         this data point.
     */
    @Override
    public String getDataLabel(final int index) {
        final String tag = dataTag.get(index);
        if (tag == null) {
            return getName() + "(" + index + "," + getX(index) + "," + getY(index) + ")";
        }
        return tag;
    }

    @Override
    public double getErrorNegative(final int dimIndex, final int index) {
        return dimIndex == DIM_X ? 0.0 : yErrorsNeg.get(index);
    }

    @Override
    public double getErrorPositive(final int dimIndex, final int index) {
        return dimIndex == DIM_X ? 0.0 : yErrorsPos.get(index);
    }

    /**
     * A string representation of the CSS style associated with this specific {@code DataSet} data point. @see
     * #getStyle()
     *
     * @param index the index of the specific data point
     * @return user-specific data set style description (ie. may be set by user)
     */
    @Override
    public String getStyle(final int index) {
        return dataStyles.get(index);
    }

    @Override
    public final double get(final int dimIndex, final int index) {
        return dimIndex == DataSet.DIM_X ? xValues.get(index) : yValues.get(index);
    }

    /**
     * resets all data
     * 
     * @return itself (fluent design)
     */
    public CircularDoubleErrorDataSet reset() {
        lock().writeLockGuard(() -> {
            xValues.reset();
            yValues.reset();
            yErrorsNeg.reset();
            yErrorsPos.reset();
            dataTag.reset();
            dataStyles.reset();
            getAxisDescriptions().forEach(AxisDescription::clear);
        });

        return fireInvalidated(new RemovedDataEvent(this));
    }
}
