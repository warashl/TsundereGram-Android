import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Правит байткод tdl-coroutines: после каждого вызова JsonClient.receive(double) результат пропускается через
 * app.yougram.core.telegram.TdJsonFix.patch. Нужно, чтобы библиотека не падала на объектах, у которых TDLib
 * не присылает «обязательные» по схеме поля.
 */
abstract class TdJsonPatchFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?,
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String?,
                        name: String?,
                        descriptor: String?,
                        isInterface: Boolean,
                    ) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                        if (owner == "org/drinkless/tdlib/JsonClient" &&
                            name == "receive" &&
                            descriptor == "(D)Ljava/lang/String;"
                        ) {
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "app/yougram/core/telegram/TdJsonFix",
                                "patch",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false,
                            )
                        }
                    }
                }
            }
        }

    override fun isInstrumentable(classData: ClassData): Boolean =
        classData.className.startsWith("dev.g000sha256.tdl.")
}
