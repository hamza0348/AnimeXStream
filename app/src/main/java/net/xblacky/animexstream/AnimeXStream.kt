package net.xblacky.animexstream

import android.app.Application
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.xblacky.animexstream.utils.preference.PreferenceHelper
import net.xblacky.animexstream.utils.realm.InitalizeRealm
import net.xblacky.animexstream.utils.rertofit.RetrofitHelper
import timber.log.Timber


@HiltAndroidApp
class AnimeXStream : Application() {


    private lateinit var appOpenManager: ExampleAppOpenManager1



    override fun onCreate() {
        super.onCreate()

        AppLovinSdk.getInstance( this ).setMediationProvider( "max" )
        AppLovinSdk.getInstance( this ).initializeSdk { configuration: AppLovinSdkConfiguration ->
            // AppLovin SDK is initialized, start loading ads
            appOpenManager = ExampleAppOpenManager1(applicationContext)

        }


        InitalizeRealm.initializeRealm(this)
        PreferenceHelper(context = this)
        RetrofitHelper(PreferenceHelper.sharedPreference.getBaseUrl())
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

    }

}

class ExampleAppOpenManager1(applicationContext: Context?) : LifecycleObserver, MaxAdListener {
    private lateinit var appOpenAd: MaxAppOpenAd
    private lateinit var context: Context

    init
    {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        if (applicationContext != null) {
            context = applicationContext
        }

        appOpenAd = MaxAppOpenAd("ff01d03d163c1a9d", applicationContext!!)
        appOpenAd.setListener(this)
        appOpenAd.loadAd()
        showAdIfReady()
    }

    private fun showAdIfReady()
    {
        if (appOpenAd == null || !AppLovinSdk.getInstance(context).isInitialized) return

        if (appOpenAd.isReady)
        {
            appOpenAd.showAd("ff01d03d163c1a9d")
        }
        else
        {
            appOpenAd.loadAd()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart()
    {
        showAdIfReady()
    }

    override fun onAdLoaded(ad: MaxAd) {}
    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
    override fun onAdDisplayed(ad: MaxAd) {}
    override fun onAdClicked(ad: MaxAd) {}

    override fun onAdHidden(ad: MaxAd)
    {
        appOpenAd.loadAd()
    }

    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError)
    {
        appOpenAd.loadAd()
    }
}