plugins {
    alias(libs.plugins.android.application) apply false
    // Thêm dòng này vào
    id("com.google.gms.google-services") version "4.4.2" apply false
}