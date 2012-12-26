package org.apache.commons.weaver.privilizer;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.apache.commons.weaver.spi.Weaver;
import org.apache.commons.weaver.utils.URLArray;

/**
 * Weaver which adds doPrivileged blocks for each method annotated with
 * {@link Privileged}.
 * An instance of this class will automatically get picked up by the
 * {@link org.apache.commons.weaver.WeaveProcessor} via the
 * {@link java.util.ServiceLoader}.
 */
public class PrivilizerWeaver implements Weaver
{
    public static final String CONFIG_WEAVER = "privilizer.";
    public static final String CONFIG_ACCESS_LEVEL = CONFIG_WEAVER + "accessLevel";
    public static final String CONFIG_POLICY = CONFIG_WEAVER + "policy";

    private static final Logger LOG = Logger.getLogger(PrivilizerWeaver.class.getName());

    private FilesystemPrivilizer privilizer;

    private Privilizer.Policy policy;

    private AccessLevel targetAccessLevel;


    @Override
    public void configure(List<String> classPath, File target, Properties config)
    {
        LOG.info("");

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
            protected AccessLevel getTargetAccessLevel()
            {
                return targetAccessLevel;
            }
        };

    }

    @Override
    public List<Class<? extends Annotation>> getInterest()
    {
        List<Class<? extends Annotation>> interest = new ArrayList<Class<? extends Annotation>>();
        interest.add(Privileged.class);
        return interest;
    }

    @Override
    public void preWeave()
    {
        // nothing to do
    }

    @Override
    public boolean weave(Class classToWeave, Class<? extends Annotation> processingAnnotation)
    {
        // Privilizer does not weave classes
        return false;
    }

    @Override
    public boolean weave(Method methodToWeave, Class<? extends Annotation> processingAnnotation)
    {
        try
        {
            privilizer.weaveClass(methodToWeave.getDeclaringClass());
        }
        catch (NotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        catch (CannotCompileException e)
        {
            throw new RuntimeException(e);
        }
        catch (ClassNotFoundException e)
        {
            throw new RuntimeException(e);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public void postWeave()
    {
        // nothing to do
    }
}
