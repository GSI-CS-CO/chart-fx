package de.gsi.chart.samples.financial.service;

import de.gsi.chart.samples.financial.dos.OrderContainer;
import de.gsi.chart.samples.financial.dos.PositionContainer;
import de.gsi.dataset.spi.financial.api.attrs.AttributeKey;

public class StandardTradePlanAttributes {
    /**
     * The performed trading orders
     */
    public static final AttributeKey<OrderContainer> ORDERS = AttributeKey.create(OrderContainer.class, "ORDERS");

    /**
     * The opened/closed trading positions
     */
    public static final AttributeKey<PositionContainer> POSITIONS = AttributeKey.create(PositionContainer.class, "POSITIONS");

    /** The trading asset identification - this symbol will be traded by execution platform
     * The more providers are supported, this one is main for trading. */
    public static final AttributeKey<String> ASSET_NAME = AttributeKey.create(String.class, "ASSET_NAME");

    /** The account ID  */
    public static final AttributeKey<String> ACCOUNT_ID = AttributeKey.create(String.class, "ACCOUNT_ID");

    private StandardTradePlanAttributes() {
    }
}
