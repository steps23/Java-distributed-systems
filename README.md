# Assegnamento-1

## Overview

**Assegnamento-1** is a Java networking project developed in **July 2024**.

The repository implements a distributed message-exchange scenario based on two communication layers:

- a **TCP coordination layer** used for client-server connection and synchronization
- a **UDP multicast layer** used for object-based message distribution among nodes

The system models a small network of clients that connect to a central server, exchange serialized messages, simulate packet loss, detect missing multicast messages, and trigger retransmission requests.

The project combines socket programming, Java object serialization, multicast communication, concurrent execution, and generated API documentation.

## Project Goals

The project is designed to represent a controlled messaging workflow in which:

- a server accepts a fixed number of clients
- each client performs an initial handshake with the server
- clients multicast serialized messages to the network
- packet loss is intentionally simulated during transmission
- receivers detect sequence gaps in received messages
- retransmission requests are generated for missing messages
- original senders resend the requested messages
- the server terminates only after all clients report completion

## Main Functional Areas

### 1. TCP Coordination

A server process listens for incoming TCP connections and waits until a predefined number of clients have joined the system. Once all required clients are connected, the server sends an initial synchronization message and then waits for each client to signal the end of its activity.

### 2. UDP Multicast Message Distribution

Each client sends serialized `Message` objects through UDP multicast. This enables all listeners on the multicast group to receive the same transmitted object stream.

### 3. Message Loss Simulation

The multicast sender introduces a configurable packet-loss simulation. Some messages are intentionally not sent, allowing the project to test missing-message detection and retransmission handling.

### 4. Gap Detection and Retransmission

Each receiver tracks the next expected message identifier for every sender. If a received message arrives with an identifier greater than expected, the receiver detects the missing range and sends retransmission notifications for the absent message IDs.

### 5. Completion Signaling

When a client finishes its message activity, it sends a final completion message to the server. The server closes the system when every client has reported completion.

## System Architecture

The project is organized around the following logical components:

### `ObjectServer`
Handles TCP coordination. It accepts client connections, sends an initial server message to connected clients, tracks client completion with a synchronization primitive, and closes the system when all expected clients finish.

### `ObjectClient`
Represents a client node in the system. It performs the TCP handshake, starts a multicast receiver thread, multicasts a sequence of messages, handles retransmission requests, and sends a completion notification to the server.

### `ObjectSender`
Serializes and sends `Message` objects over UDP multicast. It also simulates message loss according to a fixed probability.

### `ObjectReceiver`
Listens on the multicast group, deserializes incoming objects, tracks per-sender message order, detects gaps, and emits retransmission requests when missing messages are found.

### `Message`
Serializable data model used by both the TCP and UDP layers. It stores:

- the sender node identifier
- the message identifier
- a boolean flag indicating whether the message is a retransmission-related message

### `ResendListener`
Callback interface used by the receiver to notify the client when a retransmission request must be handled.

## Communication Workflow

A typical execution follows this sequence:

1. The server starts and waits for client TCP connections.
2. Each client connects to the server on `localhost:4444`.
3. Each client sends an initial serialized handshake message.
4. Once the expected number of clients is connected, the server sends a synchronization message to all of them.
5. Each client starts a multicast receiver thread.
6. Each client multicasts a sequence of serialized `Message` objects to the multicast group `230.0.0.1:4446`.
7. The sender may intentionally drop some messages due to simulated packet loss.
8. Receivers track expected message IDs for each sender and detect any gaps.
9. For every missing message ID, a retransmission request is multicast.
10. The original sender receives the retransmission request through its local receiver callback and resends the requested message.
11. After finishing its transmission cycle, each client stops its receiver and sends a completion message to the server.
12. When all required clients have completed, the server closes the system.

## Networking Details

### TCP

- **Host:** `localhost`
- **Port:** `4444`
- **Purpose:** connection establishment, handshake, synchronization, and completion signaling

### UDP Multicast

- **Group address:** `230.0.0.1`
- **Port:** `4446`
- **Purpose:** broadcast of serialized messages and retransmission notifications

## Message Model

The `Message` object is the central shared data structure of the project. It is serializable and contains three core fields:

- `nodeId`: identifier of the node that generated or sent the message
- `messageId`: numeric identifier used to track sequence order
- `resend`: boolean flag used to distinguish regular transmission flow from retransmission-related messages

This model is used consistently across both TCP and UDP communication.

## Reliability Logic

The project implements a basic recovery mechanism for multicast loss:

- receivers maintain the next expected message ID for every sender
- when a higher-than-expected message ID arrives, the receiver infers that one or more earlier messages were missed
- a retransmission request is sent for each missing message ID
- the original sender resends the requested message
- duplicate or out-of-order arrivals are identified during reception

This makes the repository suitable for demonstrating sequence tracking and elementary loss-recovery logic in a distributed Java application.

## Concurrency Model

The application uses Java concurrency in two main areas:

- the server creates one handler thread for each connected client
- each client starts a dedicated receiver thread while continuing its own transmission activity

On the server side, a `CountDownLatch` is used to synchronize global termination after all clients complete their work.

## Repository Structure

```text
Assegnamento-1/
├── code/
│   ├── Message.java
│   ├── ObjectClient.java
│   ├── ObjectReceiver.java
│   ├── ObjectSender.java
│   ├── ObjectServer.java
│   ├── ResendListener.java
│   ├── Message.class
│   ├── ObjectClient.class
│   ├── ObjectReceiver.class
│   ├── ObjectSender.class
│   ├── ObjectServer.class
│   ├── ObjectServer$ClientHandler.class
│   └── ResendListener.class
├── docs/
│   ├── generated Javadoc HTML documentation
│   ├── legal notices for bundled documentation assets
│   ├── resource files for the documentation UI
│   ├── script files for navigation and search
│   └── package-level documentation pages
├── .gitattributes
├── .DS_Store
└── README.odt
```

## Source Files

### `Message.java`
Defines the serializable message container shared by all components.

### `ObjectClient.java`
Implements the client-side workflow, including:

- TCP connection to the server
- handshake transmission
- storage of sent messages for later retransmission
- multicast sending of original messages
- reception of retransmission requests through a listener callback
- completion signaling to the server

### `ObjectReceiver.java`
Implements the multicast receiving logic, including:

- multicast group subscription
- incoming packet deserialization
- per-sender sequence tracking
- missing-message detection
- retransmission notification generation
- receiver shutdown logic

### `ObjectSender.java`
Implements object serialization and multicast transmission. It also contains the configurable packet-loss simulation used during message sending.

### `ObjectServer.java`
Implements the central TCP server. It accepts a fixed number of clients, sends an initial message to all connected nodes, and waits for completion notifications before closing the system.

### `ResendListener.java`
Defines the callback contract used to notify the client when a retransmission request has been detected for one of its previously sent messages.

## Documentation

The repository also contains a generated **Javadoc** documentation set under the `docs/` directory. It includes:

- package-level class documentation
- HTML index pages
- search index files
- JavaScript and CSS resources
- third-party asset legal notices

The main entry point for the generated documentation is:

```text
docs/index.html
```

## Build Instructions

Compile the Java sources from the repository root with:

```bash
javac -d out code/*.java
```

This command places the compiled classes in the `out/` directory while preserving the declared Java package structure.

## Execution

### Start the server

```bash
java -cp out stefano_ruggiero_assegnamento_1.ObjectServer
```

### Start the clients

Open three separate terminals and run:

```bash
java -cp out stefano_ruggiero_assegnamento_1.ObjectClient
```

Each client generates a random node identifier, connects to the server, and begins the message workflow.

## Expected Runtime Behavior

During execution, console output reports events such as:

- client connection and handshake messages
- multicast transmissions
- simulated packet losses
- detected sequence gaps
- retransmission requests
- resent messages
- client completion notifications
- final server shutdown

## Included Non-Source Files

The repository also includes additional non-source artifacts:

- `.class` files generated from the Java sources
- `.DS_Store` metadata files
- `README.odt`, an OpenDocument text file stored at repository root
- generated documentation assets and related legal/resource files

## Technologies Used

- Java
- TCP sockets
- UDP multicast
- Java object serialization
- multithreading
- `CountDownLatch`
- Javadoc-generated HTML documentation

## Project Date

This project was developed in **July 2024**.

## Notes

This repository contains both source files and generated artifacts. The implementation is centered on the `stefano_ruggiero_assegnamento_1` Java package and documents a complete communication flow that combines coordination, multicast distribution, loss simulation, and retransmission handling.
