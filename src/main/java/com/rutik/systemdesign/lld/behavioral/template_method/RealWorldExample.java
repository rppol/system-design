package com.rutik.systemdesign.lld.behavioral.template_method; /**
 * TEMPLATE METHOD PATTERN - Real-World Example: Data Mining
 *
 * Problem:
 *   A data mining application must extract, parse, and analyse data from
 *   files in different formats: CSV, JSON, and XML.
 *   The high-level pipeline is always the same:
 *     1. openFile()    -- open the file / acquire the resource
 *     2. extractData() -- parse the raw content into records
 *     3. parseData()   -- clean / validate the records
 *     4. analyseData() -- compute statistics (invariant — done once)
 *     5. sendReport()  -- send results (invariant — done once)
 *     6. closeFile()   -- release the resource
 *
 *   Steps 1, 2, 3, and 6 differ per format; steps 4 and 5 are shared.
 *
 * Template method: mine() — calls all six steps in order.
 */

import java.util.Arrays;
import java.util.List;

// ---------------------------------------------------------------------------
// DataMiner — Abstract base class (template)
// ---------------------------------------------------------------------------
abstract class DataMiner {

    /**
     * Template method: defines the fixed mining pipeline.
     * Subclasses implement the format-specific steps.
     */
    public final void mine(String filePath) {
        openFile(filePath);
        List<String> rawData = extractData();
        List<String> parsedData = parseData(rawData);
        analyseData(parsedData);   // invariant
        sendReport();              // invariant
        closeFile();
    }

    // ---- Format-specific steps (abstract) ----------------------------------

    /** Open / connect to the data source. */
    protected abstract void openFile(String filePath);

    /** Read raw records from the source. */
    protected abstract List<String> extractData();

    /** Clean/validate/transform raw records. */
    protected abstract List<String> parseData(List<String> rawData);

    /** Close the data source. */
    protected abstract void closeFile();

    // ---- Invariant steps (final — no override allowed) ---------------------

    /**
     * Compute summary statistics over the parsed records.
     * This logic is identical regardless of the source format.
     */
    private void analyseData(List<String> data) {
        System.out.println("  [Analyse] Processing " + data.size()
                + " records: " + data);
        // e.g. compute averages, find max/min, etc.
        System.out.println("  [Analyse] Analysis complete.");
    }

    /** Generate and send the report — same for all formats. */
    private void sendReport() {
        System.out.println("  [Report]  Sending report to dashboard...");
        System.out.println("  [Report]  Report sent.");
    }
}

// ---------------------------------------------------------------------------
// CSVDataMiner — handles comma-separated value files
// ---------------------------------------------------------------------------
class CSVDataMiner extends DataMiner {

    private String content; // simulated file content

    @Override
    protected void openFile(String filePath) {
        System.out.println("[CSV] Opening file: " + filePath);
        // Simulate reading a CSV file
        content = "Alice,30,Engineer\nBob,25,Designer\nCarol,35,Manager";
    }

    @Override
    protected List<String> extractData() {
        System.out.println("[CSV] Extracting rows by splitting on newline...");
        return Arrays.asList(content.split("\n"));
    }

    @Override
    protected List<String> parseData(List<String> rawData) {
        System.out.println("[CSV] Parsing: trimming whitespace and validating columns...");
        // Simulate validation: keep only rows with 3 columns
        return rawData.stream()
                .filter(row -> row.split(",").length == 3)
                .map(String::trim)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected void closeFile() {
        System.out.println("[CSV] Closing file handle.");
        content = null;
    }
}

// ---------------------------------------------------------------------------
// JSONDataMiner — handles JSON files
// ---------------------------------------------------------------------------
class JSONDataMiner extends DataMiner {

    private String jsonContent;

    @Override
    protected void openFile(String filePath) {
        System.out.println("[JSON] Opening file: " + filePath);
        // Simulate reading a JSON array (simplified — not using a real parser)
        jsonContent = "[{\"name\":\"Dave\",\"age\":28},"
                    + "{\"name\":\"Eve\",\"age\":32},"
                    + "{\"name\":\"Frank\",\"age\":45}]";
    }

    @Override
    protected List<String> extractData() {
        System.out.println("[JSON] Extracting JSON objects from array...");
        // Naive extraction: split by "},{"
        String stripped = jsonContent.substring(1, jsonContent.length() - 1); // remove [ ]
        return Arrays.asList(stripped.split("},\\{"));
    }

    @Override
    protected List<String> parseData(List<String> rawData) {
        System.out.println("[JSON] Parsing: normalising JSON fragments...");
        return rawData.stream()
                .map(s -> s.replace("{", "").replace("}", "").trim())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected void closeFile() {
        System.out.println("[JSON] Releasing JSON stream.");
        jsonContent = null;
    }
}

// ---------------------------------------------------------------------------
// XMLDataMiner — handles XML files
// ---------------------------------------------------------------------------
class XMLDataMiner extends DataMiner {

    private String xmlContent;

    @Override
    protected void openFile(String filePath) {
        System.out.println("[XML] Opening file: " + filePath);
        // Simulate reading an XML document
        xmlContent = "<employees>"
                   + "<employee><name>Grace</name><age>29</age></employee>"
                   + "<employee><name>Hank</name><age>41</age></employee>"
                   + "</employees>";
    }

    @Override
    protected List<String> extractData() {
        System.out.println("[XML] Extracting <employee> elements...");
        // Naive extraction: split on </employee>
        String inner = xmlContent.replace("<employees>", "")
                                 .replace("</employees>", "");
        return Arrays.asList(inner.split("</employee>"))
                     .stream()
                     .filter(s -> !s.isBlank())
                     .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected List<String> parseData(List<String> rawData) {
        System.out.println("[XML] Parsing: stripping XML tags...");
        return rawData.stream()
                // remove all XML tags
                .map(s -> s.replaceAll("<[^>]+>", " ").trim().replaceAll("\\s+", " "))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected void closeFile() {
        System.out.println("[XML] Closing XML parser.");
        xmlContent = null;
    }
}

// ---------------------------------------------------------------------------
// Main / Demo
// ---------------------------------------------------------------------------
public class RealWorldExample {

    public static void main(String[] args) {

        System.out.println("=== Data Mining — Template Method Pattern Demo ===\n");

        DataMiner csvMiner = new CSVDataMiner();
        System.out.println("--- Mining CSV ---");
        csvMiner.mine("employees.csv");

        System.out.println();

        DataMiner jsonMiner = new JSONDataMiner();
        System.out.println("--- Mining JSON ---");
        jsonMiner.mine("employees.json");

        System.out.println();

        DataMiner xmlMiner = new XMLDataMiner();
        System.out.println("--- Mining XML ---");
        xmlMiner.mine("employees.xml");
    }
}
