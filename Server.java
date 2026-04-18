import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) {
        // * khai bao Port ma server se lang nghe
        int port = 5000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server waiting for connection at port: " + port);

            while (true) {
                Socket socket = serverSocket.accept(); // * chap nhan ket noi tu client
                System.out.println("New client connected: " + socket.getInetAddress().getHostAddress());

                // * tao luong moi de xu ly client
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start(); // * chay luong xu ly client
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}