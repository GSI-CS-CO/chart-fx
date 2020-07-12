package de.gsi.dataset.serializer.spi.iobuffer;

import de.gsi.dataset.serializer.IoSerialiser;
import de.gsi.dataset.serializer.spi.FieldSerialiser;

/**
 * 
 * @author rstein
 *
 */
public class IoBufferFieldSerialiser extends FieldSerialiser {
    protected final IoSerialiser ioBuffer;

    /**
     * 
     * @param buffer reference to the IoBuffer to be used. The reseting/rewinding has to be done in the user-level code
     * @param reader consumer executed when reading from the back-end serialiser implementation
     * @param writer consumer executed when writing to the back-end serialiser implementation
     * @param classPrototype applicable class/interface prototype reference for which the consumers are applicable (e.g.
     *        example 1: 'List.class' for List&lt;String&gt; or example 2: 'Map.class' for Map&lt;Integer, String&gt;)
     * @param classGenericArguments applicable generics definition (e.g. 'String.class' for List&lt;String&gt; or
     *        'Integer.class, String.class' resp.)
     */
    public IoBufferFieldSerialiser(final IoSerialiser buffer, final FieldSerialiserFunction reader,
            final FieldSerialiserFunction writer, final Class<?> classPrototype,
            final Class<?>... classGenericArguments) {
        super(reader, writer, classPrototype, classGenericArguments);
        if (buffer == null) {
            throw new IllegalArgumentException("buffer must not be null");
        }
        ioBuffer = buffer;
    }

    /**
     * 
     * @return the IoSerialiser object used by this serialiser
     */
    public IoSerialiser getBuffer() {
        return ioBuffer;
    }
}
