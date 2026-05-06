package ru.nsu.fit.crackhash.manager.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nsu.fit.crackhash.manager.model.FileNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/fs")
public class FileController {

    @Value("${crackhash.project.path:/project}")
    private String projectRoot;

    @GetMapping("/tree")
    public ResponseEntity<List<FileNode>> getTree(@RequestParam(value = "path", required = false) String path) {
        String targetPath = (path == null || path.isEmpty()) ? projectRoot : projectRoot + "/" + path;
        File dir = new File(targetPath);
        
        List<FileNode> nodes = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                // sort directories first
                Arrays.sort(files, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                for (File f : files) {
                    if (f.isDirectory()) {
                        File current = f;
                        StringBuilder nameBuilder = new StringBuilder(current.getName());
                        // Compress single-child directories (VS Code style)
                        while (true) {
                            File[] children = current.listFiles();
                            if (children != null && children.length == 1 && children[0].isDirectory()) {
                                current = children[0];
                                nameBuilder.append("\\").append(current.getName());
                            } else {
                                break;
                            }
                        }
                        
                        String relPath = current.getAbsolutePath().substring(new File(projectRoot).getAbsolutePath().length());
                        if (relPath.startsWith("/") || relPath.startsWith("\\")) {
                            relPath = relPath.substring(1);
                        }
                        relPath = relPath.replace('\\', '/');
                        nodes.add(new FileNode(nameBuilder.toString(), relPath, true));
                    } else {
                        String relPath = f.getAbsolutePath().substring(new File(projectRoot).getAbsolutePath().length());
                        if (relPath.startsWith("/") || relPath.startsWith("\\")) {
                            relPath = relPath.substring(1);
                        }
                        relPath = relPath.replace('\\', '/');
                        nodes.add(new FileNode(f.getName(), relPath, false));
                    }
                }
            }
        }
        return ResponseEntity.ok(nodes);
    }

    @GetMapping(value = "/content", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> getContent(@RequestParam("path") String path) {
        try {
            // security: extremely basic, prevent path traversal
            if (path.contains("..")) return ResponseEntity.badRequest().body("Invalid path");
            
            File file = new File(projectRoot + "/" + path);
            if (file.exists() && file.isFile()) {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, StandardCharsets.UTF_8);
                // Heuristic: If UTF-8 parsing fails and produces replacement characters, fallback to Windows-1251
                if (content.contains("\uFFFD")) {
                    content = new String(bytes, Charset.forName("windows-1251"));
                }
                return ResponseEntity.ok(content);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error reading file");
        }
    }
}
