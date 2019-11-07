package de.gsi.chart.plugins.measurements;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.plugins.measurements.utils.CheckedValueField;
import de.gsi.chart.plugins.measurements.utils.DataSetSelector;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.event.EventListener;
import impl.org.controlsfx.skin.DecorationPane;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

/**
 * @author rstein
 */
public abstract class AbstractChartMeasurement implements EventListener {

    protected static final int PREFERRED_WIDTH = 300;
    protected static int markerCount;

    protected XYChart chart;
    protected final CheckedValueField valueField = new CheckedValueField();
    protected String title;
    private DataSet dataSet;
    protected VBox displayPane = new VBox();
    protected final Alert alert;
    protected final ButtonType buttonOK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    protected final ButtonType buttonDefault = new ButtonType("Defaults", ButtonBar.ButtonData.OK_DONE);
    protected final ButtonType buttonRemove = new ButtonType("Remove");
    protected final DataSetSelector dataSetSelector;
    protected final VBox vBox = new VBox();

    public AbstractChartMeasurement(final Chart chart) {
        if (chart == null) {
            throw new IllegalArgumentException("chart is null");
        }
        if (!(chart instanceof XYChart)) {
            throw new IllegalArgumentException(
                    "not (yet) designed for non XYCharts - type is " + chart.getClass().getSimpleName());
        }
        this.chart = (XYChart) chart;
        title = AbstractChartMeasurement.this.getClass().getSimpleName();
        dataSet = null;
        VBox.setMargin(displayPane, Insets.EMPTY);
        chart.getMeasurementBar(chart.getMeasurementBarSide()).getChildren().addAll(displayPane, new Pane());

        alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Measurement Config Dialog");
        alert.setHeaderText("Please, select data set and/or other parameters:");
        alert.initModality(Modality.APPLICATION_MODAL);

        final DecorationPane decorationPane = new DecorationPane();
        decorationPane.getChildren().add(vBox);
        alert.getDialogPane().setContent(decorationPane);
        alert.getButtonTypes().setAll(buttonOK, buttonDefault, buttonRemove);
        alert.setOnCloseRequest(evt -> alert.close());
        // add data set selector if necessary (ie. more than one data set available)
        dataSetSelector = new DataSetSelector(this.chart);
        if (dataSetSelector.getNumberDataSets() > 1) {
            vBox.getChildren().add(dataSetSelector);
        }

        displayPane.setOnMouseClicked(mevt -> {
            if (mevt.getButton().equals(MouseButton.SECONDARY)) {
                showConfigDialogue(); // #NOPMD cannot be called during construction (requires mouse event)
            }
        });
    }

    protected void defaultAction() {
        setDataSet(null);
    }

    public DataSet getDataSet() {
        if (dataSet == null) {
            final List<DataSet> allDataSets = new ArrayList<>(chart.getAllDatasets());
            return allDataSets.get(0);
        }

        return dataSet;
    }

    protected VBox getDialogContentBox() {
        return vBox;
    }

    public Pane getDisplayPane() {
        return displayPane;
    }

    public abstract void initialize();

    protected void nominalAction() {
        setDataSet(dataSetSelector.getSelectedDataSet());
    }

    protected void removeAction() {
        chart.getMeasurementBar(chart.getMeasurementBarSide()).getChildren().remove(displayPane);
    }

    public void setDataSet(final DataSet dataSet) {
        if (this.dataSet != null) {
            this.dataSet.removeListener(this);
        }

        if (dataSet == null) {
            valueField.setDataSetName("<unknown data set>");
            this.dataSet = dataSet;
        } else {
            valueField.setDataSetName("<" + dataSet.getName() + ">");
            this.dataSet = dataSet;
            this.dataSet.addListener(this);
        }
    }

    public void showConfigDialogue() {
        if (alert.isShowing()) {
            return;
        }
        final Optional<ButtonType> result = alert.showAndWait();
        if (!result.isPresent()) {
            defaultAction();
            return;
        }

        if (result.get() == buttonOK) {
            // ... user chose "OK"
            nominalAction();
        } else if (result.get() == buttonRemove) {
            // ... user chose "Remove"
            removeAction();
        } else {
            // default:
            defaultAction();
        }
        alert.close();
    }

}
