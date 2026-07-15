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

public final class NameTagSuppressTransformer implements ClassFileTransformer {

    static final String TARGET = "net/minecraft/client/renderer/entity/EntityRenderer";
    private static final String HOOK_METHOD = "submitNameDisplay";
    private static final String HOOK_DESC_4 =
        "(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
        + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";
    private static final String HOOK_DESC_5 =
        "(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
        + "Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V";
    public static final String HOOK_PROPERTY = "zolsi.hideNames";

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
            ZolsiLog.log("EntityRenderer.submitNameDisplay transform succeeded (" + result.length + " bytes)");
            return result;
        } catch (Throwable t) {
            ZolsiLog.log("EntityRenderer.submitNameDisplay transform FAILED", t);
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
            if (name.equals(HOOK_METHOD)
                && (descriptor.equals(HOOK_DESC_4) || descriptor.equals(HOOK_DESC_5))) {
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
            Label cont = new Label();
            visitJumpInsn(Opcodes.IFNULL, cont);
            visitInsn(Opcodes.RETURN);
            visitLabel(cont);
        }
    }
}
