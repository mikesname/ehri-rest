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

package eu.ehri.project.utils;

import com.google.common.collect.ImmutableMap;
import eu.ehri.project.definitions.Ontology;
import eu.ehri.project.models.DocumentaryUnit;
import eu.ehri.project.models.DocumentaryUnitDescription;
import eu.ehri.project.models.EntityClass;
import eu.ehri.project.models.base.Described;
import eu.ehri.project.models.base.Description;
import eu.ehri.project.test.AbstractFixtureTest;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;


public class LanguageHelpersTest extends AbstractFixtureTest {

    private DocumentaryUnitDescription addDescription(
            String id, DocumentaryUnit unit, String descriptionCode, String langCode) throws Exception {
        ImmutableMap.Builder<String, Object> data = ImmutableMap.<String, Object>builder()
                .put(Ontology.NAME_KEY, "Test description " + id)
                .put(Ontology.LANGUAGE_OF_DESCRIPTION, langCode);
        if (descriptionCode != null) {
            data.put(Ontology.IDENTIFIER_KEY, descriptionCode);
        }
        DocumentaryUnitDescription desc = graph.frame(
                manager.createVertex(id, EntityClass.DOCUMENTARY_UNIT_DESCRIPTION, data.build()),
                DocumentaryUnitDescription.class);
        if (unit != null) {
            unit.addDescription(desc);
        }
        return desc;
    }

    private static String bestId(Described item, Optional<Description> prior, String langCode, String code) {
        return LanguageHelpers.getBestDescription(item, prior, langCode, code).map(Description::getId).orElse(null);
    }

    private static String bestCode(Described item, Optional<Description> prior, String langCode, String code) {
        return LanguageHelpers.getBestDescription(item, prior, langCode, code).map(Description::getDescriptionCode).orElse(null);
    }

    @Test
    public void testIso639DashTwoCode() {
        // two-to-three
        assertEquals("sqi", LanguageHelpers.iso639DashTwoCode("sq"));
        // bibliographic to term
        assertEquals("sqi", LanguageHelpers.iso639DashTwoCode("alb"));
        // name to code
        // FIXME fails when executed on a server with a Dutch locale
        assertEquals("eng", LanguageHelpers.iso639DashTwoCode("English"));
    }

    @Test
    public void testTryIso639DashTwoCode() {
        // two-to-three
        assertEquals(Optional.of("sqi"), LanguageHelpers.tryIso639DashTwoCode("sq"));
        // bibliographic to term
        assertEquals(Optional.of("sqi"), LanguageHelpers.tryIso639DashTwoCode("alb"));
        // already a valid term code
        assertEquals(Optional.of("eng"), LanguageHelpers.tryIso639DashTwoCode("eng"));
        // already a valid term code, uppercase
        assertEquals(Optional.of("eng"), LanguageHelpers.tryIso639DashTwoCode("ENG"));
        // name to code
        assertEquals(Optional.of("eng"), LanguageHelpers.tryIso639DashTwoCode("English"));
        // not a recognisable code or name
        assertEquals(Optional.empty(), LanguageHelpers.tryIso639DashTwoCode("In het Frans"));
        assertEquals(Optional.empty(), LanguageHelpers.tryIso639DashTwoCode("xyz"));
    }

    @Test
    public void testConvertCode() {
        assertEquals(Optional.of("sqi"), LanguageHelpers.convertCode("sq"));
        assertEquals(Optional.of("sqi"), LanguageHelpers.convertCode("alb"));
        assertEquals(Optional.of("eng"), LanguageHelpers.convertCode("en"));
        assertEquals(Optional.of("heb"), LanguageHelpers.convertCode("he"));
        assertEquals(Optional.of("heb"), LanguageHelpers.convertCode("iw"));
        assertEquals(Optional.empty(), LanguageHelpers.convertCode(null));
        assertEquals(Optional.empty(), LanguageHelpers.convertCode("unknown"));
    }

    @Test
    public void testIso639DashOneCode() {
        assertEquals("en", LanguageHelpers.iso639DashOneCode("eng"));
        assertEquals("cs", LanguageHelpers.iso639DashOneCode("ces"));
        assertEquals("cs", LanguageHelpers.iso639DashOneCode("cze"));
        assertEquals("sq", LanguageHelpers.iso639DashOneCode("sqi"));
        assertEquals("he", LanguageHelpers.iso639DashOneCode("heb"));
        assertEquals("en", LanguageHelpers.iso639DashOneCode("English"));
        assertEquals("en-Latn", LanguageHelpers.iso639DashOneCode("eng-Latn"));
        assertEquals("en", LanguageHelpers.iso639DashOneCode("eng-"));
        assertEquals("---", LanguageHelpers.iso639DashOneCode("---"));
    }

    @Test
    public void testCountryCodeToContinent() {
        Optional<String> c1 = LanguageHelpers.countryCodeToContinent("gb");
        assertTrue(c1.isPresent());
        assertEquals("Europe", c1.get());

        Optional<String> c2 = LanguageHelpers.countryCodeToContinent("us");
        assertTrue(c2.isPresent());
        assertEquals("North America", c2.get());

        Optional<String> c3 = LanguageHelpers.countryCodeToContinent("nz");
        assertTrue(c3.isPresent());
        assertEquals("Australia", c3.get());
    }

    @Test
    public void testCodeToName() {
        assertEquals("English", LanguageHelpers.codeToName("eng"));
        assertEquals("German", LanguageHelpers.codeToName("deu"));
        assertEquals("German", LanguageHelpers.codeToName("ger"));
    }

    @Test
    public void countryCodeToName() {
        assertEquals("Germany", LanguageHelpers.countryCodeToName("de"));
        assertEquals("United Kingdom", LanguageHelpers.countryCodeToName("gb"));
        assertEquals("France", LanguageHelpers.countryCodeToName("fr"));
        assertEquals("Kosovo", LanguageHelpers.countryCodeToName("xk"));
    }

    @Test
    public void testGetBestDescriptionFallsBackToFirstWhenNoMatch() {
        assertEquals("c1-desc", bestCode(item, Optional.empty(), "deu", "no-such-code"));
    }

    @Test
    public void testGetBestDescriptionMatchesGivenCode() throws Exception {
        DocumentaryUnitDescription other = addDescription("c1-desc-2", item, "other-code", "fra");
        assertEquals(other.getId(), bestId(item, Optional.empty(), "eng", "other-code"));
        assertEquals("c1-desc", bestCode(item, Optional.empty(), "eng", "OTHER-CODE"));
    }

    @Test
    public void testGetBestDescriptionMatchesLanguageWhenNoCodeGiven() throws Exception {
        DocumentaryUnitDescription french = addDescription("c1-desc-2", item, null, "fra");
        assertEquals(french.getId(), bestId(item, Optional.empty(), "fra", "no-such-code"));
    }

    @Test
    public void testGetBestDescriptionMatchesParentDescriptionCodeFirst() throws Exception {
        DocumentaryUnitDescription matching = addDescription("c1-desc-2", item, "shared-code", "fra");
        DocumentaryUnitDescription prior = addDescription("prior-desc", null, "shared-code", "eng");
        assertEquals(matching.getId(), bestId(item, Optional.of(prior), "eng", "c1-desc"));
    }
}