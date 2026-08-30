package com.kes.casacontrol

import android.content.Intent
import android.widget.RemoteViewsService

class SceneWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SceneRemoteViewsFactory(this.applicationContext)
    }
}
