package net.xblacky.animexstream.ui.main.home

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxInterstitialAd
import com.google.android.material.transition.MaterialFadeThrough
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_home.view.*
import net.xblacky.animexstream.BuildConfig
import net.xblacky.animexstream.R
import net.xblacky.animexstream.ui.main.home.epoxy.HomeController
import net.xblacky.animexstream.utils.EventObserver
import net.xblacky.animexstream.utils.constants.C
import net.xblacky.animexstream.utils.model.AnimeMetaModel
import timber.log.Timber
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class HomeFragment : Fragment(), View.OnClickListener, HomeController.EpoxyAdapterCallbacks, MaxAdListener {

    lateinit var interstitialAd: MaxInterstitialAd
    var retryAttempt = 0.0
    private lateinit var rootView: View
    private lateinit var homeController: HomeController
    private var doubleClickLastTime = 0L

    private val viewModel: HomeViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_home, container, false)
        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTransitions(view)
        createInterstitialAd()
        setAdapter()
        setClickListeners()
        viewModel.queryDB()
        viewModelObserver()
    }

    private fun setAdapter() {
        homeController = HomeController(this)
        homeController.isDebugLoggingEnabled = true
        homeController.setFilterDuplicates(true)
        val homeRecyclerView = rootView.recyclerView
        homeRecyclerView.layoutManager = LinearLayoutManager(context)
        homeRecyclerView.adapter = homeController.adapter
    }

    private fun viewModelObserver() {
        viewModel.animeList.observe(viewLifecycleOwner) {
            homeController.setData(it)
        }
        viewModel.scrollToTopEvent.observe(viewLifecycleOwner, EventObserver {
            rootView.recyclerView.smoothScrollToPosition(0)
        })

        viewModel.updateModel.observe(viewLifecycleOwner) {
            Timber.e(it.whatsNew)
            if (it.versionCode > BuildConfig.VERSION_CODE) {
                showDialog(it.whatsNew)
            }
        }
    }

    private fun setupTransitions(view: View) {
        postponeEnterTransition()
        view.doOnPreDraw { startPostponedEnterTransition() }
        exitTransition = MaterialFadeThrough().apply {
            duration = 300
        }
        reenterTransition = MaterialFadeThrough().apply {
            duration = 300
        }
    }

    private fun setClickListeners() {
        rootView.header.setOnClickListener(this)
        rootView.search.setOnClickListener(this)
        rootView.favorite.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.header -> {
                doubleClickLastTime = if (System.currentTimeMillis() - doubleClickLastTime < 300) {
                    rootView.recyclerView.smoothScrollToPosition(0)
                    0L
                } else {
                    System.currentTimeMillis()
                }

            }
            R.id.search -> {
                val extras =
                    FragmentNavigatorExtras(rootView.search to resources.getString(R.string.search_transition))
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToSearchFragment(),
                    extras
                )
                show()
            }
            R.id.favorite -> {
                val extras = FragmentNavigatorExtras(
                    rootView.favorite to resources.getString(R.string.favourite_transition)

                )
                findNavController().navigate(
                    HomeFragmentDirections.actionHomeFragmentToFavouriteFragment(),
                    extras
                )
            }
        }
    }

    override fun recentSubDubEpisodeClick(model: AnimeMetaModel) {
        findNavController().navigate(
            HomeFragmentDirections.actionHomeFragmentToVideoPlayerActivity(
                episodeUrl = model.episodeUrl,
                animeName = model.title,
                episodeNumber = model.episodeNumber
            )
        )
    }

    override fun animeTitleClick(model: AnimeMetaModel, sharedTitle: View, sharedImage: View) {
        if (!model.categoryUrl.isNullOrBlank()) {

            val extras = FragmentNavigatorExtras(
                sharedTitle to resources.getString(R.string.shared_anime_title),
                sharedImage to resources.getString(R.string.shared_anime_image)
            )
            findNavController().navigate(
                HomeFragmentDirections.actionHomeFragmentToAnimeInfoFragment(
                    categoryUrl = model.categoryUrl!!,
                    animeImageUrl = model.imageUrl,
                    animeName = model.title
                ),
                extras
            )
        }

    }

    private fun showDialog(whatsNew: String) {
        AlertDialog.Builder(requireContext()).setTitle("New Update Available")
            .setMessage("What's New ! \n$whatsNew")
            .setCancelable(false)
            .setPositiveButton("Update") { _, _ ->
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(C.GIT_DOWNLOAD_URL)
                startActivity(i)
            }
            .setNegativeButton("Not now") { dialog, _ ->
                dialog.cancel()
            }.show()
    }



    fun createInterstitialAd() {
        interstitialAd = MaxInterstitialAd( getString(R.string.MaxInter), activity )
        interstitialAd.setListener( this )
        // Load the first ad
        interstitialAd.loadAd()

    }


    fun show(){

        Toast.makeText(activity,"yes",Toast.LENGTH_LONG).show()
        if  ( interstitialAd.isReady ) {

            interstitialAd.showAd()

        }else{
            Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
                override fun run() {
                    show()
                }
            },3000)
        }
    }


    // MAX Ad Listener
    override fun onAdLoaded(maxAd: MaxAd)
    {
        // Interstitial ad is ready to be shown. interstitialAd.isReady() will now return 'true'

        // Reset retry attempt
        retryAttempt = 0.0
    }

    override fun onAdLoadFailed(adUnitId: String?, error: MaxError?)
    {
        // Interstitial ad failed to load
        // AppLovin recommends that you retry with exponentially higher delays up to a maximum delay (in this case 64 seconds)

        retryAttempt++
        val delayMillis = TimeUnit.SECONDS.toMillis( Math.pow( 2.0, Math.min( 6.0, retryAttempt ) ).toLong() )

        Handler().postDelayed( { interstitialAd.loadAd() }, delayMillis )
    }

    override fun onAdDisplayFailed(ad: MaxAd?, error: MaxError?)
    {
        // Interstitial ad failed to display. AppLovin recommends that you load the next ad.
        interstitialAd.loadAd()
    }

    override fun onAdDisplayed(maxAd: MaxAd) {}

    override fun onAdClicked(maxAd: MaxAd) {}

    override fun onAdHidden(maxAd: MaxAd)
    {
        // Interstitial ad is hidden. Pre-load the next ad
        interstitialAd.loadAd()
    }





}