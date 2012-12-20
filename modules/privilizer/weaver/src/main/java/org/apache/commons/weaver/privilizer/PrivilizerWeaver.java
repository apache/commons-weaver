package org.apache.commons.weaver.privilizer;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import org.apache.commons.weaver.spi.Weaver;

/**
 * Weaver which adds doPrivileged blocks for each method annotated with
 * {@link Privileged}
 */
public class PrivilizerWeaver implements Weaver
{
    private FilesystemPrivilizer privilizer;

    private Privilizer.Policy policy;

    private AccessLevel targetAccessLevel;

    private URLClassLoader urlClassLoader;

    private File target;



    @Override
    public void configure(Map<String, Object> config)
    {
        privilizer = new FilesystemPrivilizer(policy, urlClassLoader, target) {
            @Override
            protected boolean permitMethodWeaving(final AccessLevel accessLevel) {
                return targetAccessLevel.compareTo(accessLevel) <= 0;
            }
        };
    }

    @Override
    public List<Class<? extends Annotation>> getInterest()
    {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void preWeave()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean weave(Class classToWeave)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean weave(Method methodToWeave)
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void postWeave()
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
