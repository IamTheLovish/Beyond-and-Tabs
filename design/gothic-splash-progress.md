# gothic-splash progress (title + loading screen: cathedral rose window, "Developed by Lovish")

Done
- tools/gen_splash_art.py: procedural layers -> bar/src/main/resources/assets/reignofnether/textures/gui/splash/
  (background, frame, window, rays, glow, title, byline); previews design/splash_preview_*.png
- tools/fonts/: Grenze Gotisch + UnifrakturMaguntia (both SIL OFL 1.1, licence files beside them; build-time only)

Next
- SplashArt.java (texture upload once from the mod jar, layered draw), TitleScreenMixin render swap, button restyle
- LoadingOverlay + ForgeLoadingOverlay mixins (initial launch overlay), reduce-motion config
- design/splash.md (early window findings)

Known failures: none yet (CI not run)
