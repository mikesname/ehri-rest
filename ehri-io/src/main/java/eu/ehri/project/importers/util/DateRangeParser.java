package eu.ehri.project.importers.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateRangeParser {

    private static final Logger logger = LoggerFactory.getLogger(DateRangeParser.class);
    private static final Pattern yearRange = Pattern.compile("^(?<start>\\d{4})\\s?[\\-/]\\s?(?<end>\\d{4})$");
    private static final String SEP_CHARS = "/-";

    // Recognised forms for a single date component, tried in order. Each yields the
    // start of the period (missing month/day default to the 1st); the caller widens
    // the end of a period to the last day of the month/year as appropriate. These
    // replicate the formats the previous third-party parser was configured to accept.
    private static final Pattern CIRCA = Pattern.compile("(\\d{4})\\s+ca\\.?");
    private static final Pattern SUMMER = Pattern.compile("summer\\s+(\\d{4})");
    private static final Pattern YEAR_MONTH_DAY = Pattern.compile("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");
    private static final Pattern MONTH_YEAR = Pattern.compile("(\\d{2})/(\\d{4})");
    private static final Pattern YEAR_MONTH = Pattern.compile("(\\d{4})-(\\d{2})");
    private static final Pattern YEAR = Pattern.compile("\\d{4}");

    // Textual, English-language date forms, tried after the numeric patterns above.
    private static final List<DateTimeFormatter> TEXT_FORMATS = Arrays.asList(
            DateTimeFormatter.ofPattern("MMMM d yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH));

    private DateRange parseRange(String from, String to, String orig) {
        final LocalDate d1 = parseComponent(from);
        final LocalDate d2 = parseComponent(to);

        // If we don't have a specific day or month, set these to the appropriate maximum
        // based on the length of the raw date string - a fallible heuristic for sure.
        final String rawDateString = to.replaceAll("\\D", "");
        if (rawDateString.length() < 6) {
            return DateRange.of(
                    d1,
                    d2.with(TemporalAdjusters.lastDayOfYear()),
                    orig
            );
        } else if (rawDateString.length() < 8) {
            return DateRange.of(
                    d1,
                    d2.with(TemporalAdjusters.lastDayOfMonth()),
                    orig
            );
        } else {
            return DateRange.of(d1, d2, orig);
        }
    }

    private DateRange parseSingle(String date, String orig) {
        final LocalDate d = parseComponent(date);
        if (date.replaceAll("\\D", "").length() == 4) {
            LocalDate d2 = d.with(TemporalAdjusters.lastDayOfYear());
            return DateRange.of(d, d2, orig);
        }
        return DateRange.of(d, null, orig);
    }

    /**
     * Parse a single date component into the start of the period it denotes.
     * Missing month and day fields default to the first of the period. Invalid
     * or unrecognised values throw a {@link DateTimeException}.
     *
     * @param date a single date string (not a range)
     * @return a LocalDate at the start of the denoted period
     * @throws DateTimeException if the string cannot be parsed or is not a valid date
     */
    private static LocalDate parseComponent(String date) {
        // NB: deliberately not trimmed, matching the previous parser which treated
        // a leading/trailing space (e.g. from a comma-split list) as unparsable.
        final String s = date;
        Matcher m;
        if ((m = CIRCA.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 1), 1, 1);
        } else if ((m = SUMMER.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 1), 1, 1);
        } else if ((m = YEAR_MONTH_DAY.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 1), intGroup(m, 2), intGroup(m, 3));
        } else if ((m = DAY_MONTH_YEAR.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 3), intGroup(m, 2), intGroup(m, 1));
        } else if ((m = MONTH_YEAR.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 2), intGroup(m, 1), 1);
        } else if ((m = YEAR_MONTH.matcher(s)).matches()) {
            return LocalDate.of(intGroup(m, 1), intGroup(m, 2), 1);
        } else if (YEAR.matcher(s).matches()) {
            return LocalDate.of(Integer.parseInt(s), 1, 1);
        }
        for (DateTimeFormatter format : TEXT_FORMATS) {
            try {
                return LocalDate.parse(s, format);
            } catch (DateTimeParseException ignored) {
                // try the next textual format
            }
        }
        throw new DateTimeException("Unparsable date component: " + date);
    }

    private static int intGroup(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    /**
     * Heuristically attempt to parse a date range. This handles valid dates
     * separated by a hyphen (optionally with surrounding spaces.)
     *
     * @param str a date range string
     * @return a DateRange
     * @throws DateTimeParseException if the string is not parsable
     * @throws DateTimeException if the date is invalid
     */
    public DateRange parse(String str) throws DateTimeException {
        // See if the string matches a year range...
        final Matcher matcher = yearRange.matcher(str);
        if (matcher.matches()) {
            return parseRange(matcher.group("start"), matcher.group("end"), str);
        } else if (str.contains(" - ")) {
            // If it contains a separator with whitespace
            final String[] parts = str.split("\\s-\\s");
            return parseRange(parts[0], parts[1], str);
        } else {
            final int mid = str.length() / 2;
            final char midChar = str.charAt(mid);
            // If the total string is greater or equal to the minimum length
            // for a YEAR-MONTH range and the middle char is a range separator
            // attempt to parse each part as a date...
            if (str.length() > 12 && SEP_CHARS.indexOf(midChar) != -1) {
                // Heuristics: if a string is longer than 12 chars and the
                // middle char is a '-', assume it's a date range...
                return parseRange(
                        str.subSequence(0, mid).toString().trim(),
                        str.subSequence(mid + 1, str.length()).toString().trim(), str);
            } else {
                // Otherwise, attempt to parse as a single date, or fail...
                return parseSingle(str, str);
            }
        }

    }

    /**
     * Heuristically attempt to parse a date range. This handles valid dates
     * separated by a hyphen (optionally with surrounding spaces.)
     *
     * @param str a date range string
     * @return an DateRange if the string is parsable, or an empty optional if not
     */
    public Optional<DateRange> tryParse(String str) {
        try {
            return Optional.of(parse(str));
        } catch (IllegalArgumentException | DateTimeParseException e ) {
            logger.debug(String.format("Unable to parse date range %s", str), e);
            return Optional.empty();
        } catch (DateTimeException e) {
            logger.warn(String.format("Invalid date detected parsing date range %s", str), e);
            return Optional.empty();
        }
    }
}
