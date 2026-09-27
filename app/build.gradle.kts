plugins {
    id("org.openjfx.javafxplugin") version "0.1.0"
    application
}

javafx {
    version = "25"
    modules = listOf("javafx.controls")
}

application {
    mainClass.set("com.boardarena.app.App")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":tictactoe"))
    implementation(project(":network"))
}
