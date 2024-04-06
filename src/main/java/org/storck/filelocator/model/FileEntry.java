package org.storck.filelocator.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Node
public abstract class FileEntry {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String path;

    private Instant lastModifiedTime;

    private Instant lastAccessTime;

    private Instant creationTime;

    private Long size;

    @Relationship(direction = Relationship.Direction.OUTGOING, type = "HAS_PARENT")
    private Set<FileEntry> parents = new HashSet<>();

    @Relationship(direction = Relationship.Direction.INCOMING, type = "HAS_CHILD")
    private Set<FileEntry> children = new HashSet<>();
}
