package com.kes.casacontrol

import android.content.Intent
import android.widget.RemoteViewsService

class SceneWidgetServiceGrid : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return SceneRemoteViewsFactoryGrid(this.applicationContext)
    }
}