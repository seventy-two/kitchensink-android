# Nothing reflective: JSON is read by hand from kotlinx.serialization's tree.

# Glance makes a widget's tap callbacks by reflection, from their class
# names: keep them and their no-argument constructors.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Glance finds a widget's home screen IDs by its widget class's name, saved
# against the receiver: R8 merged the two widgets into one class, so each
# redrew the other's widgets too. Keep them separate and named.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
