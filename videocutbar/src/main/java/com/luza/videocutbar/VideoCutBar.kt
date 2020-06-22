package com.luza.videocutbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.text.Format
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
    var formatDuration: Format? = null
    private val pointDown = PointF(0f, 0f)
    private var isThumbMoving = false
    var thumbIndex = THUMB_NONE
        private set

    private var drawableThumbLeft: Drawable? = null
    private var drawableThumbRight: Drawable? = null
    private var listBitmap: ArrayList<Bitmap> = ArrayList()
    private var bitmapBar: Bitmap? = null

    private var paintImage = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintThumbCut = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintThumbOverlay = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintProgressThumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private var paintProgressThumbSpread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DEF_COLOR_SPREAD
    }

    private var rectView = RectF()
    private var rectThumbLeft = Rect()
    private var rectThumbRight = Rect()
    private var rectThumbProgress = RectF()
    private var rectThumbProgressSpread = RectF()
    private var rectOverlayLeft = RectF()
    private var rectOverlayRight = RectF()
    private var rectCutBar = RectF()
    private var rectImages = RectF()
    private var listRectImage: ArrayList<RectF> = ArrayList()

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

    init {
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
                ta.getDimensionPixelSize(R.styleable.VideoCutBar_vcb_progress_thumb_spread_corners, 0)
            showThumbProgress = ta.getBoolean(R.styleable.VideoCutBar_vcb_show_thumb_progress, true)

            minProgress = ta.getInt(R.styleable.VideoCutBar_vcb_progress_min, 0).toFloat()
            maxProgress =
                ta.getInt(R.styleable.VideoCutBar_vcb_progress_max, duration.toInt()).toFloat()

            if (thumbWidth == 0 && drawableThumbLeft != null)
                thumbWidth = drawableThumbLeft!!.intrinsicWidth
            ta.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        viewWidth = MeasureSpec.getSize(widthMeasureSpec)
        val minHeight =
            max(max(thumbHeight, videoBarHeight), thumbProgressHeight) + paddingTop + paddingBottom
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
        if (result < desiredSize) {
            //eLog("The view is too small, the content might get cut")
        }
        return result
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectView.left = 0f + paddingLeft
        rectView.top = 0f + paddingTop
        rectView.bottom = (viewHeight - paddingBottom).toFloat()
        rectView.right = (viewWidth - paddingRight).toFloat()
        videoBarWidth = (rectView.width() - thumbWidth * 2).toInt()
        imageWidth = videoBarWidth.toFloat() / numberPreviewImage

        val barSpacingVertical = (rectView.height() - videoBarHeight) / 2f
        rectCutBar.set(
            rectView.left + thumbWidth,
            rectView.top + barSpacingVertical,
            rectView.right - thumbWidth,
            rectView.bottom - barSpacingVertical
        )
        rectImages.set(
            rectCutBar.left + imagePaddingHorizontal / 2f,
            rectCutBar.top + imagePaddingVertical / 2f,
            rectCutBar.right - imagePaddingHorizontal / 2f,
            rectCutBar.bottom - imagePaddingHorizontal / 2f
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
        }
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
        val minBetween = if (duration > 0) minCutProgress.toLong().ToDimensionSize() else 0F
        val thumbRect: Rect
        if (thumbIndex == THUMB_LEFT) {
            thumbRect = rectThumbLeft
            val minLeft = rectView.left
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
            val maxLeft = rectView.right - thumbWidth
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
        val realDimension = (this - thumbWidth - rectView.left)
        return ((realDimension / videoBarWidth) * duration)
    }

    private fun Long.ToDimensionSize(): Float {
        return (this.toDouble() / duration * videoBarWidth).toFloat()
    }

    private fun Number.ToDimensionPosition(): Float {
        return (this.toFloat() / duration * videoBarWidth + rectView.left + thumbWidth)
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

}