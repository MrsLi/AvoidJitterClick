package com.chusong.avoid.plugin

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.Format
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.api.transform.TransformOutputProvider
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.utils.FileUtils
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.provider.MapPropertyExtensions
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class AvoidJitterPlugin extends Transform implements Plugin<Project>{

    @Override
    void apply(Project project) {
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(new AvoidJitterPlugin())
    }

    @Override
    String getName() {
        return "avoidjitter"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    /**
     * 是否是增量编译
     * @return
     */
    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        println("====ASM INSTALL START===")

        if (!isIncremental()){ //判断如果不是增量更新,则删除所有的输出结果
            transformInvocation.outputProvider.deleteAll()
        }

        Collection<TransformInput> inputs=transformInvocation.inputs
        TransformOutputProvider outputProvider = transformInvocation.outputProvider
        //开始遍历输入文件
        inputs.each {TransformInput input->

                //文件夹中的class文件遍历
                input.directoryInputs.each { DirectoryInput dirInput->
                    handleDirectoryInput(dirInput,outputProvider)

                }

                //jar包中的class文件遍历
                input.jarInputs.each { JarInput jarInput->
                    handleJarInputs(jarInput,outputProvider)
                }

        }

        println("====ASM INSTALL END===")

    }


    private static void handleDirectoryInput(DirectoryInput directoryInput, TransformOutputProvider outputProvider){
        //是否是文件夹
        if (directoryInput.file.isDirectory()){
            directoryInput.file.eachFileRecurse { File file->
                def name = file.name
                if (name.endsWith(".class")&&!name.startsWith("R\$")&&!"R.class".equals(name) && !"BuildConfig.class".equals(name)
                        && !"android/support/v4/app/FragmentActivity.class".equals(name)){

                    println("------处理 class文件:"+name+"---------")
                    ClassReader classReader = new ClassReader(file.bytes)
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
                    ClassVisitor classVisitor = new MyClassVisitor(classWriter)
                    classReader.accept(classVisitor,ClassReader.SKIP_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    FileOutputStream fos = new FileOutputStream(
                            file.parentFile.absolutePath + File.separator + name)
                    fos.write(code)
                    fos.close()
                }

            }
        }
        //处理完输入文件之后，要把输出给下一个任务
        def dest = outputProvider.getContentLocation(directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes,
                Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)

    }


    private static  void handleJarInputs(JarInput jarInput,TransformOutputProvider outputProvider){
        if (jarInput.file.getAbsolutePath().endsWith(".jar")){
            def jarName = jarInput.name
            def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
            if (jarName.endsWith(".jar")){
                jarName = jarName.substring(0,jarName.length()-4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                //插桩class
                if (entryName.endsWith(".class") && !entryName.startsWith("R\$")
                        && !"R.class".equals(entryName) && !"BuildConfig.class".equals(entryName)
                        && "android/support/v4/app/FragmentActivity.class".equals(entryName)) {
                    //class文件处理
                    println '----------- deal with "jar" class file <' + entryName + '> -----------'
                    jarOutputStream.putNextEntry(zipEntry)
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    ClassVisitor cv = new MyClassVisitor(classWriter)
                    classReader.accept(cv, ClassReader.EXPAND_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    jarOutputStream.write(code)
                } else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            //结束
            jarOutputStream.close()
            jarFile.close()
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }

    }
}