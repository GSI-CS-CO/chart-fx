package de.gsi.chart.ui.css;

import java.util.function.LongBinaryOperator;

import javafx.beans.NamedArg;
import javafx.css.CssMetaData;
import javafx.css.SimpleStyleableLongProperty;
import javafx.css.Styleable;

public class StylishLongProperty extends SimpleStyleableLongProperty {
    protected final Runnable preInvalidateAction;
    protected final Runnable postInvalidateAction;
    protected final LongBinaryOperator filter;

    /**
     * The constructor of the {@code StylishLongProperty}.
     *
     * @param cssMetaData the CssMetaData associated with this {@code StylishLongProperty}
     * @param bean the bean of this {@code LongProperty}
     * @param name the name of this {@code LongProperty}
     * @param initialValue the initial value of the wrapped {@code Object}
     * @param filter Possibility to modify the new value based on old and updated value
     * @param invalidateActions lambda expressions executed after and before invalidation
     */
    public StylishLongProperty(@NamedArg("cssMetaData") CssMetaData<? extends Styleable, Number> cssMetaData, @NamedArg("bean") Object bean,
            @NamedArg("name") String name, @NamedArg("initialValue") long initialValue, LongBinaryOperator filter,
            Runnable... invalidateActions) {
        super(cssMetaData, bean, name, initialValue);
        this.filter = filter;
        if (invalidateActions.length > 2) {
            throw new IllegalArgumentException("Only post- and pre invalidation actions allowed, but more than two actions supplied");
        }
        this.postInvalidateAction = invalidateActions.length > 0 ? invalidateActions[0] : null;
        this.preInvalidateAction = invalidateActions.length > 1 ? invalidateActions[0] : null;
    }

    @Override
    public void set(long v) {
        if (preInvalidateAction != null) {
            preInvalidateAction.run();
        }
        if (filter == null) {
            super.set(v);
        } else {
            super.set(filter.applyAsLong(get(), v));
        }
        if (postInvalidateAction != null) {
            postInvalidateAction.run();
        }
    }
}
