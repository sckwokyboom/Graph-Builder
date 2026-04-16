package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.io.File;
import java.util.stream.Collectors;

class FileCollector {
    private String dirName;
    private final List<File> files = new ArrayList<>();

    public void collect(Path directory) {
        dirName = directory.getFileName().toString();
        List<Path> filePathes = new ArrayList<>();
        for (Path filePathe : filePathes) {
            files.add(filePathe.toFile());
        }
    }
}

class StreamFileCollector {
    public void collect(Path directory, int depth) {
        List<Path> filePathes = new ArrayList<>();
        Stream<Path> stream = filePathes.stream();
        stream.forEach(path -> {
                collect(path, depth - 1);
            });
    }
}
