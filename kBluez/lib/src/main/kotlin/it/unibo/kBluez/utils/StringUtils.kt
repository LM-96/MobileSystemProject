package it.unibo.kBluez.utils

fun String.addLevelTab(level : Int) : String {
    if(level < 1)
        throw IllegalArgumentException("Level cannot be less then 1")

    var tabString = ""
    for(i in 1..level) {
        tabString += "\t"
    }

    return this.lines().map { tabString + it }.joinToString("\n")
}