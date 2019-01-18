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

import static java.util.Collections.emptyList;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.nexus.proxy.registry.ContentClass;
import org.sonatype.nexus.proxy.targets.Target;

@Aspect
public class TargetMatchingOptimizationAspect {
    @Around(value = "call(org.sonatype.nexus.proxy.targets.Target.new(String,String,org.sonatype.nexus.proxy.registry.ContentClass,java.util.Collection))" +
            " && args(id,name,contentClass,patternTexts)",
            argNames = "id,name,contentClass,patternTexts")
    public Target create(final String id, final String name, final ContentClass contentClass, final Collection<String> patternTexts) {
        return new FastTarget(id, name, contentClass, patternTexts);
    }

    public static class FastTarget extends Target {
        private final Set<String> patternTexts;
        private final Set<Predicate<String>> matchers = new HashSet<>();

        public FastTarget(final String id, final String name,
                          final ContentClass contentClass,
                          final Collection<String> patternTexts) throws PatternSyntaxException {
            super(id, name, contentClass, emptyList()/*skip regexes, we'll reimplement this part*/);
            this.patternTexts = new HashSet<>(patternTexts);

            for (final String patternText : patternTexts) {
                if (patternText.startsWith(".*/org/talend/") && patternText.endsWith(".*")) {// first cause the most common for us
                    final String included = patternText.substring(".*".length(), patternText.length() - ".*".length());
                    matchers.add(s -> s.startsWith(included));
                    break;
                } else if (".*".equals(patternText)) { // .*maven-metadata\.xml.*
                    matchers.add(s -> true);
                    break;
                } else if ("(?!.*-sources.*).*".equals(patternText)) {
                    matchers.add(s -> !s.contains("-sources"));
                    break;
                } else if (".*maven-metadata\\.xml.*".equals(patternText)) {
                    matchers.add(s -> s.contains("maven-metadata.xml"));
                    break;
                }

                // default nexus impl
                final Pattern pattern = Pattern.compile(patternText);
                matchers.add(s -> pattern.matcher(s).matches());
            }
        }

        @Override
        public Set<String> getPatternTexts() {
            return patternTexts;
        }

        @Override
        public boolean isPathContained(final ContentClass contentClass, final String path) {
            if (StringUtils.equals(getContentClass().getId(), contentClass.getId())
                    || getContentClass().isCompatible(contentClass)
                    || contentClass.isCompatible(getContentClass())) {
                for (final Predicate<String> pattern : matchers) {
                    if (pattern.test(path)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }
}
