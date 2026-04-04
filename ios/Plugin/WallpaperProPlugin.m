#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(WallpaperProPlugin, "WallpaperPro",
    CAP_PLUGIN_METHOD(setWallpaper,             CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(setGradientWallpaper,     CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(setRandomWallpaper,       CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(setLiveWallpaper,         CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(setDepthWallpaper,        CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(schedule24HourWallpapers, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearSchedule,            CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getWallpaperInfo,         CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getHistory,               CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(undoWallpaper,            CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearHistory,             CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(checkPermissions,         CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestPermissions,       CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(preloadWallpaper,         CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearCache,               CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearVideoCache,          CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getCacheInfo,             CAPPluginReturnPromise);
)
