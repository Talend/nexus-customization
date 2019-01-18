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
import java.nio.file.Paths;
import java.util.Map;

import org.apache.felix.framework.cache.Content;
import org.apache.felix.framework.cache.DirectoryContent;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.osgi.framework.Bundle;

@Aspect
public class ClassLoaderSetupAspect {
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
    public void addManifestEntries(final Bundle bundle, final String id, final Map headers, final Content content) {
        if ("org.aspectj.weaver.loadtime.Agent".equals(headers.get("Agent-Class"))) { // this is aspectj, add missing meta
            headers.putIfAbsent("Bundle-Version", "1.0");
            headers.putIfAbsent("Export-Package", "org.aspectj.apache.bcel,org.aspectj.apache.bcel.generic,org.aspectj.apache.bcel.util," +
                    "org.aspectj.asm,org.aspectj.asm.internal,org.aspectj.bridge,org.aspectj.bridge.context," +
                    "org.aspectj.internal.lang.annotation,org.aspectj.internal.lang.reflect,org.aspectj.lang," +
                    "org.aspectj.lang.annotation,org.aspectj.lang.annotation.control,org.aspectj.lang.internal.lang," +
                    "org.aspectj.lang.reflect,org.aspectj.runtime,org.aspectj.runtime.internal,org.aspectj.runtime.internal.cflowstack," +
                    "org.aspectj.runtime.reflect,org.aspectj.util,org.aspectj.weaver,org.aspectj.weaver.ast,org.aspectj.weaver.bcel," +
                    "org.aspectj.weaver.bcel.asm,org.aspectj.weaver.internal.tools,org.aspectj.weaver.loadtime,org.aspectj.weaver.loadtime.definition," +
                    "org.aspectj.weaver.ltw,org.aspectj.weaver.model,org.aspectj.weaver.patterns,org.aspectj.weaver.reflect," +
                    "org.aspectj.weaver.tools,org.aspectj.weaver.tools.cache,aj.org.objectweb.asm,aj.org.objectweb.asm.signature");
        } else if ("org.sonatype.nexus.plugins.nexus-indexer-lucene-plugin".equals(headers.get("Bundle-SymbolicName"))) {
            // add the agent in the classpath to lucene plugin to let aspectj instrument the classes
            // (also see Activator which has a whitelisting too)
            final Object classpath = headers.get("Bundle-ClassPath");
            if (classpath != null) {
                final String bundleBase = DirectoryContent.class.cast(content).toString().substring("DIRECTORY ".length());
                final String newCp = classpath + "," + Paths.get(bundleBase).relativize(getCustomizations().toPath().toAbsolutePath());
                headers.put("Bundle-ClassPath", newCp);
            } // todo: else unexpected, fail?
        }
    }

    private File getCustomizations() {
        return requireNonNull(jarLocation(ClassLoaderSetupAspect.class), "Didn't find nexus-customizations");
    }

    private File jarLocation(final Class clazz) {
        try {
            final String classFileName = clazz.getName().replace(".", "/") + ".class";
            return jarFromResource(clazz.getClassLoader(), classFileName);
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
