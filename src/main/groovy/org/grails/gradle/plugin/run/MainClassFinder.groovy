package org.grails.gradle.plugin.run

import groovy.transform.CompileStatic
import org.grails.io.support.GrailsResourceUtils
import org.springframework.asm.ClassReader
import org.springframework.asm.ClassVisitor
import org.springframework.asm.MethodVisitor
import org.springframework.asm.Opcodes
import org.springframework.asm.Type

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class MainClassFinder {

    private static final Type STRING_ARRAY_TYPE = Type.getType(String[].class)

    private static final Type MAIN_METHOD_TYPE = Type.getMethodType(Type.VOID_TYPE, STRING_ARRAY_TYPE)

    private static final String MAIN_METHOD_NAME = "main"


    String findMainClass(File rootFolder) {
        if (!rootFolder.exists()) {
            return null // nothing to do
        }
        if (!rootFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid root folder '$rootFolder'")
        }
        String prefix =  "$rootFolder.absolutePath/"
        def stack = new ArrayDeque<File>()
        stack.push rootFolder

        while (!stack.empty) {
            File file = stack.pop()
            if (file.isFile()) {
                InputStream inputStream = file.newInputStream()
                try {
                    if (isMainClass(inputStream)) {
                        return GrailsResourceUtils.getClassNameForClassFile(prefix, file.absolutePath)
                    }
                } finally {
                    inputStream?.close()
                }
            }
            if (file.isDirectory()) {
                def files = file.listFiles()?.findAll { File f ->
                    (f.isDirectory() && !f.name.startsWith('.') && !f.hidden) ||
                            (f.isFile() && f.name.endsWith(GrailsResourceUtils.CLASS_EXTENSION))
                }

                if(files) {
                    for(File sub in files) {
                        stack.push(sub)
                    }
                }

            }
        }
        return null
    }


    protected boolean isMainClass(InputStream inputStream) {
        def classReader = new ClassReader(inputStream)
        if(classReader.superName?.startsWith('grails/boot/config/')) {
            def mainMethodFinder = new MainMethodFinder()
            classReader.accept(mainMethodFinder, ClassReader.SKIP_CODE)
            return mainMethodFinder.found
        }
        return false
    }

    @CompileStatic
    static class MainMethodFinder extends ClassVisitor  {

        boolean found = false

        MainMethodFinder() {
            super(Opcodes.ASM4)
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if(!found) {
                if (isAccess(access, Opcodes.ACC_PUBLIC, Opcodes.ACC_STATIC)
                        && MAIN_METHOD_NAME.equals(name)
                        && MAIN_METHOD_TYPE.getDescriptor().equals(desc)) {


                    this.found = true
                }
            }
            return null
        }


        private boolean isAccess(int access, int... requiredOpsCodes) {
            return !requiredOpsCodes.any { int requiredOpsCode -> (access & requiredOpsCode) == 0 }
        }
    }

}
