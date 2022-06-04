#! /usr/bin/python3
import json
import bluetooth

def updateState(state):
    print(json.dumps({"state" : state}))

def printErr(errDesc):
    print(json.dumps({"err" : errDesc}))

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
    name = msg["name"]
    uuid = msg["uuid"]
    address = msg["address"]
    print(json.dumps({"find_services_res" : bluetooth.find_service(name, uuid, address)}))



hasToTerminate = False
while(not hasToTerminate):
    updateState("IDLE")
    rawMsg = input()
    try:
        msg = json.loads(rawMsg)
        print(json.dumps({"info":"msg="+msg}))
        cmd = msg["cmd"]

        if(cmd == "scan"):
            scan()

        elif(cmd == "lookup"):
            lookup(msg)

        elif(cmd == "find_services"):
            findServices(msg)

        elif(cmd == "terminate"):
            hasToTerminate = True

        else:
            print(json.dumps({"err" : "Unsupported Operation"}))


    except (ValueError, AttributeError) as e:
        printErr(e.args)

updateState("TERMINATED")