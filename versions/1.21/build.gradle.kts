// Minecraft 1.21 specific module

version = "${rootProject.properties["mod_version"]}+mc1.21"

dependencies {
    // Minecraft and Fabric for 1.21
    minecraft("com.mojang:minecraft:1.21")
    mappings("net.fabricmc:yarn:1.21+build.9:v2")
    modImplementation("net.fabricmc:fabric-loader:0.16.9")

    // Include common module source
    implementation(project(":common"))
}

base {
    archivesName.set("${rootProject.properties["archives_base_name"]}-1.21")
}
