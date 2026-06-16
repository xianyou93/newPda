
# 一. 概述

# 二. 使用说明

### 方式一
商米提供了带摄像头设备的扫码SDK，并具有以下五个优势：
：相对ZXing等开源扫码方案有更高的识读成功率，污损扭曲条码解码效果更好；
：与商米的设备完美适配，软硬件结合可以保证功能的高效稳定。
：已支持EAN-8, EAN-13, UPC-A, UPC-E, Codabar, Code39, Code93, Code128, ISBN10, ISBN13, ISSN, DataBar, DataBar Expanded, Interleaved 2 of 5, QR Code, MicroQR, PDF417, MicroPDF417，DataMatrix，AZTEC, Hanxin.
开发者的应用调用SUNMIUI系统集成的扫码模块完成扫码，获取返回值，该方法简单易用。
开发者自己开发相机预览扫码界面，调用商米扫码SDK完成图像数据的解析，该方式相对复杂，但提供了较高的灵活度。
为了降低开发难度，SUNMI OS内置了一个扫码的模块，开发者在项目需要调用扫码的地方通过startActivityForResult()调用商米的扫码模块，然后在onActivityResult()方法中接收扫码结果返回值。调用商米的扫码模块的示例代码如下：

```java
对于target SDK 大于等于 Android 11 的项目需要在配置文件 AndroidManifest.xml 增加一下声明：\
 <manifest>\
   <!-- Android target sdk >= 30 add -->\
   <queries> \
    <package android:name=\\
```
