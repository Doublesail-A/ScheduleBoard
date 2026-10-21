package com.doublesail.scheduleboard.school

import android.content.Context
import android.graphics.BitmapFactory
import java.io.IOException

/** iFAFU's local 34-class model and pixel preprocessing. See THIRD-PARTY-NOTICES.md. */
class SchoolCaptchaRecognizer(context: Context) : SchoolCaptchaDecoder {
    private val assets = context.applicationContext.assets
    private val weights by lazy {
        val values = assets.open("theta.dat").bufferedReader().use { it.readText() }
            .trim().split(Regex("\\s+")).map(String::toDouble)
        check(values.size == 34 * 337 && values.all(Double::isFinite)) { "验证码模型损坏" }
        Array(34) { row -> DoubleArray(337) { column -> values[row * 337 + column] } }
    }

    override fun recognize(image: ByteArray): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        if (bounds.outWidth !in 30..200 || bounds.outHeight !in 7..100)
            throw IOException("教务未返回有效验证码图片，请检查网络")
        val bitmap = BitmapFactory.decodeByteArray(image, 0, image.size)
            ?: throw IOException("教务验证码无法读取")
        try {
            val piece = (bitmap.width - 22) / 8
            if ((2 * piece + 4) * (bitmap.height - 6) != 336)
                throw IOException("教务验证码样式已变化，需要更新识别器")
            return (0..3).map { letter ->
                val input = DoubleArray(337).apply { this[0] = 1.0 }
                val center = 4 + piece * (2 * letter + 1)
                var offset = 1
                for (y in 1 until bitmap.height - 5) {
                    for (x in center - (piece + 2) until center + (piece + 2)) {
                        val pixel = bitmap.getPixel(x, y)
                        val grey = (((pixel shr 16) and 255) * 30 + ((pixel shr 8) and 255) * 59 +
                            (pixel and 255) * 11 + 50) / 100
                        input[offset++] = grey / 255.0
                    }
                }
                // Sigmoid is monotonic, so the largest dot product has the same winning class.
                val winner = weights.indices.maxBy { row -> input.indices.sumOf { input[it] * weights[row][it] } }
                (if (winner <= 9) winner + 48 else if (winner <= 23) winner + 87 else winner + 88).toChar()
            }.joinToString("")
        } finally { bitmap.recycle() }
    }
}
