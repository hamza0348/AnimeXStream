package net.xblacky.animexstream.ui.main.player

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.activity_video_player.*
import kotlinx.android.synthetic.main.fragment_video_player.*
import net.xblacky.animexstream.R
import net.xblacky.animexstream.utils.model.Content
import timber.log.Timber
import java.lang.Exception
import android.view.WindowInsetsController

import android.view.WindowInsets
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Observer
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxRewardedAd
import net.xblacky.animexstream.utils.preference.Preference
import java.util.concurrent.TimeUnit
import javax.inject.Inject


@AndroidEntryPoint
class VideoPlayerActivity : AppCompatActivity(), VideoPlayerListener, MaxRewardedAdListener {

    private lateinit var rewardedAd: MaxRewardedAd
    private var retryAttempt = 0.0

    private val viewModel: VideoPlayerViewModel by viewModels()

    @Inject
    lateinit var preference: Preference
    private var episodeNumber: String? = ""
    private var animeName: String? = ""
    private lateinit var content: Content
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)
        createRewardedAd()
        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                show()
            }
        },5000)

        getExtra(intent)
        setObserver()
        goFullScreen()
    }


    override fun onNewIntent(intent: Intent?) {
        (playerFragment as VideoPlayerFragment).playOrPausePlayer(
            playWhenReady = false,
            loseAudioFocus = false
        )
        (playerFragment as VideoPlayerFragment).saveWatchedDuration()
        getExtra(intent)
        super.onNewIntent(intent)

    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }


    override fun onResume() {
        super.onResume()


    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            goFullScreen()
        }
    }

    private fun getExtra(intent: Intent?) {
        val url = intent?.extras?.getString("episodeUrl")
        episodeNumber = intent?.extras?.getString("episodeNumber")
        animeName = intent?.extras?.getString("animeName")
        viewModel.updateEpisodeContent(
            Content(
                animeName = animeName ?: "",
                episodeUrl = url,
                episodeName = "\"$episodeNumber\"",
                urls = ArrayList()
            )
        )
        viewModel.fetchEpisodeData()
    }

    @Suppress("DEPRECATION")
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && packageManager
                .hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE
                )
            && hasPipPermission()
            && (playerFragment as VideoPlayerFragment).isVideoPlaying()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = PictureInPictureParams.Builder()
                this.enterPictureInPictureMode(params.build())
            } else {
                this.enterPictureInPictureMode()
            }
        }
    }

//    override fun onStop() {
//        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
//            && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
//            && hasPipPermission()
//        ) {
//            finishAndRemoveTask()
//        }
//        super.onStop()
//    }

    override fun finish() {
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            finishAndRemoveTask()
        }
        super.finish()

        overridePendingTransition(android.R.anim.fade_in, R.anim.slide_in_down)
    }

    fun enterPipModeOrExit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && packageManager
                .hasSystemFeature(
                    PackageManager.FEATURE_PICTURE_IN_PICTURE
                )
            && (playerFragment as VideoPlayerFragment).isVideoPlaying()
            && hasPipPermission()
        ) {
            try {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val params = PictureInPictureParams.Builder()
                    this.enterPictureInPictureMode(params.build())
                } else {
                    this.enterPictureInPictureMode()
                }
            } catch (ex: Exception) {
                Timber.e(ex)
            }

        } else {
            finish()

        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration?
    ) {
        exoPlayerView.useController = !isInPictureInPictureMode
    }

    private fun hasPipPermission(): Boolean {
        val appsOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                appsOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    android.os.Process.myUid(),
                    packageName
                ) == AppOpsManager.MODE_ALLOWED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                appsOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    android.os.Process.myUid(),
                    packageName
                ) == AppOpsManager.MODE_ALLOWED
            }
            else -> {
                false
            }
        }
    }

    private fun setObserver() {

        viewModel.content.observe(this, Observer {
            this.content = it
            it?.let {
                if (!it.urls.isNullOrEmpty()) {
                    (playerFragment as VideoPlayerFragment).updateContent(it)
                }
            }
        })
        viewModel.isLoading.observe(this, Observer {
            (playerFragment as VideoPlayerFragment).showLoading(it.isLoading)
        })
        viewModel.errorModel.observe(this, Observer {
            (playerFragment as VideoPlayerFragment).showErrorLayout(
                it.show,
                it.errorMsgId,
                it.errorCode
            )
        })

        viewModel.cdnServer.observe(this) {
            Timber.e("Referrer : $it")
            preference.setReferrer(it)
        }
    }

    override fun onBackPressed() {
        enterPipModeOrExit()
    }

    private fun goFullScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }

    }

    override fun updateWatchedValue(content: Content) {
        viewModel.saveContent(content)
    }

    override fun playNextEpisode() {
        viewModel.updateEpisodeContent(
            Content(
                episodeUrl = content.nextEpisodeUrl,
                episodeName = "\"EP ${incrimentEpisodeNumber(content.episodeName!!)}\"",
                urls = ArrayList(),
                animeName = content.animeName
            )
        )
        viewModel.fetchEpisodeData()

    }

    override fun playPreviousEpisode() {

        viewModel.updateEpisodeContent(
            Content(
                episodeUrl = content.previousEpisodeUrl,
                episodeName = "\"EP ${decrimentEpisodeNumber(content.episodeName!!)}\"",
                urls = ArrayList(),
                animeName = content.animeName
            )
        )
        viewModel.fetchEpisodeData()
    }

    private fun incrimentEpisodeNumber(episodeName: String): String {
        return try {
            Timber.e("Episode Name $episodeName")
            val episodeString = episodeName.substring(
                episodeName.lastIndexOf(' ') + 1,
                episodeName.lastIndex
            )
            var episodeNumber = Integer.parseInt(episodeString)
            episodeNumber++
            episodeNumber.toString()

        } catch (obe: ArrayIndexOutOfBoundsException) {
            ""
        }
    }

    private fun decrimentEpisodeNumber(episodeName: String): String {
        return try {
            val episodeString = episodeName.substring(
                episodeName.lastIndexOf(' ') + 1,
                episodeName.lastIndex
            )
            var episodeNumber = Integer.parseInt(episodeString)
            episodeNumber--
            episodeNumber.toString()

        } catch (obe: ArrayIndexOutOfBoundsException) {
            ""
        }
    }


    fun refreshM3u8Url() {
        viewModel.fetchEpisodeData(forceRefresh = true)
    }


    fun show(){
        if ( rewardedAd.isReady() )
        {
            rewardedAd.showAd();
        }
    }


    fun createRewardedAd()
    {
        rewardedAd = MaxRewardedAd.getInstance( getString(R.string.MaxReward), this )
        rewardedAd.setListener( this )

        rewardedAd.loadAd()
    }

    // MAX Ad Listener
    override fun onAdLoaded(maxAd: MaxAd)
    {
        // Rewarded ad is ready to be shown. rewardedAd.isReady() will now return 'true'
        // Reset retry attempt
//        retryAttempt = 0.0
    }

    override fun onAdLoadFailed(adUnitId: String?, error: MaxError?)
    {
        // Rewarded ad failed to load
        // We recommend retrying with exponentially higher delays up to a maximum delay (in this case 64 seconds)

        retryAttempt++
        val delayMillis = TimeUnit.SECONDS.toMillis( Math.pow( 2.0, Math.min( 6.0, retryAttempt ) ).toLong() )

        Handler().postDelayed( { rewardedAd.loadAd() }, delayMillis )
    }

    override fun onAdDisplayFailed(ad: MaxAd?, error: MaxError?)
    {
        // Rewarded ad failed to display. We recommend loading the next ad
        rewardedAd.loadAd()
    }

    override fun onAdDisplayed(maxAd: MaxAd) {
        retryAttempt = 0.0
    }

    override fun onAdClicked(maxAd: MaxAd) {}

    override fun onAdHidden(maxAd: MaxAd)
    {
        // rewarded ad is hidden. Pre-load the next ad
        rewardedAd.loadAd()
    }

    override fun onRewardedVideoStarted(maxAd: MaxAd) {} // deprecated

    override fun onRewardedVideoCompleted(maxAd: MaxAd) {} // deprecated

    override fun onUserRewarded(maxAd: MaxAd, maxReward: MaxReward)
    {
        // Rewarded ad was displayed and user should receive the reward
    }



}