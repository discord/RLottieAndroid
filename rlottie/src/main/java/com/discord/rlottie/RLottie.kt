package com.discord.rlottie

object RLottie {

  fun init() {
    // Loads the native lib.
    // Must be called before RLottieDrawable and RLottieImageview are used for JNI bindings to work
    System.loadLibrary("dsti")
  }
}
