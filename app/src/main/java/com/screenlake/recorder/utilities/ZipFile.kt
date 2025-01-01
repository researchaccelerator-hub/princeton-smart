package com.screenlake.recorder.utilities
import android.content.Context
import android.net.Uri
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipFile {
    private val modeRead = "r"

    fun zip(zipFile: File, files: List<File>) {
        val bufferSize = 2048

        var origin: BufferedInputStream? = null
        val out = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile)))

        try {
            val data = ByteArray(bufferSize)
            for (file in files) {
                if(file.exists()) {
                    val fileInputStream = FileInputStream(file)
                    origin = BufferedInputStream(fileInputStream, bufferSize)
                    val filePath = file.absolutePath
                    try {
                        val entry = ZipEntry(filePath.substring(filePath.lastIndexOf("/") + 1))
                        out.putNextEntry(entry)
                        var count: Int
                        while (origin.read(data, 0, bufferSize).also { count = it } != -1) {
                            out.write(data, 0, count)
                        }
                    } finally {
                        origin.close()

                    }
                }
            }
        } finally {
            out.close()
        }
    }

    fun unzip(zipFile: File, location: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { inStream ->
            unzip(inStream, location)
        }
    }

    fun unzip(context: Context, zipFile: Uri, location: File) {
        context.contentResolver.openFileDescriptor(zipFile, modeRead).use { descriptor ->
            descriptor?.fileDescriptor?.let {
                ZipInputStream(BufferedInputStream(FileInputStream(it))).use { inStream ->
                    unzip(inStream, location)
                }
            }
        }
    }

    private fun unzip(inStream: ZipInputStream, location: File) {
        if (location.exists() && !location.isDirectory)
            throw IllegalStateException("Location file must be directory or not exist")

        if (!location.isDirectory) location.mkdirs()

        val locationPath = location.absolutePath.let {
            if (!it.endsWith(File.separator)) "$it${File.separator}"
            else it
        }

        var zipEntry: ZipEntry?
        var unzipFile: File
        var unzipParentDir: File?

        while (inStream.nextEntry.also { zipEntry = it } != null) {
            unzipFile = File(locationPath + zipEntry!!.name)
            if (zipEntry!!.isDirectory) {
                if (!unzipFile.isDirectory) unzipFile.mkdirs()
            } else {
                unzipParentDir = unzipFile.parentFile
                if (unzipParentDir != null && !unzipParentDir.isDirectory) {
                    unzipParentDir.mkdirs()
                }
                BufferedOutputStream(FileOutputStream(unzipFile)).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }
        }
    }
}