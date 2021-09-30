@file:Suppress("DEPRECATION")

package com.discord.rlottie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.annotation.RawRes
import java.io.File
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A drawable that supports .json Lottie files with native bindings for native quality performance.
 * Frames are loaded natively and compressed in the cache using LZ4.
 *
 * This file is heavily based on
 * https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/Components/RLottieDrawable.java
 * but converted to Kotlin, and some cleanup made
 */
@Suppress("unused")
class RLottieDrawable : BitmapDrawable, Animatable {

  private var width = 0
  private var height = 0
  private val metaData = IntArray(3)
  private var timeBetweenFrames = 0
  private var customEndFrame = -1
  private var newReplaceColors: Array<Int>? = null
  private var pendingReplaceColors: Array<Int>? = null
  private val newColorUpdates = HashMap<String, Int>()
  @Volatile
  private var pendingColorUpdates = HashMap<String, Int>()
  private var vibrationPattern: HashMap<Int, Int>? = null
  private var currentParentView: View? = null
  private var playbackMode = PlaybackMode.LOOP
  private var autoRepeatPlayCount = 0
  private var lastFrameTime: Long = 0
  @Volatile
  private var nextFrameIsLast = false
  private var cacheGenerateTask: Runnable? = null
  private var loadFrameTask: Runnable? = null
  @Volatile
  var renderingBitmap: Bitmap? = null
    private set
  @Volatile
  var nextRenderingBitmap: Bitmap? = null
    private set
  @Volatile
  var backgroundBitmap: Bitmap? = null
    private set
  private var destroyWhenDone = false
  private var decodeSingleFrame = false
  private var singleFrameDecoded = false
  private var forceFrameRedraw = false
  private var applyingLayerColors = false
  private var currentFrame = 0
  private var shouldLimitFps = false
  private var screenRefreshRate: Float = 60f
  private var scaleX = 1.0f
  private var scaleY = 1.0f
  private var applyTransformation = false
  private val dstRect = Rect()
  @Volatile
  private var isRunning = false
  @Volatile
  private var isRecycled = false
  @Volatile
  private var nativePtr: Long = 0
  private val parentViews = ArrayList<WeakReference<View?>>()

  private val uiRunnableNoFrame = Runnable {
    loadFrameTask = null
    decodeFrameFinishedInternal()
  }

  private val uiRunnableCacheFinished = Runnable {
    cacheGenerateTask = null
    decodeFrameFinishedInternal()
  }

  private val uiRunnable = Runnable {
    singleFrameDecoded = true
    invalidateInternal()
    decodeFrameFinishedInternal()
  }

  private val uiRunnableLastFrame = Runnable {
    singleFrameDecoded = true
    isRunning = false
    invalidateInternal()
    decodeFrameFinishedInternal()
  }

  private val uiRunnableGenerateCacheQueue = Runnable {
    if (cacheGenerateTask != null) {
      createCache(nativePtr, width, height)
      uiHandler.post(uiRunnableCacheFinished)
    }
  }

  private val uiRunnableGenerateCache = Runnable {
    if (!isRecycled && !destroyWhenDone && nativePtr != 0L) {
      lottieCacheGenerateQueue?.execute(
          uiRunnableGenerateCacheQueue.also { cacheGenerateTask = it })
    }
    decodeFrameFinishedInternal()
  }

  private fun checkRunningTasks() {
    if (cacheGenerateTask != null) {
      if (lottieCacheGenerateQueue!!.remove(cacheGenerateTask)) {
        cacheGenerateTask = null
      }
    }
    if (!hasParentView() && nextRenderingBitmap != null && loadFrameTask != null) {
      loadFrameTask = null
      nextRenderingBitmap = null
    }
  }

  private fun decodeFrameFinishedInternal() {
    if (destroyWhenDone) {
      checkRunningTasks()
      if (loadFrameTask == null && cacheGenerateTask == null && nativePtr != 0L) {
        destroy(nativePtr)
        nativePtr = 0
      }
    }
    if (nativePtr == 0L) {
      recycleResources()
      return
    }
    if (!hasParentView()) {
      stop()
    }
    scheduleNextGetFrame()
  }

  private fun recycleResources() {
    if (renderingBitmap != null) {
      renderingBitmap!!.recycle()
      renderingBitmap = null
    }
    if (backgroundBitmap != null) {
      backgroundBitmap!!.recycle()
      backgroundBitmap = null
    }
  }

  private val loadFrameRunnable = Runnable {
    if (isRecycled) {
      return@Runnable
    }
    if (nativePtr == 0L) {
      uiHandler.post(uiRunnableNoFrame)
      return@Runnable
    }
    if (backgroundBitmap == null) {
      try {
        backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      } catch (e: Throwable) {
        Log.e("RLottieDrawable", "Error Loading Frame in Runnable", e)
      }
    }
    if (backgroundBitmap != null) {
      try {
        if (pendingColorUpdates.isNotEmpty()) {
          for ((key, value) in pendingColorUpdates) {
            setLayerColor(nativePtr, key, value)
          }
          pendingColorUpdates.clear()
        }
      } catch (ignore: Exception) {
      }

      pendingReplaceColors?.let {
        replaceColors(nativePtr, it.toIntArray())
      }
      pendingReplaceColors = null

      try {
        val result = getFrame(
            ptr = nativePtr,
            frame = currentFrame,
            bitmap = backgroundBitmap!!,
            w = width,
            h = height,
            stride = backgroundBitmap!!.rowBytes,
            clear = true
        )
        if (result == -1) {
          uiHandler.post(uiRunnableNoFrame)
          return@Runnable
        }
        if (metaData[2] != 0) {
          uiHandler.post(uiRunnableGenerateCache)
          metaData[2] = 0
        }
        nextRenderingBitmap = backgroundBitmap
        val framesPerUpdates = if (shouldLimitFps) 2 else 1
        if (currentFrame + framesPerUpdates < metaData[0]) {
          if (playbackMode == PlaybackMode.FREEZE) {
            nextFrameIsLast = true
            autoRepeatPlayCount++
          } else {
            currentFrame += framesPerUpdates
            nextFrameIsLast = false
          }
        } else if (playbackMode == PlaybackMode.LOOP) {
          currentFrame = 0
          nextFrameIsLast = false
        } else if (playbackMode == PlaybackMode.ONCE) {
          currentFrame = 0
          nextFrameIsLast = true
          autoRepeatPlayCount++
        } else {
          nextFrameIsLast = true
        }
      } catch (e: Exception) {
        Log.e("RLottieDrawable", "Error loading frame", e)
      }
    }
    uiHandler.post(uiRunnable)
  }

  @JvmOverloads
  constructor(
    file: File,
    w: Int,
    h: Int,
    precache: Boolean,
    limitFps: Boolean,
    screenRefreshRate: Float,
    colorReplacement: IntArray? = null
  ) {
    width = w
    height = h
    shouldLimitFps = limitFps
    this.screenRefreshRate = screenRefreshRate
    paint.flags = Paint.FILTER_BITMAP_FLAG
    nativePtr = create(
        src = file.absolutePath,
        w = w,
        h = h,
        params = metaData,
        precache = precache,
        colorReplacement = colorReplacement,
        limitFps = shouldLimitFps
    )
    if (precache && lottieCacheGenerateQueue == null) {
      lottieCacheGenerateQueue = ThreadPoolExecutor(
          1,
          1,
          0L,
          TimeUnit.MILLISECONDS,
          LinkedBlockingQueue()
      )
    }
    if (nativePtr == 0L) {
      file.delete()
    }
    if (shouldLimitFps && metaData[1] < 60) {
      shouldLimitFps = false
    }
    timeBetweenFrames = max(
        if (shouldLimitFps) 33 else 16,
        (1000.0f / metaData[1]).toInt()
    )
  }

  @JvmOverloads
  constructor(
    context: Context,
    @RawRes rawRes: Int,
    name: String,
    width: Int,
    height: Int,
    screenRefreshRate: Float,
    startDecode: Boolean = true,
    colorReplacement: IntArray? = null
  ) {
    try {
      val inputStream = context.resources.openRawResource(rawRes)
      var readLen: Int
      var totalRead = 0
      while (inputStream.read(buffer, 0, buffer.size).also { readLen = it } > 0) {
        if (readBuffer.size < totalRead + readLen) {
          val newBuffer = ByteArray(readBuffer.size * 2)
          System.arraycopy(readBuffer, 0, newBuffer, 0, totalRead)
          readBuffer = newBuffer
        }
        System.arraycopy(buffer, 0, readBuffer, totalRead, readLen)
        totalRead += readLen
      }
      val jsonString = String(readBuffer, 0, totalRead)
      inputStream.close()
      this.width = width
      this.height = height
      this.screenRefreshRate = screenRefreshRate
      paint.flags = Paint.FILTER_BITMAP_FLAG
      nativePtr =
          createWithJson(jsonString, name, metaData, colorReplacement)
      timeBetweenFrames = max(16, (1000.0f / metaData[1]).toInt())
      playbackMode = PlaybackMode.LOOP
      if (startDecode) {
        setAllowDecodeSingleFrame(true)
      }
    } catch (e: Throwable) {
      Log.e("RLottieDrawable", "Error Constructing", e)
    }
  }

  fun addParentView(view: View?) {
    if (view == null) {
      return
    }
    var parentIndex = 0
    var parentViewCount = parentViews.size
    while (parentIndex < parentViewCount) {
      if (parentViews[parentIndex].get() === view) {
        return
      } else if (parentViews[parentIndex].get() == null) {
        parentViews.removeAt(parentIndex)
        parentViewCount--
        parentIndex--
      }
      parentIndex++
    }
    parentViews.add(0, WeakReference(view))
  }

  fun removeParentView(view: View?) {
    if (view == null) {
      return
    }
    var parentIndex = 0
    var parentViewCount = parentViews.size
    while (parentIndex < parentViewCount) {
      val v = parentViews[parentIndex].get()
      if (v === view || v == null) {
        parentViews.removeAt(parentIndex)
        parentViewCount--
        parentIndex--
      }
      parentIndex++
    }
  }

  private fun hasParentView(): Boolean {
    if (callback != null) {
      return true
    }
    var parentIndex = 0
    var parentViewCount = parentViews.size
    while (parentIndex < parentViewCount) {
      val view = parentViews[parentIndex].get()
      if (view != null) {
        return true
      } else {
        parentViews.removeAt(parentIndex)
        parentViewCount--
        parentIndex--
      }
      parentIndex++
    }
    return false
  }

  private fun invalidateInternal() {
    var parentIndex = 0
    var parentViewCount = parentViews.size
    while (parentIndex < parentViewCount) {
      val view = parentViews[parentIndex].get()
      if (view != null) {
        view.invalidate()
      } else {
        parentViews.removeAt(parentIndex)
        parentViewCount--
        parentIndex--
      }
      parentIndex++
    }
    if (callback != null) {
      invalidateSelf()
    }
  }

  fun setAllowDecodeSingleFrame(value: Boolean) {
    decodeSingleFrame = value
    if (decodeSingleFrame) {
      scheduleNextGetFrame()
    }
  }

  fun recycle() {
    isRunning = false
    isRecycled = true
    checkRunningTasks()
    if (loadFrameTask == null && cacheGenerateTask == null) {
      if (nativePtr != 0L) {
        destroy(nativePtr)
        nativePtr = 0
      }
      recycleResources()
    } else {
      destroyWhenDone = true
    }
  }

  fun setPlaybackMode(value: PlaybackMode) {
    if (playbackMode == PlaybackMode.ONCE && value == PlaybackMode.FREEZE && currentFrame != 0) {
      return
    }
    playbackMode = value
  }

  override fun getOpacity(): Int = PixelFormat.TRANSPARENT

  override fun start() {
    if (isRunning || playbackMode >= PlaybackMode.ONCE && autoRepeatPlayCount != 0) {
      return
    }
    isRunning = true
    scheduleNextGetFrame()
    invalidateInternal()
  }

  fun restart(): Boolean {
    if (playbackMode < PlaybackMode.ONCE || autoRepeatPlayCount == 0) {
      return false
    }
    autoRepeatPlayCount = 0
    playbackMode = PlaybackMode.ONCE
    start()
    return true
  }

  fun setVibrationPattern(pattern: HashMap<Int, Int>?) {
    vibrationPattern = pattern
  }

  fun beginApplyLayerColors() {
    applyingLayerColors = true
  }

  fun commitApplyLayerColors() {
    if (!applyingLayerColors) {
      return
    }
    applyingLayerColors = false
    if (!isRunning && decodeSingleFrame) {
      if (currentFrame <= 2) {
        currentFrame = 0
      }
      nextFrameIsLast = false
      singleFrameDecoded = false
      if (!scheduleNextGetFrame()) {
        forceFrameRedraw = true
      }
    }
    invalidateInternal()
  }

  fun replaceColors(colors: Array<Int>?) {
    newReplaceColors = colors
    requestRedrawColors()
  }

  fun setLayerColor(layerName: String, color: Int) {
    newColorUpdates[layerName] = color
    requestRedrawColors()
  }

  private fun requestRedrawColors() {
    if (!applyingLayerColors && !isRunning && decodeSingleFrame) {
      if (currentFrame <= 2) {
        currentFrame = 0
      }
      nextFrameIsLast = false
      singleFrameDecoded = false
      if (!scheduleNextGetFrame()) {
        forceFrameRedraw = true
      }
    }
    invalidateInternal()
  }

  private fun scheduleNextGetFrame(): Boolean {
    if (loadFrameTask != null || nextRenderingBitmap != null || nativePtr == 0L ||
        destroyWhenDone || !isRunning &&
        (!decodeSingleFrame || decodeSingleFrame && singleFrameDecoded)) {
      return false
    }
    if (newColorUpdates.isNotEmpty()) {
      pendingColorUpdates.putAll(newColorUpdates)
      newColorUpdates.clear()
    }
    if (newReplaceColors != null) {
      pendingReplaceColors = newReplaceColors
      newReplaceColors = null
    }
    loadFrameRunnableQueue.execute(loadFrameRunnable.also {
      loadFrameTask = it
    })
    return true
  }

  override fun stop() {
    isRunning = false
  }

  fun setProgress(oldProgress: Float) {
    var progress = oldProgress
    if (progress < 0.0f) {
      progress = 0.0f
    } else if (progress > 1.0f) {
      progress = 1.0f
    }
    currentFrame = (metaData[0] * progress).toInt()
    nextFrameIsLast = false
    singleFrameDecoded = false
    if (!scheduleNextGetFrame()) {
      forceFrameRedraw = true
    }
    invalidateSelf()
  }

  fun setCurrentParentView(view: View?) {
    currentParentView = view
  }

  private val isCurrentParentViewMaster: Boolean
    get() {
      if (callback != null) {
        return true
      }
      var parentIndex = 0
      var parentViewCount = parentViews.size
      while (parentIndex < parentViewCount) {
        if (parentViews[parentIndex].get() == null) {
          parentViews.removeAt(parentIndex)
          parentViewCount--
          parentIndex--
          parentIndex++
          continue
        }
        return parentViews[parentIndex].get() === currentParentView
      }
      return true
    }

  override fun isRunning(): Boolean = isRunning

  override fun getIntrinsicHeight(): Int = height

  override fun getIntrinsicWidth(): Int = width

  override fun onBoundsChange(bounds: Rect) {
    super.onBoundsChange(bounds)
    applyTransformation = true
  }

  override fun draw(canvas: Canvas) {
    if (nativePtr == 0L || destroyWhenDone) {
      return
    }
    val now = SystemClock.elapsedRealtime()
    val timeDiff = abs(now - lastFrameTime)
    val timeCheck: Int = if (screenRefreshRate <= 60) {
      timeBetweenFrames - 6
    } else {
      timeBetweenFrames
    }
    if (isRunning) {
      if (renderingBitmap == null && nextRenderingBitmap == null) {
        scheduleNextGetFrame()
      } else if (nextRenderingBitmap != null &&
          (renderingBitmap == null || timeDiff >= timeCheck) && isCurrentParentViewMaster) {
        if (vibrationPattern != null && currentParentView != null) {
          val force = vibrationPattern!![currentFrame - 1]
          if (force != null) {
            currentParentView?.performHapticFeedback(
                if (force == 1) HapticFeedbackConstants.LONG_PRESS
                else HapticFeedbackConstants.KEYBOARD_TAP,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
          }
        }
        backgroundBitmap = renderingBitmap
        renderingBitmap = nextRenderingBitmap
        if (nextFrameIsLast) {
          stop()
        }
        loadFrameTask = null
        singleFrameDecoded = true
        nextRenderingBitmap = null
        lastFrameTime = if (screenRefreshRate <= 60) {
          now
        } else {
          now - min(16, timeDiff - timeCheck)
        }
        scheduleNextGetFrame()
      }
    } else if ((forceFrameRedraw || decodeSingleFrame && timeDiff >= timeCheck) &&
        nextRenderingBitmap != null) {
      backgroundBitmap = renderingBitmap
      renderingBitmap = nextRenderingBitmap
      loadFrameTask = null
      singleFrameDecoded = true
      nextRenderingBitmap = null
      lastFrameTime = if (screenRefreshRate <= 60) {
        now
      } else {
        now - min(16, timeDiff - timeCheck)
      }
      if (forceFrameRedraw) {
        singleFrameDecoded = false
        forceFrameRedraw = false
      }
      scheduleNextGetFrame()
    }
    if (renderingBitmap != null) {
      if (applyTransformation) {
        dstRect.set(bounds)
        scaleX = dstRect.width().toFloat() / width
        scaleY = dstRect.height().toFloat() / height
        applyTransformation = false
      }
      canvas.save()
      canvas.translate(dstRect.left.toFloat(), dstRect.top.toFloat())
      canvas.scale(scaleX, scaleY)
      canvas.drawBitmap(renderingBitmap!!, 0f, 0f, paint)
      if (isRunning) {
        invalidateInternal()
      }
      canvas.restore()
    }
  }

  override fun getMinimumHeight(): Int = height

  override fun getMinimumWidth(): Int = width

  val animatedBitmap: Bitmap?
    get() {
      if (renderingBitmap != null) {
        return renderingBitmap
      } else if (nextRenderingBitmap != null) {
        return nextRenderingBitmap
      }
      return null
    }

  fun hasBitmap(): Boolean =
      nativePtr != 0L && (renderingBitmap != null || nextRenderingBitmap != null)

  enum class PlaybackMode {
    LOOP,
    ONCE,
    FREEZE
  }

  companion object {

    private val uiHandler = Handler(Looper.getMainLooper())
    private var readBuffer = ByteArray(64 * 1024)
    private val buffer = ByteArray(4096)
    private val loadFrameRunnableQueue =
        Executors.newCachedThreadPool()
    private var lottieCacheGenerateQueue: ThreadPoolExecutor? = null

    /* Native Methods */
    private external fun create(
      src: String,
      w: Int,
      h: Int,
      params: IntArray,
      precache: Boolean,
      colorReplacement: IntArray?,
      limitFps: Boolean
    ): Long

    private external fun createWithJson(
      json: String,
      name: String,
      params: IntArray,
      colorReplacement: IntArray?
    ): Long

    private external fun destroy(ptr: Long)
    private external fun setLayerColor(ptr: Long, layer: String, color: Int)
    private external fun replaceColors(ptr: Long, colorReplacement: IntArray)
    private external fun getFrame(
      ptr: Long,
      frame: Int,
      bitmap: Bitmap,
      w: Int,
      h: Int,
      stride: Int,
      clear: Boolean
    ): Int

    private external fun createCache(ptr: Long, w: Int, h: Int)
  }
}
