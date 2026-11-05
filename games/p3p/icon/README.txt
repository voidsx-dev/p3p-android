P3P Android icon resources
===========================

This package contains the simplified P3P icon:
- Solid vivid blue background
- White "P3" lettering
- Cyan final "P"
- No character artwork or game screenshots

Contents:
- res/mipmap-mdpi/ic_launcher.png
- res/mipmap-hdpi/ic_launcher.png
- res/mipmap-xhdpi/ic_launcher.png
- res/mipmap-xxhdpi/ic_launcher.png
- res/mipmap-xxxhdpi/ic_launcher.png
- res/mipmap-anydpi-v26/ic_launcher.xml
- res/drawable/ic_launcher_foreground.png
- res/drawable/ic_launcher_background.png

Integration:
Merge this package's "res" directory into the Android app module's
resource directory. Ensure AndroidManifest.xml uses:
android:icon="@mipmap/ic_launcher"

If your project uses another resource name or resource path, adapt the
manifest/build configuration accordingly. Check the actual Android module
before copying; do not overwrite unrelated resources.

The artwork is a newly generated typographic treatment. This does not
guarantee clearance from trademark or other intellectual-property claims.
