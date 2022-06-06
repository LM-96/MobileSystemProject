#! /usr/bin/python3
import string
import traceback
import json
import sys
import uuid
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

def newSocket(msg, sockets):
    updateState("CREATING_SOCKET")
    protocol = msg["protocol"]
    if(protocol == "RFCOMM"):
        uuid, sock = (uuid.uuid5(), bluetooth.BluetoothSocket(bluetooth.RFCOMM))
    elif(protocol == "L2CAP"):
        uuid, sock = (uuid.uuid5(), bluetooth.BluetoothSocket(bluetooth.L2CAP))
    else:
        printErr("socket protocol not supported")

    sockets[uuid] = sock
    print(json.dumps({"new_socket_uuid" : uuid}))

def socketBind(msg, sockets):
    updateState("BINDING_SOCKET")
    sockets[msg["uuid"]].bind(("", msg["port"]))

def socketListen(msg, sockets):
    updateState("LISTENING_SOCKET")
    backlog = getOrNone(msg, "backlog")
    if(backlog == None):
        sockets[msg["uuid"]].listen()
    else:
        sockets[msg["uuid"]].listen(backlog)

def socketAccept(msg, sockets):
    updateState("ACCEPTING_SOCKET")
    client_sock, addr = sockets[msg["uuid"]].accept()
    sockInfo = {"client_sock_uuid":uuid.uuid5(), "client_address" : addr, "cient_proto" : client_sock.proto}
    sockets[sockInfo["uuid"]] = client_sock
    print(json.dumps(sockInfo))

def socketReceive(msg, sockets):
    updateState("RECEIVING_SOCKET")
    data = sockets[msg["uuid"]].recv(int(msg["bufsize"]))
    print(json.dumps({"uuid" : msg["uuid"], "size" : len(data), "received_data" : data.decode('utf-8')}))

def socketClose(msg, sockets):
    updateState("CLOSING_SOCKET")
    sockets[msg["uuid"]].close()

def socketConnect(msg, sockets):
    updateState("CONNECTING_SOCKET")
    sock = sockets[msg["uuid"]]
    if(sock.proto == bluetooth.L2CAP) :
        sockets[msg["uuid"]].connect((msg["address"], hex(int(msg["port"]))))
    else:
        sockets[msg["uuid"]].connect((msg["address"], int(msg["port"])))
    

def socketSend(msg, sockets):
    updateState("SENDING_SOCKET")
    sockets[msg["uuid"]].send(msg["data"])



try:
    hasToTerminate = False
    sockets = {}
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

            elif(cmd == "sock_new"):
                newSocket(msg, sockets)

            elif(cmd == "sock_bind"):
                socketBind(msg, sockets)

            elif(cmd == "sock_listen"):
                socketListen(msg, sockets)
            
            elif(cmd == "sock_accept"):
                socketAccept(msg, sockets)

            elif(cmd == "sock_receive"):
                socketReceive(msg, sockets)

            elif(cmd == "sock_close"):
                socketClose(msg, sockets)

            elif(cmd == "sock_connect"):
                socketConnect(msg, sockets)

            elif(cmd == "sock_send"):
                socketSend(msg, sockets)

            else:
                print(json.dumps({"err" : "Unsupported Operation"}))


        except (ValueError, AttributeError) as e:
            printErr(e.args)

    updateState("TERMINATED")
except Exception as e:
    printErr(traceback.format_exc())