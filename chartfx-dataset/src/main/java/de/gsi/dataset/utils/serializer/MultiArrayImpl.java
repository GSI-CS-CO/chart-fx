package de.gsi.dataset.utils.serializer;

import java.lang.reflect.Array;
import java.util.Arrays;

import de.gsi.dataset.utils.AssertUtils;

/**
 * Implementation for multi-dimensional arrays of any type.
 * 
 * The representation of multi-dimensional array is in fact one-dimensional
 * array, because of 2 reasons: - we always want to support only rectangle
 * arrays (not arbitrary row length) - it corresponds to C++ implementation,
 * where 1D array is used as well (to support static multi-dimensional arrays)
 * 
 * <p>
 * exemplary use: double[] rawDouble = new double[10*20]; [..] int[] rawDouble =
 * new int[]{10,20}; // here: 2-dim array MultiArrayImpl<double[]> a =
 * MultiArrayImpl<>(rawDouble, dims);
 * 
 * double val = a.getDouble(new int[]{2,3});
 * 
 * @author Ilia Yastrebov, CERN
 * @author rstein
 * @param <T> generics for primitive array (ie. double[], float[], int[] ...)
 */
class MultiArrayImpl<T> implements MultiArray<T> {
    private final T elements; // Array of all elements
    private final int elementCount;

    // statically cast arrays
    protected transient Object[] elementObject;
    protected transient boolean[] elementBoolean;
    protected transient byte[] elementByte;
    protected transient short[] elementShort; // NOPMD
    protected transient int[] elementInt;
    protected transient long[] elementLong;
    protected transient float[] elementFloat;
    protected transient double[] elementDouble;
    protected transient String[] elementString;

    /** Dimensions */
    private final int[] dimensions;

    /**
     * Constructor (implicitly assumes assumes 1-dim array)
     * 
     * @param elements Elements of the array
     */
    MultiArrayImpl(final T elements) {
        this(elements, new int[] { elements == null ? 0 : Array.getLength(elements) });
    }

    /**
     * create new multi-dimensional array
     * 
     * @param elements Elements of the array
     * @param dimensions Dimensions vector
     */
    MultiArrayImpl(T elements, int[] dimensions) {
        AssertUtils.notNull("elements", elements);
        AssertUtils.notNull("dimensions", dimensions);
        this.elements = elements;
        this.elementCount = Array.getLength(elements);
        AssertUtils.gtEqThanZero("elements.length", elementCount);

        this.dimensions = dimensions;

        initPrimitiveArrays();
    }

    private void initPrimitiveArrays() { // NOPMD by rstein on 19/07/19 10:47
        // statically cast to primitive if possible
        // this adds the overhead of casting only once and subsequent double
        // get(..) calls are fast
        if (elements.getClass() == Object[].class) {
            elementObject = (Object[]) elements;
        } else if (elements.getClass() == boolean[].class) {
            elementBoolean = (boolean[]) elements;
        } else if (elements.getClass() == byte[].class) {
            elementByte = (byte[]) elements;
        } else if (elements.getClass() == short[].class) { // NOPMD
            elementShort = (short[]) elements; // NOPMD
        } else if (elements.getClass() == int[].class) {
            elementInt = (int[]) elements;
        } else if (elements.getClass() == long[].class) {
            elementLong = (long[]) elements;
        } else if (elements.getClass() == float[].class) {
            elementFloat = (float[]) elements;
        } else if (elements.getClass() == double[].class) {
            elementDouble = (double[]) elements;
        } else if (elements.getClass() == String[].class) {
            elementString = (String[]) elements;
        }
    }

    @Override
    public T getElements() {
        return this.elements;
    }

    @Override
    public int getElementsCount() {
        return elementCount;
    }

    @Override
    public Object get(int index) {
        return elementObject[index];
    }

    @Override
    public int getIndex(final int[] indices) {
        int index = 0;
        int multiplier = 1;

        for (int i = indices.length - 1; i >= 0; i--) {
            index += indices[i] * multiplier;
            multiplier *= getDimensions()[i];
        }
        return index;
    }

    @Override
    public Object get(final int[] indices) {
        return get(getIndex(indices));
    }

    @Override
    public boolean getBoolean(final int[] indices) {
        return elementBoolean[getIndex(indices)];
    }

    @Override
    public byte getByte(final int[] indices) {
        return elementByte[getIndex(indices)];
    }

    @Override
    public short getShort(final int[] indices) { // NOPMD
        return elementShort[getIndex(indices)];
    }

    @Override
    public int getInt(final int[] indices) {
        return elementInt[getIndex(indices)];
    }

    @Override
    public long getLong(final int[] indices) {
        return elementLong[getIndex(indices)];
    }

    @Override
    public float getFloat(final int[] indices) {
        return elementFloat[getIndex(indices)];
    }

    @Override
    public double getDouble(final int[] indices) {
        return elementDouble[getIndex(indices)];
    }

    @Override
    public String getString(final int[] indices) {
        return elementString[getIndex(indices)];
    }

    @Override
    public int[] getDimensions() {
        return this.dimensions;
    }

    @Override
    public String toString() {
        return String.format("MultiArray [dimensions = %s, elements = %s]", this.dimensions, this.elements.toString());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(dimensions);
        result = prime * result + ((elements == null) ? 0 : elements.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) { // NOPMD by rstein on 19/07/19 10:46
        if (obj == null || this.elements.getClass() != obj.getClass()) {
            // null object and/or different class type
            return false;
        }
        @SuppressWarnings("unchecked")
        MultiArrayImpl<T> other = (MultiArrayImpl<T>) obj;

        boolean retValue = false;
        try {
            if (Arrays.equals((Object[]) other.elements, (Object[]) this.elements)) {
                return true;
            }
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            if (Arrays.equals((boolean[]) other.elements, (boolean[]) this.elements)) {
                return true;
            }
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            retValue = Arrays.equals((byte[]) other.elements, (byte[]) this.elements);
        } catch (Exception c) {// Cover all possibilities

        }
        try {
            retValue = Arrays.equals((short[]) other.elements, (short[]) this.elements); // NOPMD
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            retValue = Arrays.equals((int[]) other.elements, (int[]) this.elements);
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            retValue = Arrays.equals((long[]) other.elements, (long[]) this.elements);
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            retValue = Arrays.equals((float[]) other.elements, (float[]) this.elements);
        } catch (Exception c) {// Cover all possibilities
        }
        try {
            retValue = Arrays.equals((double[]) other.elements, (double[]) this.elements);
        } catch (Exception c) {// Cover all possibilities
        }

        return retValue;
    }
}
