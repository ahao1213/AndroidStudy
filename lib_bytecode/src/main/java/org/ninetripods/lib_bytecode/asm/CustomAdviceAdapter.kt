package org.ninetripods.lib_bytecode.asm

import org.ninetripods.lib_bytecode.ByteConstant
import org.ninetripods.lib_bytecode.MethodTimeCostTest
import org.ninetripods.lib_bytecode.log
import org.ninetripods.lib_bytecode.util.Loader
import org.ninetripods.lib_bytecode.util.decodeAcc
import org.ninetripods.lib_bytecode.util.decodeOpcode
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

private const val FIELD_NAME_ADD = "timeCost" //新增属性名称

/**
执行结果：
visit(): owner-org/ninetripods/lib_bytecode/MethodTimeCostTest
visitMethod(): access-ACC_PUBLIC ACC_FINAL , name-addTimeCostMonitor, descriptor-()V, signature-null, exceptions-null
onMethodEnter():
onMethodExit(): opcode-RETURN
visitMethod(): access-ACC_PUBLIC , name-<init>, descriptor-()V, signature-null, exceptions-null
visitEnd():
timeCost: 1003
 */
fun main() {
    val classReader = ClassReader(MethodTimeCostTest::class.java.name)
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    val classVisitor = AddTimeCostVisitor(ByteConstant.ASM9, classWriter)
    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

    val loader = Loader()
    val addTimeClass =
        loader.defineClass(MethodTimeCostTest::class.java.name, classWriter.toByteArray())
    val instance = addTimeClass.newInstance()
    //调用插桩之后的方法
    addTimeClass.getDeclaredMethod("addTimeCostMonitor").invoke(instance)
    val timeCost = addTimeClass.getDeclaredField(FIELD_NAME_ADD).getLong(instance)
    log("timeCost: $timeCost")

}

class AddTimeCostVisitor(api: Int, classVisitor: ClassVisitor) :
    ClassVisitor(api, classVisitor) {

    private var owner = ""

    override fun visit(
        version: Int,
        access: Int,
        name: String?,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        this.owner = name ?: ""
        log("visit(): owner-$owner")
        super.visit(version, access, name, signature, superName, interfaces)
    }

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        log("visitMethod(): access-${access.decodeAcc()}, name-$name, descriptor-$descriptor, signature-$signature, exceptions-$exceptions")
        //排除<init>构造函数
        if (cv != null && name != "<init>") {
            val methodVisitor =
                cv.visitMethod(access, name, descriptor, signature, exceptions)
            return CustomAdviceAdapter(owner, api,
                methodVisitor, access, name ?: "", descriptor ?: "")
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions)
    }

    override fun visitEnd() {
        log("visitEnd():")
        if (cv != null) {
            val fieldVisitor = cv.visitField(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, FIELD_NAME_ADD, "J", null, null)
            fieldVisitor.visitEnd()
        }
        super.visitEnd()
    }
}

/*
AdviceAdapter 是继承自 MethodVisitor 的一个抽象类，可用于在一个方法的开头以及任意 RETURN 或 ATHROW 指令之前插入代码。
它的优点是可以对构造器也是有效的，在构造器中插入到构造器调用之后。事实上，这个适配器的大多数代码都是用于检测对这个构造器的调用。
注意，AdviceAdapter 继承自 LocalVariablesSorter，所以也可以轻松完成对一个局部变量的操作。
 */
class CustomAdviceAdapter(
    private val owner: String,
    api: Int,
    methodVisitor: MethodVisitor,
    inputAccess: Int,
    inputName: String,
    descriptor: String,
) : AdviceAdapter(api, methodVisitor, inputAccess, inputName, descriptor) {

    override fun visitFieldInsn(opcode: Int, owner: String?, name: String?, descriptor: String?) {
        log("visitFieldInsn(): opcode-$opcode, owner-$owner, name-$name, descriptor-$descriptor")
        super.visitFieldInsn(opcode, owner, name, descriptor)
    }

    override fun onMethodEnter() {
        log("onMethodEnter():")
        mv.visitFieldInsn(GETSTATIC, owner, FIELD_NAME_ADD, "J")
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false)
        mv.visitInsn(LSUB)
        mv.visitFieldInsn(PUTSTATIC, owner, FIELD_NAME_ADD, "J")
    }

    override fun onMethodExit(opcode: Int) {
        log("onMethodExit(): opcode-${opcode.decodeOpcode()}")
        mv.visitFieldInsn(GETSTATIC, owner, FIELD_NAME_ADD, "J")
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false)
        mv.visitInsn(LADD)
        mv.visitFieldInsn(PUTSTATIC, owner, FIELD_NAME_ADD, "J")
    }

    override fun visitMaxs(maxStack: Int, maxLocals: Int) {
        super.visitMaxs(maxStack, maxLocals)
    }
}