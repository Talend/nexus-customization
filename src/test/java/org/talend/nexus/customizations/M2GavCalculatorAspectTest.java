/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.nexus.customizations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.nexus.proxy.maven.gav.Gav;
import org.sonatype.nexus.proxy.maven.gav.M2GavCalculator;

@DisplayName("Ensure the gav does not use regex but still works as expected")
class M2GavCalculatorAspectTest {
    @Test
    @DisplayName("Ensure the path of standard talend's artifacts is still well computed")
    void compute() {
        final String path = new M2GavCalculator().gavToPath(
                new Gav("org.talend.libraries", "foo-bar", "1.2.3", null, "jar", null, null, null, false, null, false, null));
        assertEquals("/org/talend/libraries/foo-bar/1.2.3/foo-bar-1.2.3.jar", path);
    }
}
