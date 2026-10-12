package vn.edu.actvn.kstudy.ui.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

class QrView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var qrCodeText: String = "KSTUDY"
    private var bitMatrix: BitMatrix? = encodeQrCode(qrCodeText)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setQrCodeText(text: String) {
        qrCodeText = text
        bitMatrix = encodeQrCode(text)
        invalidate()
    }

    private fun encodeQrCode(text: String): BitMatrix? {
        return try {
            // Size 0 asks for the smallest matrix, one cell per QR module (about 30 x 30), which
            // onDraw scales up. A 512 x 512 matrix meant over 100 000 rectangles per frame and
            // froze the screen for seconds every time the rotating code changed.
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
        } catch (e: Exception) {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paint.color = Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val matrix = bitMatrix ?: return
        val size = width.coerceAtMost(height)
        if (size <= 0) return

        val cellSize = size.toFloat() / matrix.width.coerceAtLeast(matrix.height)
        val xOffset = (width - matrix.width * cellSize) / 2f
        val yOffset = (height - matrix.height * cellSize) / 2f

        paint.color = Color.BLACK
        for (row in 0 until matrix.height) {
            for (col in 0 until matrix.width) {
                if (matrix.get(col, row)) {
                    // Edges are snapped to whole pixels so neighbouring cells meet without a hairline gap
                    val left = (xOffset + col * cellSize).roundToInt().toFloat()
                    val top = (yOffset + row * cellSize).roundToInt().toFloat()
                    val right = (xOffset + (col + 1) * cellSize).roundToInt().toFloat()
                    val bottom = (yOffset + (row + 1) * cellSize).roundToInt().toFloat()
                    canvas.drawRect(left, top, right, bottom, paint)
                }
            }
        }
    }
}
