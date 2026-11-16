package fr.ibdjournal.app.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * Analyse CameraX des codes-barres alimentaires avec ZXing (plan Y du YUV_420_888).
 * [onBarcode] est appelé depuis le thread d'analyse.
 */
class BarcodeAnalyzer(private val onBarcode: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.EAN_13,
                    BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A,
                    BarcodeFormat.UPC_E,
                ),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val crop = proxy.cropRect
            val width = crop.width()
            val height = crop.height()
            val luma = extractLuma(proxy, crop.left, crop.top, width, height)
            // Code lu tel quel puis pivoté de 90° (téléphone tenu en portrait).
            val text = decode(luma, width, height)
                ?: decode(rotate90(luma, width, height), height, width)
            if (text != null) onBarcode(text)
        }
    }

    /** Copie la zone recadrée du plan Y en tenant compte du rowStride/pixelStride. */
    private fun extractLuma(proxy: ImageProxy, left: Int, top: Int, width: Int, height: Int): ByteArray {
        val plane = proxy.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val out = ByteArray(width * height)
        for (y in 0 until height) {
            val rowStart = (top + y) * rowStride + left * pixelStride
            if (pixelStride == 1) {
                buffer.position(rowStart)
                buffer.get(out, y * width, width)
            } else {
                for (x in 0 until width) out[y * width + x] = buffer.get(rowStart + x * pixelStride)
            }
        }
        return out
    }

    /** Rotation horaire de 90° : l'image résultante fait height × width. */
    private fun rotate90(src: ByteArray, width: Int, height: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) out[x * height + (height - 1 - y)] = src[y * width + x]
        }
        return out
    }

    private fun decode(luma: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: NotFoundException) {
            null
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
