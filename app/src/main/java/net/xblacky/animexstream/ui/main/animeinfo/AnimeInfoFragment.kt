package net.xblacky.animexstream.ui.main.animeinfo

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.TransitionInflater
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxInterstitialAd
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialContainerTransform
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.fragment_animeinfo.*
import kotlinx.android.synthetic.main.fragment_animeinfo.view.*
import kotlinx.android.synthetic.main.fragment_animeinfo.view.animeInfoRoot
import kotlinx.android.synthetic.main.loading.view.*
import net.xblacky.animexstream.R
import net.xblacky.animexstream.ui.main.animeinfo.di.AnimeInfoFactory
import net.xblacky.animexstream.ui.main.animeinfo.epoxy.AnimeInfoController
import net.xblacky.animexstream.ui.main.home.HomeFragmentDirections
import net.xblacky.animexstream.utils.ItemOffsetDecoration
import net.xblacky.animexstream.utils.Tags.GenreTags
import net.xblacky.animexstream.utils.Utils
import net.xblacky.animexstream.utils.model.AnimeInfoModel
import net.xblacky.animexstream.utils.model.EpisodeModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class AnimeInfoFragment : Fragment(), AnimeInfoController.EpisodeClickListener, MaxAdListener {

    lateinit var interstitialAd: MaxInterstitialAd
    var retryAttempt = 0.0

    private var adView: MaxAdView? = null


    private lateinit var rootView: View
    private val episodeController: AnimeInfoController by lazy {
        AnimeInfoController(this)
    }

    @Inject
    lateinit var animeInfoFactory: AnimeInfoFactory

    private val args: AnimeInfoFragmentArgs by navArgs()

    private val viewModel: AnimeInfoViewModel by viewModels {
        AnimeInfoViewModel.provideFactory(
            animeInfoFactory, args.categoryUrl
        )
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        rootView = inflater.inflate(R.layout.fragment_animeinfo, container, false)
        createInterstitialAd()

        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                show()
            }
        },3000)

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        adView = MaxAdView(getString(R.string.MaxBanner), activity)

        val rootView : FrameLayout = rootView.findViewById<FrameLayout>(R.id.ad_container)
        rootView.addView(adView)

        // Load the ad
        adView?.loadAd()


        setPreviews()
        setupRecyclerView()
        setObserver()
        transitionListener()
        setOnClickListeners()
    }

    private fun setPreviews() {
        val imageUrl = AnimeInfoFragmentArgs.fromBundle(requireArguments()).animeImageUrl
        val animeTitle = AnimeInfoFragmentArgs.fromBundle(requireArguments()).animeName
        animeInfoTitle.text = animeTitle
        rootView.animeInfoImage.apply {
            Glide.with(this).load(imageUrl).into(this)
        }


    }

    private fun setObserver() {
        viewModel.animeInfoModel.observe(viewLifecycleOwner) {
            it?.let {
                updateViews(it)
            }
        }

        viewModel.episodeList.observe(viewLifecycleOwner) {
            it?.let {
                rootView.animeInfoRoot.visibility = View.VISIBLE
                episodeController.setData(it)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) {

            if (it.isLoading) {
                rootView.loading.visibility = View.VISIBLE
            } else {
                rootView.loading.visibility = View.GONE
            }
        }



        viewModel.isFavourite.observe(viewLifecycleOwner) {
            if (it) {
                favourite.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_favorite,
                        null
                    )
                )
            } else {
                favourite.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources,
                        R.drawable.ic_unfavorite,
                        null
                    )
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition =
            TransitionInflater.from(context).inflateTransition(R.transition.shared_element)

    }

    private fun updateViews(animeInfoModel: AnimeInfoModel) {

//        Glide.with(this)
//            .load(animeInfoModel.imageUrl)
//            .transition(withCrossFade())
//            .into(rootView.animeInfoImage)

        animeInfoReleased.text = animeInfoModel.releasedTime
        animeInfoStatus.text = animeInfoModel.status
        animeInfoType.text = animeInfoModel.type
        animeInfoTitle.text = animeInfoModel.animeTitle
        toolbarText.text = animeInfoModel.animeTitle
        flowLayout.removeAllViews()
        animeInfoModel.genre.forEach {
            flowLayout.addView(
                GenreTags(requireContext()).getGenreTag(
                    genreName = it.genreName,
                    genreUrl = it.genreUrl
                )
            )
        }


        episodeController.setAnime(animeInfoModel.animeTitle)
        animeInfoSummary.text = animeInfoModel.plotSummary
        rootView.favourite.visibility = View.VISIBLE
        rootView.typeLayout.visibility = View.VISIBLE
        rootView.releasedLayout.visibility = View.VISIBLE
        rootView.statusLayout.visibility = View.VISIBLE
        rootView.animeInfoRoot.visibility = View.VISIBLE
    }

    private fun setupRecyclerView() {
        episodeController.spanCount = Utils.calculateNoOfColumns(requireContext(), 150f)
        rootView.animeInfoRecyclerView.adapter = episodeController.adapter
        val itemOffsetDecoration = ItemOffsetDecoration(context, R.dimen.episode_offset_left)
        rootView.animeInfoRecyclerView.addItemDecoration(itemOffsetDecoration)
        rootView.animeInfoRecyclerView.apply {
            layoutManager =
                GridLayoutManager(context, Utils.calculateNoOfColumns(requireContext(), 150f))
            (layoutManager as GridLayoutManager).spanSizeLookup = episodeController.spanSizeLookup

        }
    }

    private fun transitionListener() {
        rootView.motionLayout.setTransitionListener(
            object : MotionLayout.TransitionListener {
                override fun onTransitionTrigger(
                    p0: MotionLayout?,
                    p1: Int,
                    p2: Boolean,
                    p3: Float
                ) {

                }

                override fun onTransitionStarted(p0: MotionLayout?, p1: Int, p2: Int) {
                    rootView.topView.cardElevation = 0F
                }

                override fun onTransitionChange(
                    p0: MotionLayout?,
                    startId: Int,
                    endId: Int,
                    progress: Float
                ) {
                    if (startId == R.id.start) {
                        rootView.topView.cardElevation = 20F * progress
                        rootView.toolbarText.alpha = progress
                    } else {
                        rootView.topView.cardElevation = 10F * (1 - progress)
                        rootView.toolbarText.alpha = (1 - progress)
                    }
                }

                override fun onTransitionCompleted(p0: MotionLayout?, p1: Int) {
                }

            }
        )
    }

    private fun setOnClickListeners() {
        rootView.favourite.setOnClickListener {
            onFavouriteClick()
        }

        rootView.back.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun onFavouriteClick() {
        if (viewModel.isFavourite.value!!) {
            Snackbar.make(
                rootView,
                getText(R.string.removed_from_favourites),
                Snackbar.LENGTH_SHORT
            ).show()
        } else {
            Snackbar.make(rootView, getText(R.string.added_to_favourites), Snackbar.LENGTH_SHORT)
                .show()
        }
        viewModel.toggleFavourite()
    }

    override fun onResume() {
        super.onResume()
        if (episodeController.isWatchedHelperUpdated()) {
            episodeController.setData(viewModel.episodeList.value)
        }
    }

    override fun onEpisodeClick(episodeModel: EpisodeModel) {
        findNavController().navigate(
            AnimeInfoFragmentDirections.actionAnimeInfoFragmentToVideoPlayerActivity(
                episodeUrl = episodeModel.episodeurl,
                animeName = AnimeInfoFragmentArgs.fromBundle(requireArguments()).animeName,
                episodeNumber = episodeModel.episodeNumber
            )
        )
    }



    fun createInterstitialAd() {
        interstitialAd = MaxInterstitialAd( getString(R.string.MaxInter), activity )
        interstitialAd.setListener( this )
        // Load the first ad
        interstitialAd.loadAd()

    }


    fun show(){
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