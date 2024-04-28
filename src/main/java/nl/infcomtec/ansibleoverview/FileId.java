/*
 * Copyright (c) 2024 by Walter Stroebel and InfComTec.
 */
package nl.infcomtec.ansibleoverview;

import java.io.File;

/**
 * File with uid.
 */
public class FileId {

    public final int id;
    public final String path;

    public File getFile() {
        return new File(path);
    }

    public FileId(String path) {
        synchronized (Main.ansFiles) {
            this.id = Main.nextSeq++;
            this.path = path;
            Main.ansFiles.put(id, this);
        }
    }

    public String getId() {
        return "id_" + id;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("\nFileId{");
        sb.append("seq=").append(id);
        sb.append(", path=").append(path);
        sb.append("\n}\n");
        return sb.toString();
    }

}
