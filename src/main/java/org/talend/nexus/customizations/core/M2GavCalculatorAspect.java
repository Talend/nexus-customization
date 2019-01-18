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
package org.talend.nexus.customizations.core;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.sonatype.nexus.proxy.maven.gav.Gav;
import org.sonatype.nexus.proxy.maven.gav.M2GavCalculator;

@Aspect
public class M2GavCalculatorAspect {
    @Around(value = "execution(String org.sonatype.nexus.proxy.maven.gav.M2GavCalculator.gavToPath(org.sonatype.nexus.proxy.maven.gav.Gav)) && this(calculator) && args(gav)",
            argNames = "calculator,gav")
    public String gavToPath(final M2GavCalculator calculator, final Gav gav) {
        final StringBuilder path = new StringBuilder("/");
        path.append(gav.getGroupId().startsWith(".") ? "." + gav.getGroupId().substring(1).replace('.', '/') : gav.getGroupId().replace('.', '/'));
        path.append("/");
        path.append(gav.getArtifactId());
        path.append("/");
        path.append(gav.getBaseVersion());
        path.append("/");
        path.append(calculator.calculateArtifactName(gav));
        return path.toString();
    }
}
