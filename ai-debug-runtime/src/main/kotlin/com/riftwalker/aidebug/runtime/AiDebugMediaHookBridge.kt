package com.riftwalker.aidebug.runtime

import android.media.AudioRecord
import com.riftwalker.aidebug.runtime.media.MediaController
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.Executor

object AiDebugMediaHookBridge {
    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ByteArray,
        offset: Int,
        size: Int,
        callSiteId: String,
    ): Int {
        val requestedBytes = size.coerceAtLeast(0)
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(byte[], int, int)",
            overload = "byte[]",
            requestedBytes = requestedBytes,
        )
        if (replacement != null) {
            val copied = copyBytes(replacement.bytes, buffer, offset, requestedBytes)
            return copied
        }
        val read = record.read(buffer, offset, size)
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(byte[], int, int)",
            overload = "byte[]",
            requestedBytes = requestedBytes,
            returnedBytes = read,
        )
        return read
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ByteArray,
        offset: Int,
        size: Int,
        readMode: Int,
        callSiteId: String,
    ): Int {
        val requestedBytes = size.coerceAtLeast(0)
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(byte[], int, int, int)",
            overload = "byte[]",
            requestedBytes = requestedBytes,
        )
        if (replacement != null) return copyBytes(replacement.bytes, buffer, offset, requestedBytes)
        val read = record.read(buffer, offset, size, readMode)
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(byte[], int, int, int)",
            overload = "byte[]",
            requestedBytes = requestedBytes,
            returnedBytes = read,
        )
        return read
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ShortArray,
        offset: Int,
        size: Int,
        callSiteId: String,
    ): Int {
        val requestedBytes = size.coerceAtLeast(0) * Short.SIZE_BYTES
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(short[], int, int)",
            overload = "short[]",
            requestedBytes = requestedBytes,
        )
        if (replacement != null) return copyShorts(replacement.bytes, buffer, offset, size.coerceAtLeast(0))
        val read = record.read(buffer, offset, size)
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(short[], int, int)",
            overload = "short[]",
            requestedBytes = requestedBytes,
            returnedBytes = read.toBytes(Short.SIZE_BYTES),
        )
        return read
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ShortArray,
        offset: Int,
        size: Int,
        readMode: Int,
        callSiteId: String,
    ): Int {
        val requestedBytes = size.coerceAtLeast(0) * Short.SIZE_BYTES
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(short[], int, int, int)",
            overload = "short[]",
            requestedBytes = requestedBytes,
        )
        if (replacement != null) return copyShorts(replacement.bytes, buffer, offset, size.coerceAtLeast(0))
        val read = record.read(buffer, offset, size, readMode)
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(short[], int, int, int)",
            overload = "short[]",
            requestedBytes = requestedBytes,
            returnedBytes = read.toBytes(Short.SIZE_BYTES),
        )
        return read
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: FloatArray,
        offset: Int,
        size: Int,
        readMode: Int,
        callSiteId: String,
    ): Int {
        val requestedBytes = size.coerceAtLeast(0) * Float.SIZE_BYTES
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(float[], int, int, int)",
            overload = "float[]",
            requestedBytes = requestedBytes,
        )
        if (replacement != null) return copyFloats(replacement.bytes, buffer, offset, size.coerceAtLeast(0))
        val read = record.read(buffer, offset, size, readMode)
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.read(float[], int, int, int)",
            overload = "float[]",
            requestedBytes = requestedBytes,
            returnedBytes = read.toBytes(Float.SIZE_BYTES),
        )
        return read
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ByteBuffer,
        size: Int,
        callSiteId: String,
    ): Int {
        return audioRecordReadByteBuffer(
            record = record,
            buffer = buffer,
            size = size,
            readMode = null,
            callSiteId = callSiteId,
        )
    }

    @JvmStatic
    fun audioRecordRead(
        record: AudioRecord,
        buffer: ByteBuffer,
        size: Int,
        readMode: Int,
        callSiteId: String,
    ): Int {
        return audioRecordReadByteBuffer(
            record = record,
            buffer = buffer,
            size = size,
            readMode = readMode,
            callSiteId = callSiteId,
        )
    }

    @JvmStatic
    fun audioRecordStartRecording(record: AudioRecord, callSiteId: String) {
        AiDebugRuntime.mediaController().recordAudioLifecycle(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.startRecording()",
            event = "startRecording",
        )
        record.startRecording()
    }

    @JvmStatic
    fun audioRecordStop(record: AudioRecord, callSiteId: String) {
        AiDebugRuntime.mediaController().recordAudioLifecycle(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.stop()",
            event = "stop",
        )
        record.stop()
    }

    @JvmStatic
    fun audioRecordRelease(record: AudioRecord, callSiteId: String) {
        AiDebugRuntime.mediaController().recordAudioLifecycle(
            callSiteId = callSiteId,
            apiSignature = "AudioRecord.release()",
            event = "release",
        )
        record.release()
    }

    @JvmStatic
    fun cameraXSetAnalyzer(
        imageAnalysis: Any,
        executor: Executor,
        analyzer: Any,
        callSiteId: String,
    ) {
        AiDebugRuntime.mediaController().registerTarget(
            kind = MediaController.KIND_CAMERA_ANALYZER,
            callSiteId = callSiteId,
            apiSignature = "ImageAnalysis.setAnalyzer(Executor, Analyzer)",
        )
        val wrapped = wrapAnalyzer(analyzer, callSiteId)
        invokeInstance(
            receiver = imageAnalysis,
            name = "setAnalyzer",
            parameterCount = 2,
            args = arrayOf(executor, wrapped),
        )
    }

    @JvmStatic
    fun cameraXClearAnalyzer(imageAnalysis: Any, callSiteId: String) {
        AiDebugRuntime.mediaController().registerTarget(
            kind = MediaController.KIND_CAMERA_ANALYZER,
            callSiteId = callSiteId,
            apiSignature = "ImageAnalysis.clearAnalyzer()",
        )
        invokeInstance(
            receiver = imageAnalysis,
            name = "clearAnalyzer",
            parameterCount = 0,
            args = emptyArray(),
        )
    }

    @JvmStatic
    fun inputImageFromMediaImage(image: Any, rotationDegrees: Int, callSiteId: String): Any {
        val observed = mapOf("rotationDegrees" to JsonPrimitive(rotationDegrees))
        val fixture = AiDebugRuntime.mediaController().consumeCameraFrame(
            kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
            callSiteId = callSiteId,
            apiSignature = "InputImage.fromMediaImage(Image, int)",
            mode = "mlkit_input_image",
            observed = observed,
        )
        return fixtureToInputImage(fixture, image.javaClass.classLoader)
            ?: invokeInputImageFactory("fromMediaImage", arrayOf(classFor("android.media.Image"), Int::class.javaPrimitiveType!!), image, rotationDegrees)
    }

    @JvmStatic
    fun inputImageFromBitmap(bitmap: Any, rotationDegrees: Int, callSiteId: String): Any {
        val observed = mapOf("rotationDegrees" to JsonPrimitive(rotationDegrees))
        val fixture = AiDebugRuntime.mediaController().consumeCameraFrame(
            kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
            callSiteId = callSiteId,
            apiSignature = "InputImage.fromBitmap(Bitmap, int)",
            mode = "mlkit_input_image",
            observed = observed,
        )
        return fixtureToInputImage(fixture, bitmap.javaClass.classLoader)
            ?: invokeInputImageFactory("fromBitmap", arrayOf(classFor("android.graphics.Bitmap"), Int::class.javaPrimitiveType!!), bitmap, rotationDegrees)
    }

    @JvmStatic
    fun inputImageFromByteArray(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        format: Int,
        callSiteId: String,
    ): Any {
        val observed = mapOf(
            "width" to JsonPrimitive(width),
            "height" to JsonPrimitive(height),
            "rotationDegrees" to JsonPrimitive(rotationDegrees),
            "format" to JsonPrimitive(format),
        )
        val fixture = AiDebugRuntime.mediaController().consumeCameraFrame(
            kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
            callSiteId = callSiteId,
            apiSignature = "InputImage.fromByteArray(byte[], int, int, int, int)",
            mode = "mlkit_input_image",
            observed = observed,
        )
        return fixtureToInputImage(fixture, bytes.javaClass.classLoader)
            ?: invokeInputImageFactory(
                "fromByteArray",
                arrayOf(ByteArray::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                bytes,
                width,
                height,
                rotationDegrees,
                format,
            )
    }

    @JvmStatic
    fun inputImageFromByteBuffer(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rotationDegrees: Int,
        format: Int,
        callSiteId: String,
    ): Any {
        val observed = mapOf(
            "width" to JsonPrimitive(width),
            "height" to JsonPrimitive(height),
            "rotationDegrees" to JsonPrimitive(rotationDegrees),
            "format" to JsonPrimitive(format),
        )
        val fixture = AiDebugRuntime.mediaController().consumeCameraFrame(
            kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
            callSiteId = callSiteId,
            apiSignature = "InputImage.fromByteBuffer(ByteBuffer, int, int, int, int)",
            mode = "mlkit_input_image",
            observed = observed,
        )
        return fixtureToInputImage(fixture, buffer.javaClass.classLoader)
            ?: invokeInputImageFactory(
                "fromByteBuffer",
                arrayOf(ByteBuffer::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
                buffer,
                width,
                height,
                rotationDegrees,
                format,
            )
    }

    @JvmStatic
    fun inputImageFromFilePath(context: Any, uri: Any, callSiteId: String): Any {
        val fixture = AiDebugRuntime.mediaController().consumeCameraFrame(
            kind = MediaController.KIND_MLKIT_INPUT_IMAGE,
            callSiteId = callSiteId,
            apiSignature = "InputImage.fromFilePath(Context, Uri)",
            mode = "mlkit_input_image",
            observed = emptyMap(),
        )
        return fixtureToInputImage(fixture, context.javaClass.classLoader)
            ?: invokeInputImageFactory("fromFilePath", arrayOf(classFor("android.content.Context"), classFor("android.net.Uri")), context, uri)
    }

    private fun audioRecordReadByteBuffer(
        record: AudioRecord,
        buffer: ByteBuffer,
        size: Int,
        readMode: Int?,
        callSiteId: String,
    ): Int {
        val apiSignature = if (readMode == null) {
            "AudioRecord.read(ByteBuffer, int)"
        } else {
            "AudioRecord.read(ByteBuffer, int, int)"
        }
        val requestedBytes = size.coerceAtLeast(0)
        if (!buffer.isDirect) {
            AiDebugRuntime.mediaController().recordAudioFallback(
                callSiteId = callSiteId,
                apiSignature = apiSignature,
                overload = "ByteBuffer",
                requestedBytes = requestedBytes,
                returnedBytes = 0,
            )
            return 0
        }
        val replacement = AiDebugRuntime.mediaController().consumeAudioBytes(
            callSiteId = callSiteId,
            apiSignature = apiSignature,
            overload = "ByteBuffer",
            requestedBytes = minOf(requestedBytes, buffer.capacity()),
        )
        if (replacement != null) {
            val copied = minOf(replacement.bytes.size, requestedBytes, buffer.capacity())
            repeat(copied) { index -> buffer.put(index, replacement.bytes[index]) }
            return copied
        }
        val read = if (readMode == null) {
            record.read(buffer, size)
        } else {
            record.read(buffer, size, readMode)
        }
        AiDebugRuntime.mediaController().recordAudioFallback(
            callSiteId = callSiteId,
            apiSignature = apiSignature,
            overload = "ByteBuffer",
            requestedBytes = requestedBytes,
            returnedBytes = read,
        )
        return read
    }

    private fun wrapAnalyzer(delegate: Any, callSiteId: String): Any {
        val analyzerInterface = runCatching {
            classFor("androidx.camera.core.ImageAnalysis\$Analyzer", delegate.javaClass.classLoader)
        }.getOrNull() ?: return delegate
        if (!analyzerInterface.isInterface || !analyzerInterface.isInstance(delegate)) return delegate
        return Proxy.newProxyInstance(
            delegate.javaClass.classLoader,
            arrayOf(analyzerInterface),
        ) { _, method, args ->
            if (method.name == "analyze" && args?.size == 1) {
                val imageProxy = args[0]
                val observed = observeImageProxy(imageProxy)
                AiDebugRuntime.mediaController().consumeCameraFrame(
                    kind = MediaController.KIND_CAMERA_ANALYZER,
                    callSiteId = callSiteId,
                    apiSignature = "ImageAnalysis.Analyzer.analyze(ImageProxy)",
                    mode = "replace_on_real_frame",
                    observed = observed,
                )
            }
            invokeReflective(method, delegate, args ?: emptyArray())
        }
    }

    private fun observeImageProxy(imageProxy: Any?): Map<String, JsonElement> {
        if (imageProxy == null) return emptyMap()
        val values = linkedMapOf<String, JsonElement>()
        callGetter(imageProxy, "getWidth")?.let { values["width"] = JsonPrimitive((it as Number).toInt()) }
        callGetter(imageProxy, "getHeight")?.let { values["height"] = JsonPrimitive((it as Number).toInt()) }
        callGetter(imageProxy, "getFormat")?.let { values["format"] = JsonPrimitive((it as Number).toInt()) }
        val imageInfo = callGetter(imageProxy, "getImageInfo")
        if (imageInfo != null) {
            callGetter(imageInfo, "getRotationDegrees")?.let { values["rotationDegrees"] = JsonPrimitive((it as Number).toInt()) }
            callGetter(imageInfo, "getTimestamp")?.let { values["timestamp"] = JsonPrimitive((it as Number).toLong()) }
        }
        return values
    }

    private fun fixtureToInputImage(fixture: com.riftwalker.aidebug.protocol.MediaFixture?, classLoader: ClassLoader?): Any? {
        if (fixture == null || fixture.mimeType != "application/x-nv21") return null
        val width = fixture.metadata.int("width") ?: return null
        val height = fixture.metadata.int("height") ?: return null
        val rotation = fixture.metadata.int("rotationDegrees") ?: 0
        val inputImageClass = classFor("com.google.mlkit.vision.common.InputImage", classLoader)
        val format = inputImageClass.getField("IMAGE_FORMAT_NV21").getInt(null)
        val bytes = AiDebugRuntime.mediaController().fixtureBytes(fixture)
        return invokeInputImageFactory(
            name = "fromByteArray",
            parameterTypes = arrayOf(ByteArray::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            bytes,
            width,
            height,
            rotation,
            format,
        )
    }

    private fun invokeInputImageFactory(name: String, parameterTypes: Array<Class<*>>, vararg args: Any): Any {
        val inputImageClass = classFor("com.google.mlkit.vision.common.InputImage", args.firstOrNull()?.javaClass?.classLoader)
        val method = inputImageClass.getMethod(name, *parameterTypes)
        return invokeReflective(method, null, args)
            ?: error("InputImage.$name returned null")
    }

    private fun invokeInstance(receiver: Any, name: String, parameterCount: Int, args: Array<Any>) {
        val method = receiver.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.size == parameterCount }
            ?: error("Method not found: ${receiver.javaClass.name}.$name/$parameterCount")
        invokeReflective(method, receiver, args)
    }

    private fun invokeReflective(method: Method, receiver: Any?, args: Array<out Any>): Any? {
        return try {
            method.invoke(receiver, *args)
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private fun callGetter(receiver: Any, name: String): Any? {
        return runCatching {
            receiver.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
                ?.let { invokeReflective(it, receiver, emptyArray()) }
        }.getOrNull()
    }

    private fun classFor(name: String, classLoader: ClassLoader? = null): Class<*> {
        return Class.forName(
            name,
            false,
            classLoader ?: AiDebugMediaHookBridge::class.java.classLoader ?: Thread.currentThread().contextClassLoader,
        )
    }

    private fun Map<String, JsonElement>.int(name: String): Int? {
        return this[name]?.jsonPrimitive?.intOrNull
    }

    private fun copyBytes(bytes: ByteArray, buffer: ByteArray, offset: Int, requestedBytes: Int): Int {
        val copied = minOf(bytes.size, requestedBytes, buffer.size - offset).coerceAtLeast(0)
        if (copied > 0) {
            System.arraycopy(bytes, 0, buffer, offset, copied)
        }
        return copied
    }

    private fun copyShorts(bytes: ByteArray, buffer: ShortArray, offset: Int, requestedShorts: Int): Int {
        val copiedShorts = minOf(bytes.size / Short.SIZE_BYTES, requestedShorts, buffer.size - offset).coerceAtLeast(0)
        repeat(copiedShorts) { index ->
            val byteIndex = index * Short.SIZE_BYTES
            buffer[offset + index] = (
                (bytes[byteIndex].toInt() and 0xff) or
                    ((bytes[byteIndex + 1].toInt() and 0xff) shl 8)
                ).toShort()
        }
        return copiedShorts
    }

    private fun copyFloats(bytes: ByteArray, buffer: FloatArray, offset: Int, requestedFloats: Int): Int {
        val copiedFloats = minOf(bytes.size / Float.SIZE_BYTES, requestedFloats, buffer.size - offset).coerceAtLeast(0)
        repeat(copiedFloats) { index ->
            val byteIndex = index * Float.SIZE_BYTES
            val bits = (bytes[byteIndex].toInt() and 0xff) or
                ((bytes[byteIndex + 1].toInt() and 0xff) shl 8) or
                ((bytes[byteIndex + 2].toInt() and 0xff) shl 16) or
                ((bytes[byteIndex + 3].toInt() and 0xff) shl 24)
            buffer[offset + index] = Float.fromBits(bits)
        }
        return copiedFloats
    }

    private fun Int.toBytes(bytesPerUnit: Int): Int {
        return if (this > 0) this * bytesPerUnit else this
    }
}
