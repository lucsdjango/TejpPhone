package com.example.tejpphone

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.isdk.IsdkGrabMovementType
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkPanelResize
import com.meta.spatial.isdk.ResizeMode
import com.meta.spatial.runtime.LayerConfig
import com.meta.spatial.runtime.ReferenceSpace
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import java.lang.ref.WeakReference

/**
 * Mixed reality on a Meta Quest. The room shows through the headset cameras (passthrough) and
 * the drawing screen floats in it on a panel, which can be grabbed, moved and resized, for
 * example to cover the real fence. The panel shows MixedRealityPanelActivity, which is
 * see-through around the fence.
 *
 * The panel starts at the size the normal window had. Where that window was in the room is not
 * available to apps, so the panel starts straight ahead instead, where the user was looking.
 *
 * Built with the Meta Spatial SDK, following Meta's HybridSample.
 */
class ImmersiveActivity : AppSystemActivity() {

    /** Starting panel size in metres, from the normal window's size in dp if it was passed. */
    private val panelSize: Vector2 by lazy {
        val w = intent.getFloatExtra(MainActivity.EXTRA_WINDOW_WIDTH_DP, 0f)
        val h = intent.getFloatExtra(MainActivity.EXTRA_WINDOW_HEIGHT_DP, 0f)
        if (w > 0f && h > 0f) Vector2(w / DP_PER_METRE, h / DP_PER_METRE)
        else Vector2(DEFAULT_WIDTH, DEFAULT_HEIGHT)
    }

    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        systemManager.registerSystem(PlacePanelSystem())
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    override fun onSceneReady() {
        super.onSceneReady()
        scene.setReferenceSpace(ReferenceSpace.LOCAL_FLOOR)
        scene.enablePassthrough(true)
        scene.enableHolePunching(true)
        // Moving with the thumbstick would slide the panel against the real room.
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    }

    override fun registerPanels(): List<PanelRegistration> = listOf(
        PanelRegistration(R.id.fence_panel) {
            activityClass = MixedRealityPanelActivity::class.java
            config {
                themeResourceId = R.style.Theme_TejpPhone_SeeThrough
                width = panelSize.x
                height = panelSize.y
                layoutWidthInDp = panelSize.x * DP_PER_METRE
                layoutHeightInDp = panelSize.y * DP_PER_METRE
                // A compositor layer keeps the text sharp; transparent pixels stay see-through.
                layerConfig = LayerConfig()
                enableTransparent = true
                includeGlass = false
            }
        },
    )

    /** Back to the normal window in the Quest home, on the same drawing. */
    private fun leaveMixedReality() {
        val window = Intent(applicationContext, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            applicationContext, 0, window,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Horizon OS opens the home and then the window from the pending intent.
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("extra_launch_in_home_pending_intent", pending),
        )
        finish()
    }

    /** Puts the panel in front of the user as soon as the headset position is known. */
    private class PlacePanelSystem : SystemBase() {
        private var placed = false

        override fun execute() {
            if (placed) return
            val head = systemManager.tryFindSystem<PlayerBodyAttachmentSystem>()
                ?.tryGetLocalPlayerAvatarBody()?.head ?: return
            val headPose = head.tryGetComponent<Transform>()?.transform ?: return
            if (headPose == Pose()) return

            val forward = headPose.q * Vector3(0f, 0f, 1f)
            val position = headPose.t + forward * PANEL_DISTANCE
            position.y = headPose.t.y - 0.1f
            Entity.create(
                listOf(
                    Panel(R.id.fence_panel),
                    Transform(Pose(position, Quaternion.lookRotationAroundY(forward))),
                    // Follows the hand freely, so it can be turned to line up with the fence.
                    IsdkGrabbable(enabled = true, movementType = IsdkGrabMovementType.Direct),
                    // Corner handles. Relayout gives the screen more room instead of magnifying it.
                    IsdkPanelResize(
                        enabled = true,
                        resizeMode = ResizeMode.Relayout,
                        minDimensions = Vector2(MIN_WIDTH, MIN_HEIGHT),
                        maxDimensions = Vector2(MAX_WIDTH, MAX_HEIGHT),
                        preserveAspectRatio = false,
                    ),
                ),
            )
            placed = true
        }
    }

    companion object {
        /** Panel sizes in metres, and how far in front of the user it starts. */
        private const val DEFAULT_WIDTH = 1.6f
        private const val DEFAULT_HEIGHT = 1.0f
        private const val MIN_WIDTH = 0.5f
        private const val MIN_HEIGHT = 0.4f
        private const val MAX_WIDTH = 4f
        private const val MAX_HEIGHT = 3f
        private const val PANEL_DISTANCE = 1.2f
        /**
         * Panel scale. A 1024 x 640 dp window becomes 1.28 x 0.8 m. Resizing keeps this, so a
         * bigger panel gets more room rather than bigger buttons.
         */
        private const val DP_PER_METRE = 800f

        private var current: WeakReference<ImmersiveActivity>? = null

        /** Called from the panel, which is a separate activity. */
        @JvmStatic
        fun leave() {
            current?.get()?.leaveMixedReality()
        }
    }
}
