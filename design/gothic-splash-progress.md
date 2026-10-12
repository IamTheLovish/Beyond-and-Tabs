# gothic-splash progress (title + loading screen: cathedral rose window, "Developed by Lovish")

Spec and findings: design/splash.md.

Done
- tools/gen_splash_art.py: procedural layers -> bar/src/main/resources/assets/reignofnether/textures/gui/splash/
  (background, frame, window, rays, glow, title, byline); previews design/splash_preview_*.png
- tools/fonts/: Grenze Gotisch + UnifrakturMaguntia (both SIL OFL 1.1, licence files beside them; build-time only)
- hud/SplashArt.java: textures uploaded once from the mod file, layered scene, shimmer/parallax/candles, button restyle,
  loading-overlay drawing with progress rule
- mixin/TitleScreenMixin: draws SplashArt instead of panorama/logo/splash (fallback kept)
- mixin/LoadingOverlayMixin + LoadingOverlayAccessor + ForgeLoadingOverlayMixin (descriptor selector, require = 0)
- config: splash_reduce_motion (client config)
- NOTICE.md entry

Next
- CI green on edffc65 (build + bar with GameTests). Remaining: in-game check by lovish (no client can run here)

Known failures / unverified
- not run in a real client: mixin application in production (ForgeLoadingOverlay selector) is reasoned, not observed
