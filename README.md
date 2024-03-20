
# Sockets Library

The Sockets library provides a convenient way to work with the TCP/IP protocol and handle events at both the server and connection levels. This library offers interfaces and classes for creating servers, managing connections, and processing various network events.

## Features

- Creation and management of TCP/IP servers
- Establishment and management of TCP/IP connections
- Asynchronous connection handling
- Event-driven architecture for handling events at both server and connection levels
- Simplified methods for sending and receiving data over connections

## Factory Class

### `TcpFactory`

- `createServer()`: Creates an instance of a TCP server.
- `createConnection(int id)`: Creates an instance of a TCP connection with the specified ID.

## Usage Examples

### Server Event Registration and Handling

```java
IServer server = TcpFactory.createServer();
server.addServerListener(new ServerListener() {
    @Override
    public void serverStarted() {
        System.out.println("Server started listening.");
    }

    @Override
    public void connectionEstablished(IConnection connectionWithClient) {
        System.out.println("Connection established with client: " + connectionWithClient.getRemoteAddress());
    }

    @Override
    public void connectionClosed(IConnection connectionWithClient, CloseStatus status) {
        System.out.println("Connection closed with client: " + connectionWithClient.getRemoteAddress() + ", Status: " + status);
    }

    @Override
    public void connectionBytesReceived(IConnection connectionWithClient, byte[] bytes) {
        System.out.println("Received bytes from client " + connectionWithClient.getRemoteAddress() + ": " + new String(bytes));
    }

    @Override
    public void serverClosed(CloseStatus status) {
        System.out.println("Server closed. Status: " + status);
    }
});

server.listen("localhost", 8080);
```
 ### Connection Event Registration and Handling
```java
IConnection connection = TcpFactory.createConnection(1);
connection.addConnectionListener(new ConnectionListener() {
    @Override
    public void connectionEstablished() {
        System.out.println("Connection established.");
    }

    @Override
    public void connectionBytesReceived(byte[] bytes) {
        System.out.println("Received bytes: " + new String(bytes));
    }

    @Override
    public void connectionClosed(CloseStatus status) {
        System.out.println("Connection closed with status: " + status);
    }
});

connection.connect("localhost", 8080);
```

## Contribution

Contributions to the Sockets library are welcome. If you find any issues or have suggestions for improvement, feel free to open an issue or submit a pull request on GitHub.

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for additional information.
