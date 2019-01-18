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

import static java.util.Arrays.asList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.sonatype.nexus.proxy.NoSuchRepositoryException;

public class Searcher {
    private final Method createRequest;
    private final Method searchIterator;
    private final Method constructQuery;
    private final Supplier<Object> booleanQueryFactory;
    private final Method addClause;
    private final Object must;
    private final Object should;
    private final Object tooManyHits;
    private final Object groupId;
    private final Object artifactId;
    private final Object version;
    private final Object classifier;
    private final Object packaging;
    private final Class<?> artifactInfoFilter;
    private final Field classifierInfo;

    public Searcher(final ClassLoader loader) {
        try {
            final Class<?> field = loader.loadClass("org.apache.maven.index.Field");
            final Class<?> indexManager = loader.loadClass("org.sonatype.nexus.index.DefaultIndexerManager");
            artifactInfoFilter  = loader.loadClass("org.apache.maven.index.ArtifactInfoFilter");
            classifierInfo  = loader.loadClass("org.apache.maven.index.ArtifactInfo").getField("classifier");
            createRequest = indexManager.getDeclaredMethod("createRequest",
                    loader.loadClass("org.apache.lucene.search.Query"),
                    Integer.class, Integer.class, Integer.class, boolean.class, List.class);
            searchIterator = indexManager.getDeclaredMethod("searchIterator", String.class,
                    loader.loadClass("org.apache.maven.index.IteratorSearchRequest"));
            constructQuery = indexManager.getMethod("constructQuery", field,
                    String.class,
                    loader.loadClass("org.apache.maven.index.SearchType"));
            final Class<?> searchResponse = loader.loadClass("org.apache.maven.index.IteratorSearchResponse");
            final Class<?> maven = loader.loadClass("org.apache.maven.index.MAVEN");
            final Class<?> query = loader.loadClass("org.apache.lucene.search.Query");
            final Class<?> booleanQuery = loader.loadClass("org.apache.lucene.search.BooleanQuery");
            final Class<?> booleanClause = loader.loadClass("org.apache.lucene.search.BooleanClause");
            final Class<?> occur = loader.loadClass("org.apache.lucene.search.BooleanClause$Occur");
            final Constructor<?> booleanQueryCons = booleanQuery.getConstructor();
            booleanQueryFactory = () -> {
                try {
                    return booleanQueryCons.newInstance();
                } catch (final Exception e) {
                    throw new IllegalStateException(e);
                }
            };
            addClause = booleanQuery.getMethod("add", query, occur);
            must = occur.getField("MUST").get(null);
            should = occur.getField("SHOULD").get(null);
            tooManyHits = searchResponse.getField("TOO_MANY_HITS_ITERATOR_SEARCH_RESPONSE").get(null);
            groupId = maven.getField("GROUP_ID").get(null);
            artifactId = maven.getField("ARTIFACT_ID").get(null);
            version = maven.getField("VERSION").get(null);
            packaging = maven.getField("PACKAGING").get(null);
            classifier = maven.getField("CLASSIFIER").get(null);
        } catch (final Exception e) {
            throw new IllegalStateException("Not the expected createRequest or searchIterator method in DefaultIndexerManager, " +
                    "this aspect is no more compatible with nexus", e);
        }
        Stream.of(createRequest, searchIterator).forEach(it -> {
            if (!it.isAccessible()) {
                it.setAccessible(true);
            }
        });
    }

    public Object searchArtifactIterator(final Object manager,
                                         final String gTerm, final String aTerm, final String vTerm,
                                         final String pTerm, final String cTerm, final String repositoryId,
                                         final Integer from, final Integer count, final Integer hitLimit,
                                         final boolean uniqueRGA, final Object searchType,
                                         final List filters) throws
            NoSuchRepositoryException {
        if (gTerm == null && aTerm == null && vTerm == null) {
            return tooManyHits;
        }

        try {
            final Object bq = booleanQueryFactory.get();
            if (gTerm != null) {
                addClause.invoke(bq, constructQuery.invoke(manager, groupId, gTerm, searchType), must);
            }
            if (aTerm != null) { // Talend: default is the same as for gTerm but we need to support multiple values
                final Set<String> artifactIds = new HashSet<>(asList(aTerm.split(",")));
                if (artifactIds.size() > 1) {
                    final Object aq = booleanQueryFactory.get();
                    for (final String it : artifactIds) {
                        addClause.invoke(aq, constructQuery.invoke(manager, artifactId, it, searchType), should);
                    }
                    addClause.invoke(bq, aq, must);
                } else {
                    addClause.invoke(bq, constructQuery.invoke(manager, artifactId, aTerm, searchType), must);
                }
            }
            if (vTerm != null) {
                addClause.invoke(bq, constructQuery.invoke(manager, version, vTerm, searchType), must);
            }
            if (pTerm != null) {
                addClause.invoke(bq, constructQuery.invoke(manager, packaging, pTerm, searchType), must);
            }
            if (cTerm != null) {
                if ("N/P".equalsIgnoreCase(cTerm)) {
                    filters.add(0,
                        Proxy.newProxyInstance(artifactInfoFilter.getClassLoader(), new Class<?>[]{artifactInfoFilter}, new InvocationHandler() {
                            @Override
                            public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
                                if (method.getDeclaringClass() == artifactInfoFilter) {
                                    final String classifier = String.class.cast(classifierInfo.get(args[1]));
                                    return classifier == null || classifier.isEmpty();
                                }
                                return method.invoke(this, args);
                            }
                        }));
                } else {
                    addClause.invoke(bq, constructQuery.invoke(manager, classifier, cTerm, searchType), must);
                }
            }

            final Object request = createRequest.invoke(manager, bq, from, count, hitLimit, uniqueRGA, filters);
            return searchIterator.invoke(manager, repositoryId, request);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (final InvocationTargetException e) {
            final Throwable targetException = e.getTargetException();
            if (RuntimeException.class.isInstance(targetException)) {
                throw RuntimeException.class.cast(targetException);
            }
            if (NoSuchRepositoryException.class.isInstance(targetException)) {
                throw NoSuchRepositoryException.class.cast(targetException);
            }
            throw new IllegalStateException(targetException);
        }
    }
}
