/**
 * LGPL-3.0, 2020/21, GSI-CS-CO/Chart-fx, BTA HF OpenSource Java-FX Branch, Financial Charts
 */
package de.gsi.chart.samples.financial;

import static de.gsi.chart.samples.financial.service.StandardTradePlanAttributes.POSITIONS;

import java.text.ParseException;

import javafx.application.Application;

import de.gsi.chart.XYChart;
import de.gsi.chart.renderer.ErrorStyle;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.renderer.spi.financial.CandleStickRenderer;
import de.gsi.chart.renderer.spi.financial.PositionFinancialRendererPaintAfterEP;
import de.gsi.chart.samples.financial.dos.Position;
import de.gsi.chart.samples.financial.dos.Position.PositionStatus;
import de.gsi.chart.samples.financial.dos.PositionContainer;
import de.gsi.chart.samples.financial.service.CalendarUtils;
import de.gsi.chart.samples.financial.service.order.PositionFinancialDataSet;
import de.gsi.dataset.spi.DefaultDataSet;
import de.gsi.dataset.spi.financial.OhlcvDataSet;
import de.gsi.dataset.spi.financial.api.attrs.AttributeModel;

/**
 * Financial Position Sample
 *
 * @author afischer
 */
public class FinancialPositionSample extends AbstractBasicFinancialApplication {
    protected void prepareRenderers(XYChart chart, OhlcvDataSet ohlcvDataSet, DefaultDataSet indiSet) {
        // define context
        AttributeModel context = new AttributeModel()
                                         .setAttribute(POSITIONS, new PositionContainer());

        // direct define closed positions
        addClosedPosition(context, 0, "2020/09/01 16:00", 3516.75, "2020/09/04 16:00", 3407.25, 1, 1, resource);
        addClosedPosition(context, 1, "2020/09/10 16:00", 3330.00, "2020/09/18 16:00", 3316.25, 1, -1, resource);
        addClosedPosition(context, 2, "2020/09/28 16:00", 3291.00, "2020/10/05 16:00", 3393.00, 1, 1, resource);
        addClosedPosition(context, 3, "2020/09/28 16:00", 3291.00, "2020/10/19 16:00", 3422.75, 1, 1, resource);

        // position/order visualization
        PositionFinancialDataSet positionFinancialDataSet = new PositionFinancialDataSet(
                resource, ohlcvDataSet, context);

        // create and apply renderers
        CandleStickRenderer candleStickRenderer = new CandleStickRenderer();
        candleStickRenderer.getDatasets().addAll(ohlcvDataSet);
        candleStickRenderer.addPaintAfterEp(new PositionFinancialRendererPaintAfterEP(
                positionFinancialDataSet, chart));

        ErrorDataSetRenderer avgRenderer = new ErrorDataSetRenderer();
        avgRenderer.setDrawMarker(false);
        avgRenderer.setErrorType(ErrorStyle.NONE);
        avgRenderer.getDatasets().addAll(indiSet);

        chart.getRenderers().clear();
        chart.getRenderers().add(candleStickRenderer);
        chart.getRenderers().add(avgRenderer);
    }

    // helpers methods --------------------------------------------------------
    private void addClosedPosition(AttributeModel context, int id, String entryTimePattern, double entryPrice, String exitTimePattern, double exitPrice,
            int quantity, int longShort, String symbol) {
        try {
            context.getRequiredAttribute(POSITIONS).addPosition(getClosedPosition(id, entryTimePattern, entryPrice, exitTimePattern, exitPrice, quantity, longShort, symbol));
        } catch (ParseException e) {
            throw new IllegalArgumentException("The time pattern is not correctly configured! e=" + e.getMessage(), e);
        }
    }

    // Create opened position by basic attributes
    private Position getClosedPosition(int id, String entryTimePattern, double entryPrice, String exitTimePattern, double exitPrice,
            int quantity, int longShort, String symbol) throws ParseException {
        return closePosition(getOpenedPosition(id, entryTimePattern, entryPrice, quantity, longShort, symbol), exitTimePattern, exitPrice);
    }

    // Create opened position by basic attributes
    private Position getOpenedPosition(int id, String entryTimePattern, double entryPrice,
            int quantity, int longShort, String symbol) throws ParseException {
        return new Position(id, null, "strategy1",
                CalendarUtils.createByDateTime(entryTimePattern).getTime(), longShort, symbol,
                "account1", entryPrice, quantity);
    }

    // Close the position, new domain object is created.
    private Position closePosition(Position position, String exitTimePattern, double exitPrice) throws ParseException {
        Position positionClosed = position.copyDeep();
        positionClosed.setExitTime(CalendarUtils.createByDateTime(exitTimePattern).getTime());
        positionClosed.setPositionExitIndex(positionClosed.getExitTime().getTime()); // indices are driven by time for this example
        positionClosed.setExitPrice(exitPrice);
        positionClosed.setPositionStatus(PositionStatus.CLOSED);

        return positionClosed;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }
}
