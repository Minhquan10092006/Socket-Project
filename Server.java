import java.io.*;
import java.net.*;
public class Server {
    public static void main (String[] args) {
        // * khai bao Port ma server se lang nghe
        int port = 5000;

        try(ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server waiting for connection at port: "+ port);

            //accept client request connecting
            Socket socket = serverSocket.accept();
            System.out.println("Client connected successfully");

            //Create thread to receive data(inputstream)
            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            
            // ? create thread to send data(outputstream)
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            // * Read messages from client and reply
            String message = reader.readLine();
            System.out.println("Client sent: " + message);
            writer.println("Server has received client's message: " + message);
            
            socket.close();
        } catch(IOException e) {
            System.out.println("Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}