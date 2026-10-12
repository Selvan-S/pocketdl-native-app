package com.pocketdl.app.core.storage

import java.io.File

fun interface StorageSpaceChecker {
    fun getUsableSpace(directory: File): Long
}

class DefaultStorageSpaceChecker : StorageSpaceChecker {
    override fun getUsableSpace(directory: File): Long {
        var dir: File? = directory
        while (dir != null && !dir.exists()) {
            dir = dir.parentFile
        }
        return dir?.usableSpace ?: File(".").usableSpace
    }
}
