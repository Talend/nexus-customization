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

import java.io.File;
import java.util.Collection;
import java.util.Map;

import org.apache.lucene.document.Document;
import org.apache.maven.index.ArtifactContext;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.IndexerField;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ensure we add license/licenseUrl/url in the indexed fields")
class MinimalArtifactInfoIndexCreatorAspectTest {
    @Test
    @DisplayName("Check the new fields are indexed")
    void index() {
        final ArtifactContext artifactContext = getArtifactContext();
        final MinimalArtifactInfoIndexCreator creator = new MinimalArtifactInfoIndexCreator();
        creator.populateArtifactInfo(artifactContext);
        final Map<String, String> attributes = artifactContext.getArtifactInfo().getAttributes();
        assertEquals("Foo", attributes.get("license"));
        assertEquals("http://foo", attributes.get("licenseUrl"));
        assertEquals("http://fake", attributes.get("url"));
    }

    @Test
    @DisplayName("Check the new fields are listed as indexed")
    void ensureIndexedFieldsAreAdded() {
        final Collection<IndexerField> fields = new MinimalArtifactInfoIndexCreator().getIndexerFields();
        final String debugMessage = fields.toString();
        assertEquals(16, fields.size(), debugMessage);
        assertEquals(3, fields.stream().filter(it -> it.getOntology().getNamespace().contains("talend")).count(), debugMessage);
    }

    @Test
    @DisplayName("Ensure we index the needed fields with normal indexation")
    void ensureFieldsAreIndexedWithNormalUpdate() {
        final MinimalArtifactInfoIndexCreator creator = new MinimalArtifactInfoIndexCreator();
        final ArtifactContext artifactContext = getArtifactContext();
        creator.populateArtifactInfo(artifactContext);
        final Document doc = new Document();
        creator.updateDocument(artifactContext.getArtifactInfo(), doc);
        assertEquals(11, doc.getFields().size());
        assertEquals("Foo", doc.get("license"));
        assertEquals("http://foo", doc.get("licenseUrl"));
        assertEquals("http://fake", doc.get("url"));
    }

    @Test
    @DisplayName("Ensure we index the needed fields with legacy indexation")
    void ensureFieldsAreIndexedWithLegacyUpdate() {
        final MinimalArtifactInfoIndexCreator creator = new MinimalArtifactInfoIndexCreator();
        final ArtifactContext artifactContext = getArtifactContext();
        creator.populateArtifactInfo(artifactContext);
        final Document doc = new Document();
        creator.updateLegacyDocument(artifactContext.getArtifactInfo(), doc);
        assertEquals(14, doc.getFields().size());
        assertEquals("Foo", doc.get("license"));
        assertEquals("http://foo", doc.get("licenseUrl"));
        assertEquals("http://fake", doc.get("url"));
    }

    private ArtifactContext getArtifactContext() {
        final Gav gav = new Gav("org.test", "test-art", "1.0.0-SNAPSHOT");
        return new ArtifactContext(new File("src/test/resources/fakepom.xml"), null, null,
                new ArtifactInfo("test", gav.getGroupId(), gav.getArtifactId(), gav.getVersion(), null), gav);
    }
}
