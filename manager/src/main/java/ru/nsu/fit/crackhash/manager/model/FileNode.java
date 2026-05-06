package ru.nsu.fit.crackhash.manager.model;

public class FileNode {
    private String name;
    private String path;
    private boolean isDirectory;

    public FileNode(String name, String path, boolean isDirectory) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public boolean getIsDirectory() { return isDirectory; }
    public void setIsDirectory(boolean isDirectory) { this.isDirectory = isDirectory; }
}
