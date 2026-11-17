# Keep source file and line numbers in crash reports while obfuscating.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations
-renamesourcefileattribute SourceFile

# Room builds its generated WorkDatabase_Impl and AppDatabase_Impl by reflection.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class com.doxigo.muchtoman.** { *; }

# WorkManager instantiates Workers via reflection on app/background startup.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Glance AppWidgets and BroadcastReceivers declared in AndroidManifest.xml.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    <init>(...);
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    public <init>();
}
-keep class * extends android.content.BroadcastReceiver {
    public <init>();
}

# kotlinx.serialization keeps its generated serializers via companion objects.
-keep @kotlinx.serialization.Serializable class com.doxigo.muchtoman.** { *; }
-keepclassmembers class com.doxigo.muchtoman.** {
    *** Companion;
}
-keepclasseswithmembers class com.doxigo.muchtoman.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.doxigo.muchtoman.**$$serializer { *; }
