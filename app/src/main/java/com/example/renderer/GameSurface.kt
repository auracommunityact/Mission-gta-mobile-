package com.example.renderer

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.runtime.GameRuntime

@Composable
fun GameSurface(
    runtime: GameRuntime?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        runtime?.onSurfaceCreated()
                    }

                    override fun surfaceChanged(
                        holder: android.view.SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        runtime?.onSurfaceChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        runtime?.onSurfaceDestroyed()
                    }
                })
            }
        },
        modifier = modifier
    )
}
