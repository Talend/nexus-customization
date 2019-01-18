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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
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
    @AfterReturning(value = "execution(org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.new()) && this(creator)", argNames = "creator")
    public void create(final MinimalArtifactInfoIndexCreator creator) {
        LoadedByReflection.init(creator.getClass().getClassLoader());
    }

    @AfterReturning(value = "execution(void org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.updateDocument(org.apache.maven.index.ArtifactInfo,org.apache.lucene.document.Document)) && args(artifactInfo,document)")
    public void updateDocument(final ArtifactInfo artifactInfo, final Document document) {
        talendUpdateDocument(artifactInfo, document);
    }

    @AfterReturning(value = "execution(void org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.updateLegacyDocument(org.apache.maven.index.ArtifactInfo,org.apache.lucene.document.Document)) && args(artifactInfo,document)")
    public void updateLegacyDocument(final ArtifactInfo artifactInfo, final Document document) {
        talendUpdateDocument(artifactInfo, document);
    }

    @Around(value = "execution(java.util.Collection org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator.getIndexerFields())")
    public Collection<?> getIndexerFields() {
        return Collection.class.cast(LoadedByReflection.INDEXER_FIELDS);
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
        LoadedByReflection.set(LoadedByReflection.FLD_URL_ID, document, artifactInfo);
        LoadedByReflection.set(LoadedByReflection.FLD_LICENSE_ID, document, artifactInfo);
        LoadedByReflection.set(LoadedByReflection.FLD_LICENSE_URL_ID, document, artifactInfo);
    }

    private void talendUpdateDocument(final ArtifactInfo artifactInfo, final Document document) {
        final String url = artifactInfo.getAttributes().get("url");
        if (url != null) {
            LoadedByReflection.addField(document, LoadedByReflection.FLD_URL_ID, url);
        }
        final String license = artifactInfo.getAttributes().get("license");
        if (license != null) {
            LoadedByReflection.addField(document, LoadedByReflection.FLD_LICENSE_ID, license);
        }

        final String licenseUrl = artifactInfo.getAttributes().get("licenseUrl");
        if (licenseUrl != null) {
            LoadedByReflection.addField(document, LoadedByReflection.FLD_LICENSE_URL_ID, licenseUrl);
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
            info.getAttributes().put("url", model.getUrl());
        }
        final List<License> licenses = model.getLicenses();
        if (!licenses.isEmpty()) {
            final License license = licenses.get(0);
            if (license.getName() != null) {
                info.getAttributes().put("license", license.getName());
            }
            if (license.getUrl() != null) {
                info.getAttributes().put("licenseUrl", license.getUrl());
            }
        }
    }
}
