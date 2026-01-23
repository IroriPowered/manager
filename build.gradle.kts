plugins {
    id("java")
}

group = "cc.irori"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
        maven("https://maven.hytale.com/release")
    maven("https://maven.hytale.com/pre-release")
    maven("https://maven.irori.cc/repository/public/")
    maven("https://maven.hytale-modding.info/releases/")
}

dependencies {
    compileOnly(libs.hytale)
    compileOnly(libs.shodo)
    compileOnly(libs.multiplehud)
}
