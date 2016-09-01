package org.apache.maven.siteindexer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.maven.plugin.logging.Log;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Indexer {

    private static final String SEARCHBOX_FILENAME = "searchbox.html";

    private String startDir;
    private final Log log;

    public Indexer(Log log) {
        this.log = log;
    }

    private String relativeToStart(String filename) {
        return filename.replaceAll("\\\\", "\\/").substring(startDir.length());
    }

    private String clean(String textContent) {
        return textContent
                .replaceAll("\\.", " ")
                .replaceAll("\\\"", " ")
                .replaceAll("\\\\", " ")
                .replaceAll("\\/", " ")
                .replaceAll("\\'", " ")
                .replaceAll("\\-", " ")
                .replaceAll("\\.", " ")
                .replaceAll("\\:", " ")
                .replaceAll("\\;", " ")
                .replaceAll("\\!", " ")
                .replaceAll("\\?", " ")
                .replaceAll("\\|", " ")
                .replaceAll("\\(", " ")
                .replaceAll("\\)", " ")
                .replaceAll("\\[", " ")
                .replaceAll("\\]", " ")
                .replaceAll("\\{", " ")
                .replaceAll("\\}", " ")
                .replaceAll("\\$", " ")
                .replaceAll("\\=", " ")
                .replaceAll("\\+", " ")
                .replaceAll("\\*", " ")
                .replaceAll("\\^", " ")
                .replaceAll("\\~", " ")
                .replaceAll("\\©", " ")
                .replaceAll("\\,", " ");
    }

    private void tokenizeText(String textContent, FileOutputStream out) throws IOException {
        String clean = clean(textContent);
        StringTokenizer st = new StringTokenizer(clean);
        while (st.hasMoreTokens()) {
            out.write(st.nextToken().getBytes());
            out.write(" ".getBytes());
        }
    }

    private final String signature = "<!-- Search box courtesy of Maven Site Indexer -->";

    private void addTags(File file, File baseDir) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = "", oldText = "";
            while ((line = reader.readLine()) != null) {
                oldText += line + "\r\n";
            }
            reader.close();

            if (oldText.indexOf("id=\"searchbox\"") > 0) {
                log.info("tags already added to '" + file.getName() + "', quitting");
                return;
            }

            int level = findRelativeDirecotryLevel(file, baseDir);
            String relativeSearchBoxUrl = prependNewStringToExisting(SEARCHBOX_FILENAME, "../", level);

            log.info("applying tags to '" + file.getName() + "'");

            String newText = oldText.replaceAll("</body>",
                    "<div id=\"searchbox\">"
                    + "  <iframe id=\"searchbox-frame\" src=\"" + relativeSearchBoxUrl + "\" width=\"100%\" style=\"border: 0\" height=\"100%\">"
                    + "  </iframe>"
                    + "</div>"
                    + signature
                    + "</body>"
            );

            FileWriter writer = new FileWriter(file);
            writer.write(newText);
            writer.close();
            log.info("applied tags to '" + file.getName() + "'");
        } catch (IOException ioe) {
            log.error(ioe);
        }
    }

    private void parseDocument(String filename, FileOutputStream out) throws IOException {
        log.info("indexing '" + relativeToStart(filename) + "'...");
        out.write("var d = new LADDERS.search.document();\r\n".getBytes());
        out.write(("d.add(\"id\", '" + relativeToStart(filename) + "');\r\n").getBytes());
        out.write("d.add(\"text\", \"".getBytes());

        Document doc;
        try {
            doc = Jsoup.parse(new File(filename), "utf-8");
            tokenizeText(doc.text(), out);
            out.write("\");\r\n".getBytes());
            out.write(("d.add(\"title\", '" + doc.title() + "');\r\n").getBytes());
            out.write(
                    ("titles.add(\"" + relativeToStart(filename) + "\", \"" + doc.title() + "\");\r\n").getBytes()
            );
        } catch (IOException e) {
            log.error(e);
        }
        out.write("index.addDocument(d);\r\n\r\n".getBytes());
        log.info("done indexing '" + relativeToStart(filename) + "'");
    }

    private void crawlFolder(String dirName, FileOutputStream out) throws IOException {
        log.info("crawling folder '" + dirName + "'...");
        File baseDir = new File(dirName);

        List<File> files = new ArrayList<File>();
        findListedFiles(baseDir, files, new String[]{"html", "htm"}, true);

        if (!files.isEmpty()) {
            for (File file : files) {
                if (!SEARCHBOX_FILENAME.equals(file.getName())) {
                    log.info("found file " + file.getAbsolutePath());
                    parseDocument(file.getAbsolutePath(), out);
                    addTags(file, baseDir);
                }
            }
        }
        log.info("done with folder '" + dirName + "'");
    }

    public void buildIndex(String startDir, String outputFile) throws IOException {
        File file = new File(outputFile);
        FileOutputStream out = new FileOutputStream(file);
        //LADDER search reference:
        //http://dev.theladders.com/archives/2006/11/introducing_javascript_fulltex_1.html
        out.write("var index = new LADDERS.search.index();\r\n".getBytes());
        out.write("var titles = new LADDERS.search.document();\r\n".getBytes());
        log.info("index.js initialized");
        this.startDir = new File(startDir).getAbsolutePath() + File.separator;
        crawlFolder(startDir, out);
    }

    public int findRelativeDirecotryLevel(File file, File baseDir) {
        String fullFilePath = file.getAbsolutePath();
        String relativeFilePath = fullFilePath.substring(baseDir.getAbsolutePath().length());
        int level = countOccurrences(relativeFilePath, File.separatorChar) - 1;
        return level;
    }

    public String prependNewStringToExisting(String exisitingString, String newString, int number) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < number; i++) {
            builder.append(newString);
        }
        builder.append(exisitingString);
        return builder.toString();
    }

    private int countOccurrences(String haystack, char needle) {
        int count = 0;
        for (int i = 0; i < haystack.length(); i++) {
            if (haystack.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    private void findListedFiles(File directory, final List<File> foundFiles, final String[] filterTypes, final boolean withSubdirectories) {
        FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                File child = new File(dir.getAbsolutePath() + File.separator + name);
                String fileName = child.getName().toLowerCase();
                boolean retVal = child.isDirectory();
                if (child.isFile()) {
                    if (filterTypes == null) {
                        retVal = true;
                    } else {
                        for (String type : filterTypes) {
                            if (fileName.endsWith(type)) {
                                retVal = true;
                                break;
                            }
                        }
                    }
                }
                return retVal;
            }
        };

        File[] fileList = directory.listFiles(filter);

        for (File file : fileList) {
            if (file.isFile()) {
                foundFiles.add(file);
            } else if (file.isDirectory() && withSubdirectories) {
                findListedFiles(file, foundFiles, filterTypes, withSubdirectories);
            }
        }
    }

}
