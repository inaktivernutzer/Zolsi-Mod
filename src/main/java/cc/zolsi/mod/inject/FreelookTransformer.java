package cc.zolsi.mod.inject;

import cc.zolsi.mod.ZolsiLog;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class FreelookTransformer implements ClassFileTransformer {

    static final String TARGET = "net/minecraft/client/Camera";
    private static final String METHOD = "setRotation";
    public static final String PROPERTY = "zolsi.freelook";

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
            reader.accept(new CameraClassVisitor(writer), 0);
            byte[] result = writer.toByteArray();
            ZolsiLog.log("Camera transform succeeded (" + result.length + " bytes)");
            return result;
        } catch (Throwable t) {
            ZolsiLog.log("Camera transform FAILED", t);
            return null;
        }
    }

    private static final class CameraClassVisitor extends ClassVisitor {

        CameraClassVisitor(ClassVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals(METHOD) && descriptor.equals("(FF)V")) {
                return new SetRotationVisitor(mv);
            }
            return mv;
        }
    }

    private static final class SetRotationVisitor extends MethodVisitor {

        SetRotationVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties",
                "()Ljava/util/Properties;", false);
            super.visitLdcInsn(PROPERTY);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            super.visitInsn(Opcodes.DUP);
            Label skip = new Label();
            super.visitJumpInsn(Opcodes.IFNULL, skip);
            super.visitTypeInsn(Opcodes.CHECKCAST, "[F");
            super.visitVarInsn(Opcodes.ASTORE, 3);
            Label done = new Label();
            super.visitVarInsn(Opcodes.ALOAD, 3);
            super.visitInsn(Opcodes.ICONST_2);
            super.visitInsn(Opcodes.FALOAD);
            super.visitInsn(Opcodes.FCONST_0);
            super.visitInsn(Opcodes.FCMPL);
            super.visitJumpInsn(Opcodes.IFEQ, done);
            super.visitVarInsn(Opcodes.ALOAD, 3);
            super.visitInsn(Opcodes.ICONST_0);
            super.visitInsn(Opcodes.FALOAD);
            super.visitVarInsn(Opcodes.FSTORE, 1);
            super.visitVarInsn(Opcodes.ALOAD, 3);
            super.visitInsn(Opcodes.ICONST_1);
            super.visitInsn(Opcodes.FALOAD);
            super.visitVarInsn(Opcodes.FSTORE, 2);
            super.visitJumpInsn(Opcodes.GOTO, done);
            super.visitLabel(skip);
            super.visitInsn(Opcodes.POP);
            super.visitLabel(done);
        }
    }
}
