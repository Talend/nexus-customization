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

import static java.util.stream.Collectors.toList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.lucene.document.Document;
import org.apache.maven.index.ArtifactInfo;

public class LoadedByReflection {

    public static ClassLoader LOADER;

    public static Object FLD_URL_ID;

    public static Object FLD_LICENSE_ID;

    public static Object FLD_LICENSE_URL_ID;

    static Object SEARCHER;

    static Object INDEXER_FIELDS;

    private static Method TO_FIELD;
    private static Method GET_KEY;
    private static Method DOC_ADD;
    static Method SEARCH;

    private LoadedByReflection() {
        // no-op
    }

    public static <T> T execute(final Supplier<T> task) {
        final ClassLoader loader = LOADER;
        if (loader == null) {
            return task.get();
        }
        final Thread thread = Thread.currentThread();
        final ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return task.get();
        } finally {
            thread.setContextClassLoader(old);
        }
    }

    public static void init(final ClassLoader loader) {
        LOADER = loader;
        try {
            final Class<?> creator = loader.loadClass("org.apache.maven.index.creator.MinimalArtifactInfoIndexCreator");
            INDEXER_FIELDS = Stream.concat(
                    Stream.of(creator.getDeclaredFields())
                        .filter(f -> f.getName().startsWith("FLD_"))
                        .peek(f -> f.setAccessible(true)).map(f -> {
                            try {
                                return f.get(null);
                            } catch (final IllegalAccessException e) {
                                throw new IllegalStateException(e);
                            }
                        }),
                    Stream.of(FLD_URL_ID = createIndexerField(loader, "url", "Artifact url"),
                            FLD_LICENSE_ID = createIndexerField(loader, "license", "Artifact License"),
                            FLD_LICENSE_URL_ID = createIndexerField(loader, "licenseUrl", "Artifact License Url")))
                    .collect(toList());

            final Class<?> indexerField = loader.loadClass("org.apache.maven.index.IndexerField");
            TO_FIELD = indexerField.getMethod("toField", String.class);
            GET_KEY = indexerField.getMethod("getKey");

            final Class<?> document = loader.loadClass("org.apache.lucene.document.Document");
            DOC_ADD = document.getMethod("add", loader.loadClass("org.apache.lucene.document.Fieldable"));

            SEARCHER = loader.loadClass("org.talend.nexus.customizations.indexing.Searcher").getConstructor(ClassLoader.class).newInstance(loader);
            SEARCH = Stream.of(SEARCHER.getClass().getMethods())
                           .filter(it -> it.getName().equals("searchArtifactIterator")).findFirst()
                           .orElseThrow(IllegalStateException::new);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object createIndexerField(final ClassLoader loader, final String name, final String description) {
        try { final Class<?> field = loader.loadClass("org.apache.maven.index.Field");
            final Constructor<?> fieldConstructor = field.getConstructor(field, String.class, String.class, String.class);

            final Class<?> fieldVersion = loader.loadClass("org.apache.maven.index.IndexerFieldVersion");
            final Class<?> store = loader.loadClass("org.apache.lucene.document.Field$Store");
            final Class<?> index = loader.loadClass("org.apache.lucene.document.Field$Index");

            final Class<?> indexerField = loader.loadClass("org.apache.maven.index.IndexerField");
            final Constructor<?> indexerFieldConstructor = indexerField.getConstructor(
                    field, fieldVersion, String.class, String.class, store, index);

            final Object fieldInstance = fieldConstructor.newInstance(null, "urn:talend#", name, description);
            final Object fieldVersionInstance = fieldVersion.getField("V3").get(null);
            final Object storeInstance = store.getField("YES").get(null);
            final Object indexInstance = index.getField("ANALYZED").get(null);

            return indexerFieldConstructor.newInstance(fieldInstance, fieldVersionInstance, name, description + "(tokenized)", storeInstance, indexInstance);
        } catch (final Exception ex) {
            throw new IllegalStateException("can't setup added fields", ex);
        }
    }

    static void set(final Object indexerField, final Document document, final ArtifactInfo artifactInfo) {
        final String key = getKey(indexerField);
        final String value = document.get(key);
        if (value != null) {
            artifactInfo.getAttributes().put(key, value);
        }
    }

    static String getKey(final Object indexerField) {
        try {
            return String.valueOf(GET_KEY.invoke(indexerField));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Object addField(final Document document, final Object indexerField, final String value) {
        try {
            return DOC_ADD.invoke(document, TO_FIELD.invoke(indexerField, value));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
