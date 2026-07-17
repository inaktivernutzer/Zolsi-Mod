package cc.zolsi.mod.inject;

import cc.zolsi.mod.ZolsiLog;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

public final class RenderHookTransformer implements ClassFileTransformer {

    static final String TARGET = "com/mojang/blaze3d/opengl/GlSurface";
    private static final String HOOK_METHOD = "present";
    public static final String HOOK_PROPERTY = "zolsi.renderHook";

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!TARGET.equals(className)) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected ClassLoader getClassLoader() {
                    return loader != null ? loader : super.getClassLoader();
                }
            };
            reader.accept(new HookVisitor(writer), 0);
            byte[] result = writer.toByteArray();
            ZolsiLog.log("GlSurface transform succeeded (" + result.length + " bytes)");
            return result;
        } catch (Throwable t) {
            ZolsiLog.log("GlSurface transform FAILED", t);
            return null;
        }
    }

    private static final class HookVisitor extends ClassVisitor {

        HookVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals(HOOK_METHOD) && descriptor.equals("()V")) {
                return new HeadVisitor(mv);
            }
            return mv;
        }
    }

    private static final class HeadVisitor extends MethodVisitor {

        HeadVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties",
                "()Ljava/util/Properties;", false);
            visitLdcInsn(HOOK_PROPERTY);
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            visitInsn(Opcodes.DUP);
            Label skip = new Label();
            visitJumpInsn(Opcodes.IFNULL, skip);
            visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Runnable");
            visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/lang/Runnable", "run",
                "()V", true);
            Label done = new Label();
            visitJumpInsn(Opcodes.GOTO, done);
            visitLabel(skip);
            visitInsn(Opcodes.POP);
            visitLabel(done);
        }
    }
}
