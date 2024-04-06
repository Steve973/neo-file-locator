package org.storck.filelocator.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.storck.filelocator.model.FileEntry;

import java.util.Collection;

public interface FileEntryRepository extends Neo4jRepository<FileEntry, Long> {

    FileEntry findOneByPathIsAndNameIs(String path, String name);

    @Query("MATCH (n) WHERE n.name =~ $nameRegex RETURN n")
    Collection<FileEntry> searchFilesByNameInGraph(@Param("nameRegex") String nameRegex);
}
