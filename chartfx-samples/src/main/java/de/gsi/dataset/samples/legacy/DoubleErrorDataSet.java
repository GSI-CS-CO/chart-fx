package de.gsi.dataset.samples.legacy;

import java.util.Arrays;
import java.util.Map;

import de.gsi.dataset.DataSet;
import de.gsi.dataset.DataSetError;
import de.gsi.dataset.EditableDataSet;
import de.gsi.dataset.event.AddedDataEvent;
import de.gsi.dataset.event.RemovedDataEvent;
import de.gsi.dataset.event.UpdatedDataEvent;
import de.gsi.dataset.spi.AbstractErrorDataSet;
import de.gsi.dataset.utils.AssertUtils;

/**
 * Implementation of the <code>DataSetError</code> interface which stores x,y,
 * +eyn, -eyn values in separate double arrays. It provides methods allowing
 * easily manipulate of data points. <br>
 * User provides X and Y coordinates or only Y coordinates. In the former case X
 * coordinates have value of data point index. This version being optimised for
 * native double arrays.
 *
 * @see DoubleDataSet for an equivalent implementation without errors
 *
 * @author rstein
 * @deprecated this is kept for reference/performance comparisons only
 */
@SuppressWarnings("PMD")
public class DoubleErrorDataSet extends AbstractErrorDataSet<DoubleErrorDataSet>
        implements DataSetError, EditableDataSet {
    protected double[] xValues;
    protected double[] yValues;
    protected double[] yErrorsPos;
    protected double[] yErrorsNeg;
    protected int dataMaxIndex; // <= xValues.length, stores the actually used
                                // data array size

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @throws IllegalArgumentException if <code>name</code> is
     *             <code>null</code>
     */
    public DoubleErrorDataSet(final String name) {
        this(name, 0);
    }

    /**
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     *
     * @param name name of this DataSet.
     * @param initalSize maximum capacity of buffer
     * @throws IllegalArgumentException if <code>name</code> is
     *             <code>null</code>
     */
    public DoubleErrorDataSet(final String name, final int initalSize) {
        super(name);
        AssertUtils.gtEqThanZero("initalSize", initalSize);
        xValues = new double[initalSize];
        yValues = new double[initalSize];
        yErrorsPos = new double[initalSize];
        yErrorsNeg = new double[initalSize];
        setErrorType(ErrorType.Y_ASYMMETRIC);
        dataMaxIndex = 0;
    }

    /**
     * Creates a new instance of <code>DoubleDataSet</code> as copy of another
     * (deep-copy).
     *
     * @param another name of this DataSet.
     */
    public DoubleErrorDataSet(final DataSet another) {
        this("");
        another.lock();
        this.setName(another.getName());
        this.set(another); // NOPMD by rstein on 25/06/19 07:42
        another.unlock();
    }

    /**
     * <p>
     * Creates a new instance of <code>DoubleErrorDataSet</code>.
     * </p>
     *
     * @param name name of this data set.
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param yErrorsNeg Y negative coordinate error
     * @param yErrorsPos Y positive coordinate error
     * @param nData how many data points are relevant to be taken
     * @param deepCopy if true, the input array is copied
     * @throws IllegalArgumentException if any of parameters is
     *             <code>null</code> or if arrays with coordinates have
     *             different lengths
     */
    public DoubleErrorDataSet(final String name, final double[] xValues, final double[] yValues,
            final double[] yErrorsNeg, final double[] yErrorsPos, final int nData, boolean deepCopy) {
        this(name, Math.min(xValues.length, Math.min(yValues.length, nData)));
        AssertUtils.notNull("X data", xValues);
        AssertUtils.notNull("Y data", yValues);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        final int errorMin = Math.min(Math.min(yErrorsPos.length, yErrorsNeg.length), nData);
        dataMaxIndex = Math.min(xValues.length, Math.min(yValues.length, errorMin));
        AssertUtils.equalDoubleArrays(xValues, yValues, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg, dataMaxIndex);
        if (dataMaxIndex != 0 && deepCopy) {
            System.arraycopy(xValues, 0, this.xValues, 0, dataMaxIndex);
            System.arraycopy(yValues, 0, this.yValues, 0, dataMaxIndex);
            System.arraycopy(yErrorsPos, 0, this.yErrorsPos, 0, dataMaxIndex);
            System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, 0, dataMaxIndex);
        } else {
            this.xValues = xValues;
            this.yValues = yValues;
            this.yErrorsPos = yErrorsPos;
            this.yErrorsNeg = yErrorsNeg;
        }
        computeLimits();
    }

    @Override
    public double[] getXValues() {
        return xValues;
    }

    @Override
    public double[] getYValues() {
        return yValues;
    }

    @Override
    public double[] getYErrorsPositive() {
        return yErrorsPos;
    }

    @Override
    public double[] getYErrorsNegative() {
        return yErrorsNeg;
    }

    @Override
    public int getDataCount() {
        return Math.min(dataMaxIndex, xValues.length);
    }

    /**
     * clears all data
     * 
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet clearData() {
        lock();

        dataMaxIndex = 0;
        Arrays.fill(xValues, 0.0);
        Arrays.fill(yValues, 0.0);
        Arrays.fill(yErrorsPos, 0.0);
        Arrays.fill(yErrorsNeg, 0.0);
        dataLabels.isEmpty();
        dataStyles.isEmpty();

        xRange.empty();
        yRange.empty();

        return unlock().fireInvalidated(new RemovedDataEvent(this, "clearData()"));
    }

    @Override
    public double getX(final int index) {
        return xValues[index];
    }

    @Override
    public double getY(final int index) {
        return yValues[index];
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
        return yErrorsNeg[index];
    }

    @Override
    public double getYErrorPositive(final int index) {
        return yErrorsPos[index];
    }

    /**
     * replaces point coordinate of existing data point
     * 
     * @param index the index of the data point
     * @param x new horizontal coordinate
     * @param y new vertical coordinate N.B. errors are implicitly assumed to be
     *            zero
     * @return itself (fluent design)
     */
    @Override
    public DoubleErrorDataSet set(int index, double x, double y) {
        return set(index, x, y, 0.0, 0.0);
    }

    /**
     * replaces point coordinate of existing data point
     * 
     * @param index the index of the data point
     * @param x new horizontal coordinate
     * @param y new vertical coordinate
     * @param yErrorNeg new vertical negative error of y (can be asymmetric)
     * @param yErrorPos new vertical positive error of y (can be asymmetric)
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet set(final int index, final double x, final double y, final double yErrorNeg,
            final double yErrorPos) {
        lock();
        final boolean oldAuto = isAutoNotification();
        setAutoNotifaction(false);

        try {
            if (index < dataMaxIndex) {
                xValues[index] = x;
                yValues[index] = y;
                yErrorsPos[index] = yErrorPos;
                yErrorsNeg[index] = yErrorNeg;
            } else {
                this.add(x, y, yErrorNeg, yErrorPos);
            }

            xRange.add(x);
            yRange.add(y - yErrorNeg);
            yRange.add(y + yErrorPos);
        } finally {
            setAutoNotifaction(oldAuto);
            unlock();
        }

        return fireInvalidated(new UpdatedDataEvent(this));
    }

    /**
     * Add point to the DoublePoints object. Errors in y are assumed 0.
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     * @return itself
     */
    public DoubleErrorDataSet add(final double x, final double y) {
        return add(x, y, 0.0, 0.0);
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
    public DoubleErrorDataSet add(final double x, final double y, final double yErrorNeg, final double yErrorPos) {
        lock();

        // enlarge array if necessary
        if (dataMaxIndex > (xValues.length - 1)) {
            final double[] xValuesNew = new double[xValues.length + 1];
            final double[] yValuesNew = new double[yValues.length + 1];
            final double[] yErrorsNegNew = new double[yValues.length + 1];
            final double[] yErrorsPosNew = new double[yValues.length + 1];

            System.arraycopy(xValues, 0, xValuesNew, 0, xValues.length);
            System.arraycopy(yValues, 0, yValuesNew, 0, yValues.length);
            System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, yValues.length);
            System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, yValues.length);

            xValues = xValuesNew;
            yValues = yValuesNew;
            yErrorsPos = yErrorsPosNew;
            yErrorsNeg = yErrorsNegNew;
        }

        xValues[dataMaxIndex] = x;
        yValues[dataMaxIndex] = y;
        yErrorsPos[dataMaxIndex] = yErrorPos;
        yErrorsNeg[dataMaxIndex] = yErrorNeg;
        dataMaxIndex++;

        xRange.add(x);
        yRange.add(y - yErrorNeg);
        yRange.add(y + yErrorPos);

        unlock();
        fireInvalidated(new AddedDataEvent(this));
        return this;
    }

    @Override
    public EditableDataSet remove(int index) {
        return remove(index, index + 1);
    }

    /**
     * remove sub-range of data points
     * 
     * @param fromIndex start index
     * @param toIndex stop index
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet remove(final int fromIndex, final int toIndex) {
        lock();
        AssertUtils.indexInBounds(fromIndex, getDataCount(), "fromIndex");
        AssertUtils.indexInBounds(toIndex, getDataCount(), "toIndex");
        AssertUtils.indexOrder(fromIndex, "fromIndex", toIndex, "toIndex");
        final int diffLength = toIndex - fromIndex;

        // TODO: performance-critical memory/cpu tradeoff
        // check whether this really needed (keeping the data and reducing just
        // the dataMaxIndex costs some memory but saves new allocation
        final int newLength = xValues.length - diffLength;
        final double[] xValuesNew = new double[newLength];
        final double[] yValuesNew = new double[newLength];
        final double[] yErrorsNegNew = new double[newLength];
        final double[] yErrorsPosNew = new double[newLength];
        System.arraycopy(xValues, 0, xValuesNew, 0, fromIndex);
        System.arraycopy(yValues, 0, yValuesNew, 0, fromIndex);
        System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, fromIndex);
        System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, fromIndex);
        System.arraycopy(xValues, toIndex, xValuesNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yValues, toIndex, yValuesNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yErrorsNeg, toIndex, yErrorsNegNew, fromIndex, newLength - fromIndex);
        System.arraycopy(yErrorsPos, toIndex, yErrorsPosNew, fromIndex, newLength - fromIndex);
        xValues = xValuesNew;
        yValues = yValuesNew;
        yErrorsPos = yErrorsPosNew;
        yErrorsNeg = yErrorsNegNew;
        dataMaxIndex = Math.max(0, dataMaxIndex - diffLength);

        xRange.empty();
        yRange.empty();

        unlock().fireInvalidated(new RemovedDataEvent(this));
        return this;
    }

    @Override
    public EditableDataSet add(int index, double x, double y) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param yErrorsNeg the +dy errors
     * @param yErrorsPos the -dy errors
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet add(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        lock();

        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        AssertUtils.equalDoubleArrays(xValues, yValues);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos);

        final int newLength = this.getDataCount() + xValues.length;
        // need to allocate new memory
        if (newLength > this.xValues.length) {
            final double[] xValuesNew = new double[newLength];
            final double[] yValuesNew = new double[newLength];
            final double[] yErrorsNegNew = new double[newLength];
            final double[] yErrorsPosNew = new double[newLength];

            // copy old data
            System.arraycopy(this.xValues, 0, xValuesNew, 0, getDataCount());
            System.arraycopy(this.yValues, 0, yValuesNew, 0, getDataCount());
            System.arraycopy(yErrorsNeg, 0, yErrorsNegNew, 0, getDataCount());
            System.arraycopy(yErrorsPos, 0, yErrorsPosNew, 0, getDataCount());

            this.xValues = xValuesNew;
            this.yValues = yValuesNew;
            this.yErrorsNeg = yErrorsNegNew;
            this.yErrorsPos = yErrorsPosNew;
        }

        // N.B. getDataCount() should equal dataMaxIndex here
        System.arraycopy(xValues, 0, this.xValues, getDataCount(), newLength - getDataCount());
        System.arraycopy(yValues, 0, this.yValues, getDataCount(), newLength - getDataCount());
        System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, getDataCount(), newLength - getDataCount());
        System.arraycopy(yErrorsPos, 0, this.yErrorsPos, getDataCount(), newLength - getDataCount());

        dataMaxIndex = Math.max(0, dataMaxIndex + xValues.length);
        computeLimits();

        unlock();
        fireInvalidated(new AddedDataEvent(this));
        return this;
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param yErrorsNeg the +dy errors
     * @param yErrorsPos the -dy errors
     * @param copy true: makes an internal copy, false: use the pointer as is
     *            (saves memory allocation)
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet set(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos, final boolean copy) {
        lock();
        AssertUtils.notNull("X coordinates", xValues);
        AssertUtils.notNull("Y coordinates", yValues);
        AssertUtils.notNull("Y error neg", yErrorsNeg);
        AssertUtils.notNull("Y error pos", yErrorsPos);
        final int errorMin = Math.min(yErrorsPos.length, yErrorsNeg.length);
        dataMaxIndex = Math.min(xValues.length, Math.min(yValues.length, errorMin));
        AssertUtils.equalDoubleArrays(xValues, yValues, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsNeg, dataMaxIndex);
        AssertUtils.equalDoubleArrays(xValues, yErrorsPos, dataMaxIndex);

        if (!copy) {
            this.xValues = xValues;
            this.yValues = yValues;
            this.yErrorsNeg = yErrorsNeg;
            this.yErrorsPos = yErrorsPos;
            computeLimits();
            unlock();
            fireInvalidated(new UpdatedDataEvent(this));
            return this;
        }

        if (xValues.length == this.xValues.length) {
            System.arraycopy(xValues, 0, this.xValues, 0, getDataCount());
            System.arraycopy(yValues, 0, this.yValues, 0, getDataCount());
            System.arraycopy(yErrorsNeg, 0, this.yErrorsNeg, 0, getDataCount());
            System.arraycopy(yErrorsPos, 0, this.yErrorsPos, 0, getDataCount());
        } else {
            /*
             * copy into new arrays, forcing array length to be equal to the
             * xValues length
             */
            this.xValues = Arrays.copyOf(xValues, xValues.length);
            this.yValues = Arrays.copyOf(yValues, xValues.length);
            this.yErrorsNeg = Arrays.copyOf(yErrorsNeg, xValues.length);
            this.yErrorsPos = Arrays.copyOf(yErrorsPos, xValues.length);
        }

        computeLimits();

        return unlock().fireInvalidated(new UpdatedDataEvent(this));
    }

    /**
     * <p>
     * Initialises the data set with specified data.
     * </p>
     * Note: The method copies values from specified double arrays.
     *
     * @param xValues X coordinates
     * @param yValues Y coordinates
     * @param yErrorsNeg the +dy errors
     * @param yErrorsPos the -dy errors
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet set(final double[] xValues, final double[] yValues, final double[] yErrorsNeg,
            final double[] yErrorsPos) {
        return set(xValues, yValues, yErrorsNeg, yErrorsPos, true);
    }

    /**
     * clear old data and overwrite with data from 'other' data set (deep copy)
     * 
     * @param other the other data set
     * @return itself (fluent design)
     */
    public DoubleErrorDataSet set(final DataSet other) {
        lock();
        other.lock();

        // deep copy data point labels and styles
        dataLabels.clear();
        for (int index = 0; index < other.getDataCount(); index++) {
            final String label = other.getDataLabel(index);
            if (label != null && !label.isEmpty()) {
                this.addDataLabel(index, label);
            }
        }
        dataStyles.clear();
        for (int index = 0; index < other.getDataCount(); index++) {
            final String style = other.getStyle(index);
            if (style != null && !style.isEmpty()) {
                this.addDataStyle(index, style);
            }
        }

        this.setStyle(other.getStyle());

        // copy data
        if (other instanceof DataSetError) {
            this.set(other.getXValues(), other.getYValues(), ((DataSetError) other).getYErrorsNegative(),
                    ((DataSetError) other).getYErrorsPositive(), true);
        } else {
            final int count = other.getDataCount();
            this.set(other.getXValues(), other.getYValues(), new double[count], new double[count], true);
        }

        other.unlock();
        return unlock();
    }

}
