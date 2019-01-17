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
package org.apache.maven.index;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.context.NexusIndexMultiReader;
import org.apache.maven.index.context.NexusIndexMultiSearcher;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.eclipse.sisu.inject.DefaultBeanLocator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.restlet.Context;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Variant;
import org.sonatype.nexus.configuration.application.runtime.DefaultApplicationRuntimeConfigurationBuilder;
import org.sonatype.nexus.index.NexusIndexingContext;
import org.sonatype.nexus.proxy.registry.DefaultRepositoryRegistry;
import org.sonatype.nexus.proxy.registry.DefaultRepositoryTypeRegistry;
import org.sonatype.nexus.rest.indexng.SearchNGIndexPlexusResource;
import org.sonatype.nexus.rest.model.SearchNGResponse;
import org.sonatype.sisu.goodies.eventbus.internal.DefaultEventBus;
import org.sonatype.sisu.goodies.eventbus.internal.ReentrantGuavaEventBus;
import org.talend.nexus.customizations.MinimalArtifactInfoIndexCreatorAspect;
import org.talend.nexus.customizations.SearchNGIndexPlexusResourceAspect;

@DisplayName("Ensure responses of the search are enriched with custom fields")
class SearchNGIndexPlexusResourceAspectTest {
    @Test
    @DisplayName("Search and validates we have license/licenseUrl/url")
    void search() throws IOException {
        final List<IndexingContext> contexts = singletonList(new NexusIndexingContext(
                "test", "test", new File("target/repo"), new RAMDirectory(), null, null,
                singletonList(new MinimalArtifactInfoIndexCreator()), false, false));
        final SearchNGResponse response = new SearchNGIndexPlexusResource(emptyList()) {
            {
                final DefaultEventBus bus = new DefaultEventBus(new ReentrantGuavaEventBus());
                final DefaultRepositoryTypeRegistry defaultRepositoryTypeRegistry = new DefaultRepositoryTypeRegistry(
                        new HashMap<>(), new DefaultApplicationRuntimeConfigurationBuilder(new DefaultBeanLocator()));
                super.setDefaultRepositoryRegistry(new DefaultRepositoryRegistry(bus, defaultRepositoryTypeRegistry));
            }

            @Override
            public SearchNGResponse get(final Context context, final Request request,
                                        final Response response, final Variant variant) {
                try {
                    final TopDocs hits = new TopDocs(2, new ScoreDoc[]{
                            new ScoreDoc(1, 0.5f),
                            new ScoreDoc(2, 1.f)
                    }, 1.0f);
                    final Iterator<Document> docs = asList(newDoc("foo"), newDoc("bar")).iterator();
                    final NexusIndexMultiSearcher indexSearcher = new NexusIndexMultiSearcher(
                            new NexusIndexMultiReader(contexts)) {
                        @Override
                        public Document doc(final int docID) {
                            return docs.next();
                        }
                    };
                    final IteratorSearchRequest iteratorSearchRequest = new IteratorSearchRequest(null);
                    final IteratorResultSet resultSet = new DefaultIteratorResultSet(iteratorSearchRequest, indexSearcher, contexts, hits);
                    return packSearchNGResponse(request, emptyMap(), new IteratorSearchResponse(null, 2, resultSet), false);
                } catch (final IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        }.get(null, null, null, null);
        assertEquals(2, response.getData().size());
        assertEquals(asList("http://foo", "http://bar"), response.getData().stream()
                .map(SearchNGIndexPlexusResourceAspect.ExtendedNexusNGArtifact.class::cast)
                .map(SearchNGIndexPlexusResourceAspect.ExtendedNexusNGArtifact::getLicenseUrl)
                .collect(toList()));
    }

    private Document newDoc(final String artifact) {
        final Document document = new Document();
        document.add(ArtifactInfo.FLD_UINFO.toField("test|" + artifact + "|1.2.3|jar"));
        document.add(MinimalArtifactInfoIndexCreator.FLD_GROUP_ID.toField("test"));
        document.add(MinimalArtifactInfoIndexCreator.FLD_ARTIFACT_ID.toField(artifact));
        document.add(MinimalArtifactInfoIndexCreator.FLD_VERSION.toField("1.2.3"));
        document.add(MinimalArtifactInfoIndexCreator.FLD_PACKAGING.toField("jar"));
        document.add(MinimalArtifactInfoIndexCreatorAspect.FLD_URL_ID.toField("http://fake"));
        document.add(MinimalArtifactInfoIndexCreatorAspect.FLD_LICENSE_URL_ID.toField("http://" + artifact));
        document.add(MinimalArtifactInfoIndexCreatorAspect.FLD_LICENSE_ID.toField(artifact + " license"));
        return document;
    }
}
