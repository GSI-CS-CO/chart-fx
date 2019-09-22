package de.gsi.dataset.spi;

import java.util.ArrayDeque;

import de.gsi.dataset.DataSet2D;
import de.gsi.dataset.event.AddedDataEvent;

/**
 * TODO: Change to ErrorDataSet and calculate standard deviation.
 * 
 * @author braeun
 */
public class AveragingDataSet extends AbstractDataSet<AveragingDataSet> implements DataSet2D {

    private int averageSize = 1;
    private int fuzzyCount = 0;
    private InternalDataSet dataset;
    private final ArrayDeque<DataSet2D> deque = new ArrayDeque<>();

    /**
     * 
     * @param name data set name
     */
    public AveragingDataSet(String name) {
        super(name, 2);
    }

    /**
     * 
     * @param name data set name
     * @param fuzzyCount binning accuracy @see #setFuzzyCount
     */
    public AveragingDataSet(String name, int fuzzyCount) {
        super(name, 2);
        this.fuzzyCount = fuzzyCount;
    }

    /**
     * Gets the fuzzy count.
     * 
     * @return fuzzy count
     */
    public int getFuzzyCount() {
        return fuzzyCount;
    }

    /**
     * Sets the fuzzy count. The fuzzy count allows for a mismatch in the data count of the datasets added to the
     * average. Datasets will be added if the size difference is less or equal to the fuzzy count. In case of a mismatch
     * in size, the longer dataset will be truncated to the shorter one.
     * 
     * @param fuzzyCount the fuzzy count
     */
    public void setFuzzyCount(int fuzzyCount) {
        this.fuzzyCount = fuzzyCount;
    }

    /**
     * 
     * @return number of data sets that are supposed to be averaged
     */
    public int getAverageSize() {
        return averageSize;
    }

    /**
     * 
     * @param avgCount number of data sets that are supposed to be averaged
     */
    public void setAverageSize(int avgCount) {
        if (avgCount < 1) {
            return;
        }
        if (averageSize != avgCount) {
            deque.clear();
            dataset = null;
        }
        averageSize = avgCount;
    }

    /**
     * 
     * @return number of data sets that have been averaged
     */
    public int getAverageCount() {
        if (averageSize == 1) {
            return dataset == null ? 0 : 1;
        }
        return deque.size();
    }

    /**
     * clear all data
     */
    public void clear() {
        deque.clear();
        dataset = null;
    }

    /**
     * 
     * @param ds new DataSet to be added to average
     */
    public void add(DataSet2D ds) {
        if (averageSize == 1) {
            dataset = new InternalDataSet(ds);
        } else if (dataset == null || deque.isEmpty()) {
            dataset = new InternalDataSet(ds);
            deque.clear();
            deque.add(new InternalDataSet(ds));
        } else if (deque.size() < averageSize) {
            dataset.opScale(deque.size());
            dataset.opAdd(ds);
            deque.add(new InternalDataSet(ds));
            dataset.opScale(1.0 / deque.size());
        } else {
            dataset.opScale(deque.size());
            dataset.opSub(deque.pop());
            dataset.opAdd(ds);
            deque.add(new InternalDataSet(ds));
            dataset.opScale(1.0 / deque.size());
        }
        dataset.recomputeLimits(0);
        dataset.recomputeLimits(1);
        fireInvalidated(new AddedDataEvent(this));
    }

    @Override
    public int getDataCount() {
        if (dataset == null) {
            return 0;
        }
        return dataset.getDataCount();
    }

    @Override
    public double[] getYValues() {
        if (dataset == null) {
            return new double[0];
        }
        return dataset.getYValues();
    }

    @Override
    public double getX(int i) {
        if (dataset == null) {
            return 0;
        }
        return dataset.getX(i);
    }

    @Override
    public double getY(int i) {
        if (dataset == null) {
            return 0;
        }
        return dataset.getY(i);
    }

    @Override
    public int getXIndex(double x) {
        if (dataset == null) {
            return 0;
        }
        return dataset.getXIndex(x);
    }

    @Override
    public String getStyle(int index) {
        if (dataset == null) {
            return "";
        }
        return dataset.getStyle(index);
    }

    private class InternalDataSet extends DoubleDataSet {

        public InternalDataSet(DataSet2D ds) {
            super(ds.getName(), ds.getXValues(), ds.getYValues(), ds.getDataCount(), true);
            //      xValues = new double[ds.getDataCount()];
            //      yValues = new double[ds.getDataCount()];
            //      for (int i=0;i<yValues.length;i++)
            //      {
            //        xValues[i] = ds.getX(i);
            //        yValues[i] = ds.getY(i);
            //      }
            //      dataMaxIndex = ds.getDataCount();
        }

        public boolean isCompatible(DataSet2D d) {
            return Math.abs(super.getDataCount() - d.getDataCount()) <= fuzzyCount;
        }

        public void opAdd(DataSet2D d) {
            if (!isCompatible(d)) {
                throw new IllegalArgumentException("Datasets do not match");
            }

            yValues.size(d.getDataCount());
            for (int i = 0; i < yValues.size(); i++) {
                yValues.elements()[i] += d.getY(i);
            }
        }

        public void opSub(DataSet2D d) {
            if (!isCompatible(d)) {
                throw new IllegalArgumentException("Datasets do not match");
            }
            yValues.size(d.getDataCount());
            for (int i = 0; i < yValues.size(); i++) {
                yValues.elements()[i] -= d.getY(i);
            }
        }

        public void opScale(double f) {
            for (int i = 0; i < yValues.size(); i++) {
                yValues.elements()[i] *= f;
            }
        }

    }

}
