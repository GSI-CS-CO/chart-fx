package de.gsi.dataset.serializer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ_WRITE;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.gsi.dataset.DataSet;
import de.gsi.dataset.serializer.spi.BinarySerialiser;
import de.gsi.dataset.serializer.spi.ByteBuffer;
import de.gsi.dataset.serializer.spi.FastByteBuffer;
import de.gsi.dataset.spi.DefaultErrorDataSet;

/**
 * @author rstein
 */
class IoClassSerialiserTests {
    private static final int BUFFER_SIZE = 20000;
    private static final String GLOBAL_LOCK = "lock";

    @ParameterizedTest(name = "IoBuffer class - {0}")
    @ValueSource(classes = { ByteBuffer.class, FastByteBuffer.class })
    @ResourceLock(value = GLOBAL_LOCK, mode = READ_WRITE)
    void testCustomSerialiserIdentity(final Class<? extends IoBuffer> bufferClass) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        assertNotNull(bufferClass, "bufferClass being not null");
        assertNotNull(bufferClass.getConstructor(int.class), "Constructor(Integer) present");
        final IoBuffer buffer = bufferClass.getConstructor(int.class).newInstance(2 * BUFFER_SIZE);

        IoClassSerialiser serialiser = new IoClassSerialiser(new BinarySerialiser(buffer));

        final AtomicInteger writerCalled = new AtomicInteger(0);
        final AtomicInteger returnCalled = new AtomicInteger(0);

        // add custom serialiser  - for more examples see classes in de.gsi.dataset.serializer.spi.iobuffer.*Helper
        // provide a writer function
        final FieldSerialiser.TriConsumer writeFunction = (io, obj, field) -> {
            final Object localObj = field == null || field.getField() == null ? obj : field.getField().get(obj);
            if (!(localObj instanceof CustomClass)) {
                throw new IllegalArgumentException("object " + obj + " is not of type CustomClass");
            }
            CustomClass customClass = (CustomClass) localObj;

            // place custom elements/composites etc. here - N.B. ordering is of paramount importance since
            // these raw fields are not preceded by field headers
            io.getBuffer().putDouble(customClass.testDouble);
            io.getBuffer().putInt(customClass.testInt);
            io.getBuffer().putString(customClass.testString);
            // [..] anything that can be generated with the IoSerialiser and/or IoBuffer interfaces

            writerCalled.getAndIncrement();
        };

        // provide a return function (can usually be re-used for the reader function)
        final FieldSerialiser.TriFunction<CustomClass> returnFunction = (io, obj, field) -> {
            final Object sourceField = field == null ? null : field.getField().get(obj); // get raw class field content

            // place reverse custom elements/composites etc. here - N.B. ordering is of paramount importance since
            final double doubleVal = io.getBuffer().getDouble();
            final int intVal = io.getBuffer().getInt();
            final String str = io.getBuffer().getString();
            // generate custom object or modify existing one
            returnCalled.getAndIncrement();
            if (sourceField == null) {
                return new CustomClass(doubleVal, intVal, str);
            } else {
                if (!(sourceField instanceof CustomClass)) {
                    throw new IllegalArgumentException("object " + obj + " is not of type CustomClass");
                }
                CustomClass customClass = (CustomClass) sourceField;
                customClass.testDouble = doubleVal;
                customClass.testInt = intVal;
                customClass.testString = str;
                return customClass;
            }
        };

        serialiser.addClassDefinition(new FieldSerialiser<>( //
                /* reader */ (io, obj, field) -> field.getField().set(obj, returnFunction.apply(io, obj, field)), //
                /* return */ returnFunction, //
                /* write */ writeFunction, CustomClass.class));

        final CustomClass sourceClass = new CustomClass();
        final CustomClass destinationClass = new CustomClass();

        writerCalled.set(0);
        returnCalled.set(0);
        // serialise-deserialise DataSet
        buffer.reset(); // '0' writing at start of buffer
        serialiser.serialiseObject(sourceClass);
        buffer.reset(); // reset to read position (==0)
        serialiser.deserialiseObject(destinationClass);

        assertEquals(sourceClass, destinationClass);
    }

    @ParameterizedTest(name = "IoBuffer class - {0}")
    @ValueSource(classes = { ByteBuffer.class, FastByteBuffer.class })
    void testGenericSerialiserIdentity(final Class<? extends IoBuffer> bufferClass) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
        assertNotNull(bufferClass, "bufferClass being not null");
        assertNotNull(bufferClass.getConstructor(int.class), "Constructor(Integer) present");
        final IoBuffer buffer = bufferClass.getConstructor(int.class).newInstance(2 * BUFFER_SIZE);

        IoClassSerialiser serialiser = new IoClassSerialiser(new BinarySerialiser(buffer));

        TestClass sourceClass = new TestClass();
        sourceClass.integerList = new ArrayList<>();
        sourceClass.integerList.add(1);
        sourceClass.integerList.add(2);
        sourceClass.integerList.add(3);
        sourceClass.stringList = new ArrayList<>();
        sourceClass.stringList.add("String1");
        sourceClass.stringList.add("String2");
        sourceClass.dataSet = new DefaultErrorDataSet("test", //
                new double[] { 1f, 2f, 3f }, new double[] { 6f, 7f, 8f }, //
                new double[] { 0.7f, 0.8f, 0.9f }, new double[] { 7f, 8f, 9f }, 3, false);
        sourceClass.dataSetList = new ArrayList<>();
        sourceClass.dataSetList.add(new DefaultErrorDataSet("ListDataSet#1"));
        sourceClass.dataSetList.add(new DefaultErrorDataSet("ListDataSet#2"));
        sourceClass.dataSetSet = new HashSet<>();
        sourceClass.dataSetSet.add(new DefaultErrorDataSet("SetDataSet#1"));
        sourceClass.dataSetSet.add(new DefaultErrorDataSet("SetDataSet#2"));
        sourceClass.dataSetQueue = new ArrayDeque<>();
        sourceClass.dataSetQueue.add(new DefaultErrorDataSet("QueueDataSet#1"));
        sourceClass.dataSetQueue.add(new DefaultErrorDataSet("QueueDataSet#2"));

        sourceClass.dataSetMap = new HashMap<>();
        sourceClass.dataSetMap.put("dataSet1", new DefaultErrorDataSet("MapDataSet#1"));
        sourceClass.dataSetMap.put("dataSet2", new DefaultErrorDataSet("MapDataSet#2"));

        final DefaultErrorDataSet keyDataSet1 = new DefaultErrorDataSet("KeyDataSet#1");
        final DefaultErrorDataSet keyDataSet2 = new DefaultErrorDataSet("KeyDataSet#2");
        sourceClass.dataSetStringMap = new HashMap<>();
        sourceClass.dataSetStringMap.put(keyDataSet1, "keyDataSet1");
        sourceClass.dataSetStringMap.put(keyDataSet2, "keyDataSet2");

        TestClass destinationClass = new TestClass();
        destinationClass.nullIntegerList = new ArrayList<>();
        destinationClass.nullDataSet = new DefaultErrorDataSet("nullDataSet");
        assertNotEquals(sourceClass.nullIntegerList, destinationClass.nullIntegerList);
        assertNotEquals(sourceClass.nullDataSet, destinationClass.nullDataSet);

        // serialise-deserialise DataSet
        buffer.reset(); // '0' writing at start of buffer
        serialiser.serialiseObject(sourceClass);
        buffer.reset(); // reset to read position (==0)
        serialiser.deserialiseObject(destinationClass);

        buffer.reset(); // reset to read position (==0)
        serialiser.deserialiseObject(destinationClass);

        assertEquals(sourceClass.integerList, destinationClass.integerList);
        assertEquals(1, destinationClass.integerList.get(0));
        assertEquals(2, destinationClass.integerList.get(1));
        assertEquals(3, destinationClass.integerList.get(2));

        assertEquals(sourceClass.stringList, destinationClass.stringList);
        assertEquals("String1", destinationClass.stringList.get(0));
        assertEquals("String2", destinationClass.stringList.get(1));

        // assertEquals(sourceClass.emptyIntegerList, destinationClass.emptyIntegerList); cannot assure that null is serialised will map to empty list

        assertEquals(sourceClass.dataSet, destinationClass.dataSet);
        // assertEquals(sourceClass.nullDataSet, destinationClass.nullDataSet);

        assertEquals(sourceClass.dataSetList, destinationClass.dataSetList);
        assertEquals("ListDataSet#1", destinationClass.dataSetList.get(0).getName());
        assertEquals("ListDataSet#2", destinationClass.dataSetList.get(1).getName());

        assertEquals(sourceClass.dataSetSet, destinationClass.dataSetSet);
        assertTrue(destinationClass.dataSetSet.stream().anyMatch(ds -> ds.getName().equals("SetDataSet#1")));
        assertTrue(destinationClass.dataSetSet.stream().anyMatch(ds -> ds.getName().equals("SetDataSet#2")));

        //assertEquals(sourceClass.dataSetQueue, destinationClass.dataSetQueue);
        assertTrue(destinationClass.dataSetQueue.stream().anyMatch(ds -> ds.getName().equals("QueueDataSet#1")));
        assertTrue(destinationClass.dataSetQueue.stream().anyMatch(ds -> ds.getName().equals("QueueDataSet#2")));

        assertEquals(sourceClass.dataSetMap, destinationClass.dataSetMap);
        assertNotNull(destinationClass.dataSetMap.get("dataSet1"));
        assertNotNull(destinationClass.dataSetMap.get("dataSet2"));

        assertEquals(sourceClass.dataSetStringMap, destinationClass.dataSetStringMap);
        assertEquals("keyDataSet1", destinationClass.dataSetStringMap.get(keyDataSet1));
        assertEquals("keyDataSet2", destinationClass.dataSetStringMap.get(keyDataSet2));
    }

    static class CustomClass {
        public double testDouble;
        public int testInt;
        public String testString;

        public CustomClass() {
            this(-1.0, -1, "null string"); // null instantiation
        }
        public CustomClass(final double testDouble, final int testInt, final String testString) {
            this.testDouble = testDouble;
            this.testInt = testInt;
            this.testString = testString;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            final CustomClass that = (CustomClass) o;
            return Double.compare(that.testDouble, testDouble) == 0 && testInt == that.testInt && Objects.equals(testString, that.testString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(testDouble, testInt, testString);
        }

        @Override
        public String toString() {
            return "CustomClass(" + testDouble + ", " + testInt + ", " + testString + ")";
        }
    }

    /**
     * small test class to test (de-)serialisation of wrapped and/or compound object types
     */
    static class TestClass {
        public List<Integer> integerList;
        public List<String> stringList;
        public List<Integer> nullIntegerList;
        public DataSet dataSet;
        public DataSet nullDataSet;
        public List<DataSet> dataSetList;
        public Set<DataSet> dataSetSet;
        public Queue<DataSet> dataSetQueue;

        public Map<String, DataSet> dataSetMap;
        public Map<DataSet, String> dataSetStringMap;
    }
}
