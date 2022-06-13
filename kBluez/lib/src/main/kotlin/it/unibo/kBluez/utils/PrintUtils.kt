package it.unibo.kBluez.utils

val tt = "\t\t###### MAIN | "

fun printTt(line : String) {
    println("$tt $line")
}

fun askTt(question : String) {
    print("$tt \'$question\': ")
}