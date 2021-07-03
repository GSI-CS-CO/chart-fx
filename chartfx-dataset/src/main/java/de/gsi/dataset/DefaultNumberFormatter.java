package de.gsi.dataset;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * DefaultNumberFormatter implementing the Formatter&lt;T&gt; interface.
 *
 * The number representation can be set via #setFormatMode
 * <ul>
 * <li> FIXED_WIDTH_ONLY: using decimal representations only. The string width is set via #setNumberOfCharacters(int)</li>
 * <li> FIXED_WIDTH_EXP: using exponential representation only. The string width is set via #setNumberOfCharacters(int)</li>
 * <li> FIXED_WIDTH_AND_EXP: using decimal or exponential representations, with preference order: shorter representation, more significant digits.
 *         The string width is set via #setNumberOfCharacters(int)
 *         N.B. This mode is only useful for #getNumberOfCharacters width greater than 6 (due to 'E[+,-]0' overhead)
 * </li>
 * <li> OPTIMAL_WIDTH (default): using the shorter of decimal vs. exponential representation
 *         Number of significant digits is set via #setFixedPrecision(int)
 * </li>
 * <li> METRIC_PREFIX: using standard SI/metric-unit prefixes.
 *         Number of significant digits is set via #setFixedPrecision(int)
 *         The default precision is 3, i.e. 1234.5678 -&gt; "1.234k"
 * </li>
 * <li> BYTE_PREFIX: using standard SI/metric-unit prefixes with base '1024'.</li>
 * <li> JDK: JDK default using the 'toString()' method</li>
 * </ul>
 *
 * @author rstein
 * @see de.gsi.dataset.Formatter
 */
public class DefaultNumberFormatter implements Formatter<Number> {
    protected static final int NO_PREFIX_OFFSET = 8;
    protected static final String SI_PREFIX = "yzafpnµm kMGTPEZY";
    protected static final String SI_PREFIX_TEST = "yzafpnuµmkKMGTPEZY"; // N.B. doubling of micro and kilo representation for parsing
    protected static final int[] SI_PREFIX_EXP = { -24, -21, -18, -15, -12, -9, -6, -6, -3, 3, 3, 6, 9, 12, 15, 18, 21, 24 };
    protected DecimalFormat decimalFormat = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.UK));
    protected DecimalFormat decimalFormatMaxPrecision = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.UK));
    protected String fixedLengthFormat;
    protected String fixPrecisionFormat;
    protected String fixPrecisionFormatZero;
    private int numberOfCharacters = 6; // N.B. exp-form only useful for width>=6
    private int fixedPrecision = 3;
    private FormatMode formatMode = FormatMode.OPTIMAL_WIDTH;

    public DefaultNumberFormatter() {
        decimalFormat.setMaximumFractionDigits(20);
        setNumberOfCharacters(5); // NOPMD
        setFixedPrecision(fixedPrecision);
    }

    @Override
    public @NotNull Number fromString(final @NotNull String string) {
        if (FormatMode.METRIC_PREFIX.equals(formatMode)) {
            return metricParse(string, false);
        }
        if (FormatMode.BYTE_PREFIX.equals(formatMode)) {
            return metricParse(string, true);
        }
        final var ret = Double.valueOf(string); // NOSONAR NOPMD
        final var retInt = ret.longValue();
        if (retInt == ret) {
            return retInt; // return as long value
        }
        return ret;
    }

    @Override
    public final Class<Number> getClassInstance() {
        return Number.class;
    }

    public int getFixedPrecision() {
        return fixedPrecision;
    }

    public FormatMode getFormatMode() {
        return formatMode;
    }

    public int getNumberOfCharacters() {
        return numberOfCharacters;
    }

    public void setFixedPrecision(final int fixedPrecision) {
        assert fixedPrecision >= 0 : "precision must be larger 0, is: " + fixedPrecision;
        fixPrecisionFormat = "%." + fixedPrecision + "f%c";
        fixPrecisionFormatZero = "%." + fixedPrecision + "f";
        decimalFormatMaxPrecision.setMaximumFractionDigits(fixedPrecision);
        this.fixedPrecision = fixedPrecision;
    }

    public void setFormatMode(final FormatMode formatMode) {
        this.formatMode = formatMode;
    }

    public void setNumberOfCharacters(final int numberOfCharacters) {
        assert numberOfCharacters >= 0 : "numberOfCharacters must be larger 0, is: " + numberOfCharacters;
        fixedLengthFormat = "%1$" + numberOfCharacters + 's';
        this.numberOfCharacters = numberOfCharacters;
    }

    @Override
    public @NotNull String toString(@NotNull final Number number) {
        if (number.doubleValue() == Double.NEGATIVE_INFINITY) {
            // short-cut for negative infinity
            return formatMode.fixedWidth() ? String.format(fixedLengthFormat, "-∞") : "-∞";
        }
        if (number.doubleValue() == Double.POSITIVE_INFINITY) {
            // short-cut for positive infinity
            return formatMode.fixedWidth() ? String.format(fixedLengthFormat, "+∞") : "+∞";
        }
        if (Double.isNaN(number.doubleValue())) {
            // short-cut for not-a-number
            return formatMode.fixedWidth() ? String.format(fixedLengthFormat, "NaN") : "NaN";
        }

        if (FormatMode.METRIC_PREFIX.equals(formatMode)) {
            return metricFormat(number.doubleValue(), false);
        }

        if (FormatMode.BYTE_PREFIX.equals(formatMode)) {
            return metricFormat(number.doubleValue(), true);
        }

        if (FormatMode.FIXED_WIDTH_EXP.equals(formatMode)) {
            return expFormatFixedWidth(number.doubleValue(), numberOfCharacters);
        }

        if (FormatMode.OPTIMAL_WIDTH.equals(formatMode)) {
            return optimalWidthFormat(number.doubleValue());
        }

        if (FormatMode.FIXED_WIDTH_ONLY.equals(formatMode) || FormatMode.FIXED_WIDTH_AND_EXP.equals(formatMode)) {
            return fixedWidthFormat(number.doubleValue());
        }

        return number.toString(); // JDK default
    }

    protected String metricFormat(final double value, final boolean byteFormat) {
        if (value == 0) {
            // format '0' with correct number of digits
            return String.format(fixPrecisionFormatZero, 0.0);
        }

        // order of magnitude in units of '1000' (or '1024' for byteFormat==true)
        final int orderOfMagnitude3 = Math.min((int) Math.floor((byteFormat ? (Math.log10(Math.abs(value)) / Math.log10(1024.)) : (Math.log10(Math.abs(value)) / 3))), SI_PREFIX.length() - NO_PREFIX_OFFSET - 1);
        final int prefix_index = orderOfMagnitude3 + NO_PREFIX_OFFSET;
        final double scaledValue = value / (byteFormat ? Math.pow(1024., orderOfMagnitude3) : Math.pow(10., orderOfMagnitude3 * 3.0)); // scale value into the range [1, 1000]

        if (prefix_index < 0 || prefix_index >= SI_PREFIX.length()) { // outside prefix range
            return String.format("%." + fixedPrecision + "fe%d", scaledValue, orderOfMagnitude3 * 3); // NOSONAR
        }

        return String.format(prefix_index != NO_PREFIX_OFFSET ? fixPrecisionFormat : fixPrecisionFormatZero, scaledValue, SI_PREFIX.charAt(prefix_index));
    }

    protected static double metricParse(@NotNull String str, final boolean byteFormat) {
        if (StringUtils.containsAny(str, SI_PREFIX_TEST)) {
            final String[] split = StringUtils.splitByCharacterType(str);
            assert split.length > 1 : "malformed string: '" + str + "'";
            final String prefix = split[split.length - 1];
            final int index = SI_PREFIX_TEST.indexOf(prefix.charAt(0));
            assert index >= 0 : "could not find matching index for prefix '" + prefix.charAt(0) + "'";

            final var prefixScale = byteFormat ? Math.pow(1024., SI_PREFIX_EXP[index] / 3.0) : Math.pow(10., SI_PREFIX_EXP[index]);
            final var mantissa = Double.parseDouble(str.substring(0, str.length() - 1));

            return mantissa * prefixScale;
        }
        return Double.parseDouble(str);
    }

    protected static String expFormatFixedWidth(final double value, final int width) {
        final int order = (int) Math.floor(Math.log10(Math.abs(value)));
        final int orderExp = (order == 0 ? 0 : (int) Math.floor(Math.log10(Math.abs(order)))) + 1;
        final double mantissa = value / Math.pow(10, order);
        final int precision = Math.max(0, width - (4 + orderExp));

        return String.format("%." + precision + "fE%+d", mantissa, order); // NOSONAR
    }

    protected static String expFormatFixedPrecision(final double value, final int precision) {
        final int order = (int) Math.floor(Math.log10(Math.abs(value)));
        final double mantissa = value / Math.pow(10, order);

        return String.format("%." + precision + "fE%+d", mantissa, order); // NOSONAR
    }

    protected String fixedWidthFormat(final @NotNull Number number) {
        if (number.doubleValue() == 0.0) {
            // short-cut for exact '0'
            return String.format(fixedLengthFormat, "0");
        }

        var decimalForm = decimalFormat.format(number);
        var decimalFormLength = decimalForm.length();
        final var indexDecimalPoint = decimalForm.indexOf('.');
        if (formatMode.fixedWidth() && (indexDecimalPoint >= 0 && indexDecimalPoint < numberOfCharacters)) {
            // short number with decimal point
            decimalForm = decimalForm.substring(0, Math.min(numberOfCharacters, decimalFormLength));
            decimalFormLength = decimalForm.length();
            // virtual 'else' branch:
            // large integer -> need to print all digits otherwise false mathematical representation
            // N.B. small numbers may be truncated to zero in this mode though
        }

        if (FormatMode.FIXED_WIDTH_ONLY.equals(formatMode)) {
            return decimalFormLength >= numberOfCharacters ? decimalForm : String.format(fixedLengthFormat, decimalForm);
        }

        final double absValue = Math.abs(number.doubleValue());
        final String exponentialForm = expFormatFixedWidth(number.doubleValue(), numberOfCharacters);

        final double minExpLimit = Math.pow(10, -numberOfCharacters + 2.0);
        final double maxExpLimit = Math.pow(10, numberOfCharacters - 2.0);

        if ((decimalFormLength <= exponentialForm.length() || decimalFormLength <= 5) && (absValue > minExpLimit && absValue < maxExpLimit)) {
            return decimalFormLength >= numberOfCharacters ? decimalForm : String.format(fixedLengthFormat, decimalForm);
        }
        return exponentialForm;
    }

    protected String optimalWidthFormat(final @NotNull Number number) {
        if (number.doubleValue() == 0.0) {
            // short-cut for exact '0'
            return "0";
        }

        var decimalForm = decimalFormatMaxPrecision.format(number);
        var decimalFormLength = decimalForm.length();
        // choose most compact form
        final double absValue = Math.abs(number.doubleValue());
        final double minPrecision = Math.pow(10, -fixedPrecision);
        final var exponentialForm = expFormatFixedPrecision(number.doubleValue(), fixedPrecision);
        return decimalFormLength < exponentialForm.length() && absValue >= minPrecision ? decimalForm : exponentialForm;
    }

    public enum FormatMode {
        FIXED_WIDTH_ONLY,
        FIXED_WIDTH_AND_EXP,
        FIXED_WIDTH_EXP,
        OPTIMAL_WIDTH,
        METRIC_PREFIX,
        BYTE_PREFIX,
        JDK; // JDK default 'toString()' method

        boolean fixedWidth() {
            return this == FIXED_WIDTH_ONLY || this == FIXED_WIDTH_AND_EXP || this == FIXED_WIDTH_EXP;
        }
    }
}
