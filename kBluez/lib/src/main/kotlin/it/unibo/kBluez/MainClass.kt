package it.unibo.kBluez

fun main(args : Array<String>) {

    var line : String
    var terminated = false
    val kbluez = KBluezFactory.getKBluez()
    println("Started")

    while (!terminated) {
        println("Waiting command...")
        print("\t")
        line = readln()
        when(line) {
            "scan" -> {
                println("Scanning for devices")
                kbluez.scan().forEach {
                    println(it)
                }
            }

            "lookup" -> {
                print("Address: ")
                line = readln()
                println(kbluez.lookup(line))
            }

            "terminate" -> {
                kbluez.close()
                terminated = true
            }
        }
    }

    println("Terminated")

}