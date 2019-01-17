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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.aspectj.weaver.loadtime.ClassLoaderWeavingAdaptor;
import org.aspectj.weaver.loadtime.DefaultWeavingContext;
import org.aspectj.weaver.loadtime.definition.Definition;
import org.aspectj.weaver.loadtime.definition.DocumentParser;
import org.aspectj.weaver.tools.WeavingAdaptor;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;
import org.osgi.framework.wiring.BundleWiring;

public class Activator implements BundleActivator {
    private final Collection<ServiceRegistration<?>> services = new ArrayList<>();

    @Override
    public void start(final BundleContext context) {
        services.add(context.registerService(WeavingHook.class, new AspectjWeavingHook(), null));
    }

    @Override
    public void stop(final BundleContext context) {
        services.forEach(ServiceRegistration::unregister);
    }

    private static class AspectjWeavingHook implements WeavingHook {
        private final ConcurrentMap<ClassLoader, ClassLoaderWeavingAdaptor> adaptorMap = new ConcurrentHashMap<>();

        @Override
        public void weave(final WovenClass woven) { // todo: whitelist to go faster and ignore bundles we don't touch at all
            try {
                final String name = woven.getClassName();
                final BundleWiring wiring = woven.getBundleWiring();
                final ClassLoaderWeavingAdaptor adaptor = adaptorMap.computeIfAbsent(wiring.getClassLoader(), loader -> {
                    final ClassLoaderWeavingAdaptor tmp = new ClassLoaderWeavingAdaptor();
                    final AspectContext context = new AspectContext(wiring);
                    tmp.initialize(loader, context);
                    return tmp;
                });
                final byte[] source = woven.getBytes();
                final byte[] target;
                synchronized (adaptor) {
                    target = adaptor.weaveClass(name, source);
                }
                if (source != target) {
                    woven.setBytes(target);
                }
            } catch (final Throwable e) {
                throw new Error(e);
            }
        }
    }

    private static class AspectContext extends DefaultWeavingContext {
        private BundleWiring wiring;
        private volatile List<Definition> configurations;

        private AspectContext(BundleWiring wiring) {
            super(wiring.getClassLoader());
            this.wiring = wiring;
        }

        @Override
        public String getId() {
            return getClassLoaderName();
        }

        @Override
        public String getClassLoaderName() {
            return wiring.getRevision().getSymbolicName();
        }

        @Override
        public List<Definition> getDefinitions(final ClassLoader loader, final WeavingAdaptor adaptor) {
            if (configurations == null) {
                synchronized (this) {
                    if (configurations == null) {
                        final Map<URL, Definition> definitionMap = new HashMap<>();
                        try {
                            final Enumeration<URL> urls = loader.getResources("META-INF/aop.xml");
                            while (urls.hasMoreElements()) {
                                final URL url = urls.nextElement();
                                definitionMap.put(url, DocumentParser.parse(url));
                            }
                            configurations = new ArrayList<>(definitionMap.values());
                        } catch (final Exception e) {
                            throw new Error(e);
                        }
                    }
                }
            }
            return configurations;
        }
    }
}
