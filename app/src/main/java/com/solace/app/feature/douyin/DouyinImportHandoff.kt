package com.solace.app.feature.douyin

import com.solace.app.data.douyin.DouyinWork

/**
 * 解析结果从弹窗传到导入预览页的临时通道。
 *
 * 只是一次页面跳转的过渡数据，不落盘；进入预览页时取走并清空。
 */
object DouyinImportHandoff {

    private var pending: DouyinWork? = null

    fun put(work: DouyinWork) {
        pending = work
    }

    fun consume(): DouyinWork? = pending.also { pending = null }
}
