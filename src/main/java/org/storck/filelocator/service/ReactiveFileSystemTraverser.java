package org.storck.filelocator.service;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.storck.filelocator.model.*;
import org.storck.filelocator.repository.FileEntryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@Slf4j
@Service
public class ReactiveFileSystemTraverser {

    public static final String ROOT_PARENT = "<N/A>";

    private final SkipPathListGenerator skipPathListGenerator;

    private final AccessiblePathListGenerator accessiblePathListGenerator;

    private final FileEntryRepository fileEntryRepository;


    private final AtomicInteger count = new AtomicInteger(0);

    private final Driver neo4jDriver;

    protected ReactiveFileSystemTraverser(final FileEntryRepository fileEntryRepository,
                                          SkipPathListGenerator skipPathListGenerator,
                                          AccessiblePathListGenerator accessiblePathListGenerator,
                                          Driver neo4jDriver) {
        this.fileEntryRepository = fileEntryRepository;
        this.skipPathListGenerator = skipPathListGenerator;
        this.accessiblePathListGenerator = accessiblePathListGenerator;
        this.neo4jDriver = neo4jDriver;
    }

    final BiConsumer<Collection<FileEntry>, FileEntryRepository> saveFile = (entries, repo) -> {
        repo.saveAll(entries);
        count.addAndGet(entries.size());
        log.info("Batch complete");
    };

    void processPath(Path accessiblePath) {
        try (Stream<Path> accessiblePathStream = Files.list(accessiblePath)) {
            Flux.fromStream(accessiblePathStream)
                    .map(Path::toFile)
                    .filter(File::canRead)
                    .flatMap(f -> Mono.fromCallable(() -> visitFile(f))
                            .subscribeOn(Schedulers.boundedElastic())
                            .filter(Objects::nonNull))
                    .buffer(10000)
                    .handle((relations, synchronousSink) -> saveFile.accept(relations, fileEntryRepository))
                    .blockLast();
        } catch (Exception e) {
            log.error("Error processing file system", e);
        }
    }

    public String updateFileDatabase() {
        count.set(0);
        long start = System.currentTimeMillis();
        try (Session session = neo4jDriver.session()) {
            session.run("MATCH (n) DETACH DELETE n");
        }
        Collection<String> skipPaths = new ArrayList<>(skipPathListGenerator.generateSkipPathList());
        List<Path> accessiblePaths = accessiblePathListGenerator.generateAccessiblePathsList(skipPaths);
        saveFile.accept(List.of(visitFile(new File("/"))), fileEntryRepository);
        try (Stream<Path> pathStream = accessiblePaths.stream()) {
            pathStream.forEach(this::processPath);
        } catch (Exception e) {
            log.error("Error processing file system", e);
        }
        long duration = (System.currentTimeMillis() - start) / 1000;
        return String.format("Count: %d, time: %s seconds", count.get(), duration);
    }

    static FileEntry visitFile(@NonNull File file) {
        FileEntry result = null;
        try {
            BasicFileAttributes attrs = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
            result = createFileEntry(file, attrs);
        } catch (Exception e) {
            log.warn("Unexpected error when processing file", e);
        }
        return result;
    }

    static FileEntry createFileEntry(File file, BasicFileAttributes attrs) {
        FileEntry fileEntry = null;
        FileEntry.FileEntryBuilder<?, ?> fileEntryBuilder = null;
        if (attrs.isDirectory()) {
            fileEntryBuilder = DirectoryNode.builder();
        } else if (attrs.isSymbolicLink()) {
            fileEntryBuilder = LinkNode.builder();
        } else if (attrs.isRegularFile()) {
            fileEntryBuilder = FileNode.builder();
        } else if (attrs.isOther()) {
            fileEntryBuilder = OtherNode.builder();
        }
        if (fileEntryBuilder != null) {
            File parent = file.getParentFile();
            String parentPath = parent != null ? parent.getAbsolutePath() : ROOT_PARENT;
            String name = file.getName();
            fileEntry = fileEntryBuilder.name(parent == null && !StringUtils.hasText(name) ? "/": name)
                    .path(parentPath)
                    .creationTime(Instant.ofEpochMilli(attrs.creationTime().toMillis()))
                    .lastAccessTime(Instant.ofEpochMilli(attrs.lastAccessTime().toMillis()))
                    .lastModifiedTime(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()))
                    .size(attrs.size())
                    .build();
        }
        return fileEntry;
    }
}
