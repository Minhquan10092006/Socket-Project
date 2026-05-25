import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter writer;
    private String nickname;

    // *constructor to intialize socket
    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void Message(String messages) {
        writer.println(messages);
    }

    @Override
    public void run() {
        try (
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            // *khoi tao writer o pham vi lop de sendmessage co the duoc goi tu lop Server
            writer = new PrintWriter(socket.getOutputStream(), true);
            this.nickname = reader.readLine(); // * doc nickname tu client
            
            // * thong bao cho tat ca client khac khi co nguoi moi tham gia
            Server.broadcast("[SERVER]: " + nickname + " has joined the room!", this); 

            String message;
            while ((message = reader.readLine()) != null) { 
                if (message.equalsIgnoreCase("exit")) {
                    break;
                }
                System.out.println("Broadcasting: " + message);
                Server.broadcast("[" + nickname + "] said: " + message, this); // * gui message den tat ca client khac

            }
        } catch (IOException e) {
            System.out.println("Client left the chat.");
        } finally {
            Server.clients.remove(this); // * xoa client khoi danh sach khi ngat ket noi
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Error closing socket: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }

}
