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

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.DefaultNexusIndexer;
import org.apache.maven.index.DefaultQueryCreator;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.SearchType;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.sonatype.nexus.configuration.application.runtime.DefaultApplicationRuntimeConfigurationBuilder;
import org.sonatype.nexus.index.DefaultIndexerManager;
import org.sonatype.nexus.proxy.maven.MavenRepository;
import org.sonatype.nexus.proxy.maven.gav.M2GavCalculator;
import org.sonatype.nexus.proxy.maven.maven2.M2Repository;
import org.sonatype.nexus.proxy.maven.maven2.M2RepositoryConfigurator;
import org.sonatype.nexus.proxy.maven.maven2.Maven2ContentClass;
import org.sonatype.nexus.proxy.registry.DefaultRepositoryRegistry;
import org.sonatype.nexus.proxy.registry.DefaultRepositoryTypeRegistry;
import org.sonatype.nexus.proxy.repository.DefaultRepositoryKind;
import org.sonatype.nexus.proxy.repository.LocalStatus;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RepositoryKind;
import org.sonatype.sisu.goodies.eventbus.internal.DefaultEventBus;
import org.sonatype.sisu.goodies.eventbus.internal.ReentrantGuavaEventBus;

@DisplayName("Studio must be able to query multiple artifacts at once")
class DefaultIndexerManagerAspectTest {
    @Test
    @DisplayName("Ensure we call lucence and the search method is replaced")
    void searchArtifactIterator() throws Exception {
        final AtomicReference<IteratorSearchRequest> requestRef = new AtomicReference<>();
        final DefaultIndexerManager manager = new DefaultIndexerManager() {
            @Override
            protected IteratorSearchRequest createRequest(final Query bq, final Integer from, final Integer count, final Integer hitLimit,
                                                          final boolean uniqueRGA, final List<ArtifactInfoFilter> extraFilters) {
                assertNull(requestRef.get());
                final IteratorSearchRequest request = super.createRequest(bq, from, count, hitLimit, uniqueRGA, extraFilters);
                requestRef.set(request);
                return request;
            }
        };
        final DefaultNexusIndexer indexer = new DefaultNexusIndexer();
        final DefaultQueryCreator queryCreator = new DefaultQueryCreator();
        final DefaultEventBus eventBus = new DefaultEventBus(new ReentrantGuavaEventBus());
        final DefaultBeanLocator beanLocator = new DefaultBeanLocator();
        final Maven2ContentClass maven2ContentClass = new Maven2ContentClass();
        final DefaultRepositoryRegistry repositoryRegistry = new DefaultRepositoryRegistry(
                eventBus,
                new DefaultRepositoryTypeRegistry(singletonMap("maven", maven2ContentClass),
                        new DefaultApplicationRuntimeConfigurationBuilder(beanLocator)));
        final M2Repository repository = new M2Repository(maven2ContentClass, new M2GavCalculator(),
                new M2RepositoryConfigurator()) {
            @Override
            public String getProviderRole() {
                return "org.sonatype.nexus.proxy.repository.Repository";
            }

            @Override
            public String getProviderHint() {
                return "maven2";
            }

            @Override
            public String getId() {
                return "libraries";
            }

            @Override
            public String getName() {
                return "Libraries";
            }

            @Override
            public RepositoryKind getRepositoryKind() {
                return new DefaultRepositoryKind(Repository.class, singletonList(MavenRepository.class));
            }

            @Override
            public LocalStatus getLocalStatus() {
                return LocalStatus.IN_SERVICE;
            }

            @Override
            public boolean isIndexable() {
                return true;
            }
        };

        set(queryCreator, "logger", new ConsoleLogger());
        set(indexer, "queryCreator", queryCreator);
        set(manager, "mavenIndexer", indexer);
        set(manager, "maven2", maven2ContentClass);
        set(manager, "repositoryRegistry", repositoryRegistry);
        repositoryRegistry.addRepository(repository);

        // ensure MinimalArtifactInfoIndexCreator is loaded since it is the one enabling the indexer fields
        new MinimalArtifactInfoIndexCreator();

        final IteratorSearchResponse libraries = manager.searchArtifactIterator(
                "org.talend.libraries", "foo1,foo2", "1.2.3", null, null, "libraries",
                null, null, null, false, SearchType.EXACT, emptyList());
        assertEquals("+g:org.talend.libraries +(a:foo1 a:foo2) +v:1.2.3", requestRef.get().getQuery().toString());
        assertNotNull(libraries);
    }

    private static void set(final Object on, final String field, final Object value)
            throws IllegalAccessException {
        Class<?> current = on.getClass();
        while (current != Object.class) {
            try {
                final Field declaredField = current.getDeclaredField(field);
                declaredField.setAccessible(true);
                declaredField.set(on, value);
                return;
            } catch (final NoSuchFieldException nsfe) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("Didn't find " + field + " in " + on);
    }
}
