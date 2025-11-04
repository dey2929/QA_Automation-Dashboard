import java.io.File;

public class ParseAllReports {

    public static void main(String[] args) {
        // Change this to your directory holding Jenkins HTML reports
        String reportsDirPath = "C:/Users/dey.anand/VS-Workspace/qa-automation-dashboard/jenkins-parser/";
        File reportsDir = new File(reportsDirPath);
        
        if (!reportsDir.exists() || !reportsDir.isDirectory()) {
            System.out.println("Invalid directory: " + reportsDirPath);
            return;
        }
        
        File[] reportFiles = reportsDir.listFiles((dir, name) -> name.endsWith(".html"));
        
        if (reportFiles == null || reportFiles.length == 0) {
            System.out.println("No HTML report files found in directory.");
            return;
        }
        
        for (File file : reportFiles) {
            System.out.println("\nParsing report: " + file.getName());
            JenkinsReportParser.parseTestReport(file.getAbsolutePath());
        }
    }
}
