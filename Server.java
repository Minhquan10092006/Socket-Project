import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList; 

public class Server {

    public static CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        // * khai bao Port ma server se lang nghe
        int port = 5000;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server waiting for connection at port: " + port);

            while (true) {
                Socket socket = serverSocket.accept(); // * chap nhan ket noi tu client
                System.out.println("A new connection is established!");

                // * tao luong moi de xu ly client
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler); // * them client vao danh sach

                new Thread(handler).start(); // * chay luong xu ly client
            }

        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            // * gui messages den tat ca user khac ngoai nguoi gui
            if (client != sender) {
                client.Message(message);
            }
        }
    }
}