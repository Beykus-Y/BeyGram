# PROF-only R8 rules.
# SnakeYAML references these JavaBeans desktop/JDK APIs, which Android does not ship.
# These optional references are not Android implementations; do not suppress unrelated missing classes.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
