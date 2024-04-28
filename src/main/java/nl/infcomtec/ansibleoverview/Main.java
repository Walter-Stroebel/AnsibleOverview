/*
 * Copyright (c) 2024 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.ansibleoverview;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 *
 * @author walter
 */
public class Main {

    /**
     * Global Ansible configuration.
     */
    public static final File ETC_ANSIBLE_CFG = new File("/etc/ansible/ansible.cfg");
    /**
     * Global Ansible inventory.
     */
    public static final File ETC_INVENTORY = new File("/etc/ansible/hosts");
    /**
     * Local Ansible configuration (overrides global).
     */
    public static final File HOME_ANSIBLE_CFG = new File(System.getProperty("user.home"), ".ansible.cfg");
    /**
     * For Future Use: application settings
     */
    public static final File HOME_APPLICATION_CFG = new File(System.getProperty("user.home"), ".ansover.properties");
    /**
     * For Future Use: application settings
     */
    public static final Properties props = new Properties();

    public static int nextSeq = 37;
    public static final TreeMap<Integer, FileId> ansFiles = new TreeMap<>();
    public static final TreeMap<String, List<Variable>> ansVars = new TreeMap<>();
    public static final TreeMap<String, Set<String>> ansGroups = new TreeMap<>();
    private static final String[] ROLE_PARTS = {"tasks", "handlers", "defaults", "vars", "files", "templates", "meta", "library", "tests"};
    /**
     * Proper HTML line ending
     */
    public static String EOLN = "\r\n";
    public static String NAVBAR = "<nav>\r\n" + "    <ul>\r\n" + "        <li><a href=\"#playbooks\">Playbooks</a></li>\r\n" + "        <li><a href=\"#roles\">Roles</a></li>\r\n" + "        <li><a href=\"#variables\">Variables</a></li>\r\n" + "    </ul>\r\n" + "</nav>\r\n";
    public static String PLAYBOOKS = "<section id=\"playbooks\">\r\n" + "<h1>Playbooks</h1>\r\n";
    public static String VARS = "<section id=\"variables\">\r\n" + "<h1>Variables</h1>\r\n";
    public static String ROLES = "<section id=\"roles\">\r\n" + "<h1>Roles</h1>\r\n";

    public static void main(String[] args) {
        if (HOME_APPLICATION_CFG.exists()) {
            try (FileInputStream fis = new FileInputStream(HOME_APPLICATION_CFG)) {
                props.load(fis);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Error loading properties", ex);
            }
        }
        new Main().init(args);
    }

    public TreeMap<String, String> playbooks = new TreeMap<>();
    public TreeMap<String, TreeMap<String, TreeMap<String, String>>> roleFiles = new TreeMap<>();
    public FileId source;
    private final List<File> roles = new LinkedList<>();

    public static void addHost(String group, String host) {
        if (null != group && null != host) {
            Set<String> get = ansGroups.get(group);
            if (null == get) {
                get = new TreeSet<>();
                ansGroups.put(group, get);
            }
            get.add(host);
        }
    }

    private void init(String[] args) {
        PrintStream out = System.out;
        List<String> directories = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 < args.length) {
                        try {
                            out = new PrintStream(args[i + 1]);
                        } catch (FileNotFoundException ex) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "-o: " + args[i + 1], ex);
                        }
                        i++;  // Skip next argument since it's the output path
                    } else {
                        Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Output path specified but no path provided.");
                        return;
                    }
                    break;
                default:
                    directories.add(args[i]);
                    break;
            }
        }
        if (directories.isEmpty()) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "No directories provided to analyze.");
            return;
        }
        scanStandard();
        scan(directories);
        outputHTML(out);
    }

    private void outputHTML(PrintStream out) {
        out.print("<html><body>");
        out.print(EOLN);
        out.print(NAVBAR);
        out.print(PLAYBOOKS);
        out.print("<table border=\"1\">");
        out.print(EOLN);
        for (Map.Entry<String, String> e1 : playbooks.entrySet()) {
            out.format("<tr><td><h2>%s</h2></td>%s<td>%s</td></tr>%s",
                    e1.getKey(), EOLN,
                    pre(e1.getValue()), EOLN);
        }
        out.print("</table></section>");
        out.print(EOLN);
        out.print(NAVBAR);
        out.print(ROLES);
        for (Map.Entry<String, TreeMap<String, TreeMap<String, String>>> e1 : roleFiles.entrySet()) {
            out.print("<table border=\"1\"><tr><th>Role</th><th>Section</th><th>Filename</th><th>Contents</th></tr>");
            boolean f1 = true;
            for (Map.Entry<String, TreeMap<String, String>> e2 : e1.getValue().entrySet()) {
                boolean f2 = true;
                for (Map.Entry<String, String> e3 : e2.getValue().entrySet()) {
                    out.format("<tr><td><h2>%s</h2></td>%s", f1 ? e1.getKey() : "", EOLN);
                    out.format("<td><h3>%s</h3></td>%s", f2 ? e2.getKey() : "", EOLN);
                    out.format("<td>%s</td><td>%s</td></tr>%s", e3.getKey(), pre(e3.getValue()), EOLN);
                    f1 = f2 = false;
                }
            }
            out.print("</table><p>&nbsp;</p>");
        }
        out.print("</section>");
        out.print(EOLN);
        out.print(NAVBAR);
        out.print(VARS);
        out.format("<table border=\"1\">%s", EOLN);
        out.format("<tr><th>Name</th><th width=\"40%%\">Value</th><th>Role</th><th>Host</th><th>Group</th><th width=\"20%%\">File</th></tr>%s", EOLN);
        for (Map.Entry<String, List<Variable>> e : ansVars.entrySet()) {
            List<Variable> lv = e.getValue();
            boolean f1 = true;
            for (Variable v : lv) {
                out.format("<tr><td>%s</td>", f1 ? e.getKey() : "");
                f1 = false;
                out.format("<td>%s</td>", Utils.html(v.value));
                out.format("<td>%s</td>", (null == v.role) ? "&nbsp" : Utils.html(v.role));
                out.format("<td>%s</td>", (null == v.host) ? "&nbsp" : Utils.html(v.host));
                out.format("<td>%s</td>", (null == v.group) ? "&nbsp" : Utils.html(v.group));
                out.format("<td>%s</td>", Utils.html(v.fileId.path));
                out.format("</tr>%s", EOLN);
            }
        }
        out.print("</table></section>");
        out.print(EOLN);
        out.print("</body></html>");
        out.print(EOLN);
    }

    private void scanStandard() {
        if (ETC_INVENTORY.exists()) {
            inventoryParser(new FileId(ETC_INVENTORY.getAbsolutePath()));
        }
        if (ETC_ANSIBLE_CFG.exists()) {
            FileId fileId = new FileId(ETC_ANSIBLE_CFG.getAbsolutePath());
            try {
                Ini ini = new Ini(ETC_ANSIBLE_CFG);
                for (Map.Entry<String, Profile.Section> e1 : ini.entrySet()) {
                    for (Map.Entry<String, String> e2 : e1.getValue().entrySet()) {
                        String key = e2.getKey();
                        String val = e2.getValue();
                        Variable.putVar(key, null, null, null, fileId, val);
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (HOME_ANSIBLE_CFG.exists()) {
            FileId fileId = new FileId(HOME_ANSIBLE_CFG.getAbsolutePath());
            try {
                Ini ini = new Ini(HOME_ANSIBLE_CFG);
                for (Map.Entry<String, Profile.Section> e1 : ini.entrySet()) {
                    for (Map.Entry<String, String> e2 : e1.getValue().entrySet()) {
                        String key = e2.getKey();
                        String val = e2.getValue();
                        Variable.putVar(key, null, null, null, fileId, val);
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void scan(List<String> directories) {
        for (String path : directories) {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "{0} is not valid.", path);
                return;
            }
            File[] files = dir.listFiles();
            if (null != files) {
                for (File f : files) {
                    if (f.getName().equals("hosts")) {
                        FileId fileId = new FileId(f.getAbsolutePath());
                        inventoryParser(fileId);
                    } else if (f.getName().equals("ansible.cfg")) {
                        FileId fileId = new FileId(f.getAbsolutePath());
                        try {
                            Ini ini = new Ini(f);
                            for (Map.Entry<String, Profile.Section> e1 : ini.entrySet()) {
                                for (Map.Entry<String, String> e2 : e1.getValue().entrySet()) {
                                    String key = e2.getKey();
                                    String val = e2.getValue();
                                    Variable.putVar(key, null, null, null, fileId, val);
                                }
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else if (f.getName().endsWith(".yml")) {
                        FileId fileId = new FileId(f.getAbsolutePath());
                        try {
                            playbooks.put(f.getName(), Files.readString(f.toPath()));
                        } catch (IOException ex) {
                            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else if (f.getName().equals("host_vars")) {
                        File[] sub = f.listFiles();
                        if (null != sub) {
                            for (File f2 : sub) {
                                String host = f2.getName().replace(".yml", "");
                                importVars(null, host, null, f2);
                            }
                        }
                    } else if (f.getName().equals("group_vars")) {
                        File[] sub = f.listFiles();
                        if (null != sub) {
                            for (File f2 : sub) {
                                String grp = f2.getName().replace(".yml", "");
                                importVars(null, null, grp, f2);
                            }
                        }
                    } else if (f.getName().equals("roles")) {
                        roles.addAll(Arrays.asList(f.listFiles()));
                    } else if (f.getName().startsWith(".")) {
                        // ignore
                    } else {
                        System.err.println("? " + f);
                    }
                }
            }
            for (File f : roles) {
                if (f.isDirectory()) {
                    String role = f.getName();
                    for (String part : ROLE_PARTS) {
                        File partDir = new File(f, part);
                        if (partDir.exists()) {
                            switch (part) {
                                case "vars":
                                case "defaults":
                                    break;
                                default: {
                                    readFiles(role, partDir);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Should handle common Ansible inventory files.
     *
     * @param fileId File to parse.
     */
    public void inventoryParser(FileId fileId) {
        source = fileId;
        char firstCharacter = Utils.firstChar(fileId.getFile());

        switch (firstCharacter) {
            case '[':
                parseIniHybrid(fileId);
                break;
            default:
                parseYaml(fileId);
                break;
        }
    }

    public String pre(String text) {
        StringBuilder sb = new StringBuilder(EOLN).append("<pre>").append(EOLN);
        int last = 0;
        while (last < text.length()) {
            int open = text.indexOf("{{", last);
            if (open >= 0) {
                int close = text.indexOf("}}", open);
                if (close > 0) {
                    String varName = text.substring(open + 2, close).trim();
                    sb.append(Utils.html(text.substring(last, open)));
                    sb.append("<b>{{ ").append(varName).append(" }}</b>");
                    last = close + 2;
                } else {
                    sb.append("{{");
                    last = open + 2;
                }
            } else {
                sb.append(Utils.html(text.substring(last)));
                last = text.length();
            }
        }
        sb.append(EOLN).append("</pre>").append(EOLN);
        return sb.toString();
    }

    private void importVars(String role, String host, String group, File f) {
        Yaml yaml = new Yaml(new Constructor(Map.class));
        try (FileInputStream inputStream = new FileInputStream(f)) {
            Object data = yaml.load(inputStream);
            FileId fileId = new FileId(f.getAbsolutePath());

            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> d2 = (Map<String, Object>) data;
                for (Map.Entry<String, Object> e : d2.entrySet()) {
                    Variable.putVar(e.getKey(), role, host, group, fileId, e.getValue().toString());
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Failed to load YAML file: " + f, ex);
        }
    }

    private void readFiles(String role, File partDir) {
        File[] sub = partDir.listFiles();
        if (null == sub) {
            return;
        }
        for (File f : sub) {
            try {
                String text = Files.readString(f.toPath());
                putRoleFile(role, partDir.getName(), f.getName(), text);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void putRoleFile(String role, String part, String name, String text) {
        TreeMap<String, TreeMap<String, String>> roleMap = roleFiles.get(role);
        if (null == roleMap) {
            roleMap = new TreeMap<>();
            roleFiles.put(role, roleMap);
        }
        TreeMap<String, String> partMap = roleMap.get(part);
        if (null == partMap) {
            partMap = new TreeMap<>();
            roleMap.put(part, partMap);
        }
        partMap.put(name, text);
    }

    private void parseIniHybrid(FileId fileId) {
        String currentGroup = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(fileId.getFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("[") && line.endsWith("]")) {
                    currentGroup = line.substring(1, line.length() - 1);
                } else if (!line.isEmpty() && currentGroup != null) {
                    StringTokenizer toker = new StringTokenizer(line);
                    String host = toker.nextToken();
                    addHost(currentGroup, host);
                    while (toker.hasMoreTokens()) {
                        String var = toker.nextToken();
                        // abusing Ini to parse one var in a fake section
                        Ini ini = new Ini(new StringReader("[s]\n" + var));
                        for (Map.Entry<String, Profile.Section> e1 : ini.entrySet()) {
                            for (Map.Entry<String, String> e2 : e1.getValue().entrySet()) {
                                String key = e2.getKey();
                                String val = e2.getValue();
                                Variable.putVar(key, null, host, currentGroup, fileId, val);
                            }
                        }
                    }
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void parseYaml(FileId fileId) {
        Yaml yaml = new Yaml(new Constructor(Map.class));
        try (FileInputStream inputStream = new FileInputStream(fileId.getFile())) {
            Object data = yaml.load(inputStream);
            if (data instanceof Map) {
                @SuppressWarnings(value = "unchecked")
                Map<String, Object> d2 = (Map<String, Object>) data;
                parseYamlObject(d2, null);
            }
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, "Failed to load YAML file: " + fileId.path, ex);
        }
    }

    @SuppressWarnings(value = {"unchecked"})
    private void parseYamlObject(Map<String, Object> map, String parentGroup) {
        if (map.size() == 1 && null != map.get("all")) {
            parseYamlObject((Map<String, Object>) map.get("all"), "all");
        } else {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (key.equals("hosts") && value instanceof Map) {
                    // This part of the map contains host definitions
                    Map<String, Object> hosts = (Map<String, Object>) value;
                    for (String host : hosts.keySet()) {
                        @SuppressWarnings(value = "unchecked")
                        Map<String, Object> hostDetails = (Map<String, Object>) hosts.get(host);
                        for (Map.Entry<String, Object> hostDetail : hostDetails.entrySet()) {
                            Variable.putVar(hostDetail.getKey(), null, host, parentGroup, source, hostDetail.getValue().toString());
                        }
                    }
                } else if (key.equals("children") && value instanceof Map) {
                    // This part of the map defines child groups
                    Map<String, Object> children = (Map<String, Object>) value;
                    for (String groupName : children.keySet()) {
                        parseYamlObject((Map<String, Object>) children.get(groupName), groupName);
                    }
                } else if (key.equals("vars") && parentGroup != null && value instanceof Map) {
                    // Handle group variables (not implemented here, depends on needs)
                }
            }
        }
    }
}
