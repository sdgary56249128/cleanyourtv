# Personal wallpaper depth

This source folder is shared by the Chromecast font build and Sony video build. The reference launcher remains unchanged.

The wallpaper moves with vertical app-row navigation. The top row uses a closer view; lower rows zoom out and shift the background upward slightly. Horizontal navigation within a row keeps the same depth. The finite 320 ms transition transforms the existing ImageView texture, with no extra bitmap, continuous loop, network or video decoder. Background transform callbacks stop while idle and when Home is hidden. The existing reduced-motion and animation preferences disable the effect.

Home → Desktop settings → Wallpaper depth offers Default, Off, Subtle and Standard. The default is Standard. The minimum scale leaves enough cropped margin for the vertical offset, avoiding exposed edges at every intermediate progress. This is perspective applied to the whole photograph, not a separate foreground/sky depth map. Sony's optional local video layer follows the same camera transform when enabled.

[DepthGeometryTest](src/test/java/com/gathin/tvhome/DepthGeometryTest.java) checks row progress, top/bottom behavior, empty and single-row grids, disabled mode and edge coverage across 720p/1080p/2160p sizes.

[The shared verifier](../verification/README.md) supports both real TVs, with Chromecast selected by default and paused devices blocked before connection. Its read-only HomeDiagnosticsProvider is protected by Android's WRITE_SECURE_SETTINGS permission. It reports transforms, animation state and settings/order fingerprints to the paired shell; it accepts no preference writes, key injection or other commands. It reports neither Wi-Fi names nor account/NAS information.
