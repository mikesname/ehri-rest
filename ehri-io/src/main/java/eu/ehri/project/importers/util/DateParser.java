/*
 * Copyright 2026 Data Archiving and Networked Services (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen), King's College London,
 * Georg-August-Universitaet Goettingen Stiftung Oeffentlichen Rechts,
 * NIOD Institute for War, Holocaust and Genocide Studies (an institute of
 * Koninklijke Nederlandse Akademie van Wetenschappen).
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package eu.ehri.project.importers.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.importers.properties.XmlImportProperties;
import eu.ehri.project.models.DatePeriod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static eu.ehri.project.importers.util.ImportHelpers.getSubNode;

/**
 * Class for extracting date info from unstructured or semi-structured data and text.
 *
 * There are two main scenarios:
 *
 *  - Pre-structured date periods, as found within EAD-3 daterange nodes. These
 *    are a map or list of maps with the key 'DatePeriod'
 *  - Unstructured text-based dates in formats we recognise as a range, keyed to
 *    either 'unitDates', 'creationDate', or 'existDate' (or others added to
 *    `dates.properties`.)
 *
 *  Notable, the function that returns the dates removes the data from
 *  which they were extracted
 */
public class DateParser {

    private static final Logger logger = LoggerFactory.getLogger(DateParser.class);
    private static final XmlImportProperties dates = new XmlImportProperties("dates.properties");

    // Temporary keys used to carry the EAD3 @standarddate attributes on a structured
    // date sub-node until they are reconciled into the actual start/end date properties.
    static final String START_STANDARD_DATE = "startStandardDate";
    static final String END_STANDARD_DATE = "endStandardDate";

    // An ISO 8601 date whose optional month and day groups reveal its granularity.
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})(-\\d{2})?(-\\d{2})?");

    private final DateRangeParser rangeParser;

    public DateParser() {
        rangeParser = new DateRangeParser();
    }

    /**
     * Extract a set of dates from input data. The input data is mutated to
     * remove the raw data.
     *
     * @param data a map of input data
     * @return a list of parsed date period maps
     */
    public List<Map<String, Object>> extractDates(Map<String, Object> data) {
        List<Map<String, Object>> extractedDates = Lists.newArrayList();

        if (data.containsKey(Entities.DATE_PERIOD)) {
            Object dateRep = data.get(Entities.DATE_PERIOD);
            if (dateRep instanceof List) {
                for (Map<String, Object> event : (List<Map<String, Object>>) dateRep) {
                    extractedDates.add(normaliseStructuredDate(getSubNode(event)));
                }
            } else if (dateRep instanceof Map) {
                extractedDates.add(normaliseStructuredDate(getSubNode((Map<String, Object>) dateRep)));
            } else {
                logger.warn("Found a DatePeriod sub-node with unexpected type: " + dateRep);
            }
            data.remove(Entities.DATE_PERIOD);
        }

        Map<String, String> dateValues = returnDatesAsString(data);
        for (String s : dateValues.keySet()) {
            extractDate(s).ifPresent(extractedDates::add);
        }
        replaceDates(data, extractedDates, dateValues);

        return extractedDates;
    }

    private void replaceDates(Map<String, Object> data, List<Map<String, Object>> extractedDates, Map<String, String> dateValues) {
        Map<String, String> dateTypes = Maps.newHashMap();
        for (String dateValue : dateValues.keySet()) {
            dateTypes.put(dateValues.get(dateValue), null);
        }
        for (Map<String, Object> dateMap : extractedDates) {
            dateValues.remove(dateMap.get(Ontology.DATE_HAS_DESCRIPTION));
        }
        //replace dates in data map
        for (String dateValue : dateValues.keySet()) {
            String dateType = dateValues.get(dateValue);
            if (dateTypes.containsKey(dateType) && dateTypes.get(dateType) != null) {
                dateTypes.put(dateType, dateTypes.get(dateType) + ", " + dateValue.trim());
            } else {
                dateTypes.put(dateType, dateValue.trim());
            }
        }
        for (String dateType : dateTypes.keySet()) {
            if (dateTypes.get(dateType) == null) {
                data.remove(dateType);
            } else {
                data.put(dateType, dateTypes.get(dateType));
            }
        }
    }

    private Optional<Map<String, Object>> extractDate(String date) {
        return rangeParser.tryParse(date).map(DateRange::data);
    }

    /**
     * Normalise a structured (e.g. EAD3) date sub-node. The machine-readable
     * {@code @standarddate} attributes, if present, replace the human-readable
     * start/end date text since they carry the canonical ISO 8601 form. The date
     * precision is then taken from an explicit value - as exported in the EAD3
     * {@code @localtype} for the quarter/week precisions that ISO 8601 cannot
     * itself represent - or otherwise inferred from the granularity of the start
     * date. If no precision can be determined the key is left unset.
     *
     * @param node a mutable structured date sub-node
     * @return the same node, with precision resolved and temporary keys removed
     */
    private static Map<String, Object> normaliseStructuredDate(Map<String, Object> node) {
        moveIfPresent(node, START_STANDARD_DATE, Ontology.DATE_PERIOD_START_DATE);
        moveIfPresent(node, END_STANDARD_DATE, Ontology.DATE_PERIOD_END_DATE);

        Optional<DatePeriod.DatePrecision> precision = parsePrecision(node.get(Ontology.DATE_PERIOD_PRECISION));
        if (!precision.isPresent()) {
            precision = inferPrecision(node.get(Ontology.DATE_PERIOD_START_DATE));
        }
        if (precision.isPresent()) {
            node.put(Ontology.DATE_PERIOD_PRECISION, precision.get().name());
        } else {
            node.remove(Ontology.DATE_PERIOD_PRECISION);
        }
        return node;
    }

    private static void moveIfPresent(Map<String, Object> node, String from, String to) {
        Object value = node.remove(from);
        if (value != null) {
            node.put(to, value);
        }
    }

    /**
     * Resolve a date precision, preferring an explicit value (e.g. from an EAD3
     * {@code @localtype}) and otherwise inferring it from the granularity of an
     * ISO 8601 date - a plain year, a year-month, or a full year-month-day.
     * Unrecognised values yield an empty result.
     */
    private static Optional<DatePeriod.DatePrecision> parsePrecision(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(DatePeriod.DatePrecision.valueOf(value.toString().trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<DatePeriod.DatePrecision> inferPrecision(Object date) {
        if (date == null) {
            return Optional.empty();
        }
        Matcher m = ISO_DATE.matcher(date.toString().trim());
        if (!m.matches()) {
            return Optional.empty();
        } else if (m.group(3) != null) {
            return Optional.of(DatePeriod.DatePrecision.day);
        } else if (m.group(2) != null) {
            return Optional.of(DatePeriod.DatePrecision.month);
        }
        return Optional.of(DatePeriod.DatePrecision.year);
    }

    private static Map<String, String> returnDatesAsString(Map<String, Object> data) {
        Map<String, String> datesAsString = Maps.newHashMap();
        Object value;
        for (Map.Entry<String, Object> property : data.entrySet()) {
            if (dates.containsProperty(property.getKey()) && (value = property.getValue()) != null) {
                if (property.getValue() instanceof String) {
                    String dateValue = (String) value;
                    for (String d : dateValue.split(",")) {
                        datesAsString.put(d, property.getKey());
                    }
                } else if (property.getValue() instanceof List) {
                    for (String s : (List<String>) value) {
                        datesAsString.put(s, property.getKey());
                    }
                }
            }
        }
        return datesAsString;
    }
}
