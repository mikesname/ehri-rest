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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import eu.ehri.project.definitions.Entities;
import eu.ehri.project.definitions.Ontology;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DateParserTest {
    private Map<String, Object> mapWithOneParseableDate;
    private Map<String, Object> mapWithMultipleDates;
    private Map<String, Object> mapWithMultipleDatesAsList;
    private DateParser parser;

    @Before
    public void init() {
        parser = new DateParser();
        mapWithOneParseableDate = Maps.newHashMap();
        mapWithOneParseableDate.put("unitDates", "1934/1936");

        mapWithMultipleDates = Maps.newHashMap();
        mapWithMultipleDates.put("unitDates", "1934/1936, summer 1978");
        mapWithMultipleDates.put("existDate", "1900");
        mapWithMultipleDates.put(Entities.DATE_PERIOD, ImmutableMap.of(
                Ontology.DATE_PERIOD_START_DATE, "1920",
                Ontology.DATE_PERIOD_END_DATE, "1940"));

        mapWithMultipleDatesAsList = Maps.newHashMap();
        mapWithMultipleDatesAsList.put("unitDates", Lists.newArrayList("1934/1936", "1978"));
        mapWithMultipleDatesAsList.put(Entities.DATE_PERIOD, Lists.newArrayList(
                ImmutableMap.of(
                    Ontology.DATE_PERIOD_START_DATE, "1920",
                    Ontology.DATE_PERIOD_END_DATE, "1940"
                ),
                ImmutableMap.of(
                    Ontology.DATE_PERIOD_START_DATE, "1941",
                    Ontology.DATE_PERIOD_END_DATE, "1950"
                )));
    }

    @Test
    public void extractDatesFromDateProperty() {
        List<Map<String, Object>> extractedDates = parser.extractDates(mapWithOneParseableDate);
        assertEquals(1, extractedDates.size());
        assertEquals("1934/1936", extractedDates.get(0).get(Ontology.DATE_HAS_DESCRIPTION));
    }

    @Test
    public void removeDateFromDateProperty() {
        assertTrue(mapWithOneParseableDate.containsKey("unitDates"));
        parser.extractDates(mapWithOneParseableDate);
        assertFalse(mapWithOneParseableDate.containsKey("unitDates"));
    }

    @Test
    public void removeDatesFromDateProperty() {
        assertTrue(mapWithMultipleDates.containsKey("unitDates"));
        assertTrue(mapWithMultipleDates.containsKey("existDate"));
        assertTrue(mapWithMultipleDates.containsKey("unitDates"));
        assertTrue(mapWithMultipleDates.containsKey(Entities.DATE_PERIOD));
        List<Map<String, Object>> dates = parser.extractDates(mapWithMultipleDates);
        assertEquals(3, dates.size());
        assertFalse(mapWithMultipleDates.containsKey(Entities.DATE_PERIOD));
        assertEquals("summer 1978", mapWithMultipleDates.get("unitDates"));
        assertFalse(mapWithMultipleDates.containsKey("existDate"));
    }

    @Test
    public void removeDatesFromDatePropertyList() {
        assertTrue(mapWithMultipleDatesAsList.containsKey("unitDates"));
        parser.extractDates(mapWithMultipleDatesAsList);
        assertFalse(mapWithMultipleDatesAsList.containsKey("unitDates"));
    }
}