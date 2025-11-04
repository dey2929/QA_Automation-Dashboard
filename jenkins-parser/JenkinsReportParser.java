import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class JenkinsReportParser {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java JenkinsReportParser <html-file-path>");
            return;
        }
        String htmlFilePath = args[0];
        parseTestReport(htmlFilePath);
    }

    public static void parseTestReport(String htmlFilePath) {
        try {
            Document doc = Jsoup.parse(new File(htmlFilePath), "UTF-8");

            // Extract test results
            Elements testNodes = doc.select(".test-node");

            for (Element testNode : testNodes) {
                String testName = testNode.select(".test-name").text();
                String status = testNode.select(".test-status").text();
                String duration = testNode.select(".test-duration").text();

                System.out.println("Test: " + testName + ", Status: " + status + ", Duration: " + duration);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}