# Akka IoT Tutorial

This project is based on [the tutorial example for getting started with Akka from the official documentation](https://doc.akka.io/docs/akka/current/typed/guide/tutorial.html).

The plan is to reproduce the tutorial example and build on it to obtain a complete application.

## Context

This project models part of an IoT system that reports temperature measurements from sensor devices grouped together 
for each user home (see [the official documentation](https://doc.akka.io/docs/akka/current/typed/guide/tutorial.html) for more 
details).

![IoT system schema from https://doc.akka.io](https://doc.akka.io/docs/akka/current/typed/guide/diagrams/arch_boxes_diagram.png)

The first goal is to implement the following core logic of this design:
* Register new devices into new or existing groups
* Query a device to record the temperature
* Query a device to return the most recent temperature reading
* Query a device group to return the temperature readings from all devices

Further goals include:
* Register new user
* Integrate device protocol with user protocol

## Actor modelling

Following the [the official documentation](https://doc.akka.io/docs/akka/current/typed/guide/tutorial_2.html), the 
first part of the actor modelling follows the system's representation naturally, with:
* A supervisor actor as the user guardian that represents the whole application
* A single device manager actor, known and available up front, as the entry point for requests to devices and device 
  groups
* Device group actors created on demand by the device manager actor
* Device actors created on demand by a device group actor

![IoT project actor hierarchy from https://doc.akka.io](https://doc.akka.io/docs/akka/current/typed/guide/diagrams/arch_tree_diagram.png)

The various flows are represented in the following section.

Note: some parameters such as `requestId` are omitted for readability. 

### Device registration

[Reference page](https://doc.akka.io/docs/akka/current/typed/guide/tutorial_4.html)

The device manager handles requests to register a new device to a specified group. If the group does not already 
exist, it is created (*create-on-demand* and *create-watch-terminate* patterns).

Messages:
* Request: `RequestTrackDevice(groupId, deviceId)`
* Response: `DeviceRegistered(deviceActorRef)`

Note: in the happy path, the device always replies with `DeviceRegistered`, whether it already exists or not.

![Device registration actor flow](doc/device-registration-flow.png)

### Device list request

The device manager handles requests to return a list of registered devices in a specified group. If the group does 
not exist, an empty set is returned.

Messages:
* Request: `RequestDeviceList(groupId)`
* Response: `ReplyDeviceList(ids)`

![Device list request actor flow for non existent group](doc/device-list-nogroup-flow.png)

![Device list request actor flow for existing group](doc/device-list-group-flow.png)

### Temperature reading on single device

[Reference page](https://doc.akka.io/docs/akka/current/typed/guide/tutorial_3.html)

Each device can be requested to return its latest temperature measurement. This illustrates the *request-response 
pattern*.

Messages:
* Request: `ReadTemperature`
* Response: `RespondTemperature`

![Device temperature reading actor flow](doc/device-read-temperature-flow.png)

### Request temperature readings from all devices in a device group

A device group can be requested to return the temperature readings from all its devices.

This case departs from the previous in that it introduces the creation of a query actor to gather the temperature 
readings from all the devices in a group (see [the documentation](https://doc.akka.io/docs/akka/current/typed/guide/tutorial_5.html) for details).

Messages:
* Request: `RequestAllTemperatures(groupId)`
* Response: `RespondAllTemperatures(temperatures)`

![Temperature readings from all devices in a group actor flow](doc/device-group-read-all-temperatures-flow.png)

## Followup plan

* Refactor in functional style
* Add user protocol
* Add HTTP server and API
