# Taken from androbd/proguard-rules.pro (see the reasoning there):
# EcuDataItems loads pids.csv etc. via *relative* Class.getResource() paths,
# so R8 must not move classes out of their packages.
-keeppackagenames

-keep class com.fr3ts0n.pvs.ProcessVar {
    public <init>();
}
-keep class * extends com.fr3ts0n.pvs.ProcessVar {
    public <init>();
}
