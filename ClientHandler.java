import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;

    // *constructor to intialize socket
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                // *create inputstream and outputstream
                InputStream input = socket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
                OutputStream output = socket.getOutputStream();
                PrintWriter writer = new PrintWriter(output, true);) {
            String message;

            // * read messages from client until client disconnects
            while ((message = reader.readLine()) != null) {
                if (message.equalsIgnoreCase("exit")) {
                    writer.println("Goodbye!");
                    break; // * exit loop if client wants to disconnect
                }
                System.out.println("Received from client: " + message);
                writer.println("Server received: " + message); // * send response back to client
            }
        } catch (IOException e) {
            System.out.println("ClientHandler error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
            }
        }

    }

}
