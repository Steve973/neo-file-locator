package org.storck.filelocator.service;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.stereotype.Service;
import org.storck.filelocator.model.FileEntry;
import org.storck.filelocator.repository.FileEntryRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.function.BiFunction;

import static org.storck.filelocator.service.ReactiveFileSystemTraverser.ROOT_PARENT;

@Slf4j
@Service
public class FileEntriesProcessor {

    private static final BiFunction<FileEntry, FileEntryRepository, FileEntry> createEdge = (fileEntry, repo) -> {
        String fileEntryPath = fileEntry.getPath();
        String parentPath;
        String parentName;

        // No need to process the root node, it has no parent
        if (!fileEntryPath.equals(ROOT_PARENT)) {
            if (fileEntryPath.equals("/")) {
                // Adjust for the special case when the parent directory of a node is the root "/"
                parentPath = ROOT_PARENT;
                parentName = "/";
            } else {
                int lastDelimAt = fileEntryPath.lastIndexOf('/');
                parentPath = fileEntryPath.substring(0, Math.max(lastDelimAt, 1));
                parentName = fileEntryPath.substring(lastDelimAt + 1);
            }

            FileEntry parentFileEntry = repo.findOneByPathIsAndNameIs(parentPath, parentName);
            if (parentFileEntry != null) {
                fileEntry.getParents().add(parentFileEntry);
                parentFileEntry.getChildren().add(fileEntry);
            } else {
                log.info("Could not create a parent relationship to fileEntryPath: {}, fileEntryName: {}",
                        fileEntryPath, fileEntry.getName());
            }
        }

        return fileEntry;
    };

    final FileEntryRepository fileEntryRepository;

    final Driver neo4jDriver;

    public FileEntriesProcessor(FileEntryRepository fileEntryRepository,
                                Driver neo4jDriver) {
        this.fileEntryRepository = fileEntryRepository;
        this.neo4jDriver = neo4jDriver;
    }

    public void processForRelationships() {
        Flux.fromIterable(fileEntryRepository.findAll())
                .flatMap(fileEntry -> Mono.fromCallable(() -> createEdge.apply(fileEntry, fileEntryRepository))
                        .subscribeOn(Schedulers.boundedElastic()))
                .filter(fileEntry -> !fileEntry.getParents().isEmpty())
                .buffer(500)
                .doOnNext(fileEntryRepository::saveAll)
                .blockLast();
    }
}
