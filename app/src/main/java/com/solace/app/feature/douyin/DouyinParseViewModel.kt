package com.solace.app.feature.douyin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solace.app.data.douyin.DouyinParseException
import com.solace.app.data.douyin.DouyinParser
import com.solace.app.data.douyin.DouyinWork
import java.io.IOException
import kotlinx.coroutines.launch

data class DouyinParseUiState(
    val link: String = "",
    val parsing: Boolean = false,
    val error: String? = null,
    val result: DouyinWork? = null,
)

/** 弹窗里的解析状态；解析成功后把结果交给 [DouyinImportScreen]。 */
class DouyinParseViewModel : ViewModel() {

    private val parser = DouyinParser()

    var uiState by mutableStateOf(DouyinParseUiState())
        private set

    fun onLinkChange(value: String) {
        uiState = uiState.copy(link = value, error = null)
    }

    fun parse() {
        val state = uiState
        if (state.parsing) return
        if (state.link.isBlank()) {
            uiState = state.copy(error = "请先粘贴抖音分享链接")
            return
        }

        viewModelScope.launch {
            uiState = state.copy(parsing = true, error = null, result = null)
            try {
                val work = parser.parse(state.link)
                uiState = uiState.copy(parsing = false, result = work)
            } catch (e: Exception) {
                uiState = uiState.copy(parsing = false, error = friendlyMessage(e))
            }
        }
    }

    private fun friendlyMessage(e: Exception): String = when {
        e is DouyinParseException -> e.message ?: "解析失败，请稍后重试"
        e is IOException -> "网络请求失败，请检查网络后重试"
        else -> e.message ?: "解析失败，请稍后重试"
    }

    /** 结果已经被预览页消费，清掉避免下次打开弹窗重复跳转。 */
    fun clearResult() {
        uiState = uiState.copy(result = null)
    }

    /** 解析成功后清空输入，下次打开弹窗重新读剪贴板。 */
    fun clearLink() {
        uiState = uiState.copy(link = "")
    }
}
