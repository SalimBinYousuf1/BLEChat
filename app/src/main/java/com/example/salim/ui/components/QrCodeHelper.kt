package com.example.salim.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrCodeHelper {
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        darkColor: Color = Color.Black,
        lightColor: Color = Color.White
    ): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val darkArgb = darkColor.toArgb()
        val lightArgb = lightColor.toArgb()

        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) darkArgb else lightArgb)
            }
        }
        return bitmap
    }
}
