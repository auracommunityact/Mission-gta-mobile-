package com.auracommunityact.missiongtamobile.renderer

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.auracommunityact.missiongtamobile.runtime.GameRuntime
import com.auracommunityact.missiongtamobile.runtime.GameRuntimeManager

@Composable
fun GameSurface(
    runtime: GameRuntime?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        (runtime as? GameRuntimeManager)?.onSurfaceCreatedWithSurface(holder.surface)
                            ?: runtime?.onSurfaceCreated()
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        runtime?.onSurfaceChanged(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        runtime?.onSurfaceDestroyed()
                    }
                })
            }
        },
        modifier = modifier
    )
}
