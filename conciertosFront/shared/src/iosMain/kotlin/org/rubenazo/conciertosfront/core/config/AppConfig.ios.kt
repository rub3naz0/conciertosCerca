package org.rubenazo.conciertosfront.core.config

import platform.Foundation.NSBundle

/**
 * Dev backend override for iOS, read from the `BackendBaseUrl` key in Info.plist so a
 * dev machine's LAN IP can be used without recompiling Kotlin (a physical iPhone cannot
 * reach the Mac through `localhost`; it needs the Mac's LAN IP, e.g.
 * http://192.168.x.x:8080, which also works from the simulator). Blank or absent means
 * production.
 */
actual val platformDevBaseUrl: String? =
    NSBundle.mainBundle.objectForInfoDictionaryKey("BackendBaseUrl") as? String
