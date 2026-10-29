# Dakilio release rules.
#
# Everything the app relies on (Room, Hilt, kotlinx.serialization for Navigation 3 keys, Coil,
# DataStore, Compose) ships its own consumer rules, so this file intentionally stays almost empty.
# If a release/preview build crashes where debug doesn't, add the narrowest -keep here and say why.

# Keep line numbers in stack traces, but hide the original source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
