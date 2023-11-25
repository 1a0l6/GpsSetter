package com.android1500.gpssetter.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.location.AMapLocationQualityReport
import com.amap.api.maps2d.AMap
import com.amap.api.maps2d.AMap.OnMapClickListener
import com.amap.api.maps2d.AMap.OnMapLoadedListener
import com.amap.api.maps2d.AMap.OnMarkerClickListener
import com.amap.api.maps2d.CameraUpdateFactory
import com.amap.api.maps2d.SupportMapFragment
import com.amap.api.maps2d.model.BitmapDescriptorFactory
import com.amap.api.maps2d.model.LatLng
import com.amap.api.maps2d.model.Marker
import com.amap.api.maps2d.model.MarkerOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItemV2
import com.amap.api.services.geocoder.GeocodeQuery
import com.amap.api.services.geocoder.GeocodeResult
import com.amap.api.services.geocoder.GeocodeSearch
import com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener
import com.amap.api.services.geocoder.RegeocodeQuery
import com.amap.api.services.geocoder.RegeocodeResult
import com.amap.api.services.poisearch.PoiResultV2
import com.amap.api.services.poisearch.PoiSearchV2
import com.amap.api.services.poisearch.PoiSearchV2.OnPoiSearchListener
import com.android1500.gpssetter.BuildConfig
import com.android1500.gpssetter.R
import com.android1500.gpssetter.adapter.FavListAdapter
import com.android1500.gpssetter.databinding.ActivityAmapBinding
import com.android1500.gpssetter.ui.viewmodel.MainViewModel
import com.android1500.gpssetter.utils.JoystickService
import com.android1500.gpssetter.utils.NotificationsChannel
import com.android1500.gpssetter.utils.PrefManager
import com.android1500.gpssetter.utils.ext.*
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.kieronquinn.monetcompat.app.MonetCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.IOException
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.properties.Delegates


/****
 *** author：lao
 *** package：com.android1500.gpssetter.ui
 *** project：GPS Setter
 *** name：AmapActivity
 *** date：2023/11/25  9:32
 *** filename：AmapActivity
 *** desc：高德地图
 ***/
@AndroidEntryPoint
class AmapActivity :  MonetCompatActivity(), OnMapLoadedListener, OnMapClickListener
    , AMapLocationListener, OnPoiSearchListener, OnGeocodeSearchListener, OnMarkerClickListener {
    private val TAG = AmapActivity::class.java.simpleName
    private val binding by lazy { ActivityAmapBinding.inflate(layoutInflater) }
    private lateinit var aMap: AMap
    private val viewModel by viewModels<MainViewModel>()
    private val update by lazy { viewModel.getAvailableUpdate() }
    private val notificationsChannel by lazy { NotificationsChannel() }
    private var favListAdapter: FavListAdapter = FavListAdapter()
    private var mMarker: Marker? = null // 标记点
    private var mLatLng: LatLng? = null // 标记点坐标
    private var lat by Delegates.notNull<Double>()// 纬度
    private var lon by Delegates.notNull<Double>()// 经度
    private var xposedDialog: AlertDialog? = null
    private lateinit var alertDialog: MaterialAlertDialogBuilder
    private lateinit var dialog: AlertDialog
    private lateinit var locationClient : AMapLocationClient
    private lateinit var locationOption : AMapLocationClientOption
    private val PERMISSION_ID = 42
    private val zoom = 15f;
    private val geocoderSearch: GeocodeSearch? = null


    private val elevationOverlayProvider by lazy {
        ElevationOverlayProvider(this)
    }

    private val headerBackground by lazy {
        elevationOverlayProvider.compositeOverlayWithThemeSurfaceColorIfNeeded(
            resources.getDimension(R.dimen.bottom_sheet_elevation)
        )
    }
    override val applyBackgroundColorToWindow = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launchWhenCreated {
            monet.awaitMonetReady()
            setContentView(binding.root)
        }
        setSupportActionBar(binding.toolbar)
        initializeMap()
        isModuleEnable()
        updateChecker()
        setBottomSheet()
        setUpNavigationView()
        setupMonet()
        setupButton()
        setDrawer()
        if (PrefManager.isJoyStickEnable){
            startService(Intent(this, JoystickService::class.java))
        }

    }


    private fun startLocation() {
        try {
            //根据控件的选择，重新设置定位参数
//            resetOption();
            // 设置定位参数
            locationClient.setLocationOption(locationOption)
            // 启动定位
            locationClient.startLocation()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocation()
    }

    private fun stopLocation() {
        try {
            // 停止定位
            locationClient.stopLocation()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        destroyLocation()
    }

    private fun destroyLocation() {
        if (null != locationClient) {
            /**
             * 如果AMapLocationClient是在当前Activity实例化的，
             * 在Activity的onDestroy中一定要执行AMapLocationClient的onDestroy
             */
            locationClient.onDestroy()
//            locationClient = null
//            locationOption = null
        }
    }

    //    默认的定位参数
    private fun getDefaultOption(): AMapLocationClientOption {
        val mOption = AMapLocationClientOption()
        mOption.locationMode =
            AMapLocationClientOption.AMapLocationMode.Hight_Accuracy //可选，设置定位模式，可选的模式有高精度、仅设备、仅网络。默认为高精度模式
        mOption.isGpsFirst = false //可选，设置是否gps优先，只在高精度模式下有效。默认关闭
        mOption.httpTimeOut = 10000 //可选，设置网络请求超时时间。默认为30秒。在仅设备模式下无效
        mOption.interval = 2000 //可选，设置定位间隔。默认为2秒
        mOption.isNeedAddress = true //可选，设置是否返回逆地理地址信息。默认是true
        mOption.isOnceLocation = false //可选，设置是否单次定位。默认是false
        mOption.isOnceLocationLatest =
            false //可选，设置是否等待wifi刷新，默认为false.如果设置为true,会自动变为单次定位，持续定位时不要使用
        AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTP) //可选， 设置网络请求的协议。可选HTTP或者HTTPS。默认为HTTP
        mOption.isSensorEnable = false //可选，设置是否使用传感器。默认是false
        mOption.isWifiScan =
            true //可选，设置是否开启wifi扫描。默认为true，如果设置为false会同时停止主动刷新，停止以后完全依赖于系统刷新，定位位置可能存在误差
        mOption.isLocationCacheEnable = true //可选，设置是否使用缓存定位，默认为true
        mOption.geoLanguage =
            AMapLocationClientOption.GeoLanguage.DEFAULT //可选，设置逆地理信息的语言，默认值为默认语言（根据所在地区选择语言）
        return mOption
    }

    //    定位按钮的点击事件
    @SuppressLint("MissingPermission")
    private fun setupButton(){
        binding.favourite.setOnClickListener {
            //添加收藏按钮
            addFavouriteDialog()
        }
        binding.getlocationContainer.setOnClickListener {
            //获取最新定位位置按钮
            getLastLocation()
        }

        if (viewModel.isStarted) {
            binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
            binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
        }

        //开始模拟按钮
        binding.bottomSheetContainer.startSpoofing.setOnClickListener {
            viewModel.update(true, lat, lon)
            mLatLng.let {
                mMarker?.position = it!!
            }
            mMarker?.isVisible = true
            binding.bottomSheetContainer.startSpoofing.visibility = View.GONE
            binding.bottomSheetContainer.stopButton.visibility = View.VISIBLE
            lifecycleScope.launch {
                //todo:模拟位置并获取详细地址显示到通知
                showStartNotification(mLatLng.toString())
                //要通过geo转地址
//                mLatLng?.getAddress(this@AmapActivity)?.let { address ->
//                    address.collect{ value ->
//                        showStartNotification(value)
//                    }
//                }
            }
            showToast(getString(R.string.location_set))
        }
        //停止模拟按钮
        binding.bottomSheetContainer.stopButton.setOnClickListener {
            mLatLng.let {
                viewModel.update(false, it!!.latitude, it.longitude)
            }
            mMarker?.isVisible = false
            binding.bottomSheetContainer.stopButton.visibility = View.GONE
            binding.bottomSheetContainer.startSpoofing.visibility = View.VISIBLE
            cancelNotification()
            showToast(getString(R.string.location_unset))
        }

    }

    //    设置侧滑菜单
    private fun setDrawer() {
        supportActionBar?.setDisplayShowTitleEnabled(false)
        val mDrawerToggle = object : ActionBarDrawerToggle(
            this,
            binding.container,
            binding.toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        ) {
            override fun onDrawerClosed(view: View) {
                super.onDrawerClosed(view)
                invalidateOptionsMenu()
            }

            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                invalidateOptionsMenu()
            }
        }
        binding.container.setDrawerListener(mDrawerToggle)

    }

    /**
     * 设置底部sheet
     */
    private fun setBottomSheet(){
        val progress = binding.bottomSheetContainer.search.searchProgress

        val bottom = BottomSheetBehavior.from(binding.bottomSheetContainer.bottomSheet)
        with(binding.bottomSheetContainer){

            search.searchBox.setOnEditorActionListener { v, actionId, _ ->

                if (actionId == EditorInfo.IME_ACTION_SEARCH) {

                    if (isNetworkConnected()) {
                        lifecycleScope.launch(Dispatchers.Main) {
//                            37.7749,-122.4194
                            val  getInput = v.text.toString()
                            if (getInput.isNotEmpty()){
                                //通过关键词名称查询
//                                poiSearch(getInput, "深圳")
                                //通过经纬度查询地址
//                                geoSearch(LatLonPoint(22.62140, 113.94173))
                                //通过地址查询经纬度
//                                getLatlon(getInput, "0755")
                                getSearchAddress(getInput).let {
                                    it.collect { result ->
                                        when(result) {
                                            is SearchProgresss.Progress -> {
                                                progress.visibility = View.VISIBLE
                                            }
                                            is SearchProgresss.Complete -> {
                                                lon = result.lon
                                                moveMapToNewLocation(true)
                                            }

                                            is SearchProgresss.Fail -> {
                                                showToast(result.error!!)
                                            }
                                            else -> {
                                                //kotlin 1.6特性不添加报错
                                            }
                                        }
                                    }
                                }


                            }
                        }
                    } else {
                        showToast(getString(R.string.no_internet))
                    }
                    return@setOnEditorActionListener true
                }
                return@setOnEditorActionListener false
            }

        }


        binding.mapContainer.map.setOnApplyWindowInsetsListener { _, insets ->

            val topInset: Int = insets.systemWindowInsetTop
            val bottomInset: Int = insets.systemWindowInsetBottom
            bottom.peekHeight = binding.bottomSheetContainer.searchLayout.measuredHeight + bottomInset

            val searchParams = binding.bottomSheetContainer.searchLayout.layoutParams as MarginLayoutParams
            searchParams.bottomMargin  = bottomInset + searchParams.bottomMargin
            binding.navView.setPadding(0,topInset,0,0)

            insets.consumeSystemWindowInsets()
        }

        bottom.state = BottomSheetBehavior.STATE_COLLAPSED

    }

    /**
     * 设置颜色
     */
    private fun setupMonet() {
        val secondaryBackground = monet.getBackgroundColorSecondary(this)
        val background = monet.getBackgroundColor(this)
        binding.bottomSheetContainer.search.searchBox.backgroundTintList = ColorStateList.valueOf(secondaryBackground!!)
        val root =  binding.bottomSheetContainer.root.background as GradientDrawable
        root.setColor(ColorUtils.setAlphaComponent(headerBackground,235))
        binding.getlocationContainer.backgroundTintList = ColorStateList.valueOf(background)
        binding.favourite.backgroundTintList = ColorStateList.valueOf(background)

    }


    /**
     * 设置导航栏
     */
    private fun setUpNavigationView() {
        binding.navView.setNavigationItemSelectedListener {
            when(it.itemId){

                R.id.get_favourite -> {
                    openFavouriteListDialog()
                }
                R.id.settings -> {
                    startActivity(Intent(this,SettingsActivity::class.java))
                }
                R.id.about -> {
                    aboutDialog()
                }
            }
            binding.container.closeDrawer(GravityCompat.START)
            true
        }

    }

    /**
     * 初始化地图
     */
    private fun initializeMap() {
        //初始化地图
        Log.d(TAG, "initializeMap: ")
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        if (mapFragment != null && mapFragment.map != null) {
            aMap = mapFragment.map
            aMap.setOnMapLoadedListener(this);
            aMap.setOnMapClickListener(this);
            aMap.setTrafficEnabled(true);// 显示实时交通状况
        }
//        mapFragment?.getMapAsync(this)
    }

    /**
     * 检查是否开启模块
     */
    private fun isModuleEnable(){
        //检查是否开启模块
        viewModel.isXposed.observe(this) { isXposed ->
            xposedDialog?.dismiss()
            xposedDialog = null
            if (!isXposed) {
                xposedDialog = MaterialAlertDialogBuilder(this).run {
                    setTitle(R.string.error_xposed_module_missing)
                    setMessage(R.string.error_xposed_module_missing_desc)
                    setCancelable(BuildConfig.DEBUG)
                    show()
                }
            }

        }

    }

    /**
     * 地图加载完毕
     */
    override fun onMapLoaded() {
        Log.d(TAG, "onMapLoaded: ")
        //地图准备完毕
        with(aMap){
            mapType = viewModel.mapType
            lat = viewModel.getLat
            lon  = viewModel.getLng
            mLatLng = LatLng(lat, lon)
            mLatLng.let {
                mMarker = addMarker(
                    MarkerOptions().position(it!!).draggable(false)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)).visible(false)
                )
                animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
            }
//            setPadding(0,80,0,170)
            setOnMarkerClickListener(this@AmapActivity)
//            setOnMapClickListener(this@AmapActivity)
            if (viewModel.isStarted){
                mMarker?.let {
                    it.isVisible = true
                    it.showInfoWindow()
                }
            }
        }
    }

    /**
     * 地图点击事件
     */
    override fun onMapClick(latLng: LatLng) {
        Log.d(TAG, "onMapClick: ")
        //点击地图
        mLatLng = latLng
        mMarker?.let { marker ->
            mLatLng.let {
                marker.position = it!!
                marker.isVisible = true
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, zoom))
                lat = it.latitude
                lon = it.longitude
            }
        }
    }

    /**
     * 地图上移动到新的点
     */
    private fun moveMapToNewLocation(moveNewLocation: Boolean) {
        //地图上移动到新的点
        if (moveNewLocation) {
            mLatLng = LatLng(lat, lon)
            mLatLng.let { latLng ->
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng!!, zoom))
                mMarker?.apply {
                    position = latLng
                    isVisible = true
                    showInfoWindow()
                }
            }
        }

    }


    override fun onResume() {
        super.onResume()
        viewModel.updateXposedState()
    }


    /**
     * 关于
     */
    private fun aboutDialog(){
        //关于
        alertDialog = MaterialAlertDialogBuilder(this)
        layoutInflater.inflate(R.layout.about,null).apply {
            val  tittle = findViewById<TextView>(R.id.design_about_title)
            val  version = findViewById<TextView>(R.id.design_about_version)
            val  info = findViewById<TextView>(R.id.design_about_info)
            tittle.text = getString(R.string.app_name)
            version.text = BuildConfig.VERSION_NAME
            info.text = getString(R.string.about_info)
        }.run {
            alertDialog.setView(this)
            alertDialog.show()
        }
    }


    /**
     * 添加收藏
     */
    private fun addFavouriteDialog(){
        //添加收藏
        alertDialog =  MaterialAlertDialogBuilder(this).apply {
            val view = layoutInflater.inflate(R.layout.dialog_layout,null)
            val editText = view.findViewById<EditText>(R.id.search_edittxt)
            setTitle(getString(R.string.add_fav_dialog_title))
            setPositiveButton(getString(R.string.dialog_button_add)) { _, _ ->
                val s = editText.text.toString()
                if (!mMarker?.isVisible!!){
                    showToast(getString(R.string.location_not_select))
                }else{
                    viewModel.storeFavorite(s, lat, lon)
                    viewModel.response.observe(this@AmapActivity){
                        if (it == (-1).toLong()) showToast(getString(R.string.cant_save)) else showToast(getString(R.string.save))
                    }
                }
            }
            setView(view)
            show()
        }

    }


    /**
     * 打开收藏列表
     */
    private fun openFavouriteListDialog() {
        //打开收藏列表
        getAllUpdatedFavList()
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(getString(R.string.favourites))
        val view = layoutInflater.inflate(R.layout.fav,null)
        val  rcv = view.findViewById<RecyclerView>(R.id.favorites_list)
        rcv.layoutManager = LinearLayoutManager(this)
        rcv.adapter = favListAdapter
        favListAdapter.onItemClick = {
            it.let {
                lat = it.lat!!
                lon = it.lng!!
            }
            moveMapToNewLocation(true)
            if (dialog.isShowing) dialog.dismiss()

        }
        favListAdapter.onItemDelete = {
            viewModel.deleteFavourite(it)
        }
        alertDialog.setView(view)
        dialog = alertDialog.create()
        dialog.show()

    }


    private fun getAllUpdatedFavList(){
        //全部更新收藏列表
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                viewModel.doGetUserDetails()
                viewModel.allFavList.collect {
                    favListAdapter.submitList(it)
                }
            }
        }

    }

    /**
     * 更新窗口
     */
    private fun updateDialog(){
        //更新窗口
        alertDialog = MaterialAlertDialogBuilder(this)
        alertDialog.setTitle(R.string.update_available)
        alertDialog.setMessage(update?.changelog)
        alertDialog.setPositiveButton(getString(R.string.update_button)) { _, _ ->
            MaterialAlertDialogBuilder(this).apply {
                val view = layoutInflater.inflate(R.layout.update_dialog, null)
                val progress = view.findViewById<LinearProgressIndicator>(R.id.update_download_progress)
                val cancel = view.findViewById<AppCompatButton>(R.id.update_download_cancel)
                setView(view)
                cancel.setOnClickListener {
                    viewModel.cancelDownload(this@AmapActivity)
                    dialog.dismiss()
                }
                lifecycleScope.launch {
                    viewModel.downloadState.collect {
                        when (it) {
                            is MainViewModel.State.Downloading -> {
                                if (it.progress > 0) {
                                    progress.isIndeterminate = false
                                    progress.progress = it.progress
                                }
                            }
                            is MainViewModel.State.Done -> {
                                viewModel.openPackageInstaller(this@AmapActivity, it.fileUri)
                                viewModel.clearUpdate()
                                dialog.dismiss()
                            }

                            is MainViewModel.State.Failed -> {
                                Toast.makeText(
                                    this@AmapActivity,
                                    R.string.bs_update_download_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                                dialog.dismiss()

                            }
                            else -> {}
                        }
                    }
                }
                update?.let { it ->
                    viewModel.startDownload(this@AmapActivity, it)
                } ?: run {
                    dialog.dismiss()
                }
            }.run {
                dialog = create()
                dialog.show()
            }
        }
        dialog = alertDialog.create()
        dialog.show()

    }

    /**
     * 更新检查
     */
    private fun updateChecker(){
        //更新检查
        lifecycleScope.launchWhenResumed {
            viewModel.update.collect{
                if (it!= null){
                    updateDialog()
                }
            }
        }
    }



    //根据中文地址搜索
    private fun poiSearch(str: String, city: String){
        //poi搜索
        val mPoiSearchQuery = PoiSearchV2.Query(str, "", city)
//        mPoiSearchQuery.requireSubPois(true) //true 搜索结果包含POI父子关系; false

        mPoiSearchQuery.pageSize = 10
//        mPoiSearchQuery.pageNum = 0
        val poiSearch = PoiSearchV2(this@AmapActivity, mPoiSearchQuery)
        poiSearch.setOnPoiSearchListener(this)
        poiSearch.searchPOIAsyn()
    }

    /**
     * poi搜索回调
     */
    override fun onPoiSearched(poiResult: PoiResultV2?, rCode: Int) {
        Log.d(TAG, "onPoiSearched: ")
        if (rCode == 1000) {
            poiResult?.let {
                if (it.pois.size > 0) {
                    for (i in 0 until it.pois.size) {
                        val poi = it.pois[i]
                        Log.d(TAG, "onPoiSearched: ${poi.latLonPoint.toString()}")
                    }
                }
            }
        } else{
            Toast.makeText(this@AmapActivity, "搜索失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * poi item搜索回调
     */
    override fun onPoiItemSearched(p0: PoiItemV2?, p1: Int) {
        Log.d(TAG, "onPoiItemSearched: ")
    }

    //通过经纬度查询但是没有使用高德坐标
    private suspend fun getSearchAddress(address: String) = callbackFlow {
        withContext(Dispatchers.IO){
            trySend(SearchProgresss.Progress)
            val matcher: Matcher =
                Pattern.compile("[-+]?\\d{1,3}([.]\\d+)?, *[-+]?\\d{1,3}([.]\\d+)?").matcher(address)
            if (matcher.matches()){
                delay(3000)
                trySend(SearchProgresss.Complete(matcher.group().split(",")[0].toDouble(),matcher.group().split(",")[1].toDouble()))
            } else {
                val geocoder = Geocoder(this@AmapActivity)
                val addressList: List<Address>? = geocoder.getFromLocationName(address,3)

                try {
                    addressList?.let {
                        if (it.size == 1){
                            trySend(SearchProgresss.Complete(addressList[0].latitude, addressList[0].longitude))
                        }else {
                            trySend(SearchProgresss.Fail(getString(R.string.address_not_found)))
                        }
                    }
                } catch (io : IOException){
                    trySend(SearchProgresss.Fail(getString(R.string.no_internet)))
                }
            }
        }

        awaitClose { this.cancel() }
    }

    /**
     * 通过经纬度查询详细地址
     */
    private fun geoSearch(point: LatLonPoint){
        //第一个参数表示一个Latlng，第二参数表示范围多少米，第三个参数表示是火系坐标系还是GPS原生坐标系
        val regeoCodeQuery = RegeocodeQuery(point, 500f, GeocodeSearch.AMAP)
        val geocodeSearch = GeocodeSearch(this@AmapActivity)
        geocodeSearch.setOnGeocodeSearchListener(this)
        geocodeSearch.getFromLocationAsyn(regeoCodeQuery)// 设置异步逆地理编码请求
    }

    /**
     * 逆地理编码回调 纬度查详细地址
     */
    override fun onRegeocodeSearched(regeocodeResult: RegeocodeResult?, rCode: Int) {
        if (rCode == 1000) {
            if (regeocodeResult!= null && regeocodeResult.regeocodeAddress!= null && regeocodeResult.regeocodeAddress.formatAddress != null) {
                val address = regeocodeResult.regeocodeAddress.formatAddress + "附近"
                showToast(address)
                Log.d(TAG, "onRegeocodeSearched: " + address)
            }
        }
    }


    /**
     * 通过详细地址查询经纬度，高德坐标系
     */
    private fun getLatlon(address: String, city: String) {
        showToast("getLatlon")
        Log.d(TAG, "getLatlon: ")
        val query = GeocodeQuery(address, city) // 第一个参数表示地址，第二个参数表示查询城市，中文或者中文全拼，citycode、adcode，
        val geocodeSearch = GeocodeSearch(this@AmapActivity)
        geocodeSearch.setOnGeocodeSearchListener(this)
        geocodeSearch.getFromLocationNameAsyn(query) // 设置同步地理编码请求
        Log.d(TAG, "getLatlon: 222")
    }


    /**
     * 地理编码回调 详细地址查经纬度
     */
    override fun onGeocodeSearched(regeocodeResult: GeocodeResult?, rCode: Int) {
        showToast("onGeocodeSearched")
        if (rCode == 1000) {
            if (regeocodeResult!= null && regeocodeResult.geocodeAddressList!= null && regeocodeResult.geocodeAddressList.size > 0) {
                val address = regeocodeResult.geocodeAddressList[0].formatAddress
                val latLonPoint = regeocodeResult.geocodeAddressList[0].latLonPoint
                lat = latLonPoint.latitude
                lon = latLonPoint.longitude
                moveMapToNewLocation(true)
                showToast(address)
                Log.d(TAG, "onGeocodeSearched: " + address)
            }
        }
    }


    /**
     * 点击marker回调
     */
    override fun onMarkerClick(marker: Marker?): Boolean {
        val clipboard: ClipboardManager =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = mLatLng?.latitude.toString() + "," + mLatLng?.longitude.toString()
        val clip = ClipData.newPlainText("latlng", text)
        clipboard.setPrimaryClip(clip)
        showToast("已复制到剪贴板")
        return true
    }

    /**
     * 显示开始通知
     */
    private fun showStartNotification(address: String){
        //显示开始通知
        notificationsChannel.showNotification(this){
            it.setSmallIcon(R.drawable.ic_stop)
            it.setContentTitle(getString(R.string.location_set))
            it.setContentText(address)
            it.setAutoCancel(true)
            it.setCategory(Notification.CATEGORY_EVENT)
            it.priority = NotificationCompat.PRIORITY_HIGH
        }

    }

    /**
     * 取消通知
     */
    private fun cancelNotification(){
        //取消通知
        notificationsChannel.cancelAllNotifications(this)
    }

    // Get current location
    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        Log.d(TAG, "getLastLocation: ")
        //获取最新位置
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                //初始化client
                try {
                    locationClient = AMapLocationClient(this.applicationContext)
                    locationOption = getDefaultOption()
                    //设置定位参数
                    locationClient.setLocationOption(locationOption)
                    // 设置定位监听
                    locationClient.setLocationListener(this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                startLocation();
            } else {
                showToast("开启定位权限信息")
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }

    //定位监听器
    override fun onLocationChanged(location: AMapLocation?) {
        if (null != location) {
            val sb = StringBuffer()
            //errCode等于0代表定位成功，其他的为定位失败，具体的可以参照官网定位错误码说明
            if (location.errorCode == 0) {
                lat = location.latitude
                lon = location.longitude
                moveMapToNewLocation(true)
                stopLocation()
                sb.append("定位成功".trimIndent())
                sb.append("定位类型: ${location.locationType}".trimIndent())
                sb.append("经度: ${location.longitude}".trimIndent())
                sb.append("纬度: ${location.latitude}".trimIndent())
                sb.append("精度: ${location.accuracy}米".trimIndent())
                sb.append("提供者: ${location.provider}".trimIndent())
                sb.append("速度: ${location.speed}米/秒".trimIndent())
                sb.append("角度: ${location.bearing}".trimIndent())
                // 获取当前提供定位服务的卫星个数
                sb.append("星数: ${location.satellites}".trimIndent())
                sb.append("国家: ${location.country}".trimIndent())
                sb.append("省: ${location.province}".trimIndent())
                sb.append("市: ${location.city}".trimIndent())
                sb.append("城市编码: ${location.cityCode}".trimIndent())
                sb.append("区: ${location.district}".trimIndent())
                sb.append("区域码: ${location.adCode}".trimIndent())
                sb.append("地址: ${location.address}".trimIndent())
                sb.append("兴趣点: ${location.poiName}".trimIndent())
                //定位完成的时间
                //                    sb.append("定位时间: " + Utils.formatUTC(location.getTime(), "yyyy-MM-dd HH:mm:ss") + "\n");
                //todo:可能需要协程
                binding.bottomSheetContainer.firstAddress.setText(location.address);
            } else {
                //定位失败
                sb.append("定位失败".trimIndent())
                sb.append("错误码:${location.errorCode}".trimIndent())
                sb.append("错误信息:${location.errorInfo}".trimIndent())
                sb.append("错误描述:${location.locationDetail}".trimIndent())
            }
            sb.append("***定位质量报告***").append("\n")
            sb.append("* WIFI开关：")
                .append(if (location.locationQualityReport.isWifiAble) "开启" else "关闭")
                .append("\n")
            sb.append("* GPS状态：")
                .append(getGPSStatusString(location.locationQualityReport.gpsStatus))
                .append("\n")
            sb.append("* GPS星数：").append(location.locationQualityReport.gpsSatellites)
                .append("\n")
            sb.append("* 网络类型： " + location.locationQualityReport.networkType).append("\n")
            sb.append("* 网络耗时：" + location.locationQualityReport.netUseTime).append("\n")
            sb.append("****************").append("\n")
            //定位之后的回调时间
            //                sb.append("回调时间: " + Utils.formatUTC(System.currentTimeMillis(), "yyyy-MM-dd HH:mm:ss") + "\n");
            //解析定位结果，
            val result = sb.toString()
            showToast(result)
        } else {
            showToast("locationListener: 定位失败")
//            requestNewLocationData()
        }
    }

    /**
     * 根据定位结果返回定位信息的字符串
     *
     * @param location 定位结果
     * @return 定位信息字符串
     */
    private fun getGPSStatusString(statusCode: Int): String? {
        var str = ""
        when (statusCode) {
            AMapLocationQualityReport.GPS_STATUS_OK -> str = "GPS状态正常"
            AMapLocationQualityReport.GPS_STATUS_NOGPSPROVIDER -> str =
                "手机中没有GPS Provider，无法进行GPS定位"

            AMapLocationQualityReport.GPS_STATUS_OFF -> str = "GPS关闭，建议开启GPS，提高定位质量"
            AMapLocationQualityReport.GPS_STATUS_MODE_SAVING -> str =
                "选择的定位模式中不包含GPS定位，建议选择包含GPS定位的模式，提高定位质量"

            AMapLocationQualityReport.GPS_STATUS_NOGPSPERMISSION -> str =
                "没有GPS定位权限，建议开启gps定位权限"
        }
        return str
    }


    /**
     * 判断GPS是否开启，GPS或者AGPS开启一个就认为是开启的
     *
     * @return true 表示开启
     */
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }


    /**
     * 检车权限
     *
     * @return true 表示权限开启
     */
    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }


    /**
     * 申请权限
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_ID
        )
    }


    /**
     * 权限回调
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            getLastLocation()
        }
    }

}

/**
 * 搜索进度条
 */
sealed class SearchProgresss {
    //搜索进度条
    object Progress : SearchProgresss()
    data class Complete(val lat: Double , val lon : Double) : SearchProgresss()
    data class Fail(val error: String?) : SearchProgresss()
}