import java.io.*;
import java.net.*;

public class Client {
    public static void main(String[] args) {
        String hostname = "localhost"; //host address

        int port = 5000;

        try(Socket socket = new Socket(hostname, port)) {
            //* create outputstream
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            //* send messages
            String text = "Hello, I am the most handsome boy in the world.";
            writer.println(text);

            //* Create inputstream
            InputStream input = socket.getInputStream();
            BufferedReader reader =  new BufferedReader(new InputStreamReader(input));

            // * read response from server
            String response = reader.readLine();
            System.out.println("Response from server: " + response);
        } catch(UnknownHostException e) {
            System.out.println("Can't find Server: " + e.getMessage());
        } catch(IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        }
    }
}
