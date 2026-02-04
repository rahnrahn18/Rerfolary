package com.kashif.folar.plugins

import com.kashif.folar.controller.CameraController

/**
 * Legacy interface that all camera plugins must implement.
 * 
 * @deprecated Use the new [com.kashif.folar.state.FolarPlugin] interface for better
 *             integration with Compose-first state management. This interface will be 
 *             removed in v2.0.0 (12-month deprecation timeline).
 * 
 * **Migration Guide:**
 * ```kotlin
 * // Old way
 * class MyPlugin : CameraPlugin {
 *     override fun initialize(cameraController: CameraController) {
 *         // Setup with controller
 *     }
 * }
 * 
 * // New way
 * class MyPlugin : FolarPlugin {
 *     private var stateHolder: FolarStateHolder? = null
 *     
 *     override fun onAttach(stateHolder: FolarStateHolder) {
 *         this.stateHolder = stateHolder
 *         val controller = stateHolder.getController()
 *         // Setup with controller and access to state
 *     }
 *     
 *     override fun onDetach() {
 *         // Cleanup
 *         stateHolder = null
 *     }
 * }
 * ```
 * 
 * @see com.kashif.folar.state.FolarPlugin
 */
@Deprecated(
    message = "Use FolarPlugin from com.kashif.folar.state package for Compose-first state management",
    replaceWith = ReplaceWith(
        "FolarPlugin",
        "com.kashif.folar.state.FolarPlugin"
    ),
    level = DeprecationLevel.WARNING
)
interface CameraPlugin {
    /**
     * Initializes the plugin with the provided [CameraController].
     *
     * @param cameraController The [CameraController] instance.
     */
    fun initialize(cameraController: CameraController)
}