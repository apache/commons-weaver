/*
 *  Copyright the original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.commons.weaver.privilizer;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.text.StrBuilder;


/**
 * Handles weaving of methods annotated with {@link Privileged}.
 */
public abstract class Privilizer<SELF extends Privilizer<SELF>> {
    public interface ClassFileWriter {
        void write(CtClass type) throws CannotCompileException, IOException;
    }

    public interface Log {
        void debug(String message);

        void verbose(String message);

        void error(String message);

        void info(String message);

        void warn(String message);
    }

    /**
     * Weaving policy: when to use {@link PrivilegedAction}s.
     */
    public enum Policy {
        /**
         * Disables weaving.
         */
        NEVER,

        /**
         * Weaves such that the check for an active {@link SecurityManager} is
         * done once only.
         */
        ON_INIT(generateName("hasSecurityManager")),

        /**
         * Weaves such that the check for an active {@link SecurityManager} is
         * done for each {@link Privileged} method execution.
         */
        DYNAMIC(HAS_SECURITY_MANAGER_CONDITION),

        /**
         * Weaves such that {@link Privileged} methods are always executed as
         * such.
         */
        ALWAYS;

        private final String condition;

        private Policy() {
            this(null);
        }

        private Policy(String condition) {
            this.condition = condition;
        }

        private boolean isConditional() {
            return condition != null;
        }
    }

    protected static final String POLICY_NAME = "policyName";

    private static final String ACTION_SUFFIX = "_ACTION";

    private static final String GENERATE_NAME = "__privileged_%s";
    private static final String HAS_SECURITY_MANAGER_CONDITION = "System.getSecurityManager() != null";

    protected static String generateName(String simple) {
        return String.format(GENERATE_NAME, simple);
    }

    protected static String toString(byte[] b) {
        return b == null ? null : new String(b, Charset.forName("UTF-8"));
    }

    protected final Policy policy;

    protected final ClassPool classPool;

    private boolean settingsReported;

    private Log log = new Log() {
        final Logger logger = Logger.getLogger(Privilizer.class.getName());

        @Override
        public void debug(String message) {
            logger.finer(message);
        }

        @Override
        public void verbose(String message) {
            logger.fine(message);
        }

        @Override
        public void error(String message) {
            logger.severe(message);
        }

        @Override
        public void info(String message) {
            logger.info(message);
        }

        @Override
        public void warn(String message) {
            logger.warning(message);
        }

    };

    private static final Comparator<CtMethod> CTMETHOD_COMPARATOR = new Comparator<CtMethod>() {

        @Override
        public int compare(CtMethod arg0, CtMethod arg1) {
            if (ObjectUtils.equals(arg0, arg1)) {
                return 0;
            }
            if (arg0 == null) {
                return -1;
            }
            if (arg1 == null) {
                return 1;
            }
            final int result = ObjectUtils.compare(arg0.getName(), arg1.getName());
            return result == 0 ? ObjectUtils.compare(arg0.getSignature(), arg1.getSignature()) : result;
        }
    };

    private static Set<CtMethod> getPrivilegedMethods(CtClass type) throws ClassNotFoundException {
        final TreeSet<CtMethod> result = new TreeSet<CtMethod>(CTMETHOD_COMPARATOR);
        for (final CtMethod m : type.getDeclaredMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) || m.getAnnotation(Privileged.class) == null) {
                continue;
            }
            result.add(m);
        }
        return result;
    }

    public Privilizer(ClassPool classPool) {
        this(Policy.DYNAMIC, classPool);
    }

    public Privilizer(Policy policy, ClassPool classPool) {
        this.policy = Validate.notNull(policy, "policy");
        this.classPool = Validate.notNull(classPool, "classPool");
    }

    public SELF loggingTo(Log log) {
        this.log = Validate.notNull(log);
        settingsReported = false;
        @SuppressWarnings("unchecked")
        final SELF self = (SELF) this;
        return self;
    }

    /**
     * Weave the specified class.
     * 
     * @param type
     * @return whether any work was done
     * @throws NotFoundException
     * @throws IOException
     * @throws CannotCompileException
     * @throws ClassNotFoundException
     */
    public boolean weave(CtClass type) throws NotFoundException, IOException, CannotCompileException,
        ClassNotFoundException {
        reportSettings();
        final String policyName = generateName(POLICY_NAME);
        final String policyValue = toString(type.getAttribute(policyName));
        if (policyValue != null) {
            verbose("%s already woven with policy %s", type.getName(), policyValue);
            if (!policy.name().equals(policyValue)) {
                throw new AlreadyWovenException(type.getName(), Policy.valueOf(policyValue));
            }
            return false;
        }
        boolean result = false;
        if (policy.compareTo(Policy.NEVER) > 0) {
            if (policy == Policy.ON_INIT) {
                debug("Initializing field %s to %s", policy.condition, HAS_SECURITY_MANAGER_CONDITION);
                type.addField(new CtField(CtClass.booleanType, policy.condition, type),
                    CtField.Initializer.byExpr(HAS_SECURITY_MANAGER_CONDITION));
            }
            for (final CtMethod m : getPrivilegedMethods(type)) {
                result |= weave(type, m);
            }
            if (result) {
                type.setAttribute(policyName, policy.name().getBytes(Charset.forName("UTF-8")));
                getClassFileWriter().write(type);
            }
        }
        log.verbose(String.format(result ? "Wove class %s" : "Nothing to do for class %s", type.getName()));
        return result;
    }

    protected void debug(String message, Object... args) {
        log.debug(String.format(message, args));
    }

    protected void verbose(String message, Object... args) {
        log.verbose(String.format(message, args));
    }

    protected void warn(String message, Object... args) {
        log.warn(String.format(message, args));
    }

    protected abstract ClassFileWriter getClassFileWriter();

    protected void info(String message, Object... args) {
        log.info(String.format(message, args));
    }

    protected boolean permitMethodWeaving(AccessLevel accessLevel) {
        return true;
    }

    private CtClass createAction(CtClass type, CtMethod impl, Class<?> iface) throws NotFoundException,
        CannotCompileException, IOException {
        final boolean exc = impl.getExceptionTypes().length > 0;

        final CtClass actionType = classPool.get(iface.getName());

        final String simpleName = generateActionClassname(impl);
        debug("Creating action type %s for method %s", simpleName, toString(impl));
        final CtClass result = type.makeNestedClass(simpleName, true);
        result.addInterface(actionType);

        final CtField owner;
        if (Modifier.isStatic(impl.getModifiers())) {
            owner = null;
        } else {
            owner = new CtField(type, generateName("owner"), result);
            owner.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            debug("Adding owner field %s to %s", owner.getName(), simpleName);
            result.addField(owner);
        }

        final List<String> propagatedParameters = new ArrayList<String>();
        int index = -1;
        for (final CtClass param : impl.getParameterTypes()) {
            final String f = String.format("arg%s", Integer.valueOf(++index));
            final CtField fld = new CtField(param, f, result);
            fld.setModifiers(Modifier.PRIVATE | Modifier.FINAL);
            debug("Copying parameter %s from %s to %s.%s", index, toString(impl), simpleName, f);
            result.addField(fld);
            propagatedParameters.add(f);
        }
        {
            final StrBuilder constructor = new StrBuilder(simpleName).append('(');
            boolean sep = false;
            final Body body = new Body();

            for (final CtField fld : result.getDeclaredFields()) {
                if (sep) {
                    constructor.append(", ");
                } else {
                    sep = true;
                }
                constructor.append(fld.getType().getName()).append(' ').append(fld.getName());
                body.appendLine("this.%1$s = %1$s;", fld.getName());
            }
            constructor.append(") ").append(body.complete());

            final String c = constructor.toString();
            debug("Creating action constructor:");
            debug(c);
            result.addConstructor(CtNewConstructor.make(c, result));
        }
        {
            final StrBuilder run = new StrBuilder("public Object run() ");
            if (exc) {
                run.append("throws Exception ");
            }
            final Body body = new Body();
            final CtClass rt = impl.getReturnType();
            final boolean isVoid = rt.equals(CtClass.voidType);
            if (!isVoid) {
                body.append("return ");
            }
            final String deref = Modifier.isStatic(impl.getModifiers()) ? type.getName() : owner.getName();
            final String call =
                String.format("%s.%s(%s)", deref, impl.getName(), StringUtils.join(propagatedParameters, ", "));

            if (!isVoid && rt.isPrimitive()) {
                body.appendLine("%2$s.valueOf(%1$s);", call, ((CtPrimitiveType) rt).getWrapperName());
            } else {
                body.append(call).append(';').appendNewLine();

                if (isVoid) {
                    body.appendLine("return null;");
                }
            }

            run.append(body.complete());

            final String r = run.toString();
            debug("Creating run method:");
            debug(r);
            result.addMethod(CtNewMethod.make(r, result));
        }
        getClassFileWriter().write(result);
        debug("Returning action type %s", result);
        return result;
    }

    private String generateActionClassname(CtMethod m) throws NotFoundException {
        final StringBuilder b = new StringBuilder(m.getName());
        if (m.getParameterTypes().length > 0) {
            b.append("$$").append(
                StringUtils.strip(Descriptor.getParamDescriptor(m.getSignature()), "(;)").replace("[", "ARRAYOF_")
                    .replace('/', '_').replace(';', '$'));
        }
        return b.append(ACTION_SUFFIX).toString();
    }

    private String toString(CtMethod m) {
        return String.format("%s%s", m.getName(), m.getSignature());
    }

    private boolean weave(CtClass type, CtMethod method) throws ClassNotFoundException, CannotCompileException,
        NotFoundException, IOException {
        final AccessLevel accessLevel = AccessLevel.of(method.getModifiers());
        if (!permitMethodWeaving(accessLevel)) {
            warn("Ignoring %s method %s.%s", accessLevel, type.getName(), toString(method));
            return false;
        }
        if (AccessLevel.PACKAGE.compareTo(accessLevel) > 0) {
            warn("Possible security leak: granting privileges to %s method %s.%s", accessLevel, type.getName(),
                toString(method));
        }
        final String implName = generateName(method.getName());

        final CtMethod impl = CtNewMethod.copy(method, implName, type, null);
        impl.setModifiers(AccessLevel.PRIVATE.merge(method.getModifiers()));
        type.addMethod(impl);
        debug("Copied %2$s %1$s.%3$s to %4$s %1$s.%5$s", type.getName(), accessLevel, toString(method),
            AccessLevel.PRIVATE, toString(impl));

        final Body body = new Body();
        if (policy.isConditional()) {
            body.startBlock("if (%s)", policy.condition);
        }

        final boolean exc = method.getExceptionTypes().length > 0;

        if (exc) {
            body.startBlock("try");
        }

        final Class<?> iface = exc ? PrivilegedExceptionAction.class : PrivilegedAction.class;
        final CtClass actionType = createAction(type, impl, iface);
        final String action = generateName("action");

        body.append("final %s %s = new %s(", iface.getName(), action, actionType.getName());
        boolean firstParam;
        if (Modifier.isStatic(impl.getModifiers())) {
            firstParam = true;
        } else {
            body.append("$0");
            firstParam = false;
        }
        for (int i = 1, sz = impl.getParameterTypes().length; i <= sz; i++) {
            if (firstParam) {
                firstParam = false;
            } else {
                body.append(", ");
            }
            body.append('$').append(Integer.toString(i));
        }
        body.appendLine(");");

        final CtClass rt = method.getReturnType();
        final boolean isVoid = rt.equals(CtClass.voidType);

        final String doPrivileged = String.format("%1$s.doPrivileged(%2$s)", AccessController.class.getName(), action);
        if (isVoid) {
            body.append(doPrivileged).append(';').appendNewLine();
            if (policy.isConditional()) {
                body.appendLine("return;");
            }
        } else {
            final String cast = rt.isPrimitive() ? ((CtPrimitiveType) rt).getWrapperName() : rt.getName();
            // don't worry about wrapper NPEs because we should be simply
            // passing back an autoboxed value, then unboxing again
            final String result = generateName("result");
            body.appendLine("final %1$s %3$s = (%1$s) %2$s;", cast, doPrivileged, result);
            body.append("return %s", result);
            if (rt.isPrimitive()) {
                body.append(".%sValue()", rt.getName());
            }
            body.append(';').appendNewLine();
        }

        if (exc) {
            body.endBlock();
            final String e = generateName("e");
            body.startBlock("catch (%1$s %2$s)", PrivilegedActionException.class.getName(), e).appendNewLine();

            final String wrapped = generateName("wrapped");

            body.appendLine("final Exception %1$s = %2$s.getCause();", wrapped, e);
            for (final CtClass thrown : method.getExceptionTypes()) {
                body.startBlock("if (%1$s instanceof %2$s)", wrapped, thrown.getName());
                body.appendLine("throw (%2$s) %1$s;", wrapped, thrown.getName());
                body.endBlock();
            }
            body.appendLine(
                "throw %1$s instanceof RuntimeException ? (RuntimeException) %1$s : new RuntimeException(%1$s);",
                wrapped);
            body.endBlock();
        }

        if (policy.isConditional()) {
            // close if block we opened before:
            body.endBlock();
            // no security manager=> just call impl:
            if (!isVoid) {
                body.append("return ");
            }
            body.appendLine("%s($$);", impl.getName());
        }

        final String block = body.complete().toString();
        debug("Setting body of %s to:\n%s", toString(method), block);
        method.setBody(block);
        return true;
    }

    private void reportSettings() {
        if (!settingsReported) {
            settingsReported = true;
            info("Weave policy == %s", policy);
        }
    }
}
