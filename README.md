# Basic Video Cut Bar
Simple range seek bar with preview images
## Preview 
![alt text](https://github.com/ngtien137/Basic-Video-Cut-Bar/blob/master/git_resources/preview_i.gif)
## Getting Started
### Configure build.gradle (Project)
* Add these lines:
```gradle
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
### Configure build gradle (Module):
* Import module base:
```gradle
dependencies {
  implementation 'com.github.ngtien137:Basic-Video-Cut-Bar:TAG'
}
```
* You can get version of this module [here](https://jitpack.io/#ngtien137/Basic-Video-Cut-Bar)
## All Attributes 
```xml
<com.luza.videocutbar.VideoCutBar
    android:background="#fdf"
    android:paddingStart="10dp"
    android:paddingEnd="10dp"
    android:id="@+id/videoCutBar"
    android:layout_width="wrap_content"

    android:layout_height="wrap_content"
    app:layout_constraintBottom_toBottomOf="parent"

    app:layout_constraintLeft_toLeftOf="parent"
    app:layout_constraintRight_toRightOf="parent"

    app:layout_constraintTop_toTopOf="parent"

    app:vcb_number_image_preview="8"
    app:vcb_number_image_padding_vertical="4dp"
    app:vcb_number_image_padding_horizontal="0dp"

    app:vcb_show_thumb_cut="true"
    app:vcb_thumb_left="@drawable/ic_thumb_left_default"
    app:vcb_thumb_right="@drawable/ic_thumb_right_default"

    app:vcb_thumb_cut_shadow_color="#ff0"
    app:vcb_thumb_cut_shadow_radius="4dp"
    app:vcb_thumb_touch_extra_area="10dp"
    app:vcb_thumb_height="80dp"
    app:vcb_thumb_width="20dp"
    app:vcb_thumb_overlay_tail_color="#66A3A301"
    app:vcb_progress_fix_center_progress_mode="inside" //Moving thumb cut pull center progress mode

    app:vcb_progress_thumb_width="1dp"
    app:vcb_progress_thumb_color="#ff0000"
    app:vcb_progress_thumb_spread_color="#4400ff00"
    app:vcb_progress_thumb_height="90dp"
    app:vcb_progress_overlay_mode="both"
    app:vcb_thumb_overlay_tail_inside_color="#0000"

    app:vcb_indicator_show_mode="visible"
    app:vcb_indicator_position="bottom"
    app:vcb_indicator_size="10dp"
    app:vcb_indicator_spacing="4dp"
    app:vcb_indicator_format="mm:ss"
    app:vcb_indicator_font="@font/poppins"

    app:vcb_progress_min="20"
    app:vcb_progress_max="40"
    app:vcb_thumb_cut_min_progress="2000"

    app:vcb_video_bar_background_color="#fff"
    app:vcb_video_bar_border_corners="4dp"
    app:vcb_video_bar_height="80dp" />
```
## Listeners and callbacks

```kotlin
  //Set value for center progress:
  videoCutBar.setCenterProgress(500L)
  //Set path video:
  videoCutBar.videoPath = path //Desprecated in version 1.5
  From version 1.5: videoCutBar.setVideoPath(path,useHistoryBitmap:Boolean)

  videoCutBar.loadingListener = object : VideoCutBar.ILoadingListener{
    override fun onLoadingStart() {
        //Start loading image preview after setting videoPath
    }

    override fun onLoadingComplete() {
        
    }

    override fun onLoadingError() {
        
    }
  }
  
   videoCutBar.rangeChangeListener = object : VideoCutBar.OnCutRangeChangeListener{
      override fun onRangeChanged(
          videoCutBar: VideoCutBar?,
          minValue: Long,
          maxValue: Long,
          thumbIndex: Int
      ) {

      }

      override fun onRangeChanging(
          videoCutBar: VideoCutBar?,
          minValue: Long,
          maxValue: Long,
          thumbIndex: Int
      ) {
          
      }
      
      override fun onStartTouchBar() {
          eLog("Start Touch Bar")
      }

      override fun onStopTouchBar() {
          eLog("Stop Touch Bar")
      }
  }
```
