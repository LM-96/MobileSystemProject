package it.unibo.kBluez

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.UUID

fun main(args : Array<String>) {

    runBlocking {
        var line : String
        var terminated = false
        val kbluez = KBluezFactory.getKBluez(scope = this)

        println("Started")

        while (!terminated) {
            try {
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

                        kbluez.findServices(name, uuid, address).forEach {
                            println("\thost: ${it.host}, protocol: ${it.protocol}")
                        }
                    }

                    "terminate" -> {
                        kbluez.close()
                        terminated = true
                    }
                }
            } catch (e : Exception) {
                println("Unable to perform operation due to an error:")
                e.printStackTrace()
            }
        }

        println("Terminated")
        kbluez.close()

        this.cancel("termination")
    }

}