package com.zwz.lifelog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.zwz.lifelog.data.ThemePrefs
import com.zwz.lifelog.di.ServiceLocator
import com.zwz.lifelog.ui.nav.AppNav
import com.zwz.lifelog.ui.theme.LifeLogTheme
import com.zwz.lifelog.widget.WidgetRefresh

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repo = ServiceLocator.provideRepository(this)

        setContent {
            val mode by ThemePrefs.mode(this).collectAsState(initial = "auto")
            val dynamic by ThemePrefs.dynamicColor(this).collectAsState(initial = true)

            LifeLogTheme {
                AppNav(repo = repo, onDataChanged = { WidgetRefresh.request(this) })
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 退出时刷新小组件，保证桌面显示的天数是最新的
        WidgetRefresh.request(this)
    }
}
