# Basic Video Cut Bar
Simple range seek bar with preview images
## Preview 
![alt text](https://github.com/ngtien137/Basic-Video-Cut-Bar/blob/master/git_resources/preview.gif)
## Getting Started
### Configure build.gradle (Project)
* Add these lines:
```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}
```
### Configure build gradle (Module):
* Import module base:
```
dependencies {
  implementation 'com.github.ngtien137:RangeIndicatorSeekBar:TAG'
}
```
* TAG has a prefixes such as ni, bi, ti, respectively, mean no indicator, bottom indicator and top indicator. Example:
```
dependencies {
  implementation 'com.github.ngtien137:RangeIndicatorSeekBar:ni_1.0'
}
```
* You can get version of this module [here](https://jitpack.io/#ngtien137/Basic-Video-Cut-Bar)
## All Attributes 
``` 
```
