package org.storck.filelocator.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.storck.filelocator.repository.FileEntryRepository;

import java.io.File;
import java.util.List;

@Slf4j
@Service
public class FileSearchService {

    final FileEntryRepository fileEntryRepository;

    public FileSearchService(final FileEntryRepository fileEntryRepository) {
        this.fileEntryRepository = fileEntryRepository;
    }

    public List<String> searchByRegex(String start, String regex) {
        String nodePath = start == null || start.isEmpty() ? "/" : start;
        log.info("Starting at '{}', and searching with expression '{}'", nodePath, regex);
        return fileEntryRepository.searchFilesByNameInGraph(regex).stream()
                .map(fe -> String.format("%s%s%s", fe.getPath(), File.separator, fe.getName()))
                .sorted()
                .toList();
    }
}
