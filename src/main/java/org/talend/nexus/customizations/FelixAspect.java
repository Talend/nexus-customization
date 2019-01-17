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

import static java.util.Objects.requireNonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

import org.apache.felix.framework.Felix;
import org.apache.felix.framework.cache.Content;
import org.aspectj.lang.Aspects;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

@Aspect
public class FelixAspect {
    @AfterReturning(value = "execution(org.eclipse.jetty.webapp.WebAppClassLoader.new(java.lang.ClassLoader,org.eclipse.jetty.webapp.WebAppClassLoader$Context)) && this(loader)", argNames = "loader")
    public void addInWebApp(final WebAppClassLoader loader) {
        try {
            final Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            if (!addURL.isAccessible()) {
                addURL.setAccessible(true);
            }
            final File customizations = getCustomizations();
            addURL.invoke(loader, customizations.toURI().toURL());
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Before(value = "execution(org.apache.felix.framework.BundleRevisionImpl.new(org.osgi.framework.Bundle,java.lang.String,java.util.Map,org.apache.felix.framework.cache.Content)) && args(bundle,id,headers,content)", argNames = "bundle,id,headers,content")
    public void addAspectjManifestEntries(final Bundle bundle, final String id, final Map headers, final Content content) {
        if (!"org.aspectj.weaver.loadtime.Agent".equals(headers.get("Agent-Class"))) { // this not aspectj
            return;
        }
        headers.putIfAbsent("Bundle-Version", "1.0");
        headers.putIfAbsent("Export-Package", "org.aspectj.apache.bcel,org.aspectj.apache.bcel.generic,org.aspectj.apache.bcel.util," +
                "org.aspectj.asm,org.aspectj.asm.internal,org.aspectj.bridge,org.aspectj.bridge.context," +
                "org.aspectj.internal.lang.annotation,org.aspectj.internal.lang.reflect,org.aspectj.lang," +
                "org.aspectj.lang.annotation,org.aspectj.lang.annotation.control,org.aspectj.lang.internal.lang," +
                "org.aspectj.lang.reflect,org.aspectj.runtime,org.aspectj.runtime.internal,org.aspectj.runtime.internal.cflowstack," +
                "org.aspectj.runtime.reflect,org.aspectj.util,org.aspectj.weaver,org.aspectj.weaver.ast,org.aspectj.weaver.bcel," +
                "org.aspectj.weaver.bcel.asm,org.aspectj.weaver.internal.tools,org.aspectj.weaver.loadtime,org.aspectj.weaver.loadtime.definition," +
                "org.aspectj.weaver.ltw,org.aspectj.weaver.model,org.aspectj.weaver.patterns,org.aspectj.weaver.reflect," +
                "org.aspectj.weaver.tools,org.aspectj.weaver.tools.cache,orgobjectweb.asm,orgobjectweb.asm.signature");
    }

    @AfterReturning(value = "execution(void org.apache.felix.framework.Felix.start(..)) && this(felix)", argNames = "felix")
    public void addAspectsInFelix(final Felix felix) throws BundleException {
        final File customizations = getCustomizations();
        final File aspectj = requireNonNull(jarLocation(Aspects.class), "Didn't find aspectj");
        final BundleContext context = felix.getBundleContext();
        context.installBundle("reference:" + aspectj.toURI()).start();
        context.installBundle("reference:" + customizations.toURI()).start();
    }

    private String getAspectjManifest() {
        return "";
    }

    private File getCustomizations() {
        return requireNonNull(jarLocation(FelixAspect.class), "Didn't find nexus-customizations");
    }

    private File jarLocation(final Class clazz) {
        try {
            final String classFileName = clazz.getName().replace(".", "/") + ".class";
            final ClassLoader loader = clazz.getClassLoader();
            return jarFromResource(loader, classFileName);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private File jarFromResource(final ClassLoader loader, final String resourceName) {
        try {
            URL url = loader.getResource(resourceName);
            if (url == null) {
                throw new IllegalStateException("classloader.getResource(classFileName) returned a null URL");
            }

            if ("jar".equals(url.getProtocol())) {
                final String spec = url.getFile();

                int separator = spec.indexOf('!');
                /*
                 * REMIND: we don't handle nested JAR URLs
                 */
                if (separator == -1) {
                    throw new MalformedURLException("no ! found in jar url spec:" + spec);
                }

                url = new URL(spec.substring(0, separator++));

                return new File(decode(url.getFile()));

            } else if ("file".equals(url.getProtocol())) {
                return toFile(resourceName, url);
            } else {
                throw new IllegalArgumentException("Unsupported URL scheme: " + url.toExternalForm());
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private File toFile(final String classFileName, final URL url) {
        String path = url.getFile();
        path = path.substring(0, path.length() - classFileName.length());
        return new File(decode(path));
    }

    private String decode(final String fileName) {
        if (fileName.indexOf('%') == -1) {
            return fileName;
        }
        final StringBuilder result = new StringBuilder(fileName.length());
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < fileName.length();) {
            final char c = fileName.charAt(i);
            if (c == '%') {
                out.reset();
                do {
                    if (i + 2 >= fileName.length()) {
                        throw new IllegalArgumentException("Incomplete % sequence at: " + i);
                    }
                    final int d1 = Character.digit(fileName.charAt(i + 1), 16);
                    final int d2 = Character.digit(fileName.charAt(i + 2), 16);
                    if (d1 == -1 || d2 == -1) {
                        throw new IllegalArgumentException("Invalid % sequence (" + fileName.substring(i, i + 3) + ") at: " + String.valueOf(i));
                    }
                    out.write((byte) ((d1 << 4) + d2));
                    i += 3;
                } while (i < fileName.length() && fileName.charAt(i) == '%');
                result.append(out.toString());
                continue;
            } else {
                result.append(c);
            }

            i++;
        }
        return result.toString();
    }
}
