package com.chusong.avoid.plugin

import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.AdviceAdapter

class MyClassVisitor extends ClassVisitor{
    MyClassVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM6, classVisitor)
        println("-------------MyClassVisitor:-------------")
    }
    @Override
    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return new AvoidVisitMethod(methodVisitor,access,name,descriptor)
    }

    static class AvoidVisitMethod extends AdviceAdapter{
        private final String methodName
        private final int access
        private final String desc
        private final MethodVisitor methodVisitor
        private boolean inject = false //是否有注解,需要忽略

        protected AvoidVisitMethod( MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(Opcodes.ASM6, methodVisitor, access, name, descriptor)
            this.methodVisitor = methodVisitor
            this.methodName = name
            this.access = access
            this.desc = descriptor

//            println("-------------OnClick: "+descriptor+"-------------")
            //判断方法名,是否需要插装
            if(name =="onClick" && descriptor =="(Landroid/view/View;)V" || (name == "onItemClick" && desc == "(Landroid/widget/AdapterView;Landroid/view/View;IJ)V")){
                this.inject = true
                println("---------inject:true"+name+"--------------")
            }
        }

        @Override
        AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            //含有此注解的,跳过判断
//            if (descriptor == "") {
//                inject = false
//            }
            return super.visitAnnotation(descriptor, visible)
        }

        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
            //代码插装
            if (inject){
                methodVisitor.visitVarInsn(ALOAD, 1);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "android/view/View", "getId", "()I", false);
                methodVisitor.visitVarInsn(ISTORE, 2);
                Label label1 = new Label();
                methodVisitor.visitLabel(label1);
                methodVisitor.visitLineNumber(25, label1);
                methodVisitor.visitLdcInsn("ASM");
                methodVisitor.visitTypeInsn(NEW, "java/lang/StringBuilder");
                methodVisitor.visitInsn(DUP);
                methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
                methodVisitor.visitLdcInsn("onClick: ");
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitVarInsn(ILOAD, 2);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
                methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
                methodVisitor.visitMethodInsn(INVOKESTATIC, "android/util/Log", "e", "(Ljava/lang/String;Ljava/lang/String;)I", false);
                methodVisitor.visitInsn(POP);
            }

        }
    }
}