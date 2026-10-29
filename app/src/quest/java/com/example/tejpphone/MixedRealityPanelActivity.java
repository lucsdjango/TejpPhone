package com.example.tejpphone;

/**
 * The drawing screen on the panel in mixed reality (see ImmersiveActivity). The same as the
 * normal window, except that the area around the fence is see-through and the menu has a way
 * back to the normal window.
 */
public class MixedRealityPanelActivity extends MainActivity {

    @Override
    protected boolean isMixedRealityPanel() {
        return true;
    }

    @Override
    protected void leaveMixedReality() {
        ImmersiveActivity.leave();
    }
}
