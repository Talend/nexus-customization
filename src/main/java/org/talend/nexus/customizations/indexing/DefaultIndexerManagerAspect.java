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

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.sonatype.nexus.proxy.NoSuchRepositoryException;

@Aspect
public class DefaultIndexerManagerAspect {
    @Around(value = "execution(org.apache.maven.index.IteratorSearchResponse org.sonatype.nexus.index.DefaultIndexerManager.searchArtifactIterator(String,String,String,String,String,String,Integer,Integer,Integer,boolean,SearchType,List<ArtifactInfoFilter>)) && " +
            "this(manager) && args(gTerm,aTerm,vTerm,pTerm,cTerm,repositoryId,from,count,hitLimit,uniqueRGA,searchType,filters)",
            argNames = "manager,gTerm,aTerm,vTerm,pTerm,cTerm,repositoryId,from,count,hitLimit,uniqueRGA,searchType,filters")
    public Object searchArtifactIterator(final Object manager,
                                         final String gTerm, final String aTerm, final String vTerm,
                                         final String pTerm, final String cTerm, final String repositoryId,
                                         final Integer from, final Integer count, final Integer hitLimit,
                                         final boolean uniqueRGA, final Object searchType,
                                         final List<?> filters) throws NoSuchRepositoryException {
        try {
            return LoadedByReflection.execute(() -> {
                try {
                    return LoadedByReflection.SEARCH.invoke(LoadedByReflection.SEARCHER, manager, gTerm, aTerm, vTerm,
                            pTerm, cTerm, repositoryId, from, count, hitLimit, uniqueRGA, searchType, filters);
                } catch (final IllegalAccessException e) {
                    throw new IllegalStateException(e);
                } catch (final InvocationTargetException e) {
                    final Throwable targetException = e.getTargetException();
                    if (RuntimeException.class.isInstance(targetException)) {
                        throw RuntimeException.class.cast(targetException);
                    }
                    throw new IllegalStateException(targetException);
                }
            });
        } catch (final IllegalStateException ise) {
            if (NoSuchRepositoryException.class.isInstance(ise.getCause())) {
                throw NoSuchRepositoryException.class.cast(ise.getCause());
            }
            throw ise;
        }
    }
}
