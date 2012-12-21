package org.apache.commons.weaver.privilizer;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private Logger logger = Logger.getLogger(PrivilizerWeaver.class.getName());

    private FilesystemPrivilizer privilizer;

    private Privilizer.Policy policy;

    private AccessLevel targetAccessLevel;

    @Override
    public void setLogger(Logger customLogger)
    {
        this.logger = customLogger;
    }

    @Override
    public void configure(List<String> classPath, File target, Map<String, Object> config)
    {
        privilizer = new FilesystemPrivilizer(policy, new URLClassLoader(URLArray.fromPaths(classPath)), target) {
            @Override
            protected boolean permitMethodWeaving(final AccessLevel accessLevel) {
                return targetAccessLevel.compareTo(accessLevel) <= 0;
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

        return true;
    }

    @Override
    public void postWeave()
    {
        // nothing to do
    }
}
