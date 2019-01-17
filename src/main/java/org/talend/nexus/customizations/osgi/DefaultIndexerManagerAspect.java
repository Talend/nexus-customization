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

import static java.util.Arrays.asList;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.Field;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.SearchType;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.sonatype.nexus.index.DefaultIndexerManager;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;

@Aspect
public class DefaultIndexerManagerAspect {
    private final Method createRequest;
    private final Method searchIterator;

    public DefaultIndexerManagerAspect() {
        try {
            createRequest = DefaultIndexerManager.class.getDeclaredMethod("createRequest", Query.class,
                    Integer.class, Integer.class, Integer.class, boolean.class, List.class);
            searchIterator = DefaultIndexerManager.class.getDeclaredMethod("searchIterator", String.class, IteratorSearchRequest.class);
        } catch (final NoSuchMethodException e) {
            throw new IllegalStateException("Not the expected createRequest or searchIterator method in DefaultIndexerManager, " +
                    "this aspect is no more compatible with nexus", e);
        }
        Stream.of(createRequest, searchIterator).forEach(it -> {
            if (!it.isAccessible()) {
                it.setAccessible(true);
            }
        });
    }

    @Around(value = "execution(org.apache.maven.index.IteratorSearchResponse org.sonatype.nexus.index.DefaultIndexerManager.searchArtifactIterator(String,String,String,String,String,String,Integer,Integer,Integer,boolean,SearchType,List<ArtifactInfoFilter>)) && " +
            "this(manager) && args(gTerm,aTerm,vTerm,pTerm,cTerm,repositoryId,from,count,hitLimit,uniqueRGA,searchType,filters)",
            argNames = "manager,gTerm,aTerm,vTerm,pTerm,cTerm,repositoryId,from,count,hitLimit,uniqueRGA,searchType,filters")
    public IteratorSearchResponse searchArtifactIterator(final DefaultIndexerManager manager,
                                                         final String gTerm, final String aTerm, final String vTerm,
                                                         final String pTerm, final String cTerm, final String repositoryId,
                                                         final Integer from, final Integer count, final Integer hitLimit,
                                                         final boolean uniqueRGA, final SearchType searchType,
                                                         final List<ArtifactInfoFilter> filters) throws NoSuchRepositoryException {
        if (gTerm == null && aTerm == null && vTerm == null) {
            return IteratorSearchResponse.TOO_MANY_HITS_ITERATOR_SEARCH_RESPONSE;
        }

        final BooleanQuery bq = new BooleanQuery();
        if (gTerm != null) {
            bq.add(manager.constructQuery(MAVEN.GROUP_ID, gTerm, searchType), BooleanClause.Occur.MUST);
        }
        if (aTerm != null) { // Talend: default is the same as for gTerm but we need to support multiple values
            final Set<String> artifactIds = new HashSet<>(asList(aTerm.split(",")));
            if (artifactIds.size() > 1) {
                final BooleanQuery aq = new BooleanQuery();
                for (final String it : artifactIds) {
                    aq.add(manager.constructQuery(MAVEN.ARTIFACT_ID, it, searchType), BooleanClause.Occur.SHOULD);
                }
                bq.add(aq, BooleanClause.Occur.MUST);
            } else {
                bq.add(manager.constructQuery(MAVEN.ARTIFACT_ID, aTerm, searchType), BooleanClause.Occur.MUST);
            }
        }
        if (vTerm != null) {
            bq.add(manager.constructQuery(MAVEN.VERSION, vTerm, searchType), BooleanClause.Occur.MUST);
        }
        if (pTerm != null) {
            bq.add(manager.constructQuery(MAVEN.PACKAGING, pTerm, searchType), BooleanClause.Occur.MUST);
        }
        if (cTerm != null) {
            if (Field.NOT_PRESENT.equalsIgnoreCase(cTerm)) {
                filters.add(0, (ctx, ai) -> ai.classifier == null || ai.classifier.isEmpty());
            }
            else {
                bq.add(manager.constructQuery(MAVEN.CLASSIFIER, cTerm, searchType), BooleanClause.Occur.MUST);
            }
        }
        try {
            return IteratorSearchResponse.class.cast(
                    searchIterator.invoke(
                            manager,
                            repositoryId,
                            createRequest.invoke(manager, bq, from, count, hitLimit, uniqueRGA, filters)));
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
