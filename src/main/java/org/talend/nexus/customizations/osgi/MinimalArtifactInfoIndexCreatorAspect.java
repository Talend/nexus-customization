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
package org.talend.nexus.customizations.osgi;

import static java.util.Optional.ofNullable;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_ARTIFACT_ID;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_ARTIFACT_ID_KW;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_CLASSIFIER;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_DESCRIPTION;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_GROUP_ID;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_GROUP_ID_KW;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_INFO;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_LAST_MODIFIED;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_NAME;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_PACKAGING;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_SHA1;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_VERSION;
import static org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.FLD_VERSION_KW;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import org.apache.lucene.document.Document;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Field;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.IndexerFieldVersion;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.apache.maven.index.util.zip.ZipFacade;
import org.apache.maven.index.util.zip.ZipHandle;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Aspect
public class MinimalArtifactInfoIndexCreatorAspect {
    public interface Constants {
        IndexerField FLD_URL_ID = new IndexerField(
                new Field(null, "urn:talend#", "url", "Artifact Url"), IndexerFieldVersion.V3, "url",
                "Artifact url (tokenized)", org.apache.lucene.document.Field.Store.YES,
                org.apache.lucene.document.Field.Index.ANALYZED);

        IndexerField FLD_LICENSE_ID = new IndexerField(
                new Field(null, "urn:talend#", "license", "Artifact License"), IndexerFieldVersion.V3, "license",
                "Artifact License (tokenized)", org.apache.lucene.document.Field.Store.YES,
                org.apache.lucene.document.Field.Index.ANALYZED);

        IndexerField FLD_LICENSE_URL_ID = new IndexerField(new Field(null, "urn:talend#", "licenseUrl", "License Url"), IndexerFieldVersion.V3, "licenseUrl",
                "License Url (tokenized)", org.apache.lucene.document.Field.Store.YES,
                org.apache.lucene.document.Field.Index.ANALYZED);
    }

    @AfterReturning(value = "execution(void org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.updateDocument(org.apache.maven.index.ArtifactInfo,org.apache.lucene.document.Document)) && args(artifactInfo,document)")
    public void updateDocument(final ArtifactInfo artifactInfo, final Document document) {
        talendUpdateDocument(artifactInfo, document);
    }

    @AfterReturning(value = "execution(void org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.updateLegacyDocument(org.apache.maven.index.ArtifactInfo,org.apache.lucene.document.Document)) && args(artifactInfo,document)")
    public void updateLegacyDocument(final ArtifactInfo artifactInfo, final Document document) {
        talendUpdateDocument(artifactInfo, document);
    }

    @Around(value = "execution(java.util.Collection org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.getIndexerFields()) && this(creator)")
    public Collection<IndexerField> getIndexerFields(final MinimalArtifactInfoIndexCreator creator) {
        return withClassLoader(creator, () -> Arrays.asList( FLD_INFO, FLD_GROUP_ID_KW, FLD_GROUP_ID, FLD_ARTIFACT_ID_KW, FLD_ARTIFACT_ID,
                FLD_VERSION_KW, FLD_VERSION, FLD_PACKAGING, FLD_CLASSIFIER, FLD_NAME, FLD_DESCRIPTION, FLD_LAST_MODIFIED,
                FLD_SHA1,
                // additions
                Constants.FLD_LICENSE_ID, Constants.FLD_LICENSE_URL_ID, Constants.FLD_URL_ID));
    }

    @AfterReturning(value = "execution(void org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.populateArtifactInfo(org.apache.maven.index.ArtifactContext)) && args(artifactContext)", argNames = "artifactContext")
    public void populateArtifactInfo(final ArtifactContext artifactContext) {
        final Model model = readPom(artifactContext);
        if (model != null) {
            addTalendFields(model, artifactContext.getArtifactInfo());
        }
    }

    @AfterReturning(value = "execution(boolean org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.updateArtifactInfo(org.apache.lucene.document.Document,org.apache.maven.index.ArtifactInfo)) && args(document,artifactInfo)", argNames = "document,artifactInfo")
    public void updateArtifactInfo(final Document document, final ArtifactInfo artifactInfo) {
        final String urlInfo = document.get(Constants.FLD_URL_ID.getKey());
        if (urlInfo != null) {
            artifactInfo.getAttributes().put(Constants.FLD_URL_ID.getKey(), urlInfo);
        }

        final String liInfo = document.get(Constants.FLD_LICENSE_ID.getKey());
        if (liInfo != null) {
            artifactInfo.getAttributes().put(Constants.FLD_LICENSE_ID.getKey(), liInfo);
        }

        final String liUrlInfo = document.get(Constants.FLD_LICENSE_URL_ID.getKey());
        if (liUrlInfo != null) {
            artifactInfo.getAttributes().put(Constants.FLD_LICENSE_URL_ID.getKey(), liUrlInfo);
        }
    }

    private <T> T withClassLoader(final MinimalArtifactInfoIndexCreator creator, final Supplier<T> o) {
        final Thread thread = Thread.currentThread();
        final ClassLoader oldLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(ofNullable(creator.getClass().getClassLoader()).orElseGet(thread::getContextClassLoader));
        try {
            return o.get();
        } finally {
            thread.setContextClassLoader(oldLoader);
        }
    }

    private void talendUpdateDocument(final ArtifactInfo artifactInfo, final Document document) {
        final String url = artifactInfo.getAttributes().get(Constants.FLD_URL_ID.getKey());
        if (url != null) {
            document.add(Constants.FLD_URL_ID.toField(url));
        }
        final String license = artifactInfo.getAttributes().get(Constants.FLD_LICENSE_ID.getKey());
        if (license != null) {
            document.add(Constants.FLD_LICENSE_ID.toField(license));
        }

        final String licenseUrl = artifactInfo.getAttributes().get(Constants.FLD_LICENSE_URL_ID.getKey());
        if (licenseUrl != null) {
            document.add(Constants.FLD_LICENSE_URL_ID.toField(licenseUrl));
        }
    }

    // default just read minimal set of meta, we need all the pom meta
    private Model readPom(final ArtifactContext artifactContext) {
        if (artifactContext.getPom() != null && artifactContext.getPom().isFile()) {
            try {
                return new MavenXpp3Reader().read(
                        new FileInputStream(artifactContext.getPom()), false);
            } catch (final IOException | XmlPullParserException e) {
                e.printStackTrace();
            }
        }
        else if (artifactContext.getArtifact() != null && artifactContext.getArtifact().isFile()) {
            ZipHandle handle = null;

            try {
                handle = ZipFacade.getZipHandle(artifactContext.getArtifact());

                final String embeddedPomPath = "META-INF/maven/"
                        + artifactContext.getGav().getGroupId() + "/"
                        + artifactContext.getGav().getArtifactId() + "/pom.xml";

                if (handle.hasEntry(embeddedPomPath)) {
                    return new MavenXpp3Reader().read(
                            handle.getEntryContent(embeddedPomPath), false);
                }
            } catch (final IOException | XmlPullParserException e) {
                e.printStackTrace();
            } finally {
                try {
                    ZipFacade.close(handle);
                } catch (final Exception e) {
                    // no-op
                }
            }
        }

        return null;
    }

    private void addTalendFields(final Model model, final ArtifactInfo info) {
        if (model.getUrl() != null) {
            info.getAttributes().put(Constants.FLD_URL_ID.getKey(), model.getUrl());
        }
        final List<License> licenses = model.getLicenses();
        if (!licenses.isEmpty()) {
            final License license = licenses.get(0);
            if (license.getName() != null) {
                info.getAttributes().put(Constants.FLD_LICENSE_ID.getKey(), license.getName());
            }
            if (license.getUrl() != null) {
                info.getAttributes().put(Constants.FLD_LICENSE_URL_ID.getKey(), license.getUrl());
            }
        }
    }
}
