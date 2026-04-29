package com.riftwalker.aidebug.annotations

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class AiState(
    val path: String = "",
    val type: String = "string",
    val description: String = "",
)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class AiAction(
    val path: String = "",
    val description: String = "",
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class AiSetter(
    val path: String = "",
)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class AiProbe(
    val id: String = "",
    val description: String = "",
)
