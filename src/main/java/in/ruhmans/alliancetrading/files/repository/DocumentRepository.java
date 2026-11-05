package in.ruhmans.alliancetrading.files.repository;

import java.util.UUID;

import in.ruhmans.alliancetrading.files.domain.Document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
