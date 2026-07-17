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

public final class SoundSuppressTransformer implements ClassFileTransformer {

    static final String TARGET = "net/minecraft/client/sounds/SoundManager";
    public static final String HOOK_PROPERTY = "zolsi.soundFilter";

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
            ZolsiLog.log("SoundManager transform succeeded (" + result.length + " bytes)");
            return result;
        } catch (Throwable t) {
            ZolsiLog.log("SoundManager transform FAILED", t);
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
            if (name.equals("play") && descriptor.equals(
                    "(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;")) {
                return new PlayHookVisitor(mv);
            }
            if (name.equals("playDelayed") && descriptor.equals(
                    "(Lnet/minecraft/client/resources/sounds/SoundInstance;I)V")) {
                return new PlayDelayedHookVisitor(mv);
            }
            return mv;
        }
    }

    private static void injectHurtCheck(MethodVisitor mv, Runnable onSuppress) {
        // if (System.getProperties().get("zolsi.soundFilter") != null
        //     && soundInstance.getIdentifier().getPath().contains(".hurt")) {
        //     onSuppress.run();
        // }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/System", "getProperties",
            "()Ljava/util/Properties;", false);
        mv.visitLdcInsn(HOOK_PROPERTY);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/Properties", "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(Opcodes.DUP);
        Label skip = new Label();
        mv.visitJumpInsn(Opcodes.IFNULL, skip);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
            "net/minecraft/client/resources/sounds/SoundInstance", "getIdentifier",
            "()Lnet/minecraft/resources/Identifier;", true);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
            "net/minecraft/resources/Identifier", "getPath",
            "()Ljava/lang/String;", false);
        mv.visitLdcInsn(".hurt");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
            "java/lang/String", "contains",
            "(Ljava/lang/CharSequence;)Z", false);
        Label nosuppress = new Label();
        mv.visitJumpInsn(Opcodes.IFEQ, nosuppress);
        onSuppress.run();
        mv.visitLabel(nosuppress);
        Label end = new Label();
        mv.visitJumpInsn(Opcodes.GOTO, end);
        mv.visitLabel(skip);
        mv.visitInsn(Opcodes.POP);
        mv.visitLabel(end);
    }

    private static final class PlayHookVisitor extends MethodVisitor {

        PlayHookVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            injectHurtCheck(mv, () -> {
                mv.visitFieldInsn(Opcodes.GETSTATIC,
                    "net/minecraft/client/sounds/SoundEngine$PlayResult",
                    "NOT_STARTED",
                    "Lnet/minecraft/client/sounds/SoundEngine$PlayResult;");
                mv.visitInsn(Opcodes.ARETURN);
            });
        }
    }

    private static final class PlayDelayedHookVisitor extends MethodVisitor {

        PlayDelayedHookVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            injectHurtCheck(mv, () -> {
                mv.visitInsn(Opcodes.RETURN);
            });
        }
    }
}
