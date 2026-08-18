package com.tastenotebook.repository;

import com.tastenotebook.domain.PhraseKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PhraseKeywordRepository extends JpaRepository<PhraseKeyword, Long> {
    // Longest phrase first (by character length) so "rất thích" is tried before "thích".
    List<PhraseKeyword> findAllByOrderByPriorityDesc();
}
