/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.weaver.privilizer;

import java.util.Map;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;

/**
 * For any privileged method called from another privileged method (actually the internal implementation method copied
 * from the original method body) the call is replaced by a call to the target's internal implementation method, thus
 * avoiding nested privileged invocations when possible. Persists everything to a tree model until the parent is
 * complete; allowing us to use a tree model while yet building the high-level view as a stack of visitors.
 */
class InlineNestedPrivilegedCalls extends ClassNode {
    private final Privilizer privilizer;

    private final ClassVisitor next;

    /**
     * Map of original method to name of internal implementation method.
     */
    private final Map<Method, String> privilegedMethods;

    /**
     * Create a new {@link InlineNestedPrivilegedCalls} object.
     * @param privilizer owner
     * @param privilegedMethods map of original method to name of internal implementation method
     * @param next visitor
     */
    InlineNestedPrivilegedCalls(Privilizer privilizer, Map<Method, String> privilegedMethods, ClassVisitor next) {
        super(Opcodes.ASM4);
        this.privilizer = privilizer;
        this.privilegedMethods = privilegedMethods;
        this.next = next;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();

        accept(new ClassVisitor(Opcodes.ASM4, next) {
            @Override
            public MethodVisitor visitMethod(int access, final String name, String desc, String signature,
                String[] exceptions) {
                final Method outer = new Method(name, desc);
                final MethodVisitor orig = super.visitMethod(access, name, desc, signature, exceptions);
                if (privilegedMethods.containsValue(name)) {
                    return new MethodVisitor(Opcodes.ASM4, orig) {
                        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
                            String useName = name;
                            if (owner.equals(InlineNestedPrivilegedCalls.this.name)) {
                                final Method m = new Method(name, desc);
                                if (privilegedMethods.containsKey(m)) {
                                    useName = privilegedMethods.get(m);
                                    privilizer.env.debug("Inlining call from %s to %s as %s", outer, m,
                                        useName);
                                }
                            }
                            super.visitMethodInsn(opcode, owner, useName, desc);
                        }
                    };
                }
                return orig;
            }
        });
    }
}
