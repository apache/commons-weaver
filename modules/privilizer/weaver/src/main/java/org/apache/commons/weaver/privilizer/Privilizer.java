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
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewConstructor;
import javassist.CtNewMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.text.StrBuilder;
import org.apache.commons.weaver.privilizer.Privilizing.CallTo;
import org.apache.commons.weaver.utils.Assistant;
import org.apache.commons.weaver.utils.Body;

/**
 * Handles:
 * <ul>
 * <li>weaving of methods annotated with {@link Privileged}</li>
 * <li>weaving of blueprint annotation methods</li>
 * </ul>
 * 
 * @see Privileged
 * @see Privilizing
 */
public abstract class Privilizer {
    /**
     * Simple interface to abstract the act of saving modifications to a class.
     */
    public interface ModifiedClassWriter {
        void write(CtClass type) throws CannotCompileException, IOException;
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
         * Weaves such that the check for an active {@link SecurityManager} is done once only.
         */
        ON_INIT(generateName("hasSecurityManager")),

        /**
         * Weaves such that the check for an active {@link SecurityManager} is done for each {@link Privileged} method
         * execution.
         */
        DYNAMIC(HAS_SECURITY_MANAGER_CONDITION),

        /**
         * Weaves such that {@link Privileged} methods are always executed as such.
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

    private static final Logger log = Logger.getLogger(Privilizer.class.getName());

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

    private final Assistant assistant;

    public Privilizer(ClassPool classPool) {
        this(Policy.DYNAMIC, classPool);
    }

    public Privilizer(Policy policy, ClassPool classPool) {
        this.policy = Validate.notNull(policy, "policy");
        this.classPool = Validate.notNull(classPool, "classPool");
        this.assistant = new Assistant(classPool, "__privilizer_");
    }

    /**
     * Weave the specified class. Handles all {@link Privileged} methods as well as calls described by
     * {@code privilizing}.
     * 
     * @param privilizing
     * 
     * @throws NotFoundException
     * @throws IOException
     * @throws CannotCompileException
     * @throws ClassNotFoundException
     */
    public boolean weaveClass(Class<?> clazz, Privilizing privilizing) throws NotFoundException, IOException,
            CannotCompileException, ClassNotFoundException, IllegalAccessException {
        return weave(classPool.get(clazz.getName()), privilizing);
    }

    /**
     * Weave the specified class.
     * 
     * @param type
     * @param privilizing
     * @return whether any work was done
     * @throws NotFoundException
     * @throws IOException
     * @throws CannotCompileException
     * @throws ClassNotFoundException
     */
    private boolean weave(CtClass type, Privilizing privilizing) throws NotFoundException, IOException,
            CannotCompileException, ClassNotFoundException, IllegalAccessException {
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
            if (type.getAttribute(policyName) != null) {
                // if this class already got enhanced then abort
                return false;
            }

            if (policy == Policy.ON_INIT) {
                debug("Initializing field %s.%s to %s", type.getName(), policy.condition,
                        HAS_SECURITY_MANAGER_CONDITION);

                CtField securityManager = new CtField(CtClass.booleanType, policy.condition, type);
                securityManager.setModifiers(Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL);
                type.addField(securityManager, CtField.Initializer.byExpr(HAS_SECURITY_MANAGER_CONDITION));
            }

            result = privilizeBlueprints(type, privilizing) | result;

            for (final CtMethod m : getPrivilegedMethods(type)) {
                result = weave(type, m) | result;
            }
            if (result) {
                type.setAttribute(policyName, policy.name().getBytes(Charset.forName("UTF-8")));
                getModifiedClassWriter().write(type);
            }
        }
        log.info(String.format(result ? "Wove class %s" : "Nothing to do for class %s", type.getName()));
        return result;
    }

    private boolean privilizeBlueprints(CtClass type, Privilizing annotation) throws CannotCompileException,
            ClassNotFoundException, NotFoundException, IOException, IllegalAccessException {
        boolean result = false;
        if (annotation != null) {
            final CallTo[] blueprintCalls = annotation.value();
            for (CallTo callTo : blueprintCalls) {
                Validate.isTrue(!callTo.value().equals(type.getName()),
                        "Type %s cannot use itself as a privilizer blueprint", callTo.value());
            }
            for (CtMethod method : type.getDeclaredMethods()) {
                result = privilizeBlueprints(type, method, blueprintCalls) | result;
            }
        }
        return result;
    }

    private boolean privilizeBlueprints(final CtClass type, final CtMethod method, final CallTo[] blueprintCalls)
            throws CannotCompileException, ClassNotFoundException, NotFoundException, IOException,
            IllegalAccessException {
        final MutableBoolean result = new MutableBoolean();

        method.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall call) throws CannotCompileException {
                super.edit(call);
                CtMethod called;
                try {
                    called = call.getMethod();
                    if (!Modifier.isStatic(called.getModifiers())) {
                        return;
                    }
                }
                catch (NotFoundException e) {
                    return;
                }
                boolean found = false;
                for (CallTo callTo : blueprintCalls) {
                    final Class<?> owner = callTo.value();
                    if (owner.getName().equals(call.getClassName())) {
                        if (callTo.methods().length == 0) {
                            found = true;
                            break;
                        }
                        for (String m : callTo.methods()) {
                            found = StringUtils.equals(call.getMethodName(), m);
                            if (found) {
                                break;
                            }
                        }
                    }
                }
                if (found) {
                    final String name = importedMethodName(called);

                    CtMethod copy;
                    try {
                        copy = copyBlueprintTo(type, name, called, blueprintCalls);
                    }
                    catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    if (copy == null) {
                        debug("Unable to use %s as blueprint method in %s", Privilizer.this.toString(called), Privilizer.this.toString(method));
                        return;
                    }
                    Body redirect = new Body(Privilizer.this, "call %s", Privilizer.this.toString(called));
                    if (Privilizer.this.policy.isConditional()) {
                        redirect.startBlock("if (%s)", Privilizer.this.policy.condition);
                    }
                    redirect.appendLine("$_ = %s($$);", copy.getName());
                    if (Privilizer.this.policy.isConditional()) {
                        redirect.endBlock().startBlock("else").appendLine("$_ = $proceed($$);").endBlock();
                    }
                    call.replace(redirect.complete().toString());
                    result.setValue(Boolean.TRUE);
                }
            }
        });
        return result.booleanValue();
    }

    private static String importedMethodName(CtMethod blueprint) {
        return new StringBuilder(blueprint.getDeclaringClass().getName().replace('.', '_')).append('$')
                .append(blueprint.getName()).toString();
    }

    /*
     * This design is almost certainly suboptimal. Basically, we have:
     * 
     * for a declared method, look for calls to blueprint methods for each blueprint method, copy it when copying,
     * inspect blueprint method's code and recursively copy in methods from the source class of *that particular method*
     * because otherwise CtNewMethod will do it for us and we'll miss our window of opportunity now that we have a
     * copied blueprint method, inspect it for blueprint calls from other classes and do this whole thing recursively.
     * 
     * It would *seem* that we could combine the recursion/copying of methods from all blueprint classes but I can't get
     * my head around it right now. -MJB
     */
    private CtMethod copyBlueprintTo(final CtClass target, final String toName, final CtMethod method,
            final CallTo[] blueprintCalls) throws ClassNotFoundException, NotFoundException, IOException,
            IllegalAccessException, CannotCompileException {
        if (!Modifier.isStatic(method.getModifiers())) {
            return null;
        }

        try {
            final CtMethod done = target.getDeclaredMethod(toName, method.getParameterTypes());
            return done;
        }
        catch (NotFoundException e1) {
        }
        final CtClass declaring = method.getDeclaringClass();

        final List<CtField> referencedFields = new ArrayList<CtField>();
        final List<CtMethod> ownBlueprints = new ArrayList<CtMethod>();
        class CollectOwnReferences extends ExprEditor {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                super.edit(m);
                CtMethod called;
                try {
                    called = m.getMethod();
                }
                catch (NotFoundException e) {
                    return;
                }
                if (called.getDeclaringClass().equals(declaring)) {
                    ownBlueprints.add(called);
                }
            }

            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
                super.edit(f);
                try {
                    referencedFields.add(f.getField());
                }
                catch (NotFoundException e) {
                }
            }
        }
        method.instrument(new CollectOwnReferences());

        boolean isRecursive = false;

        for (CtMethod blueprint : ownBlueprints) {
            if (blueprint.equals(method)) {
                // recursive method call identified:
                isRecursive = true;
                continue;
            }
            CtMethod local = copyBlueprintTo(target, importedMethodName(blueprint), blueprint, blueprintCalls);
            if (local != null) {
                method.instrument(redirect(blueprint, local));
            }
        }

        // we have code to handle non-public fields, but the generated code gets VerifyErrors at runtime;
        // for now we must skip blueprinting such methods.
        boolean referencesPublicFieldsOnly = true;
        for (CtField refd : referencedFields) {
            if (!Modifier.isPublic(refd.getModifiers())) {
            	warn("Method %s references non-public field %s.%s", toString(method), refd.getDeclaringClass().getName(), refd.getName());
                referencesPublicFieldsOnly = false;
            }
        }
        if (!referencesPublicFieldsOnly) {
            return null;
        }

        final CtMethod result = CtNewMethod.copy(method, toName, target, null);
        result.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
        target.addMethod(result);

        if (!referencedFields.isEmpty()) {
            handleReferencedFields(target, result, method, referencedFields);
        }
        if (isRecursive) {
            CodeConverter redirectRecursive = new CodeConverter();
            redirectRecursive.redirectMethodCall(method.getName(), result);
            result.instrument(redirectRecursive);
        }
        // privilize other classes' blueprint methods recursively:
        privilizeBlueprints(target, result, blueprintCalls);

        if (!referencedFields.isEmpty()) {
            // more referenced fields handling, but reduces the amount of generated code we read through
            // when blueprinting recursively
            makeAccessible(target, result, referencedFields);
        }
        // not really possible to check for what truly may throw a SecurityException and thus requires privilizing,
        // but we'll assume any public method must be. As anything else, once copied,
        // can only be called from something we have already privilized
        // TODO? only privilize methods the instrumented class called originally, directly
        if (Modifier.isPublic(method.getModifiers())) {
            weave(target, result);
        }

        return result;
    }

    private static CodeConverter redirect(CtMethod origMethod, CtMethod substMethod) throws CannotCompileException {
        final CodeConverter result = new CodeConverter();
        result.redirectMethodCall(origMethod, substMethod);
        return result;
    }

    private void handleReferencedFields(final CtClass target, final CtMethod method, final CtMethod source,
            final List<CtField> referencedFields) throws CannotCompileException, NotFoundException {

        for (CtField ctField : referencedFields) {
            Validate.validState(!ctField.getDeclaringClass().equals(target),
                    "Circular reference; cannot blueprint method %s", toString(source));
        }

        method.instrument(new ExprEditor() {
            @Override
            public void edit(FieldAccess f) throws CannotCompileException {
                super.edit(f);
                CtField fld;
                boolean primitive;
                try {
                    fld = f.getField();
                    primitive = fld.getType().isPrimitive();
                }
                catch (NotFoundException e) {
                    // no such field implies a reference copied such that the field doesn't exist, probably the usual
                    // case with blueprinted methods containing field refs

                    fld = null;
                    primitive = false;
                    CtClass host = source.getDeclaringClass();
                    while (host != null) {
                        try {
                            fld = host.getDeclaredField(f.getFieldName());
                            primitive = fld.getType().isPrimitive();
                            break;
                        }
                        catch (NotFoundException e1) {
                        }
                        try {
                            host = host.getSuperclass();
                        }
                        catch (NotFoundException e1) {
                            break;
                        }
                    }
                    if (fld == null) {
                        throw new RuntimeException(e);
                    }
                }
                final String replacement;
                if (f.isReader()) {
                    if (Modifier.isPublic(fld.getModifiers())) {
                        replacement = String.format("$_ = %s.%s;", fld.getDeclaringClass().getName(), f.getFieldName());
                    } else {
                        replacement =
                            String.format("$_ = ($%s) %s(%s.class, \"%s\", null);", primitive ? "w" : "r", assistant
                                .fieldReader(target).getName(), fld.getDeclaringClass().getName(), f.getFieldName());
                    }
                } else {
                    if (Modifier.isPublic(fld.getModifiers())) {
                        replacement = String.format("%s.%s = $1;", fld.getDeclaringClass().getName(), f.getFieldName());
                    } else {
                        replacement =
                            String.format("%s(%s.class, \"%s\", null, %s);", assistant.fieldWriter(target).getName(),
                                fld.getDeclaringClass().getName(), f.getFieldName(), primitive ? "($w) $1" : "$1");
                    }
                }
                debug("Replacing %s access of %s.%s", f.isReader() ? "read" : "write", fld.getDeclaringClass()
                        .getName(), f.getFieldName());
                debug(replacement);
                f.replace(replacement);
            }
        });
    }

    private void makeAccessible(final CtClass target, final CtMethod method, final List<CtField> referencedFields)
            throws CannotCompileException {
        boolean allPublic = true;
        for (CtField ctField : referencedFields) {
            if (!Modifier.isPublic(ctField.getModifiers())) {
                allPublic = false;
                break;
            }
        }
        if (allPublic) {
            // no need to fiddle with access
            return;
        }
        method.insertBefore(assistant.callPushFieldAccess(target, referencedFields));
        final boolean asFinally = true;
        method.insertAfter(new StringBuilder(assistant.popFieldAccess(target).getName()).append("();").toString(),
                asFinally);
    }

    protected void debug(String message, Object... args) {
        log.fine(String.format(message, args));
    }

    protected void verbose(String message, Object... args) {
        log.fine(String.format(message, args));
    }

    protected void warn(String message, Object... args) {
        log.warning(String.format(message, args));
    }

    protected abstract ModifiedClassWriter getModifiedClassWriter();

    protected void info(String message, Object... args) {
        log.info(String.format(message, args));
    }

    protected AccessLevel getTargetAccessLevel() {
        return AccessLevel.PRIVATE;
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
        }
        else {
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
            final StrBuilder sig = new StrBuilder(simpleName).append('(');
            boolean sep = false;
            final Body body = new Body(this, "adding to %s: %s", result.getName(), sig);

            for (final CtField fld : result.getDeclaredFields()) {
                if (sep) {
                    sig.append(", ");
                }
                else {
                    sep = true;
                }
                sig.append(fld.getType().getName()).append(' ').append(fld.getName());
                body.appendLine("this.%1$s = %1$s;", fld.getName());
            }
            result.addConstructor(CtNewConstructor.make(sig.append(") ").append(body.complete()).toString(), result));
        }
        {
            final StrBuilder run = new StrBuilder("public Object run() ");
            if (exc) {
                run.append("throws Exception ");
            }
            final Body body = new Body(this, "add to %s: %s", result.getName(), run.toString());
            final CtClass rt = impl.getReturnType();
            final boolean isVoid = rt.equals(CtClass.voidType);
            if (!isVoid) {
                body.append("return ");
            }
            final String deref = Modifier.isStatic(impl.getModifiers()) ? type.getName() : owner.getName();
            final String call = String.format("%s.%s(%s)", deref, impl.getName(),
                    StringUtils.join(propagatedParameters, ", "));

            if (!isVoid && rt.isPrimitive()) {
                body.appendLine("%2$s.valueOf(%1$s);", call, ((CtPrimitiveType) rt).getWrapperName());
            }
            else {
                body.append(call).append(';').appendNewLine();

                if (isVoid) {
                    body.appendLine("return null;");
                }
            }

            result.addMethod(CtNewMethod.make(run.append(body.complete()).toString(), result));
        }
        getModifiedClassWriter().write(result);
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
            NotFoundException, IOException, IllegalAccessException {
        final AccessLevel accessLevel = AccessLevel.of(method.getModifiers());
        if (!permitMethodWeaving(accessLevel)) {
            throw new IllegalAccessException("Method " + type.getName() + "#" + toString(method)
                    + " must have maximum access level '" + getTargetAccessLevel() + "' but is defined wider ('"
                    + accessLevel + "')");
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

        final Body body = new Body(this, "new body of %s", toString(method));

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
        }
        else {
            body.append("$0");
            firstParam = false;
        }
        for (int i = 1, sz = impl.getParameterTypes().length; i <= sz; i++) {
            if (firstParam) {
                firstParam = false;
            }
            else {
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
        }
        else {
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

        method.setBody(body.complete().toString());
        return true;
    }

    private void reportSettings() {
        if (!settingsReported) {
            settingsReported = true;
            debug("Weave policy == %s", policy);
        }
    }

}
