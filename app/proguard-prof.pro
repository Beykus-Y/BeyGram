# PROF-only R8 rules.
# SnakeYAML references these JavaBeans desktop/JDK APIs, which Android does not ship.
# These optional references are not Android implementations; do not suppress unrelated missing classes.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# TDLib's libtdjni.so uses JNI FindClass/GetFieldID/GetMethodID with original
# org.drinkless.tdlib names. R8 must preserve all Java types and members,
# including generated TdApi nested classes. -keepnames alone is insufficient.
-keep class org.drinkless.tdlib.** { *; }
