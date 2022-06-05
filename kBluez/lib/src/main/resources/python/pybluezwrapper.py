#! /usr/bin/python3
import string
import traceback
import json
import sys
import bluetooth

def updateState(state : string):
    print(json.dumps({"state" : state}))

def printErr(errDesc : string):
    print(json.dumps({"err" : errDesc}), file=sys.stderr)

def getOrNone(dict, key):
    if(key in dict):
        return dict[key]
    else:
        return None


def scan():
    updateState("SCANNING")
    discovered_devices = bluetooth.discover_devices(lookup_names = True, lookup_class = True)
    devices = []
    for address, name, clazz in discovered_devices:
        devices.append({"address" : address, "name" : name, "classCode" : clazz})
    print(json.dumps({"scan_res" : devices}))

def lookup(msg) :
    updateState("LOOKING_UP")
    address = msg["address"]
    try:
        device = bluetooth.lookup_name(address)
        print(json.dumps({"lookup_res" : device}))
    except bluetooth.btcommon.BluetoothError as e:
        print(json.dumps({"lookup_res" : None, "errName" : e.name, "errArgs" : e.args}))

def findServices(msg):
    updateState("FINDING_SERVICES")

    name = getOrNone(msg, "nanem")
    uuid = getOrNone(msg, "uuid")
    address = getOrNone(msg, "address")
    print(json.dumps({"find_services_res" : bluetooth.find_service(name, uuid, address)}))


try:
    hasToTerminate = False
    while(not hasToTerminate):
        updateState("IDLE")
        rawMsg = input()
        try:
            msg = json.loads(rawMsg)
            cmd = msg["cmd"]

            if(cmd == "scan"):
                scan()

            elif(cmd == "lookup"):
                lookup(msg)

            elif(cmd == "find_services"):
                findServices(msg)

            elif(cmd == "terminate"):
                hasToTerminate = True

            elif(cmd == "ensure_idle"):
                updateState("IDLE")

            else:
                print(json.dumps({"err" : "Unsupported Operation"}))


        except (ValueError, AttributeError) as e:
            printErr(e.args)

    updateState("TERMINATED")
except Exception as e:
    printErr(traceback.format_exc())