import java.util.regex.*;
public class TestRegex {
    public static void main(String[] args) {
        String json = "{\"type\":\"auth\",\"action\":\"register\",\"username\":\"test\",\"password\":\"test\"}";
        System.out.println("type: " + extract(json, "type"));
        System.out.println("action: " + extract(json, "action"));
    }
    private static String extract(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*?)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) return matcher.group(1);
        return null;
    }
}
