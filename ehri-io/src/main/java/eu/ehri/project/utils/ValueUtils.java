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

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.List;

/**
 * Helpers for working with raw property values.
 */
public class ValueUtils {

    private ValueUtils() {
    }

    /**
     * Coerce a raw property value into a list. Graph properties may be stored as
     * a single value, a {@link List}, or an array (for multi-valued properties);
     * this normalises all of these, and null, into a list.
     *
     * @param value a property value, possibly null
     * @return a list of values, possibly empty
     */
    @SuppressWarnings("unchecked")
    public static List<Object> coerceList(Object value) {
        if (value == null) {
            return ImmutableList.of();
        } else if (value instanceof List) {
            return (List<Object>) value;
        } else if (value instanceof Object[]) {
            return Arrays.asList((Object[]) value);
        }
        return ImmutableList.of(value);
    }
}
