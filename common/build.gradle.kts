// Common module - contains shared code across all Minecraft versions
// This module is pure Java with no Minecraft dependencies

plugins {
    java
}

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // No Minecraft dependencies here - this is pure shared Java code
    // Version-specific modules will depend on this

    // ImGui for IDE (since IDE code is in common)
    val imguiVersion = rootProject.properties["imgui_version"] as String
    implementation("io.github.spair:imgui-java-binding:$imguiVersion")
    implementation("io.github.spair:imgui-java-lwjgl3:$imguiVersion")
}
