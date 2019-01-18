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
package org.talend.nexus.customizations.indexing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import org.apache.lucene.search.TopDocs;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.DefaultIteratorResultSet;
import org.apache.maven.index.IteratorResultSet;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.context.NexusIndexMultiSearcher;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.restlet.data.Request;
import org.sonatype.nexus.rest.model.NexusNGArtifact;
import org.sonatype.nexus.rest.model.SearchNGResponse;

@Aspect
public class SearchNGIndexPlexusResourceAspect {
    @Around(value = "call(protected org.apache.maven.index.DefaultIteratorResultSet.new(" +
            "org.apache.maven.index.IteratorSearchRequest,org.apache.maven.index.context.NexusIndexMultiSearcher," +
            "java.util.List,org.apache.lucene.search.TopDocs)) && " +
            "args(request,indexSearcher,contexts,hits)", argNames = "request,indexSearcher,contexts,hits")
    public DefaultIteratorResultSet createCachedDefaultIteratorResultSet(final IteratorSearchRequest request, final NexusIndexMultiSearcher indexSearcher,
                                                                         final List<IndexingContext> contexts, final TopDocs hits) throws IOException {
        return new CachedDefaultIteratorResultSet(request, indexSearcher, contexts, hits);
    }

    @Around("call(org.sonatype.nexus.rest.model.NexusNGArtifact.new())")
    public NexusNGArtifact createNexusNGArtifact() {
        return new ExtendedNexusNGArtifact();
    }

    @AfterReturning(value = "execution(org.sonatype.nexus.rest.model.SearchNGResponse org.sonatype.nexus.rest.indexng.SearchNGIndexPlexusResource.packSearchNGResponse(org.restlet.data.Request,java.util.Map,org.apache.maven.index.IteratorSearchResponse,boolean)) && args(request,terms,iterator,forceExpand)",
                    returning = "response", argNames = "response,request,terms,iterator,forceExpand")
    public void packSearchNGResponse(final SearchNGResponse response,
                                     final Request request, final Map<String, String> terms,
                                     final IteratorSearchResponse iterator, final boolean forceExpand) {
        final IteratorResultSet results = iterator.getResults();
        if (CachedDefaultIteratorResultSet.class.isInstance(results)) {
            CachedDefaultIteratorResultSet.class.cast(results).artifactInfos.forEach(it -> {
                response.getData().stream()
                        .filter(ExtendedNexusNGArtifact.class::isInstance)
                        .filter(art -> matches(art, it))
                        .map(ExtendedNexusNGArtifact.class::cast)
                        .forEach(model -> {
                            model.setUrl(it.getAttributes().get("url"));
                            model.setLicense(it.getAttributes().get("license"));
                            model.setLicenseUrl(it.getAttributes().get("licenseUrl"));
                        });
            });
        }
    }

    private boolean matches(final NexusNGArtifact art, final ArtifactInfo it) {
        return Objects.equals(art.getGroupId(), it.getFieldValue(MAVEN.GROUP_ID)) &&
                Objects.equals(art.getArtifactId(), it.getFieldValue(MAVEN.ARTIFACT_ID)) &&
                Objects.equals(art.getVersion(), it.getFieldValue(MAVEN.VERSION));
    }

    public static class CachedDefaultIteratorResultSet extends DefaultIteratorResultSet {
        private final Collection<ArtifactInfo> artifactInfos = new ArrayList<>();

        private CachedDefaultIteratorResultSet(final IteratorSearchRequest request, final NexusIndexMultiSearcher indexSearcher,
                                               final List<IndexingContext> contexts, final TopDocs hits) throws IOException {
            super(request, indexSearcher, contexts, hits);
        }

        @Override
        public ArtifactInfo next() {
            final ArtifactInfo next = super.next();
            if (next != null) {
                artifactInfos.add(next);
            }
            return next;
        }
    }

    @XmlType( name = "nexusNGArtifact" )
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ExtendedNexusNGArtifact extends NexusNGArtifact {
        private String url;
        private String license;
        private String licenseUrl;

        public String getUrl() {
            return url;
        }

        public void setUrl(final String url) {
            this.url = url;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(final String license) {
            this.license = license;
        }

        public String getLicenseUrl() {
            return licenseUrl;
        }

        public void setLicenseUrl(final String licenseUrl) {
            this.licenseUrl = licenseUrl;
        }
    }
}
