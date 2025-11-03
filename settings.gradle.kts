pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "canvasglsl"

// Include common module
include("common")

// Include version-specific modules
include("versions:1.21")
include("versions:1.21.4")
include("versions:1.21.10")
