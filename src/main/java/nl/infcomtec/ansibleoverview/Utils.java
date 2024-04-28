/*
 * Copyright (c) 2024 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.ansibleoverview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTML and other utilities.
 *
 * @author walter
 */
public class Utils {


    /**
     * Method meant to figure out what "type" of Ansible file we have by
     * checking the first non-whitespace character.
     *
     * @param path File to scan.
     * @return The first non-whitespace character if found, otherwise '?'.
     */
    public static char firstChar(File path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            int intChar;
            while ((intChar = reader.read()) != -1) { // Read character by character
                char ch = (char) intChar;
                if (!Character.isWhitespace(ch)) {
                    return ch; // Return the first non-whitespace character
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, "Failed to read file: " + path, ex);
        }
        return '?'; // Return '?' if no non-whitespace character is found or in case of an error
    }

    public static String section(String section, List<String> items) {
        StringBuilder sb = new StringBuilder(section);
        for (String s : items) {
            sb.append(Main.EOLN); // extra EOLN is no issue, presentation layer will eat them
            sb.append(s);
        }
        sb.append(Main.EOLN).append("</section>").append(Main.EOLN);
        return sb.toString();
    }

    /**
     * Converts a string to a HTML entity encoded string, preserving any
     * existing entities. This method properly encodes a string like
     * &lt;&amp;EURO;&gt; to &amp;lt;&amp;EURO;&amp;gt;.
     *
     * @param text Text with potential &lt;,&gt;, " or &amp; to encode.
     * @return The text with any &lt;,&gt;, " or &amp; converted to &amp;lt;,
     * &amp;gt;, &amp;quot; and &amp;amp; while preserving any occurrences of
     * &amp;any;.
     */
    public static String html(String text) {
        if (text == null) {
            return "";
        }
        int amp = text.indexOf('&');
        if (amp >= 0) {
            int semi = text.indexOf(';', amp);
            if (semi > amp && semi - amp < 7) { // seems a valid html entity
                StringBuilder sb = new StringBuilder();
                if (amp > 0) {
                    sb.append(html(text.substring(0, amp)));
                }
                sb.append(text.substring(amp, semi));
                if (semi < text.length() - 1) {
                    sb.append(html(text.substring(semi + 1)));
                }
                return sb.toString();
            }
        }
        StringBuilder ret = new StringBuilder();
        for (char c : text.toCharArray()) {
            ret.append(htmlChar(c));
        }
        return ret.toString();
    }

    /**
     * Translates needed characters to entities.
     *
     * @param c Possibly dangerous character.
     * @return The character as a safe string.
     */
    public static String htmlChar(char c) {
        switch (c) {
            case '"':
                return ("&quot;");
            case '&':
                return ("&amp;");
            case '<':
                return ("&lt;");
            case '>':
                return ("&gt;");
            case '€':
                return ("&euro;");
            default:
                return (Character.toString(c));
        }
    }

}
