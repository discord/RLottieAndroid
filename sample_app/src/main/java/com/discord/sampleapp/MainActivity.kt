package com.discord.sampleapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.discord.rlottie.RLottie
import kotlinx.android.synthetic.main.main_activity.*

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)

    RLottie.init()
  }

  override fun onResume() {
    super.onResume()
    rlottie_imageview.setAnimation(R.raw.gears_lottie, 200, 200)
    rlottie_imageview.playAnimation()
  }
}
