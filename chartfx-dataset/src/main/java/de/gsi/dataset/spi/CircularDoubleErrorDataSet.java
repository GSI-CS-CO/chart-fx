package de.gsi.dataset.spi;

import de.gsi.dataset.DataSetError;
import de.gsi.dataset.event.AddedDataEvent;
import de.gsi.dataset.event.InvalidatedEvent;
import de.gsi.dataset.utils.AssertUtils;
import de.gsi.dataset.utils.CircularBuffer;
import de.gsi.dataset.utils.DoubleCircularBuffer;

/**
 * @author rstein
 */
public class CircularDoubleErrorDataSet extends AbstractErrorDataSet<DoubleErrorDataSet> implements DataSetError {

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
        super(name);
        AssertUtils.gtEqThanZero("initalSize", initalSize);
        xValues = new DoubleCircularBuffer(initalSize);
        yValues = new DoubleCircularBuffer(initalSize);
        yErrorsPos = new DoubleCircularBuffer(initalSize);
        yErrorsNeg = new DoubleCircularBuffer(initalSize);
        dataTag = new CircularBuffer<>(initalSize);
        dataStyles = new CircularBuffer<>(initalSize);
        setErrorType(ErrorType.Y_ASYMMETRIC);
    }

    @Override
    public int getDataCount() {
        return xValues.available();
    }

    @Override
    public double getX(final int index) {
        return xValues.get(index);
    }

    @Override
    public double getY(final int index) {
        return yValues.get(index);
    }

    @Override
    public double getXErrorNegative(final int index) {
        return 0;
    }

    @Override
    public double getXErrorPositive(final int index) {
        return 0;
    }

    @Override
    public double getYErrorNegative(final int index) {
        return yErrorsNeg.get(index);
    }

    @Override
    public double getYErrorPositive(final int index) {
        return yErrorsPos.get(index);
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
        lock();

        xValues.put(x);
        yValues.put(y);
        yErrorsPos.put(yErrorPos);
        yErrorsNeg.put(yErrorNeg);
        dataTag.put(tag);
        dataStyles.put(style);

        computeLimits();
        unlock();
        fireInvalidated(new InvalidatedEvent(this));
        return this;
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
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double x, final double y, final double yErrorNeg,
            final double yErrorPos) {
        return add(x, y, yErrorNeg, yErrorPos, null);
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues the new x coordinates
     * @param yValues the new y coordinates
     * @param yErrorsNeg the +dy errors
     * @param yErrorsPos the -dy errors
     * @return itself
     */
    public CircularDoubleErrorDataSet add(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        lock();
        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        AssertUtils.equalDoubleArrays(xValues, yValues);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos);

        this.xValues.put(xValues, xValues.length);
        this.yValues.put(yValues, yValues.length);
        this.yErrorsNeg.put(yErrorsNeg, yErrorsNeg.length);
        this.yErrorsPos.put(yErrorsPos, yErrorsPos.length);
        dataTag.put(new String[yErrorsPos.length], yErrorsPos.length);
        dataStyles.put(new String[yErrorsPos.length], yErrorsPos.length);

        computeLimits();
        unlock();
        fireInvalidated(new InvalidatedEvent(this));
        return this;
    }

    /**
     * resets all data
     * @return itself (fluent design)
     */
    public CircularDoubleErrorDataSet reset() {
        lock();
        xValues.reset();
        yValues.reset();
        yErrorsNeg.reset();
        yErrorsPos.reset();
        dataTag.reset();
        dataStyles.reset();
        xRange.empty();
        yRange.empty();

        unlock();
        fireInvalidated(new InvalidatedEvent(this));
        return this;
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

}
