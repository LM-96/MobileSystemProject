#! /usr/bin/python3
from audioop import add
import string
import traceback
import json
import sys
from typing import Dict
import uuid
import pykka
import bluetooth

try:
    # ** ERROR HANDLING ************************************************************************** #
    class ErrActor(pykka.ThreadingActor):
        def __init__(self, stdErr):
            super().__init__()
            self.stdErr = stdErr

        def on_receive(self, message):
            try:
                print(message, file=self.stdErr)
                self.stdErr.flush()
            except:
                print(json.dumps({"source":"ErrActor", "err" : traceback.format_exc()}), file=self.stdErr)
                self.stdErr.flus()

    errActor = None

    def print_err(source : string, err_desc : string):
        errActor.tell(json.dumps({"source":source, "err" : err_desc}))

    def print_err_raw_string(sstring : string):
        errActor.tell(sstring)


    # ** OUTPUT HANDLING ************************************************************************* #
    class OutActor(pykka.ThreadingActor):
        def __init__(self, stdOut):
            super().__init__()
            self.stdOut = stdOut

        def on_receive(self, message):
            try:
                print(json.dumps(message), file=self.stdOut)
                self.stdOut.flush()
            except Exception as E:
                print_err(traceback.format_exc(), file=self.stdOut)
                self.stdOut.flush()

    outActor = None

    def print_raw_string(sstring : string):
        outActor.tell(sstring)

    def println(line : string):
        outActor.tell({"line":line})

    def sourced_print_ln(source : string, line : string):
        outActor.tell({"source":source, "line":line})

    def print_dict(dict : Dict):
        outActor.tell(dict)

    def updateState(state : string):
        print_dict({"state" : state})

    def getOrNone(dict, key):
        if(key in dict):
            return dict[key]
        else:
            return None

    # ** BLUETOOTH SOCKET HANDLING *************************************************************** #
    class BluetoothSocketActor(pykka.ThreadingActor):
        def __init__(self, uuid_str : string, sock : bluetooth.BluetoothSocket, sock_proto : string, manager_proxy : pykka.ActorProxy):
            super().__init__()
            self.uuid_str = uuid_str
            self.sock = sock
            self.sock_proto = sock_proto
            self.name = "BluetoothSocketActor[" + uuid_str + "]"
            self.manager_proxy = manager_proxy

        def sock_get_local_address(self):
            address = self.sock.getsockname()
            print_dict({"sock_uuid":self.uuid_str, "sock_local_address":address[0], "sock_local_port":address[1]})

        def sock_get_remote_address(self):
            try:
                address = self.sock.getpeername()
                print_dict({"sock_uuid":self.uuid_str, "sock_remote_address":address[0], "sock_remote_port":address[1]})
            except:
                print_err(self.name, traceback.format_exc())

        def sock_connect(self, address, port):
            try:
                if(self.sock_proto == bluetooth.L2CAP) :
                    self.sock.connect((address, hex(port)))
                else:
                    self.sock.connect((address, port))
                print_dict({"sock_uuid":self.uuid_str, "connect_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_bind(self, port):
            try:
                if(port == None):
                    self.sock.bind("", bluetooth.PORT_ANY)
                else:
                    self.sock.bind(("", port))
                print_dict({"sock_uuid":self.uuid_str, "bind_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_listen(self, backlog):
            try:
                if(backlog == None):
                    self.sock.listen()
                else:
                    self.sock.listen(backlog)
                print_dict({"sock_uuid":self.uuid_str, "listen_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_accept(self):
            try:
                client_sock, client_addr = self.sock.accept()
                res = self.manager_proxy.new_bt_socket_actor(client_sock)
                uuid_str = res.get()
                print_dict({"sock_uuid":self.uuid_str, "accept_res":uuid_str, "accept_res_address":client_addr})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_send(self, data):
            try:
                self.sock.send(data.encode('utf-8'))
                print_dict({"sock_uuid":uuid, "sent_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_receive(self, buffsize : int):
            try:
                data = self.sock.recv(buffsize)
                print_dict({"sock_uuid":uuid, "receive_res":data.decode('utf-8'), "size" : len(data)})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_close(self):
            try:
                data = self.sock.close()
                print_dict({"sock_uuid":uuid, "close_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_shutdown(self):
            try:
                self.sock.shutdown()
                print_dict({"sock_uuid":uuid, "shutdown_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_set_l2cap_mtu(self, value : int):
            if(self.sock_proto != "L2CAP"):
                print_err(self.name, "unable to set L2CAP MTU to a " + self.sock_proto + " socket")
            else:
                bluetooth.set_l2cap_mtu(self.sock, value)
                print_dict({"sock_uuid":uuid, "set_l2cap_mtu":"executed"})

        def sock_advertise_service(self, service_name : string, service_uuid : string):
            try:
                bluetooth.advertise_service(self.sock, service_name, service_uuid)
                print_dict({"sock_uuid":uuid, "advertise_service_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())

        def sock_stop_advertising(self):
            try:
                bluetooth.stop_advertising(self.sock)
                print_dict({"sock_uuid":uuid, "stop_asdvertising_res":"executed"})
            except Exception:
                print_err(self.name, traceback.format_exc())


    class BluetoothSocketActorManager(pykka.ThreadingActor):
        def __init__(self):
            super().__init__()
            self.socket_actors = {}
            self.name = "BluetoothSocketManager"

        def new_bt_socket_actor(self, sock, sock_proto: string):
            uuid_str = str(uuid.uuid4())            
            actor = BluetoothSocketActor.start(uuid_str, sock, sock_proto, self)
            self.socket_actors[uuid_str] = {"actor":actor,"proxy":actor.proxy()}
            return uuid_str

        def get_proxy(self, uuid_str):
            return self.socket_actors[uuid_str]["proxy"]

        def get_actor(self, uuid_str):
            return self.socket_actors[uuid_str]["actor"]
        
        def stop_bt_socket_actor(self, uuid_str):
            self.socket_actors[uuid_str]["actor"].stop(True)
            del self.socket_actors[uuid_str]

    bluetooth_manager_actor = BluetoothSocketActorManager.start()
    bluetooth_manager_proxy = bluetooth_manager_actor.proxy()

    # ** MAIN FUNCTIONS ************************************************************************* #
    def scan():
        updateState("SCANNING")
        discovered_devices = bluetooth.discover_devices(lookup_names = True, lookup_class = True)
        devices = []
        for address, name, clazz in discovered_devices:
            devices.append({"address" : address, "name" : name, "classCode" : clazz})
        print_dict({"scan_res" : devices})

    def lookup(msg) :
        updateState("LOOKING_UP")
        address = msg["address"]
        try:
            device = bluetooth.lookup_name(address)
            print_dict({"lookup_res" : device})
        except bluetooth.btcommon.BluetoothError as e:
            print_dict({"lookup_res" : None, "errName" : e.name, "errArgs" : e.args})

    def find_services(msg):
        updateState("FINDING_SERVICES")

        name = getOrNone(msg, "nanem")
        uuid = getOrNone(msg, "uuid")
        address = getOrNone(msg, "address")
        print_dict({"find_services_res" : bluetooth.find_service(name, uuid, address)})

    def new_sock(msg):
        updateState("CREATING_SOCKET")
        protocol = msg["protocol"]
        if(protocol == "RFCOMM"):
            sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)
        elif(protocol == "L2CAP"):
            sock = bluetooth.BluetoothSocket(bluetooth.L2CAP)
        else:
            print_err("main", "socket protocol not supported")
            return

        uuid_str = bluetooth_manager_proxy.new_bt_socket_actor(sock, protocol).get()
        print_dict({"new_socket_uuid":uuid_str})
        

    def a_sock_bind(msg):
        #updateState("BINDING_SOCKET")
        port = getOrNone(msg, "port")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_bind(int(port))

    def a_sock_get_local_address(msg):
        #updateState("GETTING_LOCAL_ADDRESS")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_get_local_address()
    
    def a_sock_get_remote_address(msg):
        #updateState("GETTING_REMOTE_ADDRESS")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_get_remote_address()

    def a_sock_listen(msg):
        #updateState("LISTENING_SOCKET")
        backlog = getOrNone(msg, "backlog")
        if(backlog == None):
            bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_listen()
        else:
            bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_listen(backlog)

    def a_sock_accept(msg):
        #updateState("ACCEPTING_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_accept()

    def a_sock_receive(msg):
        #updateState("RECEIVING_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_receive(int(msg["bufsize"]))
        

    def a_sock_close(msg):
        #updateState("CLOSING_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_close()

    def a_sock_connect(msg):
        #updateState("CONNECTING_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_connect(msg["address"], int(msg["port"]))    

    def a_sock_send(msg):
        #updateState("SENDING_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_send(msg["data"])

    def a_sock_shutdown(msg):
        #updateState("SHUTTING_DOWN_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_shutdown()

    def a_sock_set_l2cap_mtu(msg):
        #updateState("SETTING_L2CAP_MTU_SOCKET")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_set_l2cap_mtu(int(msg["mtu"]))

    def a_sock_advertise_service(msg):
        #updateState("ADVERTISING_SERVICE")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_advertise_service(msg["service_name"], msg["service_uuid"])

    def a_sock_stop_advertising(msg):
        #updateState("STOP_ADVERTISING")
        bluetooth_manager_proxy.get_proxy(msg["sock_uuid"]).get().sock_stop_advertising()


    def terminate():
        pykka.ActorRegistry.stop_all(True)
        print(json.dumps({"state":"TERMINATED"}))

    try:
        hasToTerminate = False
        stdout = sys.stdout
        stderr = sys.stderr
        errActor = ErrActor.start(stderr)
        outActor = OutActor.start(stdout)
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
                    find_services(msg)

                elif(cmd == "terminate"):
                    hasToTerminate = True

                elif(cmd == "ensure_idle"):
                    updateState("IDLE")

                elif(cmd == "sock_new"):
                    new_sock(msg)

                elif(cmd == "sock_bind"):
                    a_sock_bind(msg)

                elif(cmd == "sock_listen"):
                    a_sock_listen(msg)
                
                elif(cmd == "sock_accept"):
                    a_sock_accept(msg)

                elif(cmd == "sock_receive"):
                    a_sock_receive(msg)

                elif(cmd == "sock_close"):
                    a_sock_close(msg)

                elif(cmd == "sock_connect"):
                    a_sock_connect(msg)

                elif(cmd == "sock_send"):
                    a_sock_send(msg)

                elif(cmd == "sock_shutdown"):
                    a_sock_shutdown(msg)

                elif(cmd == "sock_set_l2cap_mtu"):
                    a_sock_set_l2cap_mtu(msg)

                elif(cmd == "sock_advertise_service"):
                    a_sock_advertise_service(msg)

                elif(cmd == "sock_get_address"):
                    a_sock_stop_advertising(msg)

                elif(cmd == "sock_get_local_address"):
                    a_sock_get_local_address(msg)

                elif(cmd == "sock_get_remote_address"):
                    a_sock_get_remote_address(msg)

                else:
                    print_err("main", "unsupported operation")

            except KeyboardInterrupt:
                break

            except Exception as e:
                print_err("main", traceback.format_exc())

        terminate()

    except KeyboardInterrupt as _:
        terminate()
    except Exception as e:
        print_err(traceback.format_exc())
except:
    print(json.dumps({"source":"main", "err": traceback.format_exc()}), file=sys.stderr)