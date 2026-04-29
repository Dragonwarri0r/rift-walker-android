package com.riftwalker.aidebug.gradle

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class AiDebugAsmClassVisitorFactory : AsmClassVisitorFactory<AiDebugAsmParameters> {
    override fun isInstrumentable(classData: ClassData): Boolean {
        val className = classData.className
        if (className.startsWith("com.riftwalker.aidebug.")) return false

        val params = parameters.get()
        if (params.mediaHooksEnabled()) return true
        return (params.traceMethods.get() + params.overrideMethods.get())
            .any { target -> target.matchesClass(className) }
    }

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        val params = parameters.get()
        return AiDebugAsmClassVisitor(
            next = nextClassVisitor,
            className = classContext.currentClassData.className,
            traceMethods = params.traceMethods.get().toSet(),
            overrideMethods = params.overrideMethods.get().toSet(),
            hookAudioRecordReads = params.hookAudioRecordReads.get(),
            hookAudioRecordLifecycle = params.hookAudioRecordLifecycle.get(),
            hookCameraXAnalyzers = params.hookCameraXAnalyzers.get(),
            hookMlKitInputImageFactories = params.hookMlKitInputImageFactories.get(),
            customFrameHooks = params.customFrameHooks.get().toSet(),
        )
    }
}

interface AiDebugAsmParameters : InstrumentationParameters {
    @get:Input
    val traceMethods: ListProperty<String>

    @get:Input
    val overrideMethods: ListProperty<String>

    @get:Input
    val hookAudioRecordReads: Property<Boolean>

    @get:Input
    val hookAudioRecordLifecycle: Property<Boolean>

    @get:Input
    val hookCameraXAnalyzers: Property<Boolean>

    @get:Input
    val hookMlKitInputImageFactories: Property<Boolean>

    @get:Input
    val customFrameHooks: ListProperty<String>
}

private class AiDebugAsmClassVisitor(
    next: ClassVisitor,
    private val className: String,
    private val traceMethods: Set<String>,
    private val overrideMethods: Set<String>,
    private val hookAudioRecordReads: Boolean,
    private val hookAudioRecordLifecycle: Boolean,
    private val hookCameraXAnalyzers: Boolean,
    private val hookMlKitInputImageFactories: Boolean,
    private val customFrameHooks: Set<String>,
) : ClassVisitor(Opcodes.ASM9, next) {
    private val mediaHooksEnabled = hookAudioRecordReads ||
        hookAudioRecordLifecycle ||
        hookCameraXAnalyzers ||
        hookMlKitInputImageFactories ||
        customFrameHooks.isNotEmpty()

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        val delegate = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (name == "<init>" || name == "<clinit>") return delegate
        if (access and (Opcodes.ACC_ABSTRACT or Opcodes.ACC_NATIVE) != 0) return delegate

        val traceId = traceMethods.firstOrNull { it.matchesMethod(className, name) }
        val hookId = overrideMethods.firstOrNull { it.matchesMethod(className, name) }
        if (traceId == null && hookId == null && !mediaHooksEnabled) return delegate

        return AiDebugMethodVisitor(
            next = delegate,
            className = className,
            methodName = name,
            methodDescriptor = descriptor,
            traceMethodId = traceId,
            hookMethodId = hookId,
            returnDescriptor = descriptor.substringAfterLast(')'),
            hookAudioRecordReads = hookAudioRecordReads,
            hookAudioRecordLifecycle = hookAudioRecordLifecycle,
            hookCameraXAnalyzers = hookCameraXAnalyzers,
            hookMlKitInputImageFactories = hookMlKitInputImageFactories,
            customFrameHooks = customFrameHooks,
        )
    }
}

private class AiDebugMethodVisitor(
    next: MethodVisitor,
    private val className: String,
    private val methodName: String,
    private val methodDescriptor: String,
    private val traceMethodId: String?,
    private val hookMethodId: String?,
    private val returnDescriptor: String,
    private val hookAudioRecordReads: Boolean,
    private val hookAudioRecordLifecycle: Boolean,
    private val hookCameraXAnalyzers: Boolean,
    private val hookMlKitInputImageFactories: Boolean,
    private val customFrameHooks: Set<String>,
) : MethodVisitor(Opcodes.ASM9, next) {
    private var callInstructionIndex: Int = 0

    override fun visitCode() {
        super.visitCode()
        traceMethodId?.let(::emitTraceEnter)
        hookMethodId?.let { id ->
            when (returnDescriptor) {
                "Z" -> emitBooleanHook(id)
                "Ljava/lang/String;" -> emitStringHook(id)
            }
        }
    }

    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean,
    ) {
        val currentIndex = callInstructionIndex++
        if (hookAudioRecordReads && rewriteAudioRecordRead(opcode, owner, name, descriptor, currentIndex)) return
        if (hookAudioRecordLifecycle && rewriteAudioRecordLifecycle(opcode, owner, name, descriptor, currentIndex)) return
        if (hookCameraXAnalyzers && rewriteCameraXAnalyzer(opcode, owner, name, descriptor, currentIndex)) return
        if (hookMlKitInputImageFactories && rewriteMlKitInputImageFactory(opcode, owner, name, descriptor, currentIndex)) return
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }

    override fun visitInsn(opcode: Int) {
        if (traceMethodId != null && opcode in RETURN_OPCODES) {
            emitTraceExit(traceMethodId)
        } else if (traceMethodId != null && opcode == Opcodes.ATHROW) {
            emitTraceThrow(traceMethodId)
        }
        super.visitInsn(opcode)
    }

    private fun rewriteAudioRecordRead(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        instructionIndex: Int,
    ): Boolean {
        if (opcode != Opcodes.INVOKEVIRTUAL || owner != "android/media/AudioRecord" || name != "read") return false
        val bridgeDescriptor = when (descriptor) {
            "([BII)I" -> "(Landroid/media/AudioRecord;[BIILjava/lang/String;)I"
            "([BIII)I" -> "(Landroid/media/AudioRecord;[BIIILjava/lang/String;)I"
            "([SII)I" -> "(Landroid/media/AudioRecord;[SIILjava/lang/String;)I"
            "([SIII)I" -> "(Landroid/media/AudioRecord;[SIIILjava/lang/String;)I"
            "([FIII)I" -> "(Landroid/media/AudioRecord;[FIIILjava/lang/String;)I"
            "(Ljava/nio/ByteBuffer;I)I" -> "(Landroid/media/AudioRecord;Ljava/nio/ByteBuffer;ILjava/lang/String;)I"
            "(Ljava/nio/ByteBuffer;II)I" -> "(Landroid/media/AudioRecord;Ljava/nio/ByteBuffer;IILjava/lang/String;)I"
            else -> return false
        }
        visitLdcInsn(callSiteId("audio:audiorecord:read", instructionIndex, "AudioRecord.read$descriptor"))
        super.visitMethodInsn(Opcodes.INVOKESTATIC, MEDIA_BRIDGE_CLASS, "audioRecordRead", bridgeDescriptor, false)
        return true
    }

    private fun rewriteAudioRecordLifecycle(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        instructionIndex: Int,
    ): Boolean {
        if (opcode != Opcodes.INVOKEVIRTUAL || owner != "android/media/AudioRecord" || descriptor != "()V") return false
        val bridgeName = when (name) {
            "startRecording" -> "audioRecordStartRecording"
            "stop" -> "audioRecordStop"
            "release" -> "audioRecordRelease"
            else -> return false
        }
        visitLdcInsn(callSiteId("audio:audiorecord:$name", instructionIndex, "AudioRecord.$name()"))
        super.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            MEDIA_BRIDGE_CLASS,
            bridgeName,
            "(Landroid/media/AudioRecord;Ljava/lang/String;)V",
            false,
        )
        return true
    }

    private fun rewriteCameraXAnalyzer(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        instructionIndex: Int,
    ): Boolean {
        if (opcode != Opcodes.INVOKEVIRTUAL || owner != "androidx/camera/core/ImageAnalysis") return false
        if (name == "setAnalyzer" && descriptor == "(Ljava/util/concurrent/Executor;Landroidx/camera/core/ImageAnalysis\$Analyzer;)V") {
            visitLdcInsn(callSiteId("camera:camerax:setAnalyzer", instructionIndex, "ImageAnalysis.setAnalyzer$descriptor"))
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                MEDIA_BRIDGE_CLASS,
                "cameraXSetAnalyzer",
                "(Ljava/lang/Object;Ljava/util/concurrent/Executor;Ljava/lang/Object;Ljava/lang/String;)V",
                false,
            )
            return true
        }
        if (name == "clearAnalyzer" && descriptor == "()V") {
            visitLdcInsn(callSiteId("camera:camerax:clearAnalyzer", instructionIndex, "ImageAnalysis.clearAnalyzer()"))
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                MEDIA_BRIDGE_CLASS,
                "cameraXClearAnalyzer",
                "(Ljava/lang/Object;Ljava/lang/String;)V",
                false,
            )
            return true
        }
        return false
    }

    private fun rewriteMlKitInputImageFactory(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        instructionIndex: Int,
    ): Boolean {
        if (opcode != Opcodes.INVOKESTATIC || owner != "com/google/mlkit/vision/common/InputImage") return false
        val bridge = when (name to descriptor) {
            "fromMediaImage" to "(Landroid/media/Image;I)Lcom/google/mlkit/vision/common/InputImage;" ->
                "inputImageFromMediaImage" to "(Ljava/lang/Object;ILjava/lang/String;)Ljava/lang/Object;"
            "fromBitmap" to "(Landroid/graphics/Bitmap;I)Lcom/google/mlkit/vision/common/InputImage;" ->
                "inputImageFromBitmap" to "(Ljava/lang/Object;ILjava/lang/String;)Ljava/lang/Object;"
            "fromByteArray" to "([BIIII)Lcom/google/mlkit/vision/common/InputImage;" ->
                "inputImageFromByteArray" to "([BIIIILjava/lang/String;)Ljava/lang/Object;"
            "fromByteBuffer" to "(Ljava/nio/ByteBuffer;IIII)Lcom/google/mlkit/vision/common/InputImage;" ->
                "inputImageFromByteBuffer" to "(Ljava/nio/ByteBuffer;IIIILjava/lang/String;)Ljava/lang/Object;"
            "fromFilePath" to "(Landroid/content/Context;Landroid/net/Uri;)Lcom/google/mlkit/vision/common/InputImage;" ->
                "inputImageFromFilePath" to "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
            else -> return false
        }
        visitLdcInsn(callSiteId("camera:mlkit:inputImage:$name", instructionIndex, "InputImage.$name$descriptor"))
        super.visitMethodInsn(Opcodes.INVOKESTATIC, MEDIA_BRIDGE_CLASS, bridge.first, bridge.second, false)
        visitTypeInsn(Opcodes.CHECKCAST, "com/google/mlkit/vision/common/InputImage")
        return true
    }

    private fun callSiteId(prefix: String, instructionIndex: Int, apiSignature: String): String {
        val owner = className.replace('/', '.')
        return "$prefix:$owner#$methodName$methodDescriptor@insn$instructionIndex:$apiSignature"
    }

    private fun emitBooleanHook(hookId: String) {
        val continueLabel = Label()
        visitLdcInsn(hookId)
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            BRIDGE_CLASS,
            "hookBoolean",
            "(Ljava/lang/String;)Ljava/lang/Boolean;",
            false,
        )
        visitInsn(Opcodes.DUP)
        visitJumpInsn(Opcodes.IFNULL, continueLabel)
        traceMethodId?.let(::emitTraceExit)
        visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false)
        super.visitInsn(Opcodes.IRETURN)
        visitLabel(continueLabel)
        visitInsn(Opcodes.POP)
    }

    private fun emitStringHook(hookId: String) {
        val continueLabel = Label()
        visitLdcInsn(hookId)
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            BRIDGE_CLASS,
            "hookString",
            "(Ljava/lang/String;)Ljava/lang/String;",
            false,
        )
        visitInsn(Opcodes.DUP)
        visitJumpInsn(Opcodes.IFNULL, continueLabel)
        traceMethodId?.let(::emitTraceExit)
        super.visitInsn(Opcodes.ARETURN)
        visitLabel(continueLabel)
        visitInsn(Opcodes.POP)
    }

    private fun emitTraceEnter(traceId: String) {
        visitLdcInsn(traceId)
        visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE_CLASS, "traceEnter", "(Ljava/lang/String;)V", false)
    }

    private fun emitTraceExit(traceId: String) {
        visitLdcInsn(traceId)
        visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE_CLASS, "traceExit", "(Ljava/lang/String;)V", false)
    }

    private fun emitTraceThrow(traceId: String) {
        visitInsn(Opcodes.DUP)
        visitLdcInsn(traceId)
        visitInsn(Opcodes.SWAP)
        visitMethodInsn(
            Opcodes.INVOKESTATIC,
            BRIDGE_CLASS,
            "traceThrow",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V",
            false,
        )
    }

    private companion object {
        const val BRIDGE_CLASS = "com/riftwalker/aidebug/runtime/AiDebugHookBridge"
        const val MEDIA_BRIDGE_CLASS = "com/riftwalker/aidebug/runtime/AiDebugMediaHookBridge"
        val RETURN_OPCODES = setOf(
            Opcodes.IRETURN,
            Opcodes.LRETURN,
            Opcodes.FRETURN,
            Opcodes.DRETURN,
            Opcodes.ARETURN,
            Opcodes.RETURN,
        )
    }
}

private fun AiDebugAsmParameters.mediaHooksEnabled(): Boolean {
    return hookAudioRecordReads.get() ||
        hookAudioRecordLifecycle.get() ||
        hookCameraXAnalyzers.get() ||
        hookMlKitInputImageFactories.get() ||
        customFrameHooks.get().isNotEmpty()
}

private fun String.matchesClass(className: String): Boolean = targetClassName() == className

private fun String.matchesMethod(className: String, methodName: String): Boolean {
    return targetClassName() == className && targetMethodName() == methodName
}

private fun String.targetClassName(): String? {
    return when {
        contains("#") -> substringBefore("#")
        contains(".") -> substringBeforeLast(".")
        else -> null
    }
}

private fun String.targetMethodName(): String {
    val methodPart = when {
        contains("#") -> substringAfter("#")
        else -> substringAfterLast(".")
    }
    return methodPart.substringBefore("(")
}
