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

import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.nexus.proxy.maven.maven2.Maven2ContentClass;
import org.sonatype.nexus.proxy.targets.Target;

@DisplayName("Target uses regexes and we have too much rules to use that at runtime")
class TargetMatchingOptimizationAspectTest {
    @Test
    @DisplayName("Ensure Target still behaves the same")
    void isPathContained() {
        final Maven2ContentClass contentClass = new Maven2ContentClass();
        final Target target = new Target("id", "name", contentClass, singletonList(".*/org/talend/foo/.*"));
        assertTrue(TargetMatchingOptimizationAspect.FastTarget.class.isInstance(target));
        assertTrue(target.isPathContained(contentClass, "/org/talend/foo/bar"));
        assertFalse(target.isPathContained(contentClass, "/org/talend/dummy/bar"));
    }
}
