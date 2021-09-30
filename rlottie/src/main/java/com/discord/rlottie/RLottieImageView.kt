package com.discord.rlottie

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RawRes
import androidx.appcompat.widget.AppCompatImageView
import java.io.File
import java.util.*

/**
 * An ImageView that can load a .json lottie file into a [RLottieDrawable], which enables
 * native quality performance
 */
@Suppress("unused")
class RLottieImageView : AppCompatImageView {
  private var layerColors: HashMap<String, Int>? = null
  private var drawable: RLottieDrawable? = null
  private var playbackMode = RLottieDrawable.PlaybackMode.FREEZE
  private var attachedToWindow = false
  private var playing = false
  private var startOnAttach = false

  constructor(context: Context) : super(context)

  constructor(
    context: Context,
    attrs: AttributeSet?
  ) : super(context, attrs)

  constructor(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
  ) : super(context, attrs, defStyleAttr)

  fun setLayerColor(layer: String, color: Int) {
    if (layerColors == null) {
      layerColors = HashMap()
    }
    layerColors!![layer] = color
    drawable?.setLayerColor(layer, color)
  }

  fun replaceColors(colors: Array<Int>?) {
    drawable?.replaceColors(colors)
  }

  fun setAnimation(resId: Int, w: Int, h: Int) {
    setAnimation(resId, w, h, null)
  }

  fun setAnimation(
    @RawRes resId: Int,
    width: Int,
    height: Int,
    colorReplacement: IntArray? = null,
    playbackMode: RLottieDrawable.PlaybackMode = RLottieDrawable.PlaybackMode.LOOP
  ) {
    drawable = RLottieDrawable(
      context = context,
      rawRes = resId,
      name = resId.toString(),
      width = width,
      height = height,
      screenRefreshRate = context.getDisplayCompat().refreshRate,
      startDecode = false,
      colorReplacement = colorReplacement
    )
    this.playbackMode = playbackMode
    drawable?.setPlaybackMode(playbackMode)
    layerColors?.let { layerColors ->
      drawable?.beginApplyLayerColors()
      for ((key, value) in layerColors) {
        drawable?.setLayerColor(key, value)
      }
      drawable?.commitApplyLayerColors()
    }
    drawable?.setAllowDecodeSingleFrame(true)
    setImageDrawable(drawable)
  }

  fun setAnimation(
    context: Context,
    file: File,
    width: Int,
    height: Int,
    playbackMode: RLottieDrawable.PlaybackMode = RLottieDrawable.PlaybackMode.LOOP
  ) {
    this.drawable = RLottieDrawable(
      file = file,
      w = width,
      h = height,
      precache = false,
      limitFps = true,
      screenRefreshRate = context.getDisplayCompat().refreshRate
    )
    this.playbackMode = playbackMode
    drawable?.setPlaybackMode(playbackMode)
    drawable?.setAllowDecodeSingleFrame(true)
    setImageDrawable(drawable)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    attachedToWindow = true
    if (playing) {
      drawable?.start()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    attachedToWindow = false
    drawable?.stop()
  }

  fun setPlaybackMode(playbackMode: RLottieDrawable.PlaybackMode) {
    this.playbackMode = playbackMode
    drawable?.setPlaybackMode(playbackMode)
  }

  fun setProgress(progress: Float) {
    drawable?.setProgress(progress)
  }

  fun playAnimation() {
    if (drawable == null) {
      return
    }
    playing = true
    if (attachedToWindow) {
      drawable?.start()
    } else {
      startOnAttach = true
    }
  }

  fun pauseAnimation() {
    if (drawable == null) {
      return
    }
    playing = false
    if (attachedToWindow) {
      drawable?.stop()
    } else {
      startOnAttach = false
    }
  }

  @SuppressLint("AnnotateVersionCheck")
  private fun Context.getDisplayCompat(): Display =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      display!!
    } else {
      @Suppress("DEPRECATION")
      (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
}
