package it.unibo.kBluez

import java.util.UUID

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
                print("\tAddress: ")
                line = readln()
                println(kbluez.lookup(line))
            }

            "find_services" -> {
                print("\tName (enter for null): ")
                line = readln().trim()
                val name = if(line.isBlank()) null else line

                print("\tUUID (enter for null): ")
                line = readln().trim()
                val uuid = if(line.isBlank()) null else UUID.fromString(line)

                print("\tAddress (enter for null): ")
                line = readln().trim()
                val address = if(line.isBlank()) null else line

                println(kbluez.findServices(name, uuid, address))
            }

            "terminate" -> {
                kbluez.close()
                terminated = true
            }
        }
    }

    println("Terminated")

}