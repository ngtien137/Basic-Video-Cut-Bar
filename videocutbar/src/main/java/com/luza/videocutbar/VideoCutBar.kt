package com.luza.videocutbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.Format
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class VideoCutBar @JvmOverloads constructor(
    context: Context, var attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private val DEF_COLOR_SPREAD = Color.parseColor("#70ffffff")
        private val DEF_COLOR_THUMB_OVERLAY = Color.parseColor("#90000000")
        private const val DEF_COLOR_THUMB_CUT_SHADOW = Color.GRAY
        const val DEF_NUMBER_PREVIEW_IMAGE = 8
        const val THUMB_LEFT = 0
        const val THUMB_RIGHT = 1
        const val THUMB_NONE = -1
        const val EXTRA_HEIGHT_MULTIPLY = 1
    }

    private var viewWidth = 0
    private var viewHeight = 0
    private var videoBarHeight = 0
    private var videoBarWidth = 0
    private var imageWidth = 0f
    private var imagePaddingVertical = 0f
    private var imagePaddingHorizontal = 0f
    private var barCorners = 0f

    private var lastFocusThumbIndex = THUMB_NONE

    var oldIndexLeft = 0f
    var oldIndexRight = 0f

    var videoPath: String = ""
        set(value) {
            field = value
            setPath()
        }

    var duration = 100f
        set(value) {
            field = value
            maxProgress = value
            if (minProgress > maxProgress)
                minProgress = 0f
        }
    var progress = 0f
        private set
    var maxProgress = 0f
        private set
    var minProgress = 0f
        private set
    private val pointDown = PointF(0f, 0f)
    private var isThumbMoving = false
    var thumbIndex = THUMB_NONE
        private set

    private var drawableThumbLeft: Drawable? = null
    private var drawableThumbRight: Drawable? = null
    private var listBitmap: ArrayList<Bitmap> = ArrayList()
    private var bitmapBar: Bitmap? = null

    private val paintImage = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumbCut = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumbOverlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintProgressThumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintProgressThumbSpread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEF_COLOR_SPREAD
    }
    private val paintIndicator = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val rectView = RectF()
    private val rectThumbLeft = Rect()
    private val rectThumbRight = Rect()
    private val rectThumbProgress = RectF()
    private val rectThumbProgressSpread = RectF()
    private val rectOverlayLeft = RectF()
    private val rectOverlayRight = RectF()
    private val rectCutBar = RectF()
    private val rectImages = RectF()
    private val rectIndicator = Rect()

    //number of preview image in video bar
    private var numberPreviewImage = DEF_NUMBER_PREVIEW_IMAGE

    //thumb center (thumb progress)'s width
    private var thumbProgressWidth = 0
    private var thumbProgressSpreadWidth = 0
    private var thumbProgressHeight = 0
    private var thumbProgressCorners = 0
    private var thumbProgressSpreadCorners = 0

    //thumb cut width
    private var thumbWidth = 0
    private var thumbHeight = 0

    //extra touch area for thumb cut
    private var thumbCutShadowRadius = 0f
    private var touchAreaExtra = 0

    //The min value between two cut thumb, unit: miliseconds
    private var minCutProgress = 0f

    var isInteract = false  //Check if user moving thumb or setting thumb progress by code

    //Show two thumb cut or not
    var showThumbCut = true

    //Show thumb of progress (thumb center)
    private var showThumbProgress = true

    //isLoading video path to view rotateListener
    var loadingListener: ILoadingListener? = null

    //moving thumb cut rotateListener
    var rangeChangeListener: OnCutRangeChangeListener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var textIndicatorSpacing = 0f
    private var indicatorMode = IndicatorMode.HIDDEN
    private var indicatorPosition = IndicatorPosition.TOP
    private var indicatorFormat: Format? = null

    init {
        initView(attrs)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        var minHeight =
            max(max(thumbHeight, videoBarHeight), thumbProgressHeight) + paddingTop + paddingBottom
        if (paintIndicator.textSize > 0f && indicatorMode != IndicatorMode.HIDDEN) {
            paintIndicator.getTextBounds("1", 0, 1, rectIndicator)
            minHeight += (textIndicatorSpacing + rectIndicator.height() * EXTRA_HEIGHT_MULTIPLY).roundToInt()
        }
        viewHeight = measureDimension(minHeight, heightMeasureSpec)
        setMeasuredDimension(viewWidth, viewHeight)
    }

    private fun measureDimension(desiredSize: Int, measureSpec: Int): Int {
        var result: Int
        val specMode = MeasureSpec.getMode(measureSpec)
        val specSize = MeasureSpec.getSize(measureSpec)
        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize
        } else {
            result = desiredSize
            if (specMode == MeasureSpec.AT_MOST) {
                result = kotlin.math.min(result, specSize)
            }
        }
        //if (result < desiredSize) {
        //eLog("The view is too small, the content might get cut")
        //}
        return result
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectView.left = 0f + paddingLeft
        rectView.top = 0f + paddingTop
        rectView.bottom = (viewHeight - paddingBottom).toFloat()
        rectView.right = (viewWidth - paddingRight).toFloat()
        videoBarWidth = (rectView.width() - thumbWidth * 2 - imagePaddingHorizontal - barCorners*2).toInt()
        imageWidth = videoBarWidth.toFloat() / numberPreviewImage

        var barSpacingVertical = (rectView.height() - videoBarHeight) / 2f
        rectCutBar.set(
            rectView.left + thumbWidth,
            rectView.top + barSpacingVertical,
            rectView.right - thumbWidth,
            rectView.bottom - barSpacingVertical
        )

        if (paintIndicator.textSize > 0f && indicatorMode != IndicatorMode.HIDDEN) {
            barSpacingVertical =
                (rectView.height() - videoBarHeight - textIndicatorSpacing - rectIndicator.height() * EXTRA_HEIGHT_MULTIPLY) / 2f
            if (indicatorPosition == IndicatorPosition.BOTTOM) {
                rectCutBar.top = rectView.top + barSpacingVertical
                rectCutBar.bottom = rectCutBar.top + videoBarHeight
            } else {
                rectCutBar.bottom = rectView.bottom - barSpacingVertical
                rectCutBar.top = rectCutBar.bottom - videoBarHeight
            }
        }

        rectImages.set(
            rectCutBar.left + imagePaddingHorizontal / 2f,
            rectCutBar.top + imagePaddingVertical / 2f,
            rectCutBar.right - imagePaddingHorizontal / 2f,
            rectCutBar.bottom - imagePaddingVertical / 2f
        )

        val thumbTop = rectCutBar.centerY() - thumbHeight / 2f
        val thumbBottom = rectCutBar.centerY() + thumbHeight / 2f
        rectThumbLeft.setThumbVerticalSize(thumbTop, thumbBottom)
        rectThumbRight.setThumbVerticalSize(thumbTop, thumbBottom)
        invalidateCutBarWithRange()
    }

    private fun Rect.setThumbVerticalSize(top: Number, bottom: Number) {
        this.top = top.toInt()
        this.bottom = bottom.toInt()
    }

    private fun invalidateOverlayType() {
        rectOverlayLeft.set(
            rectCutBar.left,
            rectCutBar.top,
            rectThumbLeft.centerX().toFloat(),
            rectCutBar.bottom
        )
        rectOverlayRight.set(
            rectThumbRight.centerX().toFloat(),
            rectCutBar.top,
            rectCutBar.right,
            rectCutBar.bottom
        )
    }

    private fun invalidateCutBarWithRange() {
        rectThumbLeft.left = minProgress.ToDimensionPosition().toInt() - thumbWidth
        rectThumbLeft.right = rectThumbLeft.left + thumbWidth
        rectThumbRight.left = maxProgress.ToDimensionPosition().toInt()
        rectThumbRight.right = rectThumbRight.left + thumbWidth
    }

    private fun invalidateCenterThumbWithProgress() {
        val thumbLeft = progress.ToDimensionPosition() - thumbProgressWidth / 2f
        rectThumbProgress.set(
            thumbLeft,
            rectCutBar.centerY() - thumbProgressHeight / 2f,
            thumbLeft + thumbProgressWidth,
            rectCutBar.centerY() + thumbProgressHeight / 2f
        )
        rectThumbProgressSpread.setCenter(
            rectThumbProgress.centerX(),
            rectThumbProgress.centerY(),
            thumbProgressSpreadWidth,
            thumbProgressHeight
        )
    }

    private fun Drawable.drawAt(rect: Rect, canvas: Canvas) {
        setBounds(rect.left, rect.top, rect.right, rect.bottom)
        draw(canvas)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.let {
            canvas.drawRoundRect(rectCutBar, barCorners, barCorners, paintImage)
            bitmapBar?.let {
                canvas.drawBitmap(it, null, rectImages, paintImage)
            }

            if (showThumbProgress) {
                invalidateCenterThumbWithProgress()
                canvas.drawRoundRect(
                    rectThumbProgressSpread,
                    thumbProgressSpreadCorners.toFloat(),
                    thumbProgressSpreadCorners.toFloat(),
                    paintProgressThumbSpread
                )
                canvas.drawRoundRect(
                    rectThumbProgress,
                    thumbProgressCorners.toFloat(),
                    thumbProgressCorners.toFloat(),
                    paintProgressThumb
                )
            }

            if (showThumbCut) {
                invalidateOverlayType()
                canvas.drawRoundRect(rectOverlayLeft, barCorners, barCorners, paintThumbOverlay)
                canvas.drawRoundRect(rectOverlayRight, barCorners, barCorners, paintThumbOverlay)
                canvas.drawRect(rectThumbLeft, paintThumbCut)
                canvas.drawRect(rectThumbRight, paintThumbCut)
                drawableThumbLeft?.drawAt(rectThumbLeft, canvas)
                drawableThumbRight?.drawAt(rectThumbRight, canvas)
            }

            if (paintIndicator.textSize > 0f) {
                var textLeft = minProgress.toString()
                var textRight = maxProgress.toString()
                if (indicatorFormat != null) {
                    textLeft = indicatorFormat!!.format(minProgress.toLong())
                    textRight = indicatorFormat!!.format(maxProgress.toLong())
                }
                val textWidthLeft = paintIndicator.measureText(textLeft)
                val textWidthRight = paintIndicator.measureText(textRight)
                when (indicatorMode) {
                    IndicatorMode.VISIBLE -> {
                        canvas.drawIndicator(
                            rectThumbLeft,
                            textLeft,
                            textWidthLeft / 2f,
                            rectThumbRight.centerX() - textWidthRight / 2f - textWidthLeft / 2f
                        )
                        canvas.drawIndicator(
                            rectThumbRight,
                            textRight,
                            rectThumbLeft.centerX() + textWidthLeft / 2f + textWidthRight / 2f,
                            width - textWidthRight / 2f
                        )
                    }
                    IndicatorMode.ONLY_FOCUS->{
                        if (isThumbMoving){
                            if (thumbIndex== THUMB_LEFT){
                                canvas.drawIndicator(rectThumbLeft,textLeft,textWidthLeft / 2f, width-textWidthLeft/2f)
                            }else{
                                canvas.drawIndicator(rectThumbRight,textRight,textWidthRight / 2f, width-textWidthRight/2f)
                            }
                        }
                    }
                    else->{

                    }
                }
            }
        }
    }

    private fun Canvas.drawIndicator(
        rectThumb: Rect,
        text: String,
        minPosition: Float,
        maxPosition: Float
    ) {
        val yText = if (indicatorPosition == IndicatorPosition.TOP) {
            if (videoBarHeight > rectThumb.height()) {
                rectCutBar.top - textIndicatorSpacing
            } else {
                rectThumb.top - textIndicatorSpacing
            }
        } else {
            if (videoBarHeight > rectThumb.height()) {
                rectCutBar.bottom + textIndicatorSpacing + rectIndicator.height()
            } else {
                rectThumb.bottom + textIndicatorSpacing + rectIndicator.height()
            }
        }
        var xText = rectThumb.centerX().toFloat()
        if (xText < minPosition)
            xText = minPosition
        else if (xText > maxPosition)
            xText = maxPosition
        drawText(text, xText, yText, paintIndicator)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.let {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (showThumbCut) {
                        pointDown.set(event.x, event.y)
                        thumbIndex = getThumbFocus()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (showThumbCut) {
                        if (thumbIndex == THUMB_NONE || duration == 0f)
                            return true
                        val disMove = event.x - pointDown.x
                        if (isThumbMoving) {
                            pointDown.x = event.x
                            moveThumb(disMove)
                        } else {
                            if (abs(disMove) >= touchSlop) {
                                isThumbMoving = true
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (showThumbCut) {
                        pointDown.set(0f, 0f)
                        if (isThumbMoving) {
                            rangeChangeListener?.onRangeChanged(
                                this,
                                minProgress.toLong(),
                                maxProgress.toLong(),
                                thumbIndex
                            )
                            thumbIndex = THUMB_NONE
                            isThumbMoving = false
                        }
                        isInteract = false
                        invalidate()
                    }
                }
            }
        }
        return true
    }

    private fun getThumbFocus(): Int {
        var isFocusThumbLeft = false
        var isFocusThumbRight = false
        if (pointDown.x.toInt() in rectThumbLeft.left - touchAreaExtra..rectThumbLeft.right + touchAreaExtra) {
            isFocusThumbLeft = true
        }
        if (pointDown.x.toInt() in rectThumbRight.left - touchAreaExtra..rectThumbRight.right + touchAreaExtra) {
            isFocusThumbRight = true
        }
        return if (isFocusThumbLeft && isFocusThumbRight) {
            if (abs(pointDown.x - rectThumbLeft.centerX()) > abs(rectThumbRight.centerX() - pointDown.x)) {
                1
            } else
                0
        } else if (isFocusThumbLeft)
            0
        else if (isFocusThumbRight)
            1
        else
            -1
    }

    private fun moveThumb(distance: Float) {
        val disMove = distance.toInt()
        oldIndexLeft = minProgress
        oldIndexRight = maxProgress
        isInteract = true
        lastFocusThumbIndex = thumbIndex
        val minCutProgress = if (duration > this.minCutProgress) this.minCutProgress else 0f
        val minBetween =
            if (duration > minCutProgress) minCutProgress.toLong().ToDimensionSize() else 0F
        val thumbRect: Rect
        if (thumbIndex == THUMB_LEFT) {
            thumbRect = rectThumbLeft
            val minLeft = rectView.left + barCorners
            val maxLeft = (rectThumbRight.left - thumbWidth).toFloat() - minBetween
            adjustMove(thumbRect, disMove, minLeft, maxLeft)
            minProgress = thumbRect.right.toFloat().ToProgress()

            if (minProgress > maxProgress - minCutProgress)
                minProgress = maxProgress - minCutProgress
        } else if (thumbIndex == THUMB_RIGHT) {
            thumbRect = rectThumbRight
            val minLeft =
                rectThumbLeft.right + minBetween//Bỏ đi giới hạn ở giữa
            //(minCutProgress + minProgress).ToDimensionPosition()
            val maxLeft = rectView.right - thumbWidth - barCorners
            adjustMove(thumbRect, disMove, minLeft, maxLeft)
            maxProgress = thumbRect.left.toFloat().ToProgress()
            if (maxProgress < minProgress + minCutProgress)
                maxProgress = minProgress + minCutProgress
        }
        if (minProgress < 0)
            minProgress = 0f
        if (maxProgress > duration)
            maxProgress = duration
        //log("Min: $minProgress, Max: $maxProgress, Duration: $duration")
        fixProgressCenterWhenMoveThumb()
        rangeChangeListener?.onRangeChanging(
            this,
            minProgress.toLong(),
            maxProgress.toLong(),
            thumbIndex
        )
        invalidate()
    }

    private fun fixProgressCenterWhenMoveThumb() {
        if (progress < minProgress) {
            progress = minProgress
        } else if (progress > maxProgress)
            progress = maxProgress
    }

    private fun adjustMove(thumbRect: Rect, disMove: Int, minLeft: Float, maxLeft: Float) {
        when {
            thumbRect.left + disMove < minLeft -> thumbRect.left = minLeft.roundToInt()
            thumbRect.left + disMove > maxLeft -> thumbRect.left = maxLeft.roundToInt()
            else -> thumbRect.left += disMove
        }
        thumbRect.right = thumbRect.left + thumbWidth
    }

    private fun Float.ToProgress(): Float {
        val realDimension = (this - thumbWidth - rectView.left - barCorners)
        return ((realDimension / videoBarWidth) * duration)
    }

    private fun Long.ToDimensionSize(): Float {
        return (this.toDouble() / duration * videoBarWidth).toFloat()
    }

    private fun Number.ToDimensionPosition(): Float {
        return (this.toFloat() / duration * videoBarWidth + rectView.left + thumbWidth + barCorners)
    }

    /**
     * For setting data programmatically
     */
    fun setVisibilityThumbCut(isShow: Boolean) {
        showThumbCut = isShow
        postInvalidate()
    }

    fun setThumbImage(@DrawableRes thumbLeftId: Int, @DrawableRes thumbRightId: Int) {
        drawableThumbLeft = ContextCompat.getDrawable(context, thumbLeftId)
        drawableThumbRight = ContextCompat.getDrawable(context, thumbRightId)
        postInvalidate()

    }

    fun setCenterProgress(progress: Number) {
        var p = progress.toFloat()
        if (p < minProgress) {
            p = minProgress
        } else if (p > maxProgress) {
            p = maxProgress
        }
        this.progress = p
        postInvalidate()
    }

    fun setThumbShadow(@ColorInt color: Int, radius: Number = thumbCutShadowRadius) {
        paintThumbCut.setShadowLayer(radius.toFloat(), 0f, 0f, color)
        paintThumbCut.color = color
        invalidate()
    }

    fun setThumbShadow(color: String, radius: Number = thumbCutShadowRadius) {
        //color String format: #FF00FF
        try {
            val c = Color.parseColor(color)
            setThumbShadow(c, radius)
        } catch (e: StringIndexOutOfBoundsException) {
            //empty or format error
            //eLog("Set shadow color error")
        } catch (e: IllegalArgumentException) {
            //Unknown color or empty or format error
            //eLog("Set shadow color error")
        }
    }

    private fun setCustomDuration(duration: Number, resetView: Boolean = true) {
        var d = duration.toFloat()
        if (d < 0)
            d = 100f
        if (maxProgress > d || resetView) {
            maxProgress = d
        }
        if (minProgress > d || minProgress >= maxProgress || resetView)
            minProgress = 0f
        if (progress > d || resetView)
            progress = 0f
        this.duration = d
        invalidateCutBarWithRange()
        invalidateOverlayType()
        invalidateCenterThumbWithProgress()
        postInvalidate()
    }

    fun setRangeProgress(minValue: Number, maxValue: Number) {
        if (duration < 0)
            return
        var min = minValue.toFloat()
        var max = maxValue.toFloat()
        if (min > duration || min < 0) {
            min = 0f
        }
        if (max > duration || max < min) {
            max = duration
        }
        if (progress !in min..max) {
            progress = min
        }
        this.minProgress = min
        this.maxProgress = max
        invalidateCutBarWithRange()
        invalidateCenterThumbWithProgress()
        invalidateOverlayType()
        postInvalidate()
    }

    private fun setPath() {
        val file = File(videoPath)
        if (!file.exists()) {
            loadingListener?.onLoadingError()
            return
        }
        post {
            setPath(true)
        }
    }

    private fun setPath(set: Boolean) {
        loadingListener?.onLoadingStart()
        cancelLoading()
        doJob({
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val sDuration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            try {
                duration = sDuration.toFloat()
                maxProgress = duration
                if (minCutProgress > duration)
                    minCutProgress = 0f
            } catch (e: Exception) {
                //Log.e("Can't get duration")
            }
            bitmapBar = Bitmap.createBitmap(
                videoBarWidth,
                rectImages.height().roundToInt(),
                Bitmap.Config.RGB_565
            )
            val canvas = Canvas(bitmapBar!!)
            var offset = 0f
            val scaleHeight = rectImages.height()
            for (i in 0 until numberPreviewImage) {
                val bitmap =
                    retriever.getFrameAtTime((duration / numberPreviewImage * i.toLong() * 1000).toLong())
                val newBitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    imageWidth.toInt(),
                    scaleHeight.roundToInt(),
                    false
                )
                canvas.drawBitmap(newBitmap, offset, 0f, paintImage)
                offset += imageWidth
            }
            retriever.release()
        }, {
            loadingListener?.onLoadingComplete()
            invalidate()
        }, dispathcherOut = Dispatchers.Main)

    }

    fun cancelLoading() = com.luza.videocutbar.cancelLoading()

    override fun invalidate() {
        try {
            super.invalidate()
        } catch (e: Exception) {
        }
    }

    interface ILoadingListener {
        fun onLoadingStart() {}
        fun onLoadingComplete() {}
        fun onLoadingError() {}
    }

    interface OnCutRangeChangeListener {
        fun onRangeChanging(
            videoCutBar: VideoCutBar?,
            minValue: Long,
            maxValue: Long,
            thumbIndex: Int
        ) {
        }

        fun onRangeChanged(
            videoCutBar: VideoCutBar?,
            minValue: Long,
            maxValue: Long,
            thumbIndex: Int
        ) {
        }

    }

    enum class IndicatorPosition(var value: Int) {
        TOP(0), BOTTOM(1)
    }

    enum class IndicatorMode(var value: Int) {
        VISIBLE(0), ONLY_FOCUS(1), HIDDEN(-1)
    }

    private fun initView(attrs: AttributeSet?) {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        attrs?.let {
            val ta = context.obtainStyledAttributes(attrs, R.styleable.VideoCutBar)
            videoBarHeight =
                ta.getDimensionPixelSize(
                    R.styleable.VideoCutBar_vcb_video_bar_height,
                    context.resources.getDimensionPixelSize(R.dimen.vcb_def_bar_height)
                )
            barCorners = ta.getDimension(R.styleable.VideoCutBar_vcb_video_bar_border_corners, 0f)
            numberPreviewImage = ta.getInt(R.styleable.VideoCutBar_vcb_number_image_preview, 8)
            imagePaddingVertical =
                ta.getDimension(R.styleable.VideoCutBar_vcb_number_image_padding_vertical, 0f)
            imagePaddingHorizontal =
                ta.getDimension(R.styleable.VideoCutBar_vcb_number_image_padding_horizontal, 0f)
            paintImage.color =
                ta.getColor(R.styleable.VideoCutBar_vcb_video_bar_background_color, Color.BLACK)

            showThumbCut = ta.getBoolean(R.styleable.VideoCutBar_vcb_show_thumb_cut, true)
            var colorShadowThumbCut = ta.getColor(
                R.styleable.VideoCutBar_vcb_thumb_cut_shadow_color,
                DEF_COLOR_THUMB_CUT_SHADOW
            )
            thumbCutShadowRadius =
                ta.getDimension(R.styleable.VideoCutBar_vcb_thumb_cut_shadow_radius, 0f)
            if (thumbCutShadowRadius < 0f) {
                colorShadowThumbCut = Color.TRANSPARENT
            }
            paintThumbCut.color = colorShadowThumbCut
            paintThumbCut.setShadowLayer(thumbCutShadowRadius, 0f, 0f, colorShadowThumbCut)
            minCutProgress =
                ta.getInt(R.styleable.VideoCutBar_vcb_thumb_cut_min_progress, 0).toFloat()
            thumbWidth = ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_thumb_width, 20)
            thumbHeight =
                ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_thumb_height, videoBarHeight)
            touchAreaExtra =
                ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_thumb_touch_extra_area, 0)
            drawableThumbLeft =
                ta.getDrawable(R.styleable.VideoCutBar_vcb_thumb_left) ?: ContextCompat.getDrawable(
                    context,
                    R.drawable.ic_thumb_left_default
                )
            drawableThumbRight = ta.getDrawable(R.styleable.VideoCutBar_vcb_thumb_right)
                ?: ContextCompat.getDrawable(context, R.drawable.ic_thumb_right_default)
            paintThumbOverlay.color =
                ta.getColor(
                    R.styleable.VideoCutBar_vcb_thumb_overlay_tail_color,
                    DEF_COLOR_THUMB_OVERLAY
                )
            paintProgressThumb.color =
                ta.getColor(R.styleable.VideoCutBar_vcb_progress_thumb_color, Color.TRANSPARENT)
            paintProgressThumbSpread.color =
                ta.getColor(
                    R.styleable.VideoCutBar_vcb_progress_thumb_spread_color,
                    DEF_COLOR_SPREAD
                )
            thumbProgressWidth =
                ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_progress_thumb_width, 10)
            thumbProgressSpreadWidth =
                ta.getDimensionPixelSize(
                    R.styleable.VideoCutBar_vcb_progress_thumb_spread_width,
                    thumbProgressWidth * 3
                )
            thumbProgressHeight =
                ta.getDimensionPixelSize(
                    R.styleable.VideoCutBar_vcb_progress_thumb_height,
                    thumbHeight
                )
            thumbProgressCorners =
                ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_progress_thumb_corners, 0)
            thumbProgressSpreadCorners =
                ta.getDimensionPixelSize(
                    R.styleable.VideoCutBar_vcb_progress_thumb_spread_corners,
                    0
                )
            showThumbProgress = ta.getBoolean(R.styleable.VideoCutBar_vcb_show_thumb_progress, true)

            minProgress = ta.getFloat(R.styleable.VideoCutBar_vcb_progress_min, 0f)
            maxProgress =
                ta.getFloat(R.styleable.VideoCutBar_vcb_progress_max, duration)
            progress =
                ta.getFloat(R.styleable.VideoCutBar_vcb_progress_center, minProgress)

            if (thumbWidth == 0 && drawableThumbLeft != null)
                thumbWidth = drawableThumbLeft!!.intrinsicWidth

            //Text Indicator
            paintIndicator.color =
                ta.getColor(R.styleable.VideoCutBar_vcb_indicator_color, Color.BLACK)
            paintIndicator.textSize =
                ta.getDimension(R.styleable.VideoCutBar_vcb_indicator_size, 0f)
            textIndicatorSpacing =
                ta.getDimension(R.styleable.VideoCutBar_vcb_indicator_spacing, 0f)
            indicatorMode =
                when (ta.getInt(
                    R.styleable.VideoCutBar_vcb_indicator_show_mode,
                    IndicatorMode.HIDDEN.value
                )) {
                    IndicatorMode.VISIBLE.value -> {
                        IndicatorMode.VISIBLE
                    }
                    IndicatorMode.ONLY_FOCUS.value -> {
                        IndicatorMode.ONLY_FOCUS
                    }
                    else -> {
                        IndicatorMode.HIDDEN
                    }
                }
            //eLog("Indicator Mode: $indicatorMode")

            indicatorPosition =
                when (ta.getInt(
                    R.styleable.VideoCutBar_vcb_indicator_position,
                    IndicatorPosition.TOP.value
                )) {
                    IndicatorPosition.BOTTOM.value -> {
                        IndicatorPosition.BOTTOM
                    }
                    else -> {
                        IndicatorPosition.TOP
                    }
                }
            //eLog("Indicator Position: $indicatorPosition")
            val fontId = ta.getResourceId(R.styleable.VideoCutBar_vcb_indicator_font, -1)
            if (fontId != -1) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    paintIndicator.typeface = resources.getFont(fontId)
                } else
                    paintIndicator.typeface = ResourcesCompat.getFont(context, fontId)
            }
            val format = ta.getString(R.styleable.VideoCutBar_vcb_indicator_format)
            eLog("Format: $format")
            if (!format.isNullOrEmpty()) {
                indicatorFormat = SimpleDateFormat(format, Locale.getDefault())
            }
            ta.recycle()
        }
    }
}