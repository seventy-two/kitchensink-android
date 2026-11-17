# Nothing reflective: JSON is read by hand from kotlinx.serialization's tree.

# Glance makes a widget's tap callbacks by reflection, from their class
# names: keep them and their no-argument constructors.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
