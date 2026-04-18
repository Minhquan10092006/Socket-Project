import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        String hostname = "localhost"; // host address

        int port = 5000;

        try (Socket socket = new Socket(hostname, port)) {
            System.out.println("Connected to server at " + hostname + ":" + port);

            // * create outputstream & inputstream
            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            InputStream input = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            // * Setup scanner to read user input
            Scanner scanner = new Scanner(System.in);
            String text;
            System.out.print("Start chatting with the server (type 'exit' to quit): ");

            do {
                System.out.print("You: ");
                text = scanner.nextLine(); // read user input
                writer.println(text); // send to server

                // * read response from server
                String response = reader.readLine();
                System.out.println("Server replied:" + response);

            } while (!text.equals("exit")); // stop chatting when user types 'exit'

        } catch (UnknownHostException e) {
            System.out.println("Can't find Server: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO Error: " + e.getMessage());
        }
    }
}
