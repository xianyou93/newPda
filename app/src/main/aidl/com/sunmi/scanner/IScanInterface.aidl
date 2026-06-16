package com.sunmi.scanner;

import android.view.KeyEvent;

interface IScanInterface {
    void sendKeyEvent(in KeyEvent key);   // 自定义按键触发扫码
    void scan();                           // 触发开始扫码
    void stop();                           // 触发停止扫码
    int getScannerModel();                 // 获取扫码头类型
}
