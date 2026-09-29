package com.neurasamu.build.model

import java.io.File

data class SamuModel(
    val id: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val ctx: Int = 2048,
    val threads: Int = 4
) {
    val apiName: String get() = id
}
