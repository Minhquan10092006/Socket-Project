public class TestWSKey {
    public static void main(String[] args) throws Exception {
        String webSocketKey = "pEtOFUtTX8P+PoeGULfjFg==";
        String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String acceptKey = java.util.Base64.getEncoder().encodeToString(
                java.security.MessageDigest.getInstance("SHA-1")
                        .digest((webSocketKey + WEBSOCKET_MAGIC).getBytes("UTF-8"))
        );
        System.out.println("Accept: '" + acceptKey + "'");
    }
}
