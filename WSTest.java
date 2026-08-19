import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class WSTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:5001"), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        System.out.println("CONNECTED");
                        webSocket.sendText("{\"type\":\"auth\",\"action\":\"register\",\"username\":\"wsuser\",\"password\":\"test\"}", true);
                        WebSocket.Listener.super.onOpen(webSocket);
                    }
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        System.out.println("RECEIVED: " + data);
                        latch.countDown();
                        return WebSocket.Listener.super.onText(webSocket, data, last);
                    }
                }).join();
        latch.await();
    }
}
