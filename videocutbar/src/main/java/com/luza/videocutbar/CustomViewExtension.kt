package com.luza.videocutbar

import android.graphics.*
import android.util.Log
import kotlinx.coroutines.*
import java.math.BigDecimal


fun RectF.set(l: Number, t: Number, r: Number, b: Number) {
    set(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat())
}

fun Rect.set(l: Number, t: Number, r: Number, b: Number) {
    set(l.toInt(), t.toInt(), r.toInt(), b.toInt())
}

fun RectF.setCenter(cX: Number, cY: Number, sizeX: Number, sizeY: Number) {
    val centerX = cX.toFloat()
    val centerY = cY.toFloat()
    val width = sizeX.toFloat()
    val height = sizeY.toFloat()
    set(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f)
}

fun RectF.setCenterX(cX: Number, sizeX: Number) {
    val centerX = cX.toFloat()
    val width = sizeX.toFloat()
    set(centerX - width / 2f, top, centerX + width / 2f, bottom)
}

fun Rect.setCenter(cX: Number, cY: Number, sizeX: Number, sizeY: Number) {
    val centerX = cX.toInt()
    val centerY = cY.toInt()
    val width = sizeX.toInt()
    val height = sizeY.toInt()
    set(centerX - width / 2f, centerY - height / 2, centerX + width / 2, centerY + height / 2)
}

var isLogEnable = true
val TAG_LOG = "VIEW_LOG"
fun eLog(message: String) {
    TAG_LOG.eLog(message)
}

fun String.eLog(message: String) {
    if (isLogEnable)
        Log.e(this, message)
}

fun Float.abs(): Float {
    return kotlin.math.abs(this)
}

fun Int.abs(): Int {
    return kotlin.math.abs(this)
}

/**Loading
 **/

private fun <T> async(
    doIn: (scopeDoIn: CoroutineScope) -> T,
    doOut: (T) -> Unit = {},
    dispatcherIn: CoroutineDispatcher = Dispatchers.IO,
    dispathcherOut: CoroutineDispatcher = Dispatchers.Unconfined
): Job {
    return GlobalScope.launch(dispatcherIn) {
        val data = doIn(this)
        withContext(dispathcherOut) {
            doOut(data)
        }
    }
}

private var jobLoading: Job? = null
fun <T> doJob(
    doIn: (scopeDoIn: CoroutineScope) -> T,
    doOut: (T) -> Unit = {},
    dispatcherIn: CoroutineDispatcher = Dispatchers.IO,
    dispathcherOut: CoroutineDispatcher = Dispatchers.Unconfined
) {
    jobLoading = async(doIn, doOut, dispatcherIn, dispathcherOut)
}

fun cancelLoading() {
    jobLoading?.cancel()
}

fun Bitmap.scaleBitmap(width: Number, height: Number): Bitmap {
    val background = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)

    val originalWidth: Float = getWidth().toFloat()
    val originalHeight: Float = getHeight().toFloat()

    val canvas = Canvas(background)

    val scale = width.toInt() / originalWidth

    val xTranslation = 0.0f
    val yTranslation = (height.toInt() - originalHeight * scale) / 2.0f

    val transformation = Matrix()
    transformation.postTranslate(xTranslation, yTranslation)
    transformation.preScale(scale, scale)

    val paint = Paint()
    paint.isFilterBitmap = true

    canvas.drawBitmap(this, transformation, paint)

    return background
}

fun Float.scale(numberDigitsAfterComma:Int):Float{
    return BigDecimal(this.toDouble()).setScale(numberDigitsAfterComma, BigDecimal.ROUND_HALF_EVEN).toFloat()
}

