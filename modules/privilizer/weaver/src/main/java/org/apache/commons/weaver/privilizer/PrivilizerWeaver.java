package org.apache.commons.weaver.privilizer;

import java.io.File;
import java.lang.annotation.ElementType;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Properties;

import org.apache.commons.weaver.model.ScanRequest;
import org.apache.commons.weaver.model.ScanResult;
import org.apache.commons.weaver.model.WeavableClass;
import org.apache.commons.weaver.model.WeaveInterest;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;

/**
 * Weaver which adds doPrivileged blocks for each method annotated with {@link Privileged}. An instance of this class
 * will automatically get picked up by the {@link org.apache.commons.weaver.WeaveProcessor} via the
 * {@link java.util.ServiceLoader}.
 */
public class PrivilizerWeaver implements Weaver {
    public static final String CONFIG_WEAVER = "privilizer.";
    public static final String CONFIG_ACCESS_LEVEL = CONFIG_WEAVER + "accessLevel";
    public static final String CONFIG_POLICY = CONFIG_WEAVER + "policy";

    private Privilizer privilizer;
    private Privilizer.Policy policy;
    private AccessLevel targetAccessLevel;

    @Override
    public void configure(List<String> classPath, File target, Properties config) {
        String accessLevel = config.getProperty(CONFIG_ACCESS_LEVEL);
        if (accessLevel == null || accessLevel.length() == 0) {
            throw new IllegalArgumentException(CONFIG_ACCESS_LEVEL + " property is missing!");
        }
        targetAccessLevel = AccessLevel.valueOf(accessLevel);

        String policyConfig = config.getProperty(CONFIG_POLICY);
        if (policyConfig == null || policyConfig.length() == 0) {
            throw new IllegalArgumentException(CONFIG_POLICY + " property is missing!");
        }
        policy = Privilizer.Policy.valueOf(policyConfig);

        privilizer = new FilesystemPrivilizer(policy, new URLClassLoader(URLArray.fromPaths(classPath)), target) {
            @Override
            protected boolean permitMethodWeaving(final AccessLevel accessLevel) {
                return targetAccessLevel.compareTo(accessLevel) <= 0;
            }

            @Override
            protected AccessLevel getTargetAccessLevel() {
                return targetAccessLevel;
            }
        };

    }

    @Override
    public ScanRequest getScanRequest() {
        return new ScanRequest().add(WeaveInterest.of(Privileged.class, ElementType.METHOD)).add(
            WeaveInterest.of(Privilizing.class, ElementType.TYPE));
    }

    @Override
    public boolean process(ScanResult scanResult) {
        boolean result = false;
        for (WeavableClass<?> weavableClass : scanResult.getClasses()) {
            try {
                result =
                    privilizer.weaveClass(weavableClass.getTarget(), weavableClass.getAnnotation(Privilizing.class))
                        | result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }
}
